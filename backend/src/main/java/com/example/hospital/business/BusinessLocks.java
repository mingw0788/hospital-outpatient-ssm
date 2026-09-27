package com.example.hospital.business;

import com.example.hospital.common.Actor;
import com.example.hospital.common.BizException;
import org.springframework.stereotype.Component;
import java.util.Map;

/** 所有临床写事务共用的加锁顺序：患者→医生→排班→挂号→账单/就诊/处方→药品ID升序。 */
@Component
public class BusinessLocks {
    private final BusinessMapper db;
    public BusinessLocks(BusinessMapper db) {this.db=db;}
    public record Context(Map<String,Object> patient,Map<String,Object> doctor,Map<String,Object> schedule,Map<String,Object> registration) {}
    Context registration(long id) {
        Map<String,Object> ref=B.require(db.registration(id));
        Map<String,Object> patient=B.require(db.lockUser(B.id(ref,"patient_id")));
        Map<String,Object> sr=B.require(db.schedule(B.id(ref,"schedule_id")));
        Map<String,Object> doctor=B.require(db.lockDoctor(B.id(sr,"doctor_id")));
        Map<String,Object> schedule=B.require(db.lockSchedule(B.id(sr,"id")));
        return new Context(patient,doctor,schedule,B.require(db.lockRegistration(id)));
    }
    Context bill(long id) {return registration(B.id(B.require(db.bill(id)),"registration_id"));}
    Context visit(long id) {return registration(B.id(B.require(db.visit(id)),"registration_id"));}
    Context prescription(long id) {return visit(B.id(B.require(db.prescription(id)),"visit_id"));}
    static void owner(Map<String,Object> row) {if(B.id(row,"patient_id")!=Actor.current().id())throw new BizException("FORBIDDEN","无权访问该记录");}
    static void doctor(Map<String,Object> row) {Actor a=Actor.current();a.require("DOCTOR");if(a.doctorId()==null||B.id(row,"doctor_id")!=a.doctorId())throw new BizException("FORBIDDEN","只能操作本人的接诊记录");}
    static boolean enabled(Map<String,Object> row){Object e=row.get("enabled");return Boolean.TRUE.equals(e)||"1".equals(String.valueOf(e));}
    static void open(Context c){B.check("OPEN".equals(B.str(c.schedule,"status")),"排班已停诊或未开放");}
}
