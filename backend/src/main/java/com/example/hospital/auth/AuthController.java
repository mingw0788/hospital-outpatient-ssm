package com.example.hospital.auth;
import com.example.hospital.common.*;
import jakarta.servlet.http.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.security.authentication.*;
import org.springframework.security.core.context.*;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.*;
import org.springframework.web.bind.annotation.*;
@RestController
public class AuthController {
    private final AuthenticationManager manager;private final SecurityContextRepository contexts;private final CsrfTokenRepository tokens;private final UserService service;private final UserMapper users;
    private final Map<String,Deque<Instant>> attempts=new ConcurrentHashMap<>();
    public AuthController(AuthenticationManager m,SecurityContextRepository c,CsrfTokenRepository t,UserService s,UserMapper u){manager=m;contexts=c;tokens=t;service=s;users=u;}
    @GetMapping("/api/auth/csrf") public Object csrf(CsrfToken token){return Api.ok(Map.of("token",token.getToken(),"headerName",token.getHeaderName()));}
    @PostMapping("/api/auth/login") public Object login(@RequestBody Map<String,Object> p,HttpServletRequest req,HttpServletResponse res){
        String key=req.getRemoteAddr();var q=attempts.computeIfAbsent(key,k->new ArrayDeque<>());synchronized(q){Instant now=Instant.now();while(!q.isEmpty()&&q.peek().isBefore(now.minusSeconds(60)))q.poll();if(q.size()>=30)throw new BizException("LOGIN_RATE_LIMIT","登录尝试过多，请一分钟后重试");q.add(now);}
        var auth=manager.authenticate(new UsernamePasswordAuthenticationToken(Rows.str(p,"username"),Rows.str(p,"password")));
        if(req.getSession(false)!=null)req.changeSessionId();var ctx=SecurityContextHolder.createEmptyContext();ctx.setAuthentication(auth);SecurityContextHolder.setContext(ctx);contexts.saveContext(ctx,req,res);tokens.saveToken(null,req,res);return Api.ok(users.byId(((LoginUser)auth.getPrincipal()).id()));
    }
    @PostMapping("/api/auth/register") public Object register(@RequestBody Map<String,Object> p){return Api.ok(service.register(p));}
    @GetMapping("/api/auth/me") public Object me(){return Api.ok(users.byId(Actor.current().id()));}
    @PostMapping("/api/auth/logout") public Object logout(HttpServletRequest req,HttpServletResponse res){SecurityContextHolder.clearContext();var s=req.getSession(false);if(s!=null)s.invalidate();return Api.ok(Map.of("message","已退出"));}
    @GetMapping("/api/profile") public Object profile(){return Api.ok(users.profile(Actor.current().id()));}
    @PutMapping("/api/profile") public Object update(@RequestBody Map<String,Object> p){return Api.ok(service.profile(p));}
}
