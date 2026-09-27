package com.example.hospital.advanced;

import com.example.hospital.business.RegistrationService;
import com.example.hospital.common.BizException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import static com.example.hospital.advanced.AdvancedValues.*;

@Service
public class QueueProcessor {
    private static final Logger log=LoggerFactory.getLogger(QueueProcessor.class);
    private final AdvancedMapper mapper;
    private final RegistrationService registrations;
    private final TransactionTemplate tx;
    private final Clock clock;
    private final int maxAttempts;
    public QueueProcessor(AdvancedMapper mapper,RegistrationService registrations,PlatformTransactionManager transactionManager,
            Clock clock,@Value("${app.queue.max-attempts:3}") int maxAttempts) {
        this.mapper=mapper; this.registrations=registrations; this.tx=new TransactionTemplate(transactionManager);
        this.clock=clock; this.maxAttempts=Math.max(1,Math.min(10,maxAttempts));
    }
    /** True means a terminal result is committed and the Redis message may be acknowledged. */
    public boolean process(long id) {
        try {
            return Boolean.TRUE.equals(tx.execute(status->{
                Map<String,Object> row=mapper.lockRequest(id);
                if(row==null) return true; // No task exists for this malformed/orphaned transport message.
                if(terminal(row)) { mapper.duplicate(id); return true; }
                LocalDateTime now=LocalDateTime.now(clock);
                if(!time(row.get("expires_at")).isAfter(now)) {
                    failure(row,"EXPIRED","QUEUE_EXPIRED","排队请求已超时，请重新选择排班",0,now); return true;
                }
                if(time(row.get("next_attempt_at")).isAfter(now)) return false;
                mapper.processing(id,now);
                // Both the registration and this terminal task update use the same MySQL transaction.
                // The request deadline is rechecked after business row locks have been acquired.
                Map<String,Object> registration=registrations.createFor(number(row,"patient_id"),number(row,"schedule_id"),
                    text(row,"request_key"),time(row.get("expires_at")));
                mapper.succeeded(id,number(registration,"id"),LocalDateTime.now(clock));
                return true;
            }));
        } catch(RuntimeException e) {
            // Do not swallow a business exception in its transaction: rollback is complete before
            // recording a retry/failure in a second transaction. Database outages leave Redis pending.
            if(!(e instanceof BizException)) log.warn("Queue task {} failed; applying bounded retry",id,e);
            return Boolean.TRUE.equals(tx.execute(status->{
                Map<String,Object> row=mapper.lockRequest(id);
                if(row==null || terminal(row)) return true;
                LocalDateTime now=LocalDateTime.now(clock);
                if(!time(row.get("expires_at")).isAfter(now)) {
                    failure(row,"EXPIRED","QUEUE_EXPIRED","排队请求已超时，请重新选择排班",1,now); return true;
                }
                if(e instanceof BizException) {
                    failure(row,"FAILED","BUSINESS_REJECTED",safeMessage(e.getMessage()),1,now); return true;
                }
                int attempts=(int)number(row,"attempts")+1;
                if(attempts>=maxAttempts) {
                    failure(row,"FAILED","RETRY_EXHAUSTED","暂时无法完成挂号，技术重试已耗尽",1,now); return true;
                }
                failure(row,"RETRY","TEMPORARY_ERROR","系统繁忙，正在自动重试",1,now.plusSeconds(Math.min(20,attempts*3L)));
                return false;
            }));
        }
    }
    private String safeMessage(String message) { return message==null ? "挂号条件不满足" : message.substring(0,Math.min(500,message.length())); }
    private void failure(Map<String,Object> row,String state,String code,String message,int increment,LocalDateTime next) {
        Map<String,Object> values=new HashMap<>(); values.put("id",number(row,"id")); values.put("status",state);
        values.put("error_code",code); values.put("error_message",message); values.put("attempt_increment",increment);
        values.put("next_attempt_at",next); values.put("now",LocalDateTime.now(clock)); mapper.failed(values);
    }
}
