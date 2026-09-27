package com.example.hospital.auth;
import java.util.Map;
import org.apache.ibatis.annotations.*;
@Mapper
public interface UserMapper {
    @Select("SELECT u.*,d.id doctor_id FROM app_user u LEFT JOIN doctor d ON d.user_id=u.id WHERE username=#{username}") Map<String,Object> byUsername(String username);
    @Select("SELECT u.id,u.username,u.display_name,u.role,u.enabled,d.id doctor_id FROM app_user u LEFT JOIN doctor d ON d.user_id=u.id WHERE u.id=#{id}") Map<String,Object> byId(long id);
    @Insert("INSERT INTO app_user(username,password_hash,display_name,role,enabled) VALUES(#{username},#{password_hash},#{display_name},#{role},true)") @Options(useGeneratedKeys=true,keyProperty="id") int insert(Map<String,Object> u);
    @Insert("INSERT INTO patient_profile(user_id,phone,allergies) VALUES(#{id},#{phone},'')") int patient(Map<String,Object> u);
    @Select("SELECT u.id,u.username,u.role,u.display_name,p.phone,p.birth_date,p.allergies FROM app_user u LEFT JOIN patient_profile p ON p.user_id=u.id WHERE u.id=#{id}") Map<String,Object> profile(long id);
    @Update("UPDATE app_user SET display_name=#{display_name} WHERE id=#{id}") int name(Map<String,Object> p);
    @Update("UPDATE patient_profile SET phone=#{phone},birth_date=#{birth_date},allergies=#{allergies} WHERE user_id=#{id}") int profileUpdate(Map<String,Object> p);
}
