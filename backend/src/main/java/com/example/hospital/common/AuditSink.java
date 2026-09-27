package com.example.hospital.common;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import com.example.hospital.admin.AdminMapper;
@Service public class AuditSink {
    private final AdminMapper mapper;
    public AuditSink(AdminMapper mapper){this.mapper=mapper;}
    @Transactional(propagation=Propagation.REQUIRES_NEW) public void save(Map<String,Object> row){mapper.audit(row);}
}
