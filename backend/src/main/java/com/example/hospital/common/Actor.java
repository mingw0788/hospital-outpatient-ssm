package com.example.hospital.common;
import java.util.Arrays;
import com.example.hospital.auth.LoginUser;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.access.AccessDeniedException;

public record Actor(long id, String role, Long doctorId) {
    public static Actor current() {
        var a=SecurityContextHolder.getContext().getAuthentication();
        if(a==null || !(a.getPrincipal() instanceof LoginUser u)) throw new AccessDeniedException("请先登录");
        return new Actor(u.id(),u.role(),u.doctorId());
    }
    public boolean is(String role){return this.role.equals(role);}
    public void require(String...roles){if(Arrays.stream(roles).noneMatch(this::is))throw new AccessDeniedException("没有操作权限");}
}
