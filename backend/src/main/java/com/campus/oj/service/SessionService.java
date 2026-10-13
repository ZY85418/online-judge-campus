package com.campus.oj.service;

import com.campus.oj.entity.User;
import com.campus.oj.repository.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class SessionService {
    private static final long SESSION_DURATION = 8 * 60 * 60 * 1000L;
    private final Map<String, Session> sessions = new ConcurrentHashMap<>();
    private final UserRepository users;

    public SessionService(UserRepository users) { this.users = users; }

    public String create(User user) {
        requireActive(user);
        long now = System.currentTimeMillis();
        sessions.entrySet().removeIf(entry -> entry.getValue().expiresAt <= now);
        String token = UUID.randomUUID().toString();
        sessions.put(token, new Session(user.id, now + SESSION_DURATION));
        return token;
    }

    public User authenticate(String authorization) {
        String token = token(authorization);
        Session session = sessions.get(token);
        if (session == null || session.expiresAt <= System.currentTimeMillis()) {
            sessions.remove(token);
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Login required or session expired");
        }
        User user = users.selectById(session.userId);
        requireActive(user);
        return user;
    }

    public void logout(String authorization) { sessions.remove(token(authorization)); }

    private String token(String authorization) {
        if (authorization == null || !authorization.startsWith("Bearer ") || authorization.substring(7).trim().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Bearer token required");
        }
        return authorization.substring(7).trim();
    }

    private void requireActive(User user) {
        if (user == null || !"ACTIVE".equals(user.status)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Account unavailable");
        }
    }

    private static class Session {
        final Long userId;
        final long expiresAt;
        Session(Long userId, long expiresAt) { this.userId = userId; this.expiresAt = expiresAt; }
    }
}
