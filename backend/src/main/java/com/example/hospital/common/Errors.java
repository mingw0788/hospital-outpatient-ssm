package com.example.hospital.common;
import java.util.Map;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.http.converter.HttpMessageNotReadableException;

@RestControllerAdvice
public class Errors {
    @ExceptionHandler(BizException.class) ResponseEntity<?> business(BizException e){return ResponseEntity.status(409).body(Map.of("code",e.getCode(),"message",e.getMessage()));}
    @ExceptionHandler(AccessDeniedException.class) ResponseEntity<?> denied(Exception e){return ResponseEntity.status(403).body(Map.of("code","FORBIDDEN","message",e.getMessage()));}
    @ExceptionHandler(AuthenticationException.class) ResponseEntity<?> auth(Exception e){return ResponseEntity.status(401).body(Map.of("code","UNAUTHORIZED","message","账号或密码错误，或账户已停用"));}
    @ExceptionHandler({IllegalArgumentException.class,MethodArgumentNotValidException.class,HttpMessageNotReadableException.class}) ResponseEntity<?> invalid(Exception e){return ResponseEntity.badRequest().body(Map.of("code","INVALID_INPUT","message","请检查必填项、日期格式及数值范围"));}
    @ExceptionHandler(DataIntegrityViolationException.class) ResponseEntity<?> conflict(Exception e){return ResponseEntity.status(409).body(Map.of("code","DATA_CONFLICT","message","记录已存在、数据关联冲突或数值不符合约束，请刷新后重试"));}
    @ExceptionHandler(CannotAcquireLockException.class) ResponseEntity<?> busy(Exception e){return ResponseEntity.status(409).body(Map.of("code","RETRY","message","当前业务繁忙，请使用原请求编号重试"));}
    @ExceptionHandler(Exception.class) ResponseEntity<?> unknown(Exception e){LoggerFactory.getLogger(getClass()).error("Unhandled request error",e);return ResponseEntity.status(500).body(Map.of("code","INTERNAL_ERROR","message","服务处理失败，请稍后重试"));}
}
