package com.example.hospital.advanced;

import com.example.hospital.common.Actor;
import com.example.hospital.common.Audit;
import com.example.hospital.common.BizException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import static com.example.hospital.advanced.AdvancedValues.*;

@Service
public class ScheduleTemplateService {
    private static final Logger log=LoggerFactory.getLogger(ScheduleTemplateService.class);
    private final AdvancedMapper mapper;
    private final Clock clock;
    private final TransactionTemplate tx;
    public ScheduleTemplateService(AdvancedMapper mapper,Clock clock,PlatformTransactionManager transactionManager) {
        this.mapper=mapper; this.clock=clock; this.tx=new TransactionTemplate(transactionManager);
    }
    public List<Map<String,Object>> list(Long doctorId,int page,int size) {
        Actor.current().require("ADMIN"); Map<String,Object> filters=page(page,size); filters.put("doctor_id",doctorId);
        return mapper.templates(filters);
    }
    @Transactional(rollbackFor=Exception.class)
    @Audit("维护周排班模板")
    public Map<String,Object> save(Long id,TemplateInput input) {
        Actor.current().require("ADMIN");
        LocalTime start,end; LocalDate from,to;
        try { start=LocalTime.parse(input.start_time()); end=LocalTime.parse(input.end_time());
            from=LocalDate.parse(input.effective_from()); to=LocalDate.parse(input.effective_to());
        } catch(RuntimeException e) { throw new BizException("请填写有效的模板日期和时间"); }
        if(input.doctor_id()<1 || input.day_of_week()<1 || input.day_of_week()>7 ||
            !("AM".equals(input.period()) || "PM".equals(input.period())) || !end.isAfter(start) || from.isAfter(to) ||
            input.total()<1 || input.total()>10000 || input.fee()==null || input.fee().signum()<0 ||
            input.fee().compareTo(new BigDecimal("99999999.99"))>0 || input.fee().scale()>2) throw new BizException("排班模板参数无效");
        // Serialize template writes and generation, then use the same doctor lock as manual scheduling.
        mapper.lockGuard(2);
        if(id!=null && mapper.template(id)==null) throw new BizException("模板不存在");
        Map<String,Object> doctor=mapper.lockDoctor(input.doctor_id());
        if(doctor==null) throw new BizException("医生不存在");
        Map<String,Object> values=new HashMap<>(); values.put("id",id); values.put("doctor_id",input.doctor_id());
        values.put("day_of_week",input.day_of_week()); values.put("period",input.period());
        values.put("start_time",start); values.put("end_time",end); values.put("total",input.total()); values.put("fee",input.fee());
        values.put("effective_from",from); values.put("effective_to",to); values.put("enabled",input.enabled()); values.put("now",LocalDateTime.now(clock));
        if(input.enabled() && mapper.conflictingTemplates(values)>0) throw new BizException("该医生已有日期和时段重叠的有效模板");
        if(id==null) mapper.insertTemplate(values); else mapper.updateTemplate(values);
        return mapper.template(number(values,"id"));
    }
    public List<Map<String,Object>> jobs(int page,int size) {
        Actor.current().require("ADMIN"); return mapper.jobs(page(page,size));
    }
    /** Each run is durable and generates D..D+6 in one database transaction. */
    public Map<String,Object> generate(String triggerType) {
        Map<String,Object> job=new HashMap<>(); job.put("trigger_type",triggerType); job.put("started_at",LocalDateTime.now(clock));
        tx.executeWithoutResult(status->mapper.insertJob(job));
        long jobId=number(job,"id");
        try {
            tx.executeWithoutResult(status->{
                mapper.lockGuard(2);
                int generated=0,skipped=0;
                LocalDate today=LocalDate.now(clock);
                for(Map<String,Object> template:mapper.enabledTemplates()) {
                    Map<String,Object> doctor=mapper.lockDoctor(number(template,"doctor_id"));
                    boolean doctorActive=doctor!=null && enabled(doctor.get("enabled")) && enabled(doctor.get("user_enabled")) && enabled(doctor.get("department_enabled"));
                    for(int offset=0;offset<7;offset++) {
                        LocalDate day=today.plusDays(offset);
                        if(day.getDayOfWeek().getValue()!=number(template,"day_of_week") ||
                            day.isBefore(date(template.get("effective_from"))) || day.isAfter(date(template.get("effective_to")))) continue;
                        LocalDateTime start=day.atTime(clockTime(template.get("start_time")));
                        if(!doctorActive || !start.isAfter(LocalDateTime.now(clock))) { skipped++; continue; }
                        Map<String,Object> values=new HashMap<>(template);
                        values.put("work_date",day); values.put("start_time",start); values.put("end_time",day.atTime(clockTime(template.get("end_time"))));
                        // Check ALL rows, including stopped, draft and manual schedules. Never overwrite them.
                        if(mapper.scheduleExists(values)>0 || mapper.scheduleOverlap(values)>0) { skipped++; continue; }
                        mapper.insertGeneratedSchedule(values); generated++;
                    }
                }
                finish(jobId,"SUCCEEDED",generated,skipped,null);
            });
        } catch(RuntimeException e) {
            log.error("Schedule generation job {} rolled back",jobId,e);
            // The generated rows have rolled back. Preserve the independent failure log.
            tx.executeWithoutResult(status->finish(jobId,"FAILED",0,0,"排班生成失败，本次新增已回滚；请查看服务器日志后重试"));
        }
        return mapper.job(jobId);
    }
    private void finish(long id,String status,int generated,int skipped,String error) {
        Map<String,Object> values=new HashMap<>(); values.put("id",id); values.put("status",status);
        values.put("generated_count",generated); values.put("skipped_count",skipped); values.put("error_message",error);
        values.put("finished_at",LocalDateTime.now(clock)); mapper.finishJob(values);
    }
    public record TemplateInput(long doctor_id,int day_of_week,String period,String start_time,String end_time,
        int total,BigDecimal fee,String effective_from,String effective_to,boolean enabled) {}
}
