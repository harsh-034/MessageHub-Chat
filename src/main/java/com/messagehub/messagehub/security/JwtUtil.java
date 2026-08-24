package com.messagehub.messagehub.security;


import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

@Component
public class JwtUtil {

    private final SecretKey secretKey;

    public JwtUtil(
            @Value("${messagehub.jwt.secret}") String secret
    ) {
        this.secretKey = Keys.hmacShaKeyFor(
                secret.getBytes(StandardCharsets.UTF_8)
        );
    }

    // =====================================================
    // CREATE TOKEN
    // =====================================================

    public String generateToken(
            Long userId,
            String username
    ) {

        return Jwts.builder()
                .subject(String.valueOf(userId))
                .claim("username", username)
                .issuedAt(new Date())
                .expiration(
                        new Date(
                                System.currentTimeMillis()
                                        + (7L * 24 * 60 * 60 * 1000)
                        )
                )
                .signWith(secretKey)
                .compact();
    }

    // =====================================================
    // GET USER ID
    // =====================================================

    public Long getUserId(String token) {

        Claims claims = getClaims(token);

        return Long.valueOf(
                claims.getSubject()
        );
    }

    // =====================================================
    // GET USERNAME
    // =====================================================

    public String getUsername(String token) {

        Claims claims = getClaims(token);

        return claims.get("username", String.class);
    }

    // =====================================================
    // VALIDATE TOKEN
    // =====================================================

    public boolean isValid(String token) {

        try {

            getClaims(token);

            return true;

        } catch (Exception error) {

            return false;
        }
    }

    // =====================================================
    // GET CLAIMS
    // =====================================================

    private Claims getClaims(String token) {

        return Jwts.parser()
                .verifyWith(secretKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}