package com.campus.oj.controller;

import com.campus.oj.entity.Submission;
import com.campus.oj.repository.SubmissionRepository;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import org.springframework.web.bind.annotation.*;
import java.util.*;
import com.campus.oj.service.ExamService;
import com.campus.oj.entity.User;
import javax.servlet.http.HttpServletRequest;

@RestController @RequestMapping("/api/results")
public class ResultController {
    private final ExamService exams;
    public ResultController(ExamService exams) { this.exams=exams; }
    @GetMapping("/my") public Object mine(@RequestHeader("X-User-Id") Long userId) { return exams.results(userId,null,false); }
    @GetMapping public Object list(@RequestParam(required=false) Long userId,@RequestParam(required=false) Long examId) { return exams.results(userId,examId,true); }
    @GetMapping("/analytics") public Object analytics(@RequestParam Long examId) { return exams.analytics(examId); }
    @GetMapping("/{examId}") public Object exam(@PathVariable Long examId,@RequestParam(required=false) Long userId) { return exams.results(userId,examId,true); }
    @GetMapping("/{examId}/detail") public Object detail(@PathVariable Long examId,@RequestParam(required=false) Long userId,HttpServletRequest request) {
        User user=(User)request.getAttribute("authenticatedUser");
        if(!"ADMIN".equals(user.role) && userId!=null && !user.id.equals(userId))
            throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.FORBIDDEN,"不能查看他人的成绩");
        return exams.resultDetail(examId,"ADMIN".equals(user.role) && userId!=null ? userId : user.id);
    }
}
