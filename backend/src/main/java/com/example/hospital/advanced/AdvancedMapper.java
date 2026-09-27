package com.example.hospital.advanced;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Mapper
public interface AdvancedMapper {
    Map<String,Object> lockGuard(@Param("id") int id);
    Map<String,Object> patient(@Param("id") long id);
    Map<String,Object> schedule(@Param("id") long id);
    Map<String,Object> requestByKey(@Param("patientId") long patientId,@Param("key") String key);
    Map<String,Object> request(@Param("id") long id);
    Map<String,Object> lockRequest(@Param("id") long id);
    long pendingCount();
    long recentCount(@Param("patientId") long patientId,@Param("since") LocalDateTime since);
    int insertRequest(Map<String,Object> values);
    List<Map<String,Object>> requests(Map<String,Object> filters);
    List<Long> dueRequests(@Param("now") LocalDateTime now,@Param("stale") LocalDateTime stale);
    int delivered(@Param("id") long id,@Param("streamId") String streamId,@Param("now") LocalDateTime now);
    int processing(@Param("id") long id,@Param("now") LocalDateTime now);
    int succeeded(@Param("id") long id,@Param("registrationId") long registrationId,@Param("now") LocalDateTime now);
    int failed(Map<String,Object> values);
    int expireRequests(@Param("now") LocalDateTime now);
    int retryRequest(@Param("id") long id,@Param("now") LocalDateTime now);
    int duplicate(@Param("id") long id);
    Map<String,Object> lockDoctor(@Param("id") long id);
    List<Map<String,Object>> templates(Map<String,Object> filters);
    Map<String,Object> template(@Param("id") long id);
    List<Map<String,Object>> enabledTemplates();
    int conflictingTemplates(Map<String,Object> values);
    int insertTemplate(Map<String,Object> values);
    int updateTemplate(Map<String,Object> values);
    int scheduleExists(Map<String,Object> values);
    int scheduleOverlap(Map<String,Object> values);
    int insertGeneratedSchedule(Map<String,Object> values);
    int insertJob(Map<String,Object> values);
    int finishJob(Map<String,Object> values);
    Map<String,Object> job(@Param("id") long id);
    List<Map<String,Object>> jobs(Map<String,Object> filters);
}
