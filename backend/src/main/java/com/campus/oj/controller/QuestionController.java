package com.campus.oj.controller;

import com.campus.oj.entity.Question;
import com.campus.oj.repository.QuestionRepository;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import java.util.List;
import java.util.stream.Collectors;

@RestController @RequestMapping("/api/questions")
public class QuestionController {
    private final QuestionRepository questions;
    public QuestionController(QuestionRepository questions) { this.questions = questions; }
    @GetMapping public List<Question> list(@RequestParam(required=false) String category, @RequestParam(required=false) String difficulty) {
        return questions.findAll().stream().filter(q -> category == null || category.equals(q.category)).filter(q -> difficulty == null || difficulty.equals(q.difficulty)).collect(Collectors.toList());
    }
    @GetMapping("/{id}") public Question one(@PathVariable Long id) { return find(id); }
    @PostMapping @ResponseStatus(HttpStatus.CREATED) public Question create(@RequestBody Question body) { body.id = null; return questions.save(body); }
    @PutMapping("/{id}") public Question update(@PathVariable Long id, @RequestBody Question body) { body.id = id; return questions.save(body); }
    @DeleteMapping("/{id}") @ResponseStatus(HttpStatus.NO_CONTENT) public void delete(@PathVariable Long id) { questions.delete(find(id)); }
    private Question find(Long id) { return questions.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "题目不存在")); }
}
