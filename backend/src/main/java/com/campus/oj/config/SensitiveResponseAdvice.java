package com.campus.oj.config;

import com.campus.oj.entity.Question;
import com.campus.oj.entity.User;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.core.MethodParameter;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice;
import java.util.*;

@RestControllerAdvice
public class SensitiveResponseAdvice implements ResponseBodyAdvice<Object> {
    private final ObjectMapper json;
    public SensitiveResponseAdvice(ObjectMapper json) { this.json = json; }

    @Override public boolean supports(MethodParameter method, Class<? extends HttpMessageConverter<?>> converter) { return true; }

    @Override public Object beforeBodyWrite(Object body, MethodParameter method, MediaType type,
            Class<? extends HttpMessageConverter<?>> converter, ServerHttpRequest request, ServerHttpResponse response) {
        User user = request instanceof ServletServerHttpRequest
                ? (User) ((ServletServerHttpRequest) request).getServletRequest().getAttribute("authenticatedUser") : null;
        return sanitize(body, user != null && "ADMIN".equals(user.role));
    }

    private Object sanitize(Object body, boolean admin) {
        if (body instanceof Question && !admin) {
            Map<?, ?> values = json.convertValue(body, Map.class);
            Map<Object, Object> safe = new LinkedHashMap<>(values);
            safe.remove("standardAnswer");
            return safe;
        }
        if (body instanceof Collection<?>) {
            List<Object> safe = new ArrayList<>();
            for (Object item : (Collection<?>) body) safe.add(sanitize(item, admin));
            return safe;
        }
        if (body instanceof Map<?, ?>) {
            Map<Object, Object> safe = new LinkedHashMap<>();
            ((Map<?, ?>) body).forEach((key, value) -> safe.put(key, sanitize(value, admin)));
            return safe;
        }
        return body;
    }
}
