package com.example.hospital.advanced;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Range;
import org.springframework.data.redis.connection.stream.Consumer;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.PendingMessage;
import org.springframework.data.redis.connection.stream.PendingMessages;
import org.springframework.data.redis.connection.stream.ReadOffset;
import org.springframework.data.redis.connection.stream.RecordId;
import org.springframework.data.redis.connection.stream.StreamOffset;
import org.springframework.data.redis.connection.stream.StreamReadOptions;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.StreamOperations;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Redis contains delivery messages only; business state is durable in MySQL. */
@Component
public class RedisQueueGateway {
    private static final DefaultRedisScript<String> CREATE_GROUP=new DefaultRedisScript<>(
        "local r=redis.pcall('XGROUP','CREATE',KEYS[1],ARGV[1],'0-0','MKSTREAM'); " +
        "if type(r)=='table' and r.err and not string.find(r.err,'BUSYGROUP') then return redis.error_reply(r.err) end; return 'OK'",String.class);
    private final StringRedisTemplate redis;
    private final String stream;
    private final String group;
    private final String consumer="worker-"+UUID.randomUUID();
    public RedisQueueGateway(StringRedisTemplate redis,
            @Value("${app.queue.stream:hospital:registration}") String stream,
            @Value("${app.queue.group:hospital-workers}") String group) {
        this.redis=redis; this.stream=stream; this.group=group;
    }
    private StreamOperations<String,String,String> ops() { return redis.opsForStream(); }
    public void ensureAvailable() {
        // XGROUP also verifies write permission; an existing group is an expected success.
        redis.execute(CREATE_GROUP,List.of(stream),group);
    }
    public String publish(long requestId) {
        RecordId id=ops().add(stream,Map.of("request_id",Long.toString(requestId)));
        if(id==null) throw new IllegalStateException("Redis未返回消息编号");
        return id.getValue();
    }
    public List<MapRecord<String,String,String>> read() {
        List<MapRecord<String,String,String>> records=ops().read(Consumer.from(group,consumer),
            StreamReadOptions.empty().count(20),StreamOffset.create(stream,ReadOffset.lastConsumed()));
        return records==null ? List.of() : records;
    }
    public List<MapRecord<String,String,String>> reclaim() {
        PendingMessages pending=ops().pending(stream,group,Range.unbounded(),50);
        List<MapRecord<String,String,String>> records=new ArrayList<>();
        if(pending==null) return records;
        for(PendingMessage message:pending) {
            if(message.getElapsedTimeSinceLastDelivery().compareTo(Duration.ofSeconds(15))<0) continue;
            List<MapRecord<String,String,String>> claimed=ops().claim(stream,group,consumer,Duration.ofSeconds(15),message.getId());
            if(claimed!=null) records.addAll(claimed);
        }
        return records;
    }
    public void acknowledge(RecordId id) {
        // Called only after a durable terminal result. No MAXLEN trimming of pending messages.
        ops().acknowledge(stream,group,id);
        ops().delete(stream,id);
    }
}
