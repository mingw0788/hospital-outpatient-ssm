package com.example.hospital.config;
import com.example.hospital.auth.*;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.io.IOException;
import org.springframework.context.annotation.*;
import org.springframework.security.authentication.*;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.*;
import org.springframework.security.web.context.*;
import org.springframework.security.web.csrf.*;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

@Configuration
public class SecurityConfig {
    @Bean PasswordEncoder passwordEncoder(){return new BCryptPasswordEncoder();}
    @Bean AuthenticationManager authenticationManager(UserService users,PasswordEncoder encoder){var p=new DaoAuthenticationProvider(users);p.setPasswordEncoder(encoder);return new ProviderManager(p);}
    @Bean SecurityContextRepository contextRepository(){return new HttpSessionSecurityContextRepository();}
    @Bean CsrfTokenRepository csrfRepository(){return new HttpSessionCsrfTokenRepository();}
    @Bean SecurityFilterChain chain(HttpSecurity http,SecurityContextRepository repository,CsrfTokenRepository csrf,UserMapper users)throws Exception{
        http.authorizeHttpRequests(a->a.requestMatchers("/api/auth/login","/api/auth/register","/api/auth/csrf","/error","/","/index.html","/assets/**","/favicon.ico").permitAll().requestMatchers("/api/**").authenticated().anyRequest().permitAll())
            .securityContext(c->c.securityContextRepository(repository))
            .csrf(c->c.csrfTokenRepository(csrf).csrfTokenRequestHandler(new CsrfTokenRequestAttributeHandler()))
            .formLogin(c->c.disable()).httpBasic(c->c.disable()).logout(c->c.disable())
            .exceptionHandling(c->c.authenticationEntryPoint((req,res,e)->json(res,401,"请先登录" )).accessDeniedHandler((req,res,e)->json(res,403,"权限不足或安全令牌已失效，请刷新页面")));
        // 每次请求验证账户启停状态，旧会话不能绕过管理员停用。
        http.addFilterAfter(new OncePerRequestFilter(){@Override protected void doFilterInternal(HttpServletRequest req,HttpServletResponse res,FilterChain chain)throws ServletException,IOException{
            var auth=SecurityContextHolder.getContext().getAuthentication();
            if(auth!=null && auth.getPrincipal() instanceof LoginUser u){var fresh=users.byId(u.id());if(fresh==null||!com.example.hospital.common.Rows.bool(fresh,"enabled")){SecurityContextHolder.clearContext();var s=req.getSession(false);if(s!=null)s.invalidate();json(res,401,"账户已停用");return;}}
            chain.doFilter(req,res);
        }},SecurityContextHolderFilter.class);
        return http.build();
    }
    private static void json(HttpServletResponse res,int status,String text)throws IOException{res.setStatus(status);res.setContentType("application/json;charset=UTF-8");res.getWriter().write("{\"code\":\"AUTH_ERROR\",\"message\":\""+text+"\"}");}
}
