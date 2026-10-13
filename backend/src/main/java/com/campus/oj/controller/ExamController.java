package com.campus.oj.controller;

import com.campus.oj.entity.Exam;
import com.campus.oj.entity.User;
import com.campus.oj.service.ExamService;
import javax.servlet.http.HttpServletRequest;
import java.util.Map;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import java.time.LocalDateTime;
import java.util.List;

@RestController @RequestMapping("/api/exams")
public class ExamController {
    private final ExamService exams;
    public ExamController(ExamService exams) { this.exams=exams; }
    @GetMapping public List<Exam> list(HttpServletRequest request) { return exams.list("ADMIN".equals(((User)request.getAttribute("authenticatedUser")).role)); }
    @GetMapping("/my-status") public List<Map<String,Object>> myStatuses(@RequestHeader("X-User-Id") Long userId) { return exams.myStatuses(userId); }
    @PostMapping @ResponseStatus(HttpStatus.CREATED) public Exam create(@RequestBody Exam exam) { return exams.save(null,exam); }
    @PutMapping("/{id}") public Exam update(@PathVariable Long id,@RequestBody Exam exam) { return exams.save(id,exam); }
    @PostMapping("/{id}/close") public Exam close(@PathVariable Long id) { return exams.close(id); }
    @PostMapping("/{id}/start") public Map<String,Object> start(@PathVariable Long id,@RequestHeader("X-User-Id") Long userId) { return exams.start(id,userId); }
    @GetMapping("/{id}/session") public Map<String,Object> current(@PathVariable Long id,@RequestHeader("X-User-Id") Long userId) { return exams.current(id,userId); }
    @PostMapping("/{id}/submit") public Map<String,Object> finish(@PathVariable Long id,@RequestHeader("X-User-Id") Long userId) { return exams.finish(id,userId); }
}
