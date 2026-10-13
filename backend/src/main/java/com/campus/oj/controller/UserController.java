package com.campus.oj.controller;

import com.campus.oj.service.UserService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import java.util.Map;

@RestController @RequestMapping("/api/users")
public class UserController {
    private final UserService users;
    public UserController(UserService users) { this.users=users; }

    @GetMapping("/students") public Map<String,Object> students(@RequestParam(required=false) String keyword,
            @RequestParam(required=false) Long userId,@RequestParam(required=false) String status,
            @RequestParam(defaultValue="1") int page,@RequestParam(defaultValue="50") int pageSize) {
        if(page<1 || pageSize<1 || pageSize>100 || (userId!=null && userId<=0)
                || (status!=null && !java.util.Arrays.asList("ACTIVE","INACTIVE").contains(status)))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"学生筛选或分页参数无效");
        return users.students(keyword,userId,status,page,pageSize);
    }

    @GetMapping("/students/{id}") public Map<String,Object> detail(@PathVariable Long id) {
        if(id==null || id<=0) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"学生编号无效");
        return users.detail(id);
    }
}
