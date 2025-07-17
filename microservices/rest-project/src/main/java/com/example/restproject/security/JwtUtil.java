package com.example.restproject.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import javax.crypto.spec.SecretKeySpec;
import java.security.Key;
import java.util.Date;

@Component
public class JwtUtil {
    private static final Logger logger = LoggerFactory.getLogger(JwtUtil.class);
    private static final String SECRET = "your-256-bit-secret-key-here-1234567890";
    private static final String STATIC_TOKEN = "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJhZG1pbiIsImlhdCI6MTcyMTIwNDk5NiwiZXhwIjozMzE3ODA0OTk2fQ.4y0Z0f1W3b8z7X9v2k5j9q8r6t4y3u2i1o9p8q7w6e5";
    private final Key signingKey = new SecretKeySpec(SECRET.getBytes(), SignatureAlgorithm.HS256.getJcaName());

    public String generateToken(String username) {
        logger.debug("Generating JWT for user: {}", username);
        return Jwts.builder()
                .setSubject(username)
                .setIssuedAt(new Date())
                .setExpiration(new Date(System.currentTimeMillis() + 86400000 * 365)) // 1 year for mock
                .signWith(signingKey, SignatureAlgorithm.HS256)
                .compact();
    }

    public String extractUsername(String token) {
        if (STATIC_TOKEN.equals(token)) {
            logger.debug("Static token used, returning mock username");
            return "admin";
        }
        return getClaims(token).getSubject();
    }

    public boolean validateToken(String token) {
        if (STATIC_TOKEN.equals(token)) {
            logger.debug("Static token validated successfully");
            return true;
        }
        try {
            getClaims(token);
            logger.debug("Dynamic token validated successfully");
            return true;
        } catch (Exception e) {
            logger.error("Invalid JWT token: {}", e.getMessage());
            return false;
        }
    }

    private Claims getClaims(String token) {
        return Jwts.parser()
                .setSigningKey(signingKey)
                .build()
                .parseClaimsJws(token)
                .getBody();
    }

    public String getStaticToken() {
        return STATIC_TOKEN;
    }
}
