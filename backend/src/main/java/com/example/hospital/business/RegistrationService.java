package com.example.hospital.business;

import com.example.hospital.common.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.*;
import java.util.*;

@Service
public class RegistrationService {
    private final BusinessMapper db; private final BusinessLocks locks; private final Clock clock;
    public RegistrationService(BusinessMapper db,BusinessLocks locks,Clock clock){this.db=db;this.locks=locks;this.clock=clock;}
    @Transactional(rollbackFor=Exception.class) @Audit("挂号")
    public Map<String,Object> create(long scheduleId,String key){Actor.current().require("PATIENT");return createFor(Actor.current().id(),scheduleId,key,null);}
    @Transactional(rollbackFor=Exception.class) @Audit("队列挂号")
    public Map<String,Object> createFor(long patientId,long scheduleId,String requestKey){return createFor(patientId,scheduleId,requestKey,null);}
    @Transactional(rollbackFor=Exception.class) @Audit("队列挂号")
    public Map<String,Object> createFor(long patientId,long scheduleId,String requestKey,LocalDateTime requestDeadline){
        B.key(requestKey);
        Map<String,Object> user=B.require(db.lockUser(patientId));
        B.check("PATIENT".equals(B.str(user,"role"))&&BusinessLocks.enabled(user),"患者账号已停用");
        Map<String,Object> previous=db.byRequest(patientId,requestKey);
        if(previous!=null){B.check(B.id(previous,"schedule_id")==scheduleId,"同一请求标识不能用于不同排班");return detail(previous);}
        Map<String,Object> ref=B.require(db.schedule(scheduleId));
        Map<String,Object> doctor=B.require(db.lockDoctor(B.id(ref,"doctor_id")));
        Map<String,Object> schedule=B.require(db.lockSchedule(scheduleId));
        LocalDateTime now=LocalDateTime.now(clock);
        if(requestDeadline!=null&&!now.isBefore(requestDeadline))throw new BizException("QUEUE_EXPIRED","排队请求已超时");
        B.check(BusinessLocks.enabled(doctor),"医生已停诊");
        B.check("OPEN".equals(B.str(schedule,"status")),"排班未开放或已停诊");
        LocalDateTime start=B.time(schedule,"start_time");
        B.check(start.isAfter(now)&&!start.toLocalDate().isAfter(now.toLocalDate().plusDays(6)),"不在可预约时间内");
        B.check(db.activeRegistration(patientId,scheduleId)==null,"您已预约此排班，请查看我的挂号");
        B.check(db.takeSlot(scheduleId,now)==1,"号源已满");
        LocalDateTime deadline=now.plusMinutes(15).isBefore(start)?now.plusMinutes(15):start;
        Map<String,Object> row=B.map("patient_id",patientId,"schedule_id",scheduleId,"request_key",requestKey,"deadline",deadline);
        db.insertRegistration(row);
        Map<String,Object> bill=B.map("registration_id",B.id(row,"id"),"patient_id",patientId,"prescription_id",null,"type","REGISTRATION","amount",schedule.get("fee"),"deadline",deadline);
        db.insertBill(bill);return detail(B.require(db.registration(B.id(row,"id"))));
    }
    private Map<String,Object> detail(Map<String,Object> row){Map<String,Object> bill=db.registrationBill(B.id(row,"id"));row.put("bill_id",bill.get("id"));row.put("bill_status",bill.get("status"));row.put("amount",bill.get("amount"));return row;}
    @Transactional(rollbackFor=Exception.class) @Audit("取消挂号")
    public Map<String,Object> cancel(long id){
        Actor.current().require("PATIENT");var c=locks.registration(id);BusinessLocks.owner(c.registration());
        if(B.state(c.registration(),"CANCELLED","EXPIRED"))return detail(c.registration());
        B.check(B.state(c.registration(),"RESERVED"),"已付款挂号请使用退号退款");
        closeReserved(c,"CANCELLED","患者取消");return detail(db.registration(id));
    }
    void closeReserved(BusinessLocks.Context c,String status,String reason){
        if(!B.state(c.registration(),"RESERVED"))return;
        Map<String,Object> bill=db.registrationBill(B.id(c.registration(),"id"));
        B.check(B.state(bill,"UNPAID"),"账单状态已改变");
        db.billState(B.id(bill,"id"),"CLOSED",LocalDateTime.now(clock));
        db.registrationState(B.id(c.registration(),"id"),status,reason);
        B.check(db.releaseSlot(B.id(c.schedule(),"id"))==1,"号源释放状态异常");
    }
    @Transactional(rollbackFor=Exception.class) @Audit("患者报到")
    public Map<String,Object> checkIn(long id){
        Actor.current().require("PATIENT");var c=locks.registration(id);BusinessLocks.owner(c.registration());BusinessLocks.open(c);
        B.check(B.state(c.registration(),"BOOKED"),"请先支付挂号费");
        Map<String,Object> old=db.registrationVisit(id);if(old!=null)return old;
        LocalDateTime now=LocalDateTime.now(clock),start=B.time(c.schedule(),"start_time"),end=B.time(c.schedule(),"end_time");
        B.check(now.toLocalDate().equals(start.toLocalDate())&&!now.isBefore(start.minusMinutes(30))&&now.isBefore(end),"仅就诊当天开诊前30分钟至结束前可报到");
        db.nextQueue(B.id(c.schedule(),"id"));int number=B.integer(db.lockSchedule(B.id(c.schedule(),"id")),"next_queue");
        Map<String,Object> v=B.map("registration_id",id,"patient_id",B.id(c.registration(),"patient_id"),"doctor_id",B.id(c.doctor(),"id"),"schedule_id",B.id(c.schedule(),"id"),"queue_no",number,"sort_no",number);
        db.insertVisit(v);db.queueEvent(B.id(v,"id"),"CHECK_IN",Actor.current().id(),null);return db.visit(B.id(v,"id"));
    }
}
