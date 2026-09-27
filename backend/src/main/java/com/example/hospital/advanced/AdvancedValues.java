package com.example.hospital.advanced;

import com.example.hospital.common.BizException;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.HashMap;
import java.util.Map;

final class AdvancedValues {
    private AdvancedValues() {}
    static long number(Map<String,Object> row,String key) { return ((Number)row.get(key)).longValue(); }
    static String text(Map<String,Object> row,String key) { return String.valueOf(row.get(key)); }
    static boolean enabled(Object value) { return Boolean.TRUE.equals(value) || (value instanceof Number n && n.intValue()!=0); }
    static LocalDateTime time(Object value) {
        if (value instanceof LocalDateTime d) return d;
        if (value instanceof Timestamp t) return t.toLocalDateTime();
        return LocalDateTime.parse(value.toString().replace(' ','T'));
    }
    static LocalDate date(Object value) { return value instanceof java.sql.Date d ? d.toLocalDate() : LocalDate.parse(value.toString()); }
    static LocalTime clockTime(Object value) { return value instanceof java.sql.Time t ? t.toLocalTime() : LocalTime.parse(value.toString()); }
    static boolean terminal(Map<String,Object> row) { return switch(text(row,"status")) { case "SUCCEEDED","FAILED","EXPIRED" -> true; default -> false; }; }
    static Map<String,Object> page(int page,int size) {
        if(page<1 || page>1000000 || size<1 || size>100) throw new BizException("分页参数无效");
        Map<String,Object> p=new HashMap<>(); p.put("size",size); p.put("offset",(page-1)*size); return p;
    }
}
