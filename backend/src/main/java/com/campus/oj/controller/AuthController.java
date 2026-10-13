package com.campus.oj.controller;

import com.campus.oj.entity.User;
import com.campus.oj.repository.UserRepository;
import com.campus.oj.service.SessionService;
import com.campus.oj.service.PasswordService;
import java.nio.charset.StandardCharsets;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import java.util.*;
import javax.servlet.http.HttpServletRequest;

@RestController @RequestMapping("/api/auth")
public class AuthController {
    private final UserRepository users;
    private final SessionService sessions;
    private final PasswordService passwords;
    public AuthController(UserRepository users, SessionService sessions, PasswordService passwords) {
        this.users = users; this.sessions = sessions; this.passwords = passwords;
    }
    @PostMapping("/register") public User register(@RequestBody User body) {
        validateCredentials(body.username, body.password);
        body.username = body.username.trim();
        if (body.realName != null && body.realName.length() > 50) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "姓名不能超过50个字符");
        if (findByUsername(body.username) != null) throw new ResponseStatusException(HttpStatus.CONFLICT, "用户名已存在");
        body.id = null; body.role = "STUDENT"; body.status = "ACTIVE"; body.createTime=java.time.LocalDateTime.now();
        body.password = passwords.encode(body.password); users.insert(body); return body;
    }
    @PostMapping("/login") public Map<String, Object> login(@RequestBody Map<String, String> body) {
        validateCredentials(body.get("username"), body.get("password"));
        User user = findByUsername(body.get("username").trim());
        if (user == null || !passwords.matches(body.get("password"), user.password)) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "用户名或密码错误");
        if (!"ACTIVE".equals(user.status)) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "账号不可用");
        if (!passwords.isEncoded(user.password)) {
            User update = new User(); update.id = user.id; update.password = passwords.encode(body.get("password"));
            update.role = null; update.status = null; update.createTime = null;
            users.updateById(update); user.password = update.password;
        }
        String token = sessions.create(user);
        Map<String, Object> result = new LinkedHashMap<>(); result.put("token", token); result.put("user", user); return result;
    }
    @PostMapping("/logout") public void logout(@RequestHeader("Authorization") String token) { sessions.logout(token); }
    @GetMapping("/current") public User current(@RequestHeader("Authorization") String token) { return sessions.authenticate(token); }
    @PutMapping("/profile") public User profile(@RequestBody Map<String,String> body,HttpServletRequest request) {
        String name=body.get("realName");
        if(name==null || name.trim().isEmpty() || name.length()>50 || body.size()!=1)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"仅允许修改姓名，姓名须为1至50个字符");
        User current=(User)request.getAttribute("authenticatedUser");
        User update=new User(); update.id=current.id; update.realName=name.trim(); update.role=null; update.status=null; update.createTime=null;
        if(users.updateById(update)!=1) throw new ResponseStatusException(HttpStatus.NOT_FOUND,"用户不存在");
        current.realName=update.realName; return current;
    }
    private User findByUsername(String username) { return users.selectOne(new QueryWrapper<User>().eq("username", username)); }
    private void validateCredentials(String username, String password) {
        if (username == null || username.trim().isEmpty() || password == null || password.trim().isEmpty())
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "用户名和密码不能为空");
        if (username.trim().length() > 50 || password.getBytes(StandardCharsets.UTF_8).length > 72)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "用户名最多50个字符，密码最多72字节");
    }
}
