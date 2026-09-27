package com.example.hospital.common;
import java.util.*;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.*;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.*;
import org.springframework.web.context.request.*;
import org.slf4j.LoggerFactory;

@Aspect @Component @Order(0)
public class AuditAspect {
    private final AuditSink sink;
    public AuditAspect(AuditSink sink){this.sink=sink;}
    @Around("@annotation(audit)") public Object log(ProceedingJoinPoint join,Audit audit)throws Throwable{
        var row=new HashMap<String,Object>();try{row.put("actor_id",Actor.current().id());}catch(Exception ignored){row.put("actor_id",null);}
        row.put("operation",audit.value());row.put("request_id",UUID.randomUUID().toString());String detail=join.getSignature().getName();
        if(RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attr){String id=attr.getRequest().getHeader("X-Request-ID");if(id!=null&&id.matches("[a-zA-Z0-9_-]{1,80}"))row.put("request_id",id);detail=attr.getRequest().getMethod()+" "+attr.getRequest().getRequestURI();}
        row.put("detail",detail);
        try{Object result=join.proceed();row.put("success",true);if(result instanceof Map<?,?> map&&map.get("id")!=null)row.put("detail",detail+" business_id="+map.get("id"));
            if(TransactionSynchronizationManager.isSynchronizationActive())TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization(){public void afterCompletion(int status){row.put("success",status==STATUS_COMMITTED);persist(row);}});else persist(row);return result;
        }catch(Throwable e){row.put("success",false);row.put("detail",detail+" "+e.getClass().getSimpleName());persist(row);throw e;}
    }
    private void persist(Map<String,Object> row){try{sink.save(row);}catch(Exception e){LoggerFactory.getLogger(getClass()).error("Audit persistence failed: {}",row.get("request_id"),e);}}
}
