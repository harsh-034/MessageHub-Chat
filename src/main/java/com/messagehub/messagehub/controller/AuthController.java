package com.messagehub.messagehub.controller;

import com.messagehub.messagehub.security.JwtUtil;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class AuthController {

    private final JdbcTemplate db;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;

    public AuthController(
            JdbcTemplate db,
            PasswordEncoder passwordEncoder,
            JwtUtil jwtUtil
    ) {
        this.db = db;
        this.passwordEncoder = passwordEncoder;
        this.jwtUtil = jwtUtil;
    }

    // =====================================================
    // REGISTER
    // =====================================================

    @PostMapping("/register")
    public ResponseEntity<Map<String, Object>> register(
            @RequestBody Map<String, String> body
    ) {

        try {

            String username =
                    body.getOrDefault(
                            "username",
                            ""
                    ).trim();

            String password =
                    body.getOrDefault(
                            "password",
                            ""
                    );

            // -----------------------------
            // Validation
            // -----------------------------

            if (username.isEmpty() ||
                    password.isEmpty()) {

                return error(
                        HttpStatus.BAD_REQUEST,
                        "Username and password are required"
                );
            }

            if (username.length() < 2) {

                return error(
                        HttpStatus.BAD_REQUEST,
                        "Username must contain at least 2 characters"
                );
            }

            if (username.length() > 30) {

                return error(
                        HttpStatus.BAD_REQUEST,
                        "Username cannot exceed 30 characters"
                );
            }

            if (password.length() < 6) {

                return error(
                        HttpStatus.BAD_REQUEST,
                        "Password must contain at least 6 characters"
                );
            }

            // -----------------------------
            // Check existing username
            // -----------------------------

            Integer count = db.queryForObject(
                    """
                    SELECT COUNT(*)
                    FROM users
                    WHERE username = ?
                    """,
                    Integer.class,
                    username
            );

            if (count != null && count > 0) {

                return error(
                        HttpStatus.CONFLICT,
                        "Username already exists"
                );
            }

            // -----------------------------
            // Hash password
            // -----------------------------

            String hashedPassword =
                    passwordEncoder.encode(password);

            // -----------------------------
            // Insert user
            // -----------------------------

            int result = db.update(
                    """
                    INSERT INTO users
                    (
                        username,
                        password,
                        last_seen
                    )
                    VALUES (?, ?, NOW())
                    """,
                    username,
                    hashedPassword
            );

            if (result == 0) {

                return error(
                        HttpStatus.INTERNAL_SERVER_ERROR,
                        "Registration failed"
                );
            }

            // -----------------------------
            // Get new user
            // -----------------------------

            Long userId = db.queryForObject(
                    """
                    SELECT id
                    FROM users
                    WHERE username = ?
                    LIMIT 1
                    """,
                    Long.class,
                    username
            );

            if (userId == null) {

                return error(
                        HttpStatus.INTERNAL_SERVER_ERROR,
                        "Failed to create user"
                );
            }

            // -----------------------------
            // Create JWT
            // -----------------------------

            String token =
                    jwtUtil.generateToken(
                            userId,
                            username
                    );

            Map<String, Object> user =
                    new HashMap<>();

            user.put("id", userId);
            user.put("username", username);

            Map<String, Object> response =
                    new HashMap<>();

            response.put("success", true);
            response.put("token", token);
            response.put("user", user);

            return ResponseEntity
                    .status(HttpStatus.CREATED)
                    .body(response);

        } catch (Exception error) {

            error.printStackTrace();

            return error(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "Registration failed"
            );
        }
    }

    // =====================================================
    // LOGIN
    // =====================================================

    @PostMapping("/login")
    public ResponseEntity<Map<String, Object>> login(
            @RequestBody Map<String, String> body
    ) {

        try {

            String username =
                    body.getOrDefault(
                            "username",
                            ""
                    ).trim();

            String password =
                    body.getOrDefault(
                            "password",
                            ""
                    );

            // -----------------------------
            // Validation
            // -----------------------------

            if (username.isEmpty() ||
                    password.isEmpty()) {

                return error(
                        HttpStatus.BAD_REQUEST,
                        "Username and password are required"
                );
            }

            // -----------------------------
            // Find user
            // -----------------------------

            List<Map<String, Object>> rows =
                    db.queryForList(
                            """
                            SELECT
                                id,
                                username,
                                password
                            FROM users
                            WHERE username = ?
                            LIMIT 1
                            """,
                            username
                    );

            if (rows.isEmpty()) {

                return error(
                        HttpStatus.UNAUTHORIZED,
                        "Invalid username or password"
                );
            }

            Map<String, Object> userRow =
                    rows.get(0);

            Long userId =
                    ((Number) userRow.get("id"))
                            .longValue();

            String dbUsername =
                    String.valueOf(
                            userRow.get("username")
                    );

            String storedPassword =
                    String.valueOf(
                            userRow.get("password")
                    );

            // -----------------------------
            // Check password
            // -----------------------------

            boolean valid =
                    passwordEncoder.matches(
                            password,
                            storedPassword
                    );

            if (!valid) {

                return error(
                        HttpStatus.UNAUTHORIZED,
                        "Invalid username or password"
                );
            }

            // -----------------------------
            // Update last seen
            // -----------------------------

            db.update(
                    """
                    UPDATE users
                    SET last_seen = NOW()
                    WHERE id = ?
                    """,
                    userId
            );

            // -----------------------------
            // Create JWT
            // -----------------------------

            String token =
                    jwtUtil.generateToken(
                            userId,
                            dbUsername
                    );

            Map<String, Object> user =
                    new HashMap<>();

            user.put("id", userId);
            user.put("username", dbUsername);

            Map<String, Object> response =
                    new HashMap<>();

            response.put("success", true);
            response.put("token", token);
            response.put("user", user);

            return ResponseEntity.ok(response);

        } catch (Exception error) {

            error.printStackTrace();

            return error(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "Login failed"
            );
        }
    }

    // =====================================================
    // ERROR RESPONSE
    // =====================================================

    private ResponseEntity<Map<String, Object>> error(
            HttpStatus status,
            String message
    ) {

        Map<String, Object> response =
                new HashMap<>();

        response.put("success", false);
        response.put("error", message);

        return ResponseEntity
                .status(status)
                .body(response);
    }
}