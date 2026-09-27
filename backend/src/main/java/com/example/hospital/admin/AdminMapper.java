package com.example.hospital.admin;
import java.util.*;
import org.apache.ibatis.annotations.*;
@Mapper
public interface AdminMapper {
    List<Map<String,Object>> departments(Map<String,Object> p);
    List<Map<String,Object>> doctors(Map<String,Object> p);
    List<Map<String,Object>> schedules(Map<String,Object> p);
    List<Map<String,Object>> drugs(Map<String,Object> p);
    List<Map<String,Object>> users(Map<String,Object> p);
    List<Map<String,Object>> logs(Map<String,Object> p);
    List<Map<String,Object>> movements(Map<String,Object> p);
    @Select("SELECT * FROM doctor WHERE id=#{id} FOR UPDATE") Map<String,Object> lockDoctor(long id);
    @Select("SELECT * FROM schedule WHERE id=#{id} FOR UPDATE") Map<String,Object> lockSchedule(long id);
    @Select("SELECT * FROM drug WHERE id=#{id} FOR UPDATE") Map<String,Object> lockDrug(long id);
    @Select("SELECT COUNT(*) FROM schedule WHERE doctor_id=#{doctor_id} AND start_time<#{end_time} AND end_time>#{start_time} AND id<>#{id}") int overlaps(Map<String,Object> p);
    @Insert("INSERT INTO department(name,location,description,enabled) VALUES(#{name},#{location},#{description},#{enabled})") @Options(useGeneratedKeys=true,keyProperty="id") int addDepartment(Map<String,Object> p);
    @Update("UPDATE department SET name=#{name},location=#{location},description=#{description},enabled=#{enabled} WHERE id=#{id}") int updateDepartment(Map<String,Object> p);
    @Insert("INSERT INTO doctor(user_id,department_id,title,specialty,enabled) VALUES(#{user_id},#{department_id},#{title},#{specialty},#{enabled})") @Options(useGeneratedKeys=true,keyProperty="id") int addDoctor(Map<String,Object> p);
    @Update("UPDATE doctor SET department_id=#{department_id},title=#{title},specialty=#{specialty},enabled=#{enabled} WHERE id=#{id}") int updateDoctor(Map<String,Object> p);
    @Insert("INSERT INTO schedule(doctor_id,work_date,period,start_time,end_time,total,remaining,fee,status,source) VALUES(#{doctor_id},#{work_date},#{period},#{start_time},#{end_time},#{total},#{total},#{fee},#{status},'MANUAL')") @Options(useGeneratedKeys=true,keyProperty="id") int addSchedule(Map<String,Object> p);
    @Update("UPDATE schedule SET work_date=#{work_date},period=#{period},start_time=#{start_time},end_time=#{end_time},remaining=remaining+#{total}-total,total=#{total},fee=#{fee},status=#{status},source='MANUAL' WHERE id=#{id}") int updateSchedule(Map<String,Object> p);
    @Insert("INSERT INTO drug(code,name,spec,unit,price,stock,reserved,threshold,enabled) VALUES(#{code},#{name},#{spec},#{unit},#{price},0,0,#{threshold},#{enabled})") @Options(useGeneratedKeys=true,keyProperty="id") int addDrug(Map<String,Object> p);
    @Update("UPDATE drug SET code=#{code},name=#{name},spec=#{spec},unit=#{unit},price=#{price},threshold=#{threshold},enabled=#{enabled} WHERE id=#{id}") int updateDrug(Map<String,Object> p);
    @Update("UPDATE drug SET stock=stock+#{quantity} WHERE id=#{id} AND stock+#{quantity}>=reserved") int adjust(Map<String,Object> p);
    @Insert("INSERT INTO stock_movement(drug_id,actor_id,type,quantity,reason) VALUES(#{id},#{actor_id},'ADJUST',#{quantity},#{reason})") int movement(Map<String,Object> p);
    @Update("<script>UPDATE app_user SET enabled=#{enabled}<if test='password_hash != null'>,password_hash=#{password_hash}</if> WHERE id=#{id}</script>") int updateUser(Map<String,Object> p);
    @Insert("INSERT INTO operation_log(actor_id,operation,request_id,success,detail) VALUES(#{actor_id},#{operation},#{request_id},#{success},#{detail})") int audit(Map<String,Object> p);
    @Select("SELECT COUNT(*) registrations FROM registration WHERE created_at>=#{from} AND created_at<#{to}") long registrationCount(Map<String,Object> p);
    @Select("SELECT COUNT(*) FROM visit WHERE status='COMPLETED' AND completed_at>=#{from} AND completed_at<#{to}") long completedCount(Map<String,Object> p);
    @Select("SELECT COALESCE(SUM(amount),0) FROM payment_record WHERE created_at>=#{from} AND created_at<#{to}") java.math.BigDecimal income(Map<String,Object> p);
    @Select("SELECT COALESCE(SUM(amount),0) FROM refund_record WHERE created_at>=#{from} AND created_at<#{to}") java.math.BigDecimal refunds(Map<String,Object> p);
    @Select("SELECT COUNT(*) FROM drug WHERE enabled=true AND stock-reserved<=threshold") long lowStock();
    @Select("SELECT dep.name,COUNT(r.id) count FROM department dep LEFT JOIN doctor d ON d.department_id=dep.id LEFT JOIN schedule s ON s.doctor_id=d.id LEFT JOIN registration r ON r.schedule_id=s.id AND r.created_at>=#{from} AND r.created_at<#{to} GROUP BY dep.id,dep.name ORDER BY count DESC") List<Map<String,Object>> departmentCounts(Map<String,Object> p);
}
