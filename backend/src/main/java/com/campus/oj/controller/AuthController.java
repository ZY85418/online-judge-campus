package com.campus.oj.controller;

import com.campus.oj.entity.User;
import com.campus.oj.repository.UserRepository;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import java.util.*;

@RestController @RequestMapping("/api/auth")
public class AuthController {
    private final UserRepository users;
    private final Map<String, User> sessions = new HashMap<>();
    public AuthController(UserRepository users) { this.users = users; }
    @PostMapping("/register") public User register(@RequestBody User body) {
        if (body.username == null || body.password == null || body.username.trim().isEmpty()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "用户名和密码不能为空");
        if (findByUsername(body.username) != null) throw new ResponseStatusException(HttpStatus.CONFLICT, "用户名已存在");
        body.id = null; body.role = "STUDENT"; body.status = "ACTIVE"; users.insert(body); return body;
    }
    @PostMapping("/login") public Map<String, Object> login(@RequestBody Map<String, String> body) {
        User user = findByUsername(body.get("username"));
        if (user == null || !user.password.equals(body.get("password"))) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "用户名或密码错误");
        String token = UUID.randomUUID().toString(); sessions.put(token, user);
        Map<String, Object> result = new LinkedHashMap<>(); result.put("token", token); result.put("user", user); return result;
    }
    @PostMapping("/logout") public void logout(@RequestHeader(value="Authorization", required=false) String token) { if (token != null) sessions.remove(token); }
    @GetMapping("/current") public User current(@RequestHeader("Authorization") String token) { return getUser(token); }
    private User getUser(String token) { User user = sessions.get(token); if (user == null) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "登录已过期"); return user; }
    private User findByUsername(String username) { return users.selectOne(new QueryWrapper<User>().eq("username", username)); }
}
