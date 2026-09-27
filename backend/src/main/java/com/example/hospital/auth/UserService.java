package com.example.hospital.auth;
import java.util.*;
import org.springframework.security.core.userdetails.*;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.example.hospital.common.*;
@Service
public class UserService implements UserDetailsService {
    private final UserMapper mapper; private final PasswordEncoder encoder;
    public UserService(UserMapper mapper,PasswordEncoder encoder){this.mapper=mapper;this.encoder=encoder;}
    @Override public UserDetails loadUserByUsername(String username){var u=mapper.byUsername(username);if(u==null)throw new UsernameNotFoundException("用户不存在");return new LoginUser(Rows.longVal(u,"id"),Rows.str(u,"username"),Rows.str(u,"password_hash"),Rows.str(u,"display_name"),Rows.str(u,"role"),u.get("doctor_id")==null?null:Rows.longVal(u,"doctor_id"),Rows.bool(u,"enabled"));}
    @Transactional public Map<String,Object> register(Map<String,Object> input){var p=new HashMap<String,Object>(input);String username=Rows.required(p,"username",64);if(!username.matches("[A-Za-z0-9_]{4,64}"))throw new BizException("用户名应为4～64位字母、数字或下划线");String pw=Rows.required(p,"password",72);if(pw.length()<8)throw new BizException("密码至少8位");p.put("username",username);p.put("display_name",Rows.required(p,"display_name",80));p.put("phone",Rows.str(p,"phone"));p.put("role","PATIENT");p.put("password_hash",encoder.encode(pw));mapper.insert(p);mapper.patient(p);return mapper.byId(Rows.longVal(p,"id"));}
    @Transactional public Object profile(Map<String,Object> input){Actor a=Actor.current();var p=new HashMap<String,Object>(input);p.put("id",a.id());p.put("display_name",Rows.required(p,"display_name",80));p.put("phone",Rows.str(p,"phone"));p.put("allergies",Rows.str(p,"allergies"));String d=Rows.str(p,"birth_date");p.put("birth_date",d.isBlank()?null:java.time.LocalDate.parse(d));mapper.name(p);if(a.is("PATIENT"))mapper.profileUpdate(p);return mapper.profile(a.id());}
}
