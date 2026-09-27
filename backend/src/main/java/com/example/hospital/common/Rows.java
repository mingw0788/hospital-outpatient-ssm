package com.example.hospital.common;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Map;
public final class Rows {
    private Rows(){}
    public static long longVal(Map<String,?> m,String k){Object v=m.get(k); if(v==null)throw new BizException("缺少字段："+k);return v instanceof Number n?n.longValue():Long.parseLong(v.toString());}
    public static int intVal(Map<String,?> m,String k){return Math.toIntExact(longVal(m,k));}
    public static String str(Map<String,?> m,String k){Object v=m.get(k);return v==null?"":v.toString();}
    public static BigDecimal decimal(Map<String,?> m,String k){return new BigDecimal(str(m,k));}
    public static LocalDateTime dateTime(Map<String,?> m,String k){Object v=m.get(k);if(v instanceof LocalDateTime d)return d;if(v instanceof Timestamp t)return t.toLocalDateTime();return LocalDateTime.parse(v.toString().replace(' ','T'));}
    public static LocalDate date(Map<String,?> m,String k){return LocalDate.parse(str(m,k));}
    public static boolean bool(Map<String,?> m,String k){Object v=m.get(k);return Boolean.TRUE.equals(v)||"1".equals(String.valueOf(v))||"true".equalsIgnoreCase(String.valueOf(v));}
    public static String required(Map<String,?> m,String k,int max){String v=str(m,k).trim();if(v.isEmpty()||v.length()>max)throw new BizException("字段 "+k+" 不能为空且长度不能超过 "+max);return v;}
}
