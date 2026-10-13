package com.campus.oj.config;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.*;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import java.util.*;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@RestControllerAdvice
public class ApiExceptionHandler {
    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, Object>> businessError(ResponseStatusException error) {
        return response(error.getStatus(), error.getReason());
    }
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Map<String, Object>> invalidJson(HttpMessageNotReadableException error) {
        return response(HttpStatus.BAD_REQUEST, "请求格式或字段类型不正确");
    }
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<Map<String,Object>> invalidParameter(MethodArgumentTypeMismatchException error) {
        return response(HttpStatus.BAD_REQUEST,"请求参数类型不正确");
    }
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<Map<String, Object>> conflictingData(DataIntegrityViolationException error) {
        return response(HttpStatus.CONFLICT, "数据冲突或字段不符合要求，请检查后重试");
    }
    private ResponseEntity<Map<String, Object>> response(HttpStatus status, String message) {
        Map<String, Object> body = new LinkedHashMap<>(); body.put("status", status.value()); body.put("message", message);
        return ResponseEntity.status(status).body(body);
    }
}
