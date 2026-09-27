package com.example.hospital.advanced;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import static com.example.hospital.advanced.AdvancedValues.*;

@Component
@ConditionalOnProperty(name="app.queue.enabled",havingValue="true")
public class QueueWorker {
    private static final Logger log=LoggerFactory.getLogger(QueueWorker.class);
    private final AdvancedMapper mapper;
    private final RedisQueueGateway redis;
    private final QueueProcessor processor;
    private final TransactionTemplate tx;
    private final Clock clock;
    private volatile long lastWarningAt;
    public QueueWorker(AdvancedMapper mapper,RedisQueueGateway redis,QueueProcessor processor,
            PlatformTransactionManager transactionManager,Clock clock) {
        this.mapper=mapper; this.redis=redis; this.processor=processor;
        this.tx=new TransactionTemplate(transactionManager); this.clock=clock;
    }
    @Scheduled(fixedDelayString="${app.queue.dispatch-delay-ms:1000}")
    public void dispatch() {
        try {
            LocalDateTime now=LocalDateTime.now(clock);
            // Expiry progresses even while Redis is down. No expired task can acquire a number later.
            tx.executeWithoutResult(status->mapper.expireRequests(now));
            List<Long> due=mapper.dueRequests(now,now.minusSeconds(30));
            if(due.isEmpty()) return;
            redis.ensureAvailable();
            for(long id:due) tx.executeWithoutResult(status->{
                Map<String,Object> row=mapper.lockRequest(id);
                LocalDateTime current=LocalDateTime.now(clock);
                if(row==null || terminal(row) || !time(row.get("expires_at")).isAfter(current)) return;
                if(time(row.get("next_attempt_at")).isAfter(current)) return;
                Object delivered=row.get("last_delivered_at");
                if(delivered!=null && time(delivered).isAfter(current.minusSeconds(30))) return;
                // A crash after XADD but before the DB commit may duplicate delivery, never lose a task.
                String streamId=redis.publish(id);
                mapper.delivered(id,streamId,current);
            });
        } catch(RuntimeException e) { warn(e); }
    }
    @Scheduled(fixedDelayString="${app.queue.poll-delay-ms:300}")
    public void poll() {
        try { redis.ensureAvailable(); consume(redis.read()); }
        catch(RuntimeException e) { warn(e); }
    }
    @Scheduled(fixedDelayString="${app.queue.reclaim-delay-ms:5000}")
    public void recoverPending() {
        try { redis.ensureAvailable(); consume(redis.reclaim()); }
        catch(RuntimeException e) { warn(e); }
    }
    private void consume(List<MapRecord<String,String,String>> records) {
        for(MapRecord<String,String,String> message:records) {
            String value=message.getValue().get("request_id");
            if(value==null || !value.matches("[0-9]{1,18}")) {
                log.warn("Discarding malformed queue transport message {}",message.getId());
                redis.acknowledge(message.getId()); continue;
            }
            if(processor.process(Long.parseLong(value))) redis.acknowledge(message.getId());
        }
    }
    private void warn(RuntimeException e) {
        long now=clock.millis();
        if(now-lastWarningAt>30000) { lastWarningAt=now; log.warn("Queue transport/worker unavailable; MySQL outbox and deadlines remain authoritative",e); }
    }
}
