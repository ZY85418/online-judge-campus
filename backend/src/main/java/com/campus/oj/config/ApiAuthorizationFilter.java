package com.campus.oj.config;

import com.campus.oj.entity.User;
import com.campus.oj.service.SessionService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.util.UrlPathHelper;
import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletRequestWrapper;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.*;

@Component
public class ApiAuthorizationFilter extends OncePerRequestFilter {
    private final SessionService sessions;
    private final ObjectMapper json;

    public ApiAuthorizationFilter(SessionService sessions, ObjectMapper json) {
        this.sessions = sessions;
        this.json = json;
    }

    @Override protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                              FilterChain chain) throws ServletException, IOException {
        String path = new UrlPathHelper().getPathWithinApplication(request);
        String method = request.getMethod();
        if (!path.startsWith("/api/") || "OPTIONS".equals(method)
                || ("POST".equals(method) && ("/api/auth/login".equals(path) || "/api/auth/register".equals(path)))) {
            chain.doFilter(request, response);
            return;
        }
        User user;
        try {
            user = sessions.authenticate(request.getHeader("Authorization"));
            if (!"ADMIN".equals(user.role) && !"STUDENT".equals(user.role)) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Unsupported role");
            }
            boolean read = "GET".equals(method) || "HEAD".equals(method);
            boolean studentAllowed = (read && (path.matches("/api/questions(?:/[0-9]+)?/?")
                        || path.matches("/api/exams/?") || "/api/exams/my-status".equals(path) || "/api/submissions/my".equals(path)
                        || "/api/results/my".equals(path) || path.matches("/api/results/[0-9]+/detail")
                        || path.matches("/api/submissions/[0-9]+") || path.matches("/api/exams/[0-9]+/session")
                        || "/api/auth/current".equals(path)))
                    || ("PUT".equals(method) && "/api/auth/profile".equals(path))
                    || ("POST".equals(method) && ("/api/submissions/run".equals(path)
                        || "/api/submissions/submit".equals(path) || "/api/auth/logout".equals(path)
                        || path.matches("/api/exams/[0-9]+/(?:start|submit)")));
            if (!studentAllowed && !"ADMIN".equals(user.role)) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Administrator role required");
            }
        } catch (ResponseStatusException error) {
            response.setStatus(error.getStatus().value());
            response.setContentType("application/json");
            response.setCharacterEncoding("UTF-8");
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("status", error.getStatus().value());
            body.put("message", error.getReason());
            json.writeValue(response.getWriter(), body);
            return;
        }
        final String userId = String.valueOf(user.id);
        request.setAttribute("authenticatedUser", user);
        // Existing controllers receive only the identity verified from the session.
        HttpServletRequest verifiedRequest = new HttpServletRequestWrapper(request) {
            @Override public String getHeader(String name) {
                return "X-User-Id".equalsIgnoreCase(name) ? userId : super.getHeader(name);
            }
            @Override public Enumeration<String> getHeaders(String name) {
                return "X-User-Id".equalsIgnoreCase(name)
                        ? Collections.enumeration(Collections.singletonList(userId)) : super.getHeaders(name);
            }
            @Override public Enumeration<String> getHeaderNames() {
                List<String> names = Collections.list(super.getHeaderNames());
                names.removeIf(name -> "X-User-Id".equalsIgnoreCase(name));
                names.add("X-User-Id");
                return Collections.enumeration(names);
            }
        };
        chain.doFilter(verifiedRequest, response);
    }
}
