package com.example.hospital.business;

import com.example.hospital.common.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.*;
import java.util.*;

@Service
public class ScheduleBusinessService {
    private final BusinessMapper db;private final BusinessLocks locks;private final RegistrationService registrations;private final BillingService billing;private final Clock clock;
    public ScheduleBusinessService(BusinessMapper db,BusinessLocks locks,RegistrationService registrations,BillingService billing,Clock clock){this.db=db;this.locks=locks;this.registrations=registrations;this.billing=billing;this.clock=clock;}
    @Transactional(rollbackFor=Exception.class) @Audit("排班停诊")
    public List<Long> markStopped(long id,String reason){
        Actor.current().require("ADMIN");B.check(reason!=null&&!reason.isBlank()&&reason.length()<=500,"请填写500字以内停诊原因");Map<String,Object> ref=B.require(db.schedule(id));db.lockDoctor(B.id(ref,"doctor_id"));db.lockSchedule(id);db.stopSchedule(id,reason);return db.scheduleRegistrations(id);
    }
    @Transactional(rollbackFor=Exception.class) @Audit("停诊订单处理")
    public String settleStopped(long registrationId){
        Actor.current().require("ADMIN");var c=locks.registration(registrationId);B.check("STOPPED".equals(B.str(c.schedule(),"status")),"排班未停诊");
        Map<String,Object> v=db.registrationVisit(registrationId);if(v!=null&&B.state(v,"IN_PROGRESS","COMPLETED"))return "已接诊保留";
        if(B.state(c.registration(),"RESERVED")){registrations.closeReserved(c,"CANCELLED","医院停诊："+B.str(c.schedule(),"reason"));return "已关闭";}
        if(B.state(c.registration(),"BOOKED")){billing.refundLocked(c,db.registrationBill(registrationId),Actor.current().id(),"医院停诊："+B.str(c.schedule(),"reason"),true);return "已退款";}
        return "已处理";
    }
    @Transactional(rollbackFor=Exception.class) @Audit("结束候诊队列")
    public Map<String,Object> closeQueue(long id){
        Actor.current().require("DOCTOR");Map<String,Object> ref=B.require(db.schedule(id));BusinessLocks.doctor(ref);db.lockDoctor(B.id(ref,"doctor_id"));Map<String,Object> schedule=B.require(db.lockSchedule(id));
        B.check(!LocalDateTime.now(clock).isBefore(B.time(schedule,"end_time")),"时段尚未结束");B.check(db.waitingCount(id)==0,"仍有候诊或接诊患者，不能结束队列");int skipped=db.skippedNoShow(id);return B.map("skipped_no_show",skipped,"message","队列已结束，未报到与过号患者记为未到诊");
    }
}
