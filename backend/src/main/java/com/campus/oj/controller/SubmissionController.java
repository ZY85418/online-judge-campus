package com.campus.oj.controller;

import com.campus.oj.entity.Question;
import com.campus.oj.repository.QuestionRepository;
import com.campus.oj.repository.SubmissionRepository;
import com.campus.oj.service.JudgeService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import java.util.*;

@RestController @RequestMapping("/api/submissions")
public class SubmissionController {
    private final QuestionRepository questions; private final SubmissionRepository submissions; private final JudgeService judge;
    public SubmissionController(QuestionRepository questions, SubmissionRepository submissions, JudgeService judge) { this.questions=questions; this.submissions=submissions; this.judge=judge; }
    @PostMapping("/run") public Map<String,Object> run(@RequestBody Map<String,Object> body) { return judge.judge(null, null, question(body), (String)body.get("code"), false); }
    @PostMapping("/submit") public Map<String,Object> submit(@RequestBody Map<String,Object> body, @RequestHeader(value="X-User-Id", defaultValue="1") Long userId) { return judge.judge(userId, longValue(body.get("examId")), question(body), (String)body.get("code"), true); }
    @GetMapping("/my") public Object mine(@RequestHeader(value="X-User-Id", defaultValue="1") Long userId) { return submissions.findByUserIdOrderBySubmitTimeDesc(userId); }
    private Question question(Map<String,Object> body) { return questions.findById(longValue(body.get("questionId"))).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,"题目不存在")); }
    private Long longValue(Object value) { if (value == null) return null; return ((Number)value).longValue(); }
}
