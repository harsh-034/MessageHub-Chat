package com.messagehub.messagehub.controller;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class UserController {

    private final JdbcTemplate db;

    public UserController(JdbcTemplate db) {
        this.db = db;
    }

    // =====================================================
    // CURRENT USER
    // GET /api/me
    // =====================================================

    @GetMapping("/me")
    public ResponseEntity<Map<String, Object>> getCurrentUser(
            Authentication authentication
    ) {

        try {

            Long userId = getUserId(authentication);

            if (userId == null) {
                return error(
                        HttpStatus.UNAUTHORIZED,
                        "Authentication required"
                );
            }

            // Update last seen
            db.update(
                    """
                    UPDATE users
                    SET last_seen = NOW()
                    WHERE id = ?
                    """,
                    userId
            );

            List<Map<String, Object>> rows =
                    db.queryForList(
                            """
                            SELECT
                                id,
                                username,
                                last_seen
                            FROM users
                            WHERE id = ?
                            LIMIT 1
                            """,
                            userId
                    );

            if (rows.isEmpty()) {

                return error(
                        HttpStatus.NOT_FOUND,
                        "User not found"
                );
            }

            Map<String, Object> user =
                    rows.get(0);

            Map<String, Object> response =
                    new HashMap<>();

            response.put("success", true);
            response.put("user", user);

            return ResponseEntity.ok(response);

        } catch (Exception error) {

            error.printStackTrace();

            return error(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "Failed to load current user"
            );
        }
    }

    // =====================================================
    // ALL USERS
    // GET /api/users
    // =====================================================

    @GetMapping("/users")
    public ResponseEntity<Map<String, Object>> getUsers(
            Authentication authentication
    ) {

        try {

            Long currentUserId =
                    getUserId(authentication);

            if (currentUserId == null) {
                return error(
                        HttpStatus.UNAUTHORIZED,
                        "Authentication required"
                );
            }

            // Update own last seen
            db.update(
                    """
                    UPDATE users
                    SET last_seen = NOW()
                    WHERE id = ?
                    """,
                    currentUserId
            );

            // Get all other users
            List<Map<String, Object>> users =
                    db.queryForList(
                            """
                            SELECT
                                id,
                                username,
                                last_seen,

                                CASE
                                    WHEN last_seen >=
                                        DATE_SUB(
                                            NOW(),
                                            INTERVAL 45 SECOND
                                        )
                                    THEN 1
                                    ELSE 0
                                END AS online

                            FROM users

                            WHERE id <> ?

                            ORDER BY username ASC
                            """,
                            currentUserId
                    );

            Map<String, Object> response =
                    new HashMap<>();

            response.put("success", true);
            response.put("users", users);

            return ResponseEntity.ok(response);

        } catch (Exception error) {

            error.printStackTrace();

            return error(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "Failed to load users"
            );
        }
    }

    // =====================================================
    // DELETE ACCOUNT
    // DELETE /api/account
    // =====================================================

    @DeleteMapping("/account")
    public ResponseEntity<Map<String, Object>> deleteAccount(
            Authentication authentication
    ) {

        try {

            Long userId = getUserId(authentication);

            if (userId == null) {
                return error(
                        HttpStatus.UNAUTHORIZED,
                        "Authentication required"
                );
            }

            // -------------------------------------------------
            // Delete messages sent/received by user
            // -------------------------------------------------

            db.update(
                    """
                    DELETE FROM messages
                    WHERE sender_id = ?
                       OR receiver_id = ?
                    """,
                    userId,
                    userId
            );

            // -------------------------------------------------
            // Delete conversation memberships
            // -------------------------------------------------

            db.update(
                    """
                    DELETE FROM conversation_members
                    WHERE user_id = ?
                    """,
                    userId
            );

            // -------------------------------------------------
            // Delete conversations that no longer
            // have members
            // -------------------------------------------------

            db.update(
                    """
                    DELETE FROM conversations
                    WHERE id NOT IN (
                        SELECT DISTINCT conversation_id
                        FROM conversation_members
                    )
                    """
            );

            // -------------------------------------------------
            // Delete user
            // -------------------------------------------------

            int deleted =
                    db.update(
                            """
                            DELETE FROM users
                            WHERE id = ?
                            """,
                            userId
                    );

            if (deleted == 0) {

                return error(
                        HttpStatus.NOT_FOUND,
                        "User not found"
                );
            }

            Map<String, Object> response =
                    new HashMap<>();

            response.put("success", true);
            response.put(
                    "message",
                    "Account deleted successfully"
            );

            return ResponseEntity.ok(response);

        } catch (Exception error) {

            error.printStackTrace();

            return error(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "Failed to delete account"
            );
        }
    }

    // =====================================================
    // GET USER ID FROM JWT
    // =====================================================

    private Long getUserId(
            Authentication authentication
    ) {

        if (authentication == null ||
                authentication.getPrincipal() == null) {

            return null;
        }

        try {

            return Long.valueOf(
                    String.valueOf(
                            authentication.getPrincipal()
                    )
            );

        } catch (Exception error) {

            return null;
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