package com.example.hospital.business;

import com.example.hospital.common.BizException;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.*;

/** 业务输入统一转换，禁止客户端金额参与结算。 */
final class B {
    private B() {}
    static Map<String,Object> require(Map<String,Object> row) { if(row==null) throw new BizException("记录不存在或无权访问"); return row; }
    static long id(Map<String,?> row,String key) { try { return Long.parseLong(String.valueOf(row.get(key))); } catch(Exception e) { throw new BizException("参数不正确："+key); } }
    static int integer(Map<String,?> row,String key) { long n=id(row,key); if(n<Integer.MIN_VALUE||n>Integer.MAX_VALUE) throw new BizException("整数超出范围"); return (int)n; }
    static String str(Map<String,?> row,String key) { Object value=row.get(key); return value==null?"":value.toString(); }
    static BigDecimal money(Map<String,?> row,String key) { return new BigDecimal(str(row,key)); }
    static LocalDateTime time(Map<String,?> row,String key) { Object x=row.get(key); if(x instanceof LocalDateTime d)return d; if(x instanceof Timestamp t)return t.toLocalDateTime(); return LocalDateTime.parse(x.toString().replace(' ','T')); }
    static void check(boolean ok,String message) { if(!ok)throw new BizException(message); }
    static boolean state(Map<String,?> row,String... states) { return Arrays.asList(states).contains(str(row,"status")); }
    static String key(String value) { check(value!=null&&value.matches("[A-Za-z0-9_-]{1,80}"),"request_key 须为1～80位字母、数字、下划线或横线");return value; }
    static Map<String,Object> map(Object... values) { Map<String,Object> m=new LinkedHashMap<>();for(int i=0;i<values.length;i+=2)m.put(values[i].toString(),values[i+1]);return m; }
    static Map<String,Object> page(Map<String,String> query) { Map<String,Object> m=new HashMap<>(query);int page=1,size=20;try { page=Integer.parseInt(query.getOrDefault("page","1"));size=Integer.parseInt(query.getOrDefault("size","20")); } catch(Exception e){throw new BizException("分页参数错误");}check(page>=1&&page<=100000&&size>=1&&size<=100,"分页范围错误");m.put("offset",(page-1)*size);m.put("size",size);return m; }
    static String text(Map<String,?> body,String key,int max) {String s=str(body,key).trim();check(s.length()<=max,key+" 内容过长");return s;}
}
