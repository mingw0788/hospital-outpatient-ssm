package com.example.hospital.advanced;

import com.example.hospital.common.Actor;
import com.example.hospital.common.Audit;
import com.example.hospital.common.BizException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import static com.example.hospital.advanced.AdvancedValues.*;

@Service
public class QueueService {
    private final AdvancedMapper mapper;
    private final RedisQueueGateway redis;
    private final Clock clock;
    private final boolean enabled;
    private final int maxPending;
    private final int deadlineSeconds;
    public QueueService(AdvancedMapper mapper,RedisQueueGateway redis,Clock clock,
        @Value("${app.queue.enabled:false}") boolean enabled,@Value("${app.queue.max-pending:2000}") int maxPending,
        @Value("${app.queue.deadline-seconds:120}") int deadlineSeconds) {
        this.mapper=mapper; this.redis=redis; this.clock=clock; this.enabled=enabled;
        this.maxPending=Math.max(1,maxPending); this.deadlineSeconds=Math.max(1,Math.min(120,deadlineSeconds));
    }
    @Transactional(rollbackFor=Exception.class)
    @Audit("提交排队挂号请求")
    public Map<String,Object> submit(long scheduleId,String requestKey) {
        Actor actor=Actor.current(); actor.require("PATIENT");
        if(requestKey==null || !requestKey.matches("[A-Za-z0-9_-]{1,80}") || scheduleId<1) throw new BizException("请求参数无效");
        // Admission is serialized by one short database lock to enforce a bounded backlog.
        // No Redis message is sent in this transaction: the table is the durable outbox.
        mapper.lockGuard(1);
        Map<String,Object> existing=mapper.requestByKey(actor.id(),requestKey);
        if(existing!=null) {
            if(number(existing,"schedule_id")!=scheduleId) throw new BizException("同一请求标识不能用于不同排班");
            return existing;
        }
        if(!enabled) throw new BizException("QUEUE_DISABLED","队列模式未启用，请使用普通挂号");
        try { redis.ensureAvailable(); } catch(RuntimeException e) { throw new BizException("QUEUE_UNAVAILABLE","挂号队列暂不可用，请稍后重试"); }
        LocalDateTime now=LocalDateTime.now(clock);
        if(mapper.pendingCount()>=maxPending) throw new BizException("QUEUE_FULL","当前排队人数较多，请稍后重试");
        if(mapper.recentCount(actor.id(),now.minusMinutes(1))>=6) throw new BizException("RATE_LIMITED","提交过于频繁，请稍后再试");
        Map<String,Object> schedule=mapper.schedule(scheduleId);
        if(schedule==null || !"OPEN".equals(schedule.get("status"))) throw new BizException("排班不可预约");
        LocalDateTime starts=time(schedule.get("start_time"));
        LocalDate today=LocalDate.now(clock),day=date(schedule.get("work_date"));
        if(!starts.isAfter(now) || day.isBefore(today) || day.isAfter(today.plusDays(6))) throw new BizException("排班不在预约时间范围内");
        LocalDateTime deadline=now.plusSeconds(deadlineSeconds);
        if(starts.isBefore(deadline)) deadline=starts;
        Map<String,Object> values=new HashMap<>();
        values.put("patient_id",actor.id()); values.put("schedule_id",scheduleId); values.put("request_key",requestKey);
        values.put("expires_at",deadline); values.put("now",now); mapper.insertRequest(values);
        return mapper.request(number(values,"id"));
    }
    public Map<String,Object> get(long id) {
        Actor actor=Actor.current(); actor.require("PATIENT","ADMIN");
        Map<String,Object> row=mapper.request(id);
        if(row==null || (!actor.is("ADMIN") && number(row,"patient_id")!=actor.id())) throw new BizException("请求不存在或无权访问");
        return row;
    }
    public List<Map<String,Object>> list(String status,int page,int size) {
        Actor.current().require("ADMIN"); Map<String,Object> filters=page(page,size); filters.put("status",status); return mapper.requests(filters);
    }
    @Transactional(rollbackFor=Exception.class)
    @Audit("重试排队挂号请求")
    public Map<String,Object> retry(long id) {
        Actor.current().require("ADMIN");
        if(!enabled) throw new BizException("队列模式未启用");
        Map<String,Object> row=mapper.lockRequest(id);
        if(row==null) throw new BizException("请求不存在");
        if(!"FAILED".equals(row.get("status")) || !"RETRY_EXHAUSTED".equals(row.get("error_code"))) throw new BizException("仅技术重试耗尽的请求可以人工重试");
        LocalDateTime now=LocalDateTime.now(clock);
        if(!time(row.get("expires_at")).isAfter(now)) throw new BizException("请求已过期，不能再次挂号；患者可提交新的请求");
        try { redis.ensureAvailable(); } catch(RuntimeException e) { throw new BizException("QUEUE_UNAVAILABLE","Redis不可用，暂不能重试"); }
        mapper.retryRequest(id,now); return mapper.request(id);
    }
}
