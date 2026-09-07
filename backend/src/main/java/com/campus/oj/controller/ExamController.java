package com.campus.oj.controller;

import com.campus.oj.entity.Exam;
import com.campus.oj.repository.ExamRepository;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import java.time.LocalDateTime;
import java.util.List;

@RestController @RequestMapping("/api/exams")
public class ExamController {
    private final ExamRepository exams;
    public ExamController(ExamRepository exams) { this.exams=exams; }
    @GetMapping public List<Exam> list() { return exams.findAll(); }
    @PostMapping @ResponseStatus(HttpStatus.CREATED) public Exam create(@RequestBody Exam exam) { exam.id=null; return exams.save(exam); }
    @PutMapping("/{id}") public Exam update(@PathVariable Long id, @RequestBody Exam exam) { exam.id=id; return exams.save(exam); }
    @PostMapping("/{id}/start") public Exam start(@PathVariable Long id) { Exam exam=find(id); if (!"PUBLISHED".equals(exam.status)) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"考试尚未发布"); return exam; }
    @PostMapping("/{id}/submit") public Exam finish(@PathVariable Long id) { return find(id); }
    private Exam find(Long id) { return exams.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,"考试不存在")); }
}
