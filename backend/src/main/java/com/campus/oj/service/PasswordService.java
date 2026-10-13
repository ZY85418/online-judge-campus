package com.campus.oj.service;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

@Service
public class PasswordService {
    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();

    public String encode(String password) { return encoder.encode(password); }

    public boolean isEncoded(String password) {
        return password != null && password.matches("\\$2[aby]\\$[0-9]{2}\\$[./A-Za-z0-9]{53}");
    }

    public boolean matches(String raw, String stored) {
        if (raw == null || stored == null || raw.getBytes(StandardCharsets.UTF_8).length > 72) return false;
        if (isEncoded(stored)) return encoder.matches(raw, stored);
        return MessageDigest.isEqual(raw.getBytes(StandardCharsets.UTF_8), stored.getBytes(StandardCharsets.UTF_8));
    }
}
