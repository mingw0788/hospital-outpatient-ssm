package com.example.hospital.config;
import com.example.hospital.auth.UserMapper;
import com.example.hospital.admin.AdminMapper;
import java.time.*;
import java.util.*;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** 仅显式启用演示模式时初始化空数据库；正常业务全部通过 MyBatis。 */
@Component @ConditionalOnProperty(name="app.seed-demo",havingValue="true")
public class DemoData implements ApplicationRunner {
    private final UserMapper users;private final AdminMapper admin;private final JdbcTemplate jdbc;private final PasswordEncoder passwords;private final Clock clock;private final String password;
    public DemoData(UserMapper u,AdminMapper a,JdbcTemplate j,PasswordEncoder p,Clock c,@Value("${app.demo-password}")String password){users=u;admin=a;jdbc=j;passwords=p;clock=c;this.password=password;}
    @Override @Transactional public void run(ApplicationArguments args){
        if(jdbc.queryForObject("SELECT COUNT(*) FROM app_user",Long.class)==0)initializeBaseData();
        else if(!isProjectDemoDatabase())return;
        addMoreDemoData();
    }
    private void initializeBaseData(){
        for(String[] x:new String[][]{{"admin","系统管理员","ADMIN"},{"doctor","陈明（演示）","DOCTOR"},{"doctor2","林悦（演示）","DOCTOR"},{"cashier","门诊收费员","CASHIER"},{"pharmacist","药房药师","PHARMACIST"},{"patient","张同学（演示）","PATIENT"},{"patient2","李同学（演示）","PATIENT"}}){var u=new HashMap<String,Object>();u.put("username",x[0]);u.put("display_name",x[1]);u.put("role",x[2]);u.put("password_hash",passwords.encode(password));users.insert(u);if(x[2].equals("PATIENT")){u.put("phone","13800000000");users.patient(u);}}
        long internal=department("内科","门诊一楼 A 区","常见内科疾病门诊（教学演示）"),surgery=department("外科","门诊二楼 B 区","普通外科门诊（教学演示）");department("口腔科","门诊三楼 C 区","口腔健康门诊（教学演示）");
        long d1=doctor("doctor",internal,"主治医师","普通内科门诊"),d2=doctor("doctor2",surgery,"副主任医师","普通外科门诊");
        String[][] names={{"DEMO001","演示药品 A","10片/盒","盒","12.50"},{"DEMO002","演示药品 B","20粒/盒","盒","24.80"},{"DEMO003","演示药品 C","100毫升/瓶","瓶","18.00"}};
        for(String[] x:names)addDrug(x[0],x[1],x[2],x[3],x[4]);
        var today=LocalDate.now(clock);for(long d:new long[]{d1,d2})for(int day=0;day<7;day++){var date=today.plusDays(day);for(String period:List.of("AM","PM")){var start=date.atTime(period.equals("AM")?8:14,0);if(start.isAfter(LocalDateTime.now(clock)))addSchedule(d,date,period,start,date.atTime(period.equals("AM")?12:17,0),"20.00");}}
        // 提供一个开诊前15分钟的体验时段，便于同日演示报到、接诊。
        var start=LocalDateTime.now(clock).plusMinutes(15).withNano(0);var end=start.plusMinutes(40);if(start.toLocalDate().equals(today)&&end.toLocalDate().equals(today)&&jdbc.queryForObject("SELECT COUNT(*) FROM schedule WHERE doctor_id=? AND start_time<? AND end_time>?",Integer.class,d1,end,start)==0)addSchedule(d1,today,"DEMO",start,end,"10.00");
    }
    private boolean isProjectDemoDatabase(){
        Map<String,Object> adminUser=users.byUsername("admin"),doctorUser=users.byUsername("doctor"),patientUser=users.byUsername("patient");
        return adminUser!=null&&"ADMIN".equals(adminUser.get("role"))
                &&doctorUser!=null&&"DOCTOR".equals(doctorUser.get("role"))&&doctorUser.get("doctor_id")!=null
                &&patientUser!=null&&"PATIENT".equals(patientUser.get("role"));
    }
    private void addMoreDemoData(){
        ensureUser("patient3","王同学（演示）","PATIENT","13800000003");
        ensureUser("patient4","赵同学（演示）","PATIENT","13800000004");
        ensureUser("patient5","刘同学（演示）","PATIENT","13800000005");
        long pediatrics=department("儿科","门诊四楼 D 区","儿童常见病门诊（教学演示）");
        long dermatology=department("皮肤科","门诊五楼 E 区","皮肤健康门诊（教学演示）");
        long d3=doctor("doctor3",pediatrics,"主治医师","儿童常见病与健康咨询门诊");
        long d4=doctor("doctor4",dermatology,"主治医师","常见皮肤问题门诊");
        addDrug("DEMO004","演示药品 D","10片/板","盒","8.60");
        addDrug("DEMO005","演示药品 E","12粒/盒","盒","16.00");
        addDrug("DEMO006","演示药品 F","20毫升/瓶","瓶","21.50");
        refreshRollingSchedules();
        addDemoAppointments();
        addDemoClinicalHistory();
    }
    /** Keep demo appointment dates current at startup and after each Shanghai midnight. */
    @Scheduled(cron="${app.demo-schedule.cron:0 5 0 * * *}",zone="Asia/Shanghai")
    @Transactional
    public void refreshRollingSchedules(){
        if(!isProjectDemoDatabase())return;
        LocalDate today=LocalDate.now(clock);LocalDateTime now=LocalDateTime.now(clock);
        for(String username:List.of("doctor","doctor2","doctor3","doctor4")){
            Map<String,Object> user=users.byUsername(username);if(user==null||user.get("doctor_id")==null)continue;
            long doctor=((Number)user.get("doctor_id")).longValue();
            for(int offset=0;offset<7;offset++){
                LocalDate date=today.plusDays(offset);
                for(String period:List.of("AM","PM")){
                    LocalDateTime start=date.atTime(period.equals("AM")?8:14,0);
                    if(start.isAfter(now))ensureSchedule(doctor,date,period,start,date.atTime(period.equals("AM")?12:17,0),"20.00");
                }
            }
        }
    }
    private void addDemoAppointments(){
        long patients=jdbc.queryForObject("SELECT COUNT(*) FROM app_user WHERE username IN ('patient3','patient4','patient5') AND role='PATIENT'",Long.class);
        if(patients<3||jdbc.queryForObject("SELECT COUNT(*) FROM registration WHERE patient_id=(SELECT id FROM app_user WHERE username='patient3') AND request_key='DEMO-SEED-REG-001'",Integer.class)>0)return;
        List<Map<String,Object>> schedules=jdbc.queryForList("SELECT id,start_time,fee FROM schedule WHERE doctor_id=(SELECT id FROM doctor WHERE user_id=(SELECT id FROM app_user WHERE username='doctor3')) AND status='OPEN' AND remaining>=3 AND start_time>? ORDER BY start_time,id LIMIT 1",LocalDateTime.now(clock));
        if(schedules.isEmpty())return;
        Map<String,Object> schedule=schedules.get(0);long scheduleId=((Number)schedule.get("id")).longValue();
        LocalDateTime start=asLocalDateTime(schedule.get("start_time"));
        int taken=jdbc.update("UPDATE schedule SET remaining=remaining-3 WHERE id=? AND remaining>=3 AND status='OPEN'",scheduleId);
        if(taken!=1)throw new IllegalStateException("无法为演示挂号预占排班号源");
        String[] names={"patient3","patient4","patient5"};
        for(int i=0;i<names.length;i++){
            Map<String,Object> patient=users.byUsername(names[i]);long patientId=((Number)patient.get("id")).longValue();
            String requestKey=String.format("DEMO-SEED-REG-%03d",i+1);
            LocalDateTime deadline=i<2?start:LocalDateTime.now(clock).plusMinutes(15);
            jdbc.update("INSERT INTO registration(patient_id,schedule_id,request_key,status,deadline,created_at) VALUES(?,?,?, ?,?,CURRENT_TIMESTAMP(6))",patientId,scheduleId,requestKey,i<2?"BOOKED":"RESERVED",deadline);
            long registrationId=jdbc.queryForObject("SELECT id FROM registration WHERE patient_id=? AND request_key=?",Long.class,patientId,requestKey);
            LocalDateTime paidAt=i<2?LocalDateTime.now(clock):null;
            jdbc.update("INSERT INTO bill(registration_id,patient_id,type,status,amount,deadline,paid_at) VALUES(?,?, 'REGISTRATION',?,?,?,?)",registrationId,patientId,i<2?"PAID":"UNPAID",schedule.get("fee"),deadline,paidAt);
            if(i<2){
                long billId=jdbc.queryForObject("SELECT id FROM bill WHERE registration_id=? AND type='REGISTRATION'",Long.class,registrationId);
                jdbc.update("INSERT INTO payment_record(bill_id,patient_id,actor_id,amount,transaction_no) VALUES(?,?,?,?,?)",billId,patientId,patientId,schedule.get("fee"),"DEMO-SEED-PAY-"+(i+1));
            }
        }
    }
    private void addDemoClinicalHistory(){
        if(jdbc.queryForObject("SELECT COUNT(*) FROM registration WHERE patient_id=(SELECT id FROM app_user WHERE username='patient2') AND request_key='DEMO-SEED-HISTORY-001'",Integer.class)>0)return;
        Map<String,Object> patient=users.byUsername("patient2"),doctorUser=users.byUsername("doctor2"),drug=jdbc.queryForMap("SELECT id,name,spec,unit,price FROM drug WHERE code='DEMO001'");
        if(patient==null||doctorUser==null||doctorUser.get("doctor_id")==null)return;
        long patientId=((Number)patient.get("id")).longValue(),doctorId=((Number)doctorUser.get("doctor_id")).longValue(),drugId=((Number)drug.get("id")).longValue();
        LocalDate visitDate=LocalDate.now(clock).minusDays(1);LocalDateTime start=visitDate.atTime(9,0),end=visitDate.atTime(9,40);
        jdbc.update("INSERT INTO schedule(doctor_id,work_date,period,start_time,end_time,total,remaining,fee,status,reason,source) VALUES(?,?,'HISTORY-DEMO',?,?,30,29,20.00,'STOPPED','历史门诊演示时段','MANUAL')",doctorId,visitDate,start,end);
        long scheduleId=jdbc.queryForObject("SELECT id FROM schedule WHERE doctor_id=? AND work_date=? AND period='HISTORY-DEMO'",Long.class,doctorId,visitDate);
        jdbc.update("INSERT INTO registration(patient_id,schedule_id,request_key,status,deadline,created_at) VALUES(?,?,'DEMO-SEED-HISTORY-001','COMPLETED',?,?)",patientId,scheduleId,start,start.minusMinutes(30));
        long registrationId=jdbc.queryForObject("SELECT id FROM registration WHERE patient_id=? AND request_key='DEMO-SEED-HISTORY-001'",Long.class,patientId);
        jdbc.update("INSERT INTO bill(registration_id,patient_id,type,status,amount,deadline,created_at,paid_at) VALUES(?,?,'REGISTRATION','PAID',20.00,?,?,?)",registrationId,patientId,start,start.minusHours(1),start.minusHours(1));
        long registrationBill=jdbc.queryForObject("SELECT id FROM bill WHERE registration_id=? AND type='REGISTRATION'",Long.class,registrationId);
        jdbc.update("INSERT INTO payment_record(bill_id,patient_id,actor_id,amount,transaction_no,created_at) VALUES(?,?,?,?,?,?)",registrationBill,patientId,patientId,new java.math.BigDecimal("20.00"),"DEMO-SEED-HISTORY-REG-PAY",start.minusHours(1));
        Long pharmacistId=jdbc.queryForObject("SELECT id FROM app_user WHERE username='pharmacist'",Long.class);
        jdbc.update("INSERT INTO visit(registration_id,patient_id,doctor_id,schedule_id,queue_no,sort_no,status,called_at,started_at,completed_at,created_at) VALUES(?,?,?,?,1,1,'COMPLETED',?,?,?,?)",registrationId,patientId,doctorId,scheduleId,start.plusMinutes(15),start.plusMinutes(20),start.plusMinutes(35),start.plusMinutes(10));
        long visitId=jdbc.queryForObject("SELECT id FROM visit WHERE registration_id=?",Long.class,registrationId);
        jdbc.update("INSERT INTO medical_record(visit_id,patient_id,doctor_id,chief_complaint,history,past_history,allergies,diagnosis,advice,status,draft_items,submitted_at,created_at) VALUES(?,?,?,'咳嗽两天（虚构教学样例）','仅用于演示病历页面的数据流程。','无（演示数据）','无（演示数据）','呼吸道症状（虚构教学示例）','此记录为虚构教学样例，不构成医疗建议。','SUBMITTED','[]',?,?)",visitId,patientId,doctorId,start.plusMinutes(35),start.plusMinutes(20));
        java.math.BigDecimal drugPrice=(java.math.BigDecimal)drug.get("price");
        jdbc.update("INSERT INTO prescription(visit_id,patient_id,doctor_id,status,amount,dispensed_at,created_at) VALUES(?,?,?,'DISPENSED',?,?,?)",visitId,patientId,doctorId,drugPrice,start.plusMinutes(38),start.plusMinutes(35));
        long prescriptionId=jdbc.queryForObject("SELECT id FROM prescription WHERE visit_id=?",Long.class,visitId);
        jdbc.update("INSERT INTO prescription_item(prescription_id,drug_id,drug_name,spec,unit,unit_price,quantity,usage_text) VALUES(?,?,?,?,?,?,1,'演示用法；非真实处方')",prescriptionId,drugId,drug.get("name"),drug.get("spec"),drug.get("unit"),drugPrice);
        jdbc.update("INSERT INTO bill(registration_id,patient_id,prescription_id,type,status,amount,deadline,created_at,paid_at) VALUES(?,?,?,'PRESCRIPTION','PAID',?,?,?,?)",registrationId,patientId,prescriptionId,drugPrice,start,start.plusMinutes(35),start.plusMinutes(36));
        long prescriptionBill=jdbc.queryForObject("SELECT id FROM bill WHERE prescription_id=? AND type='PRESCRIPTION'",Long.class,prescriptionId);
        jdbc.update("INSERT INTO payment_record(bill_id,patient_id,actor_id,amount,transaction_no,created_at) VALUES(?,?,?,?,?,?)",prescriptionBill,patientId,patientId,drugPrice,"DEMO-SEED-HISTORY-MED-PAY",start.plusMinutes(36));
        if(jdbc.update("UPDATE drug SET stock=stock-1 WHERE id=? AND stock-reserved>=1",drugId)!=1)throw new IllegalStateException("历史演示处方库存不足");
        jdbc.update("INSERT INTO stock_movement(drug_id,actor_id,business_id,type,quantity,reason,created_at) VALUES(?,? ,?,'DISPENSE',-1,'虚构历史处方的演示出库',?)",drugId,pharmacistId,prescriptionId,start.plusMinutes(38));
        jdbc.update("INSERT INTO queue_event(visit_id,action,actor_id,created_at) VALUES(?,'CHECK_IN',?,?)",visitId,patientId,start.plusMinutes(10));
        jdbc.update("INSERT INTO queue_event(visit_id,action,actor_id,created_at) VALUES(?,'CALL',?,?)",visitId,doctorUser.get("id"),start.plusMinutes(15));
        jdbc.update("INSERT INTO queue_event(visit_id,action,actor_id,created_at) VALUES(?,'START',?,?)",visitId,doctorUser.get("id"),start.plusMinutes(20));
        jdbc.update("INSERT INTO queue_event(visit_id,action,actor_id,created_at) VALUES(?,'COMPLETE',?,?)",visitId,doctorUser.get("id"),start.plusMinutes(35));
    }
    private LocalDateTime asLocalDateTime(Object value){if(value instanceof LocalDateTime dateTime)return dateTime;if(value instanceof java.sql.Timestamp timestamp)return timestamp.toLocalDateTime();return LocalDateTime.parse(value.toString().replace(' ','T'));}
    private void ensureUser(String username,String name,String role,String phone){
        Map<String,Object> existing=users.byUsername(username);Map<String,Object> user;
        if(existing==null){user=new HashMap<>();user.put("username",username);user.put("display_name",name);user.put("role",role);user.put("password_hash",passwords.encode(password));users.insert(user);}
        else user=new HashMap<>(existing);
        if("PATIENT".equals(role)&&jdbc.queryForObject("SELECT COUNT(*) FROM patient_profile WHERE user_id=?",Integer.class,user.get("id"))==0){user.put("phone",phone);users.patient(user);}
    }
    private long department(String name,String location,String description){
        List<Long> ids=jdbc.query("SELECT id FROM department WHERE name=?",(result,index)->result.getLong(1),name);if(!ids.isEmpty())return ids.get(0);
        var row=new HashMap<String,Object>();row.put("name",name);row.put("location",location);row.put("description",description);row.put("enabled",true);admin.addDepartment(row);return ((Number)row.get("id")).longValue();
    }
    private long doctor(String username,long department,String title,String specialty){
        Map<String,Object> user=users.byUsername(username);if(user==null){ensureUser(username,username+"（演示）","DOCTOR",null);user=users.byUsername(username);}
        if(user.get("doctor_id")!=null)return ((Number)user.get("doctor_id")).longValue();
        var row=new HashMap<String,Object>();row.put("user_id",user.get("id"));row.put("department_id",department);row.put("title",title);row.put("specialty",specialty);row.put("enabled",true);admin.addDoctor(row);return ((Number)row.get("id")).longValue();
    }
    private void addDrug(String code,String name,String spec,String unit,String price){
        List<Long> ids=jdbc.query("SELECT id FROM drug WHERE code=?",(result,index)->result.getLong(1),code);if(!ids.isEmpty())return;
        var row=new HashMap<String,Object>();row.put("code",code);row.put("name",name);row.put("spec",spec);row.put("unit",unit);row.put("price",new java.math.BigDecimal(price));row.put("threshold",10);row.put("enabled",true);admin.addDrug(row);jdbc.update("UPDATE drug SET stock=200 WHERE id=?",row.get("id"));jdbc.update("INSERT INTO stock_movement(drug_id,type,quantity,reason) VALUES(?,'INITIAL',200,'演示库存初始化')",row.get("id"));
    }
    private void ensureSchedule(long doctor,LocalDate date,String period,LocalDateTime start,LocalDateTime end,String fee){
        if(jdbc.queryForObject("SELECT COUNT(*) FROM schedule WHERE doctor_id=? AND work_date=? AND (period=? OR (start_time<? AND end_time>?))",Integer.class,doctor,date,period,end,start)>0)return;
        addSchedule(doctor,date,period,start,end,fee);
    }
    private void addSchedule(long doctor,LocalDate date,String period,LocalDateTime start,LocalDateTime end,String fee){var m=new HashMap<String,Object>();m.put("doctor_id",doctor);m.put("work_date",date);m.put("period",period);m.put("start_time",start);m.put("end_time",end);m.put("total",30);m.put("fee",new java.math.BigDecimal(fee));m.put("status","OPEN");admin.addSchedule(m);}
}
