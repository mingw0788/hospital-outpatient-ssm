package com.example.hospital.business;

import com.example.hospital.common.*;
import org.springframework.stereotype.Service;
import java.util.*;

@Service
public class BusinessQueryService {
    private final BusinessMapper db;private final ClinicalService clinical;
    public BusinessQueryService(BusinessMapper db,ClinicalService clinical){this.db=db;this.clinical=clinical;}
    private Map<String,Object> financeQuery(Map<String,String> query){Actor a=Actor.current();a.require("PATIENT","CASHIER","ADMIN");Map<String,Object> q=B.page(query);if(a.is("PATIENT"))q.put("patientId",a.id());return q;}
    public List<Map<String,Object>> registrations(Map<String,String> query){return db.registrations(financeQuery(query));}
    public List<Map<String,Object>> bills(Map<String,String> query){return db.bills(financeQuery(query));}
    public List<Map<String,Object>> payments(Map<String,String> query){return db.payments(financeQuery(query));}
    public List<Map<String,Object>> refunds(Map<String,String> query){return db.refunds(financeQuery(query));}
    public Map<String,Object> bill(long id){Map<String,Object> q=financeQuery(Map.of());q.put("id",id);List<Map<String,Object>> rows=db.bills(q);B.check(!rows.isEmpty(),"记录不存在或无权访问");Map<String,Object> row=rows.get(0);q.put("billId",id);row.put("payments",db.payments(q));row.put("refunds",db.refunds(q));row.put("items",row.get("prescription_id")==null?List.of(B.map("name","门诊挂号费","quantity",1,"unit_price",row.get("amount"))):db.items(B.id(row,"prescription_id")));return row;}
    public List<Map<String,Object>> visits(Map<String,String> query){Actor a=Actor.current();a.require("PATIENT","DOCTOR");Map<String,Object> q=B.page(query);if(a.is("PATIENT"))q.put("patientId",a.id());else q.put("doctorId",a.doctorId());return db.visits(q);}
    @Audit("查询就诊记录")
    public List<Map<String,Object>> records(Map<String,String> query){
        Actor a=Actor.current();a.require("PATIENT","DOCTOR");Map<String,Object> q=B.page(query);
        if(a.is("PATIENT")){q.put("patientId",a.id());q.put("patientOnly",true);}else if(query.containsKey("patientId")&&!query.get("patientId").isBlank()){
            long patientId=B.id(q,"patientId");B.check(db.treatmentRelation(a.doctorId(),patientId)>0,"仅可查看实际接诊患者的历史记录");q.put("patientOnly",true);
        }else q.put("doctorId",a.doctorId());return db.records(q);
    }
    @Audit("读取病历")
    public Map<String,Object> record(long visitId){
        Actor a=Actor.current();a.require("PATIENT","DOCTOR");Map<String,Object> v=B.require(db.visit(visitId)),r=db.record(visitId);
        if(a.is("PATIENT")){BusinessLocks.owner(v);B.check(r!=null&&B.state(r,"SUBMITTED"),"尚无已提交病历");}
        else if(B.id(v,"doctor_id")!=a.doctorId()){B.check(r!=null&&B.state(r,"SUBMITTED")&&db.treatmentRelation(a.doctorId(),B.id(v,"patient_id"))>0,"无权查看该病历");}
        Map<String,Object> result=B.map("visit",v,"record",r==null?new HashMap<>():new HashMap<>(r),"items",clinical.draftItems(r),"prescriptions",db.visitPrescriptions(visitId));
        ((Map<?,?>)result.get("record")).remove("draft_items");return result;
    }
    private Map<String,Object> prescriptionQuery(Map<String,String> query){Actor a=Actor.current();a.require("PATIENT","DOCTOR","PHARMACIST");Map<String,Object> q=B.page(query);if(a.is("PATIENT"))q.put("patientId",a.id());if(a.is("DOCTOR"))q.put("doctorId",a.doctorId());if(a.is("PHARMACIST"))q.put("pharmacyOnly",true);return q;}
    public List<Map<String,Object>> prescriptions(Map<String,String> query){List<Map<String,Object>> rows=db.prescriptions(prescriptionQuery(query));for(var row:rows)row.put("items",db.items(B.id(row,"id")));return rows;}
    public Map<String,Object> prescription(long id){Map<String,Object> q=prescriptionQuery(Map.of());q.put("id",id);List<Map<String,Object>> rows=db.prescriptions(q);B.check(!rows.isEmpty(),"记录不存在或无权访问");Map<String,Object> p=rows.get(0);p.put("items",db.items(id));return p;}
}
