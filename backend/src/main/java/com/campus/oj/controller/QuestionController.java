package com.campus.oj.controller;

import com.campus.oj.entity.*;
import com.campus.oj.service.QuestionService;
import javax.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import java.util.List;
import java.util.stream.Collectors;

@RestController @RequestMapping("/api/questions")
public class QuestionController {
    private final QuestionService questions;
    public QuestionController(QuestionService questions) { this.questions = questions; }
    @GetMapping public List<Question> list(@RequestParam(required=false) String category, @RequestParam(required=false) String difficulty, HttpServletRequest request) {
        return questions.list(category, difficulty, admin(request));
    }
    @GetMapping("/{id}") public Question one(@PathVariable Long id, HttpServletRequest request) { return questions.find(id, admin(request)); }
    @PostMapping @ResponseStatus(HttpStatus.CREATED) public Question create(@RequestBody Question body) { return questions.save(null, body); }
    @PutMapping("/{id}") public Question update(@PathVariable Long id, @RequestBody Question body) { return questions.save(id, body); }
    @DeleteMapping("/{id}") @ResponseStatus(HttpStatus.NO_CONTENT) public void delete(@PathVariable Long id) { questions.delete(id); }
    private boolean admin(HttpServletRequest request) { return "ADMIN".equals(((User) request.getAttribute("authenticatedUser")).role); }
}
