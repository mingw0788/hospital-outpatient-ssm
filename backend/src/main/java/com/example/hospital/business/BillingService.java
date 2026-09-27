package com.example.hospital.business;

import com.example.hospital.common.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.*;
import java.util.*;

@Service
public class BillingService {
    private final BusinessMapper db;private final BusinessLocks locks;private final RegistrationService registrations;private final Clock clock;
    public BillingService(BusinessMapper db,BusinessLocks locks,RegistrationService registrations,Clock clock){this.db=db;this.locks=locks;this.registrations=registrations;this.clock=clock;}
    @Transactional(rollbackFor=Exception.class) @Audit("模拟缴费")
    public Map<String,Object> pay(long id,boolean failure){
        Actor actor=Actor.current();actor.require("PATIENT","CASHIER");var c=locks.bill(id);Map<String,Object> bill=B.require(db.lockBill(id));
        if(actor.is("PATIENT"))BusinessLocks.owner(bill);
        if(B.state(bill,"PAID"))return bill;
        B.check(B.state(bill,"UNPAID"),"账单已关闭或退款，不能支付");
        LocalDateTime now=LocalDateTime.now(clock);B.check(now.isBefore(B.time(bill,"deadline")),"账单已超时，不能支付");
        B.check(!failure,"模拟支付失败，未扣款");
        if("REGISTRATION".equals(B.str(bill,"type"))){
            BusinessLocks.open(c);B.check(B.state(c.registration(),"RESERVED"),"挂号状态不允许支付");
            db.registrationState(B.id(c.registration(),"id"),"BOOKED",null);
        }else{
            Map<String,Object> prescription=B.require(db.lockPrescription(B.id(bill,"prescription_id")));
            B.check(B.state(prescription,"UNPAID"),"处方状态不允许支付");
            // 所有药品按ID升序加锁；任何一种不足都会回滚整单预占。
            for(Map<String,Object> item:db.items(B.id(prescription,"id"))){
                long drugId=B.id(item,"drug_id");int qty=B.integer(item,"quantity");Map<String,Object> drug=B.require(db.lockDrug(drugId));
                B.check(db.reserveDrug(drugId,qty)==1,B.str(drug,"name")+" 库存不足或已停用");
                db.stockMovement(drugId,actor.id(),B.id(prescription,"id"),"RESERVE",qty,"药费支付预占");
            }
            db.prescriptionState(B.id(prescription,"id"),"PAID",now);
        }
        db.billState(id,"PAID",now);Map<String,Object> payment=new HashMap<>(bill);payment.put("actor_id",actor.id());payment.put("transaction_no","PAY-"+UUID.randomUUID());db.insertPayment(payment);return db.bill(id);
    }
    @Transactional(rollbackFor=Exception.class) @Audit("退号退款")
    public Map<String,Object> refundRegistration(long id){
        Actor a=Actor.current();a.require("PATIENT","CASHIER");var c=locks.registration(id);if(a.is("PATIENT"))BusinessLocks.owner(c.registration());
        Map<String,Object> bill=B.require(db.registrationBill(id));return refundLocked(c,bill,a.id(),"退号退款",false);
    }
    @Transactional(rollbackFor=Exception.class) @Audit("账单退款")
    public Map<String,Object> refund(long id){
        Actor a=Actor.current();a.require("PATIENT","CASHIER");var c=locks.bill(id);Map<String,Object> bill=B.require(db.lockBill(id));
        if(a.is("PATIENT")){BusinessLocks.owner(bill);B.check("REGISTRATION".equals(B.str(bill,"type")),"药费请先申请退款，由收费员办理");}
        return refundLocked(c,bill,a.id(),"整单退款",false);
    }
    Map<String,Object> refundLocked(BusinessLocks.Context c,Map<String,Object> bill,Long actorId,String reason,boolean forced){
        if(B.state(bill,"REFUNDED"))return bill;
        B.check(B.state(bill,"PAID"),"账单未支付，不能退款");LocalDateTime now=LocalDateTime.now(clock);
        if("REGISTRATION".equals(B.str(bill,"type"))){
            Map<String,Object> visit=db.registrationVisit(B.id(c.registration(),"id"));
            if(!forced)B.check(now.isBefore(B.time(c.schedule(),"start_time"))&&visit==null,"开诊或报到后不能自行退号");
            B.check(visit==null||!B.state(visit,"IN_PROGRESS","COMPLETED"),"已接诊挂号不能退款");
            B.check(B.state(c.registration(),"BOOKED"),"挂号已取消");
            db.registrationState(B.id(c.registration(),"id"),"CANCELLED",reason);
            if(visit!=null)db.visitState(B.id(visit,"id"),"CANCELLED",now);
            B.check(db.releaseSlot(B.id(c.schedule(),"id"))==1,"号源释放状态异常");
        }else{
            Map<String,Object> p=B.require(db.lockPrescription(B.id(bill,"prescription_id")));B.check(B.state(p,"PAID"),"处方已发药或不能退款");
            for(Map<String,Object> item:db.items(B.id(p,"id"))){long drugId=B.id(item,"drug_id");int qty=B.integer(item,"quantity");db.lockDrug(drugId);B.check(db.releaseDrug(drugId,qty)==1,"预占库存异常");db.stockMovement(drugId,actorId,B.id(p,"id"),"RELEASE",-qty,"药费退款释放预占");}
            db.prescriptionState(B.id(p,"id"),"REFUNDED",now);
        }
        db.billState(B.id(bill,"id"),"REFUNDED",now);Map<String,Object> refund=new HashMap<>(bill);refund.put("actor_id",actorId);refund.put("transaction_no","REF-"+UUID.randomUUID());refund.put("reason",reason);db.insertRefund(refund);return db.bill(B.id(bill,"id"));
    }
    @Transactional(rollbackFor=Exception.class) @Audit("申请药费退款")
    public Map<String,Object> requestRefund(long id){Actor.current().require("PATIENT");locks.bill(id);Map<String,Object> bill=B.require(db.lockBill(id));BusinessLocks.owner(bill);B.check("PRESCRIPTION".equals(B.str(bill,"type"))&&B.state(bill,"PAID"),"仅已支付药费可申请退款");Map<String,Object> p=B.require(db.lockPrescription(B.id(bill,"prescription_id")));B.check(B.state(p,"PAID"),"已发药不能退款");db.requestRefund(id);return db.bill(id);}
    @Transactional(rollbackFor=Exception.class) @Audit("账单超时关闭")
    public void expire(long id){
        var c=locks.bill(id);Map<String,Object> b=B.require(db.lockBill(id));LocalDateTime now=LocalDateTime.now(clock);
        if(!B.state(b,"UNPAID")||now.isBefore(B.time(b,"deadline")))return;
        if("REGISTRATION".equals(B.str(b,"type")))registrations.closeReserved(c,"EXPIRED","支付超时");else db.billState(id,"CLOSED",now);
    }
    @Transactional(rollbackFor=Exception.class) @Audit("发药")
    public Map<String,Object> dispense(long id){
        Actor.current().require("PHARMACIST");locks.prescription(id);Map<String,Object> p=B.require(db.lockPrescription(id));
        if(B.state(p,"DISPENSED"))return p;B.check(B.state(p,"PAID"),"仅已付款且未发药处方可发药");Map<String,Object> bill=B.require(db.prescriptionBill(id));B.check(B.state(bill,"PAID"),"账单未支付或已退款");
        for(Map<String,Object> item:db.items(id)){long drugId=B.id(item,"drug_id");int qty=B.integer(item,"quantity");db.lockDrug(drugId);B.check(db.dispenseDrug(drugId,qty)==1,"预占库存异常");db.stockMovement(drugId,Actor.current().id(),id,"DISPENSE",-qty,"核对发药出库");}
        db.prescriptionState(id,"DISPENSED",LocalDateTime.now(clock));return db.prescription(id);
    }
}
