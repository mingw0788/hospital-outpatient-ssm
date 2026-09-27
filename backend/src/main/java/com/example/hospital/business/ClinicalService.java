package com.example.hospital.business;

import com.example.hospital.common.*;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;

@Service
public class ClinicalService {
    private final BusinessMapper db;private final BusinessLocks locks;private final Clock clock;private final ObjectMapper json;
    public ClinicalService(BusinessMapper db,BusinessLocks locks,Clock clock,ObjectMapper json){this.db=db;this.locks=locks;this.clock=clock;this.json=json;}
    @Transactional(rollbackFor=Exception.class) @Audit("医生叫号")
    public Map<String,Object> call(long id){
        var c=locks.visit(id);Map<String,Object> v=B.require(db.lockVisit(id));BusinessLocks.doctor(v);BusinessLocks.open(c);
        if(B.state(v,"CALLED","IN_PROGRESS"))return v;B.check(B.state(v,"WAITING"),"当前患者不在候诊状态");
        B.check(db.currentVisit(B.id(v,"doctor_id"))==null,"请先完成当前患者接诊或将其标记过号");
        B.check(Objects.equals(db.firstWaiting(B.id(v,"schedule_id")),id),"请按报到顺序呼叫最前方患者");
        db.visitState(id,"CALLED",LocalDateTime.now(clock));event(id,"CALL",null);return db.visit(id);
    }
    @Transactional(rollbackFor=Exception.class) @Audit("开始接诊")
    public Map<String,Object> start(long id){
        var c=locks.visit(id);Map<String,Object> v=B.require(db.lockVisit(id));BusinessLocks.doctor(v);
        if(B.state(v,"IN_PROGRESS"))return v;BusinessLocks.open(c);B.check(B.state(v,"CALLED"),"请先叫号");
        db.visitState(id,"IN_PROGRESS",LocalDateTime.now(clock));event(id,"START",null);return db.visit(id);
    }
    @Transactional(rollbackFor=Exception.class) @Audit("标记过号")
    public Map<String,Object> skip(long id){var c=locks.visit(id);Map<String,Object> v=B.require(db.lockVisit(id));BusinessLocks.doctor(v);BusinessLocks.open(c);if(B.state(v,"SKIPPED"))return v;B.check(B.state(v,"CALLED"),"仅已叫号患者可以标记过号");db.visitState(id,"SKIPPED",LocalDateTime.now(clock));event(id,"SKIP",null);return db.visit(id);}
    @Transactional(rollbackFor=Exception.class) @Audit("过号重排")
    public Map<String,Object> requeue(long id,String requestKey){
        Actor a=Actor.current();a.require("PATIENT","DOCTOR");B.key(requestKey);var c=locks.visit(id);Map<String,Object> v=B.require(db.lockVisit(id));if(a.is("PATIENT"))BusinessLocks.owner(v);else BusinessLocks.doctor(v);
        if(db.requeueExists(id,requestKey)>0)return v;BusinessLocks.open(c);B.check(LocalDateTime.now(clock).isBefore(B.time(c.schedule(),"end_time")),"时段已结束，不能重新排队");B.check(B.state(v,"SKIPPED"),"只有过号患者可以重新排队");
        db.nextQueue(B.id(c.schedule(),"id"));int number=B.integer(db.lockSchedule(B.id(c.schedule(),"id")),"next_queue");db.requeue(id,number);event(id,"REQUEUE",requestKey);return db.visit(id);
    }
    private void event(long id,String action,String key){db.queueEvent(id,action,Actor.current().id(),key);}
    @Transactional(rollbackFor=Exception.class) @Audit("保存病历草稿")
    public Map<String,Object> save(long id,Map<String,Object> body){return saveInternal(id,body,false);}
    @Transactional(rollbackFor=Exception.class) @Audit("提交病历处方")
    public Map<String,Object> complete(long id,Map<String,Object> body){return saveInternal(id,body,true);}
    private Map<String,Object> saveInternal(long id,Map<String,Object> body,boolean submit){
        var c=locks.visit(id);Map<String,Object> v=B.require(db.lockVisit(id));BusinessLocks.doctor(v);
        if(submit&&B.state(v,"COMPLETED"))return B.map("visit",v,"record",db.record(id),"prescriptions",db.visitPrescriptions(id));
        B.check(B.state(v,"IN_PROGRESS"),"仅接诊中的病历可以编辑");Map<String,Object> old=db.record(id);B.check(old==null||!B.state(old,"SUBMITTED"),"已提交病历不能覆盖");
        Map<String,Object> record=B.map("visit_id",id,"patient_id",B.id(v,"patient_id"),"doctor_id",B.id(v,"doctor_id"),"chief_complaint",B.text(body,"chief_complaint",1000),"history",B.text(body,"history",10000),"past_history",B.text(body,"past_history",10000),"allergies",B.text(body,"allergies",1000),"diagnosis",B.text(body,"diagnosis",2000),"advice",B.text(body,"advice",10000),"status",submit?"SUBMITTED":"DRAFT","submitted_at",submit?LocalDateTime.now(clock):null);
        List<Map<String,Object>> items=parseItems(body);try{record.put("draft_items",json.writeValueAsString(items));}catch(Exception e){throw new BizException("处方内容格式不正确");}
        if(submit){B.check(!B.str(record,"chief_complaint").isBlank()&&!B.str(record,"diagnosis").isBlank(),"主诉和诊断为必填项");if(!items.isEmpty())createPrescription(c,v,items,null);}
        db.saveRecord(record);if(submit){db.visitState(id,"COMPLETED",LocalDateTime.now(clock));event(id,"COMPLETE",null);}return B.map("visit",db.visit(id),"record",db.record(id),"prescriptions",db.visitPrescriptions(id));
    }
    @SuppressWarnings("unchecked")
    private List<Map<String,Object>> parseItems(Map<String,Object> body){
        Object raw=body.get("items");if(raw==null)return List.of();B.check(raw instanceof List<?>,"处方药品格式错误");List<?> input=(List<?>)raw;B.check(input.size()<=50,"每张处方最多50种药品");List<Map<String,Object>> result=new ArrayList<>();Set<Long> seen=new HashSet<>();
        for(Object value:input){B.check(value instanceof Map<?,?>,"处方药品格式错误");Map<String,Object> row=(Map<String,Object>)value;long drugId=B.id(row,"drug_id");int qty=B.integer(row,"quantity");String usage=B.text(row,"usage_text",500);B.check(drugId>0&&qty>0&&qty<=1000&&!usage.isBlank(),"药品数量须为1～1000整数，用法用量必填");B.check(seen.add(drugId),"同一药品不能重复添加");result.add(B.map("drug_id",drugId,"quantity",qty,"usage_text",usage));}
        result.sort(Comparator.comparingLong(x->B.id(x,"drug_id")));return result;
    }
    private Map<String,Object> createPrescription(BusinessLocks.Context c,Map<String,Object> v,List<Map<String,Object>> items,Long replacesId){
        BigDecimal total=BigDecimal.ZERO;List<Map<String,Object>> snapshots=new ArrayList<>();
        for(Map<String,Object> item:items){Map<String,Object> drug=B.require(db.lockDrug(B.id(item,"drug_id")));B.check(BusinessLocks.enabled(drug),"处方含已停用药品");Map<String,Object> snapshot=new HashMap<>(item);snapshot.put("drug_name",drug.get("name"));snapshot.put("spec",drug.get("spec"));snapshot.put("unit",drug.get("unit"));snapshot.put("unit_price",drug.get("price"));total=total.add(B.money(drug,"price").multiply(BigDecimal.valueOf(B.integer(item,"quantity"))));snapshots.add(snapshot);}
        B.check(total.compareTo(new BigDecimal("99999999.99"))<=0,"处方总金额超出范围");Map<String,Object> p=B.map("visit_id",B.id(v,"id"),"patient_id",B.id(v,"patient_id"),"doctor_id",B.id(v,"doctor_id"),"amount",total,"replaces_id",replacesId);db.insertPrescription(p);
        for(Map<String,Object> item:snapshots){item.put("prescription_id",B.id(p,"id"));db.insertItem(item);}newBill(c,p);return db.prescription(B.id(p,"id"));
    }
    private Map<String,Object> newBill(BusinessLocks.Context c,Map<String,Object> p){Map<String,Object> bill=B.map("registration_id",B.id(c.registration(),"id"),"patient_id",B.id(c.registration(),"patient_id"),"prescription_id",B.id(p,"id"),"type","PRESCRIPTION","amount",p.get("amount"),"deadline",LocalDateTime.now(clock).plusHours(24));db.insertBill(bill);return db.bill(B.id(bill,"id"));}
    @Transactional(rollbackFor=Exception.class) @Audit("作废处方")
    public Map<String,Object> voidPrescription(long id){locks.prescription(id);Map<String,Object> p=B.require(db.lockPrescription(id));BusinessLocks.doctor(p);if(B.state(p,"VOID"))return p;B.check(B.state(p,"UNPAID","REFUNDED"),"仅未付款或已退款处方可作废");Map<String,Object> bill=db.prescriptionBill(id);if(bill!=null&&B.state(bill,"UNPAID"))db.billState(B.id(bill,"id"),"CLOSED",LocalDateTime.now(clock));db.prescriptionState(id,"VOID",LocalDateTime.now(clock));return db.prescription(id);}
    @Transactional(rollbackFor=Exception.class) @Audit("重开药费账单")
    public Map<String,Object> rebill(long id){
        var c=locks.prescription(id);Map<String,Object> p=B.require(db.lockPrescription(id));BusinessLocks.doctor(p);B.check(B.state(p,"UNPAID","REFUNDED","PAID"),"已发药或作废处方不能重开账单");Map<String,Object> old=db.prescriptionBill(id);LocalDateTime now=LocalDateTime.now(clock);
        if(old!=null&&B.state(old,"PAID"))return old;if(old!=null&&B.state(old,"UNPAID")){if(now.isBefore(B.time(old,"deadline")))return old;db.billState(B.id(old,"id"),"CLOSED",now);}
        db.prescriptionState(id,"UNPAID",now);return newBill(c,p);
    }
    @Transactional(rollbackFor=Exception.class) @Audit("更换处方")
    public Map<String,Object> replace(long visitId,Map<String,Object> body){
        var c=locks.visit(visitId);Map<String,Object> v=B.require(db.lockVisit(visitId));BusinessLocks.doctor(v);B.check(B.state(v,"COMPLETED"),"请先提交病历完成接诊");List<Map<String,Object>> all=db.visitPrescriptions(visitId);
        Long replaces=null;for(Map<String,Object> p:all){if(!B.state(p,"VOID"))return p;if(replaces==null)replaces=B.id(p,"id");}
        B.check(replaces!=null,"仅已有作废处方可更换处方");List<Map<String,Object>> items=parseItems(body);B.check(!items.isEmpty(),"替代处方至少包含一种药品");return createPrescription(c,v,items,replaces);
    }
    List<Map<String,Object>> draftItems(Map<String,Object> record){if(record==null||B.str(record,"draft_items").isBlank())return List.of();try{return json.readValue(B.str(record,"draft_items"),new TypeReference<List<Map<String,Object>>>(){});}catch(Exception e){throw new BizException("病历草稿读取失败");}}
}
