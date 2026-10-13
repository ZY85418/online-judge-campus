package com.campus.oj.controller;

import com.campus.oj.entity.Question;
import com.campus.oj.repository.QuestionRepository;
import com.campus.oj.repository.SubmissionRepository;
import com.campus.oj.service.JudgeService;
import com.campus.oj.service.ExamService;
import com.campus.oj.entity.Submission;
import com.campus.oj.entity.User;
import javax.servlet.http.HttpServletRequest;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import java.util.*;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;

@RestController @RequestMapping("/api/submissions")
public class SubmissionController {
    private final QuestionRepository questions; private final SubmissionRepository submissions; private final ExamService exams;
    public SubmissionController(QuestionRepository questions, SubmissionRepository submissions, ExamService exams) { this.questions=questions; this.submissions=submissions; this.exams=exams; }
    @PostMapping("/run") public Map<String,Object> run(@RequestBody Map<String,Object> body,@RequestHeader("X-User-Id") Long userId) { return exams.judge(longValue(body.get("examId")),userId,questionId(body),code(body),false); }
    @PostMapping("/submit") public Map<String,Object> submit(@RequestBody Map<String,Object> body, @RequestHeader("X-User-Id") Long userId) { return exams.judge(longValue(body.get("examId")),userId,questionId(body),code(body),true); }
    @GetMapping("/my") public Object mine(@RequestHeader(value="X-User-Id", defaultValue="1") Long userId) { return submissions.selectList(new QueryWrapper<com.campus.oj.entity.Submission>().eq("user_id", userId).orderByDesc("submit_time")); }
    @GetMapping("/admin") public List<Map<String,Object>> all(@RequestParam(required=false) Long userId,
            @RequestParam(required=false) Long examId, @RequestParam(required=false) Long questionId,
            @RequestParam(required=false) String result) {
        QueryWrapper<Submission> query = new QueryWrapper<Submission>().select("id", "user_id", "exam_id", "question_id", "result", "score", "judge_time", "submit_time");
        if (userId != null) query.eq("user_id", validFilterId(userId));
        if (examId != null) query.eq("exam_id", validFilterId(examId));
        if (questionId != null) query.eq("question_id", validFilterId(questionId));
        if (result != null && !result.trim().isEmpty()) {
            String normalized = result.trim();
            if (!Arrays.asList("AC", "WA", "RE").contains(normalized)) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "判题结果筛选值无效");
            query.eq("result", normalized);
        }
        List<Map<String,Object>> rows = new ArrayList<>();
        for (Submission row : submissions.selectList(query.orderByDesc("submit_time").last("LIMIT 200"))) {
            Map<String,Object> summary = new LinkedHashMap<>();
            summary.put("id", row.id); summary.put("userId", row.userId); summary.put("examId", row.examId);
            summary.put("questionId", row.questionId); summary.put("result", row.result); summary.put("score", row.score);
            summary.put("judgeTime", row.judgeTime); summary.put("submitTime", row.submitTime);
            rows.add(summary);
        }
        return rows;
    }
    @GetMapping("/{id}") public Submission detail(@PathVariable Long id,HttpServletRequest request) {
        Submission row=submissions.selectById(id); User user=(User)request.getAttribute("authenticatedUser");
        if(row==null) throw new ResponseStatusException(HttpStatus.NOT_FOUND,"提交记录不存在");
        if(!"ADMIN".equals(user.role) && !user.id.equals(row.userId)) throw new ResponseStatusException(HttpStatus.FORBIDDEN,"不能查看他人的提交记录");
        return row;
    }
    private Long questionId(Map<String,Object> body) {
        Long id = longValue(body.get("questionId"));
        if (id == null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "缺少题目编号");
        return id;
    }
    private Long longValue(Object value) {
        if (value == null) return null;
        try {
            if (!(value instanceof Number)) throw new ArithmeticException();
            long id = new BigDecimal(value.toString()).longValueExact();
            if (id <= 0) throw new ArithmeticException();
            return id;
        } catch (ArithmeticException | NumberFormatException error) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "编号必须是有效的正整数");
        }
    }
    private Long validFilterId(Long id) {
        if (id == null || id <= 0) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "筛选编号必须是正整数");
        return id;
    }
    private String code(Map<String,Object> body) {
        Object value = body.get("code");
        if (value == null) return "";
        if (!(value instanceof String)) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "代码必须是文本");
        String code = (String) value;
        if (code.getBytes(StandardCharsets.UTF_8).length > 60000) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "代码不能超过60000字节");
        return code;
    }
}
