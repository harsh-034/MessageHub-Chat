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
@RequestMapping("/api/messages")
public class MessageController {

    private final JdbcTemplate db;

    public MessageController(JdbcTemplate db) {
        this.db = db;
    }

    // =====================================================
    // GET MESSAGES
    // GET /api/messages/{receiverId}
    // =====================================================

    @GetMapping("/{receiverId}")
    public ResponseEntity<Map<String, Object>> getMessages(
            @PathVariable Long receiverId,
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

            if (receiverId == null ||
                    receiverId <= 0) {

                return error(
                        HttpStatus.BAD_REQUEST,
                        "Invalid receiver"
                );
            }

            if (receiverId.equals(currentUserId)) {

                return error(
                        HttpStatus.BAD_REQUEST,
                        "You cannot message yourself"
                );
            }

            // -------------------------------------------------
            // Check receiver
            // -------------------------------------------------

            Integer receiverCount =
                    db.queryForObject(
                            """
                            SELECT COUNT(*)
                            FROM users
                            WHERE id = ?
                            """,
                            Integer.class,
                            receiverId
                    );

            if (receiverCount == null ||
                    receiverCount == 0) {

                return error(
                        HttpStatus.NOT_FOUND,
                        "Receiver not found"
                );
            }

            // -------------------------------------------------
            // Find conversation
            // -------------------------------------------------

            Long conversationId =
                    findConversation(
                            currentUserId,
                            receiverId
                    );

            // No conversation = no messages
            if (conversationId == null) {

                Map<String, Object> response =
                        new HashMap<>();

                response.put("success", true);
                response.put(
                        "messages",
                        List.of()
                );

                return ResponseEntity.ok(response);
            }

            // -------------------------------------------------
            // Get messages
            // -------------------------------------------------

            List<Map<String, Object>> messages =
                    db.queryForList(
                            """
                            SELECT
                                m.id,
                                m.sender_id,
                                m.receiver_id,
                                m.message,
                                m.created_at,
                                u.username

                            FROM messages m

                            JOIN users u
                                ON u.id = m.sender_id

                            WHERE m.conversation_id = ?

                            ORDER BY
                                m.created_at ASC,
                                m.id ASC
                            """,
                            conversationId
                    );

            Map<String, Object> response =
                    new HashMap<>();

            response.put("success", true);
            response.put("messages", messages);

            return ResponseEntity.ok(response);

        } catch (Exception error) {

            error.printStackTrace();

            return error(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "Failed to load messages"
            );
        }
    }

    // =====================================================
    // SEND MESSAGE
    // POST /api/messages
    // =====================================================

    @PostMapping
    public ResponseEntity<Map<String, Object>> sendMessage(
            @RequestBody Map<String, Object> body,
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

            // -------------------------------------------------
            // Receiver ID
            // -------------------------------------------------

            Object receiverObject =
                    body.get("receiverId");

            Long receiverId;

            try {

                receiverId =
                        Long.valueOf(
                                String.valueOf(
                                        receiverObject
                                )
                        );

            } catch (Exception error) {

                return error(
                        HttpStatus.BAD_REQUEST,
                        "Invalid receiver"
                );
            }

            // -------------------------------------------------
            // Message
            // -------------------------------------------------

            String message =
                    String.valueOf(
                            body.getOrDefault(
                                    "message",
                                    ""
                            )
                    ).trim();

            if (message.isEmpty()) {

                return error(
                        HttpStatus.BAD_REQUEST,
                        "Message cannot be empty"
                );
            }

            if (message.length() > 5000) {

                return error(
                        HttpStatus.BAD_REQUEST,
                        "Message is too long"
                );
            }

            // -------------------------------------------------
            // Prevent self messaging
            // -------------------------------------------------

            if (receiverId.equals(currentUserId)) {

                return error(
                        HttpStatus.BAD_REQUEST,
                        "You cannot message yourself"
                );
            }

            // -------------------------------------------------
            // Check receiver
            // -------------------------------------------------

            Integer receiverCount =
                    db.queryForObject(
                            """
                            SELECT COUNT(*)
                            FROM users
                            WHERE id = ?
                            """,
                            Integer.class,
                            receiverId
                    );

            if (receiverCount == null ||
                    receiverCount == 0) {

                return error(
                        HttpStatus.NOT_FOUND,
                        "Receiver not found"
                );
            }

            // -------------------------------------------------
            // Get or create conversation
            // -------------------------------------------------

            Long conversationId =
                    getOrCreateConversation(
                            currentUserId,
                            receiverId
                    );

            // -------------------------------------------------
            // Insert message
            // -------------------------------------------------

            int result =
                    db.update(
                            """
                            INSERT INTO messages
                            (
                                conversation_id,
                                sender_id,
                                receiver_id,
                                message
                            )
                            VALUES (?, ?, ?, ?)
                            """,
                            conversationId,
                            currentUserId,
                            receiverId,
                            message
                    );

            if (result == 0) {

                return error(
                        HttpStatus.INTERNAL_SERVER_ERROR,
                        "Failed to send message"
                );
            }

            // -------------------------------------------------
            // Get inserted message ID
            // -------------------------------------------------

            Long messageId =
                    db.queryForObject(
                            """
                            SELECT id
                            FROM messages
                            WHERE conversation_id = ?
                              AND sender_id = ?
                              AND receiver_id = ?
                              AND message = ?
                            ORDER BY id DESC
                            LIMIT 1
                            """,
                            Long.class,
                            conversationId,
                            currentUserId,
                            receiverId,
                            message
                    );

            // -------------------------------------------------
            // Update last seen
            // -------------------------------------------------

            db.update(
                    """
                    UPDATE users
                    SET last_seen = NOW()
                    WHERE id = ?
                    """,
                    currentUserId
            );

            Map<String, Object> response =
                    new HashMap<>();

            response.put("success", true);
            response.put("messageId", messageId);
            response.put(
                    "conversationId",
                    conversationId
            );

            return ResponseEntity
                    .status(HttpStatus.CREATED)
                    .body(response);

        } catch (Exception error) {

            error.printStackTrace();

            return error(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "Failed to send message"
            );
        }
    }

    // =====================================================
    // DELETE MESSAGE
    // DELETE /api/messages/{id}
    // =====================================================

    @DeleteMapping("/{id}")
    public ResponseEntity<Map<String, Object>> deleteMessage(
            @PathVariable Long id,
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

            if (id == null || id <= 0) {

                return error(
                        HttpStatus.BAD_REQUEST,
                        "Invalid message ID"
                );
            }

            // -------------------------------------------------
            // Delete ONLY own message
            // -------------------------------------------------

            int deleted =
                    db.update(
                            """
                            DELETE FROM messages
                            WHERE id = ?
                              AND sender_id = ?
                            """,
                            id,
                            currentUserId
                    );

            if (deleted == 0) {

                return error(
                        HttpStatus.NOT_FOUND,
                        "Message not found or not owned by you"
                );
            }

            Map<String, Object> response =
                    new HashMap<>();

            response.put("success", true);
            response.put(
                    "message",
                    "Message deleted successfully"
            );

            return ResponseEntity.ok(response);

        } catch (Exception error) {

            error.printStackTrace();

            return error(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "Failed to delete message"
            );
        }
    }

    // =====================================================
    // FIND CONVERSATION
    // =====================================================

    private Long findConversation(
            Long userA,
            Long userB
    ) {

        List<Map<String, Object>> rows =
                db.queryForList(
                        """
                        SELECT c.id

                        FROM conversations c

                        JOIN conversation_members cm1
                            ON cm1.conversation_id = c.id

                        JOIN conversation_members cm2
                            ON cm2.conversation_id = c.id

                        WHERE c.type = 'direct'
                          AND cm1.user_id = ?
                          AND cm2.user_id = ?

                        LIMIT 1
                        """,
                        userA,
                        userB
                );

        if (rows.isEmpty()) {
            return null;
        }

        return ((Number) rows.get(0).get("id"))
                .longValue();
    }

    // =====================================================
    // GET OR CREATE CONVERSATION
    // =====================================================

    private Long getOrCreateConversation(
            Long userA,
            Long userB
    ) {

        Long existing =
                findConversation(
                        userA,
                        userB
                );

        if (existing != null) {
            return existing;
        }

        // -------------------------------------------------
        // Create conversation
        // -------------------------------------------------

        db.update(
                """
                INSERT INTO conversations
                (type)
                VALUES ('direct')
                """
        );

        Long conversationId =
                db.queryForObject(
                        """
                        SELECT id
                        FROM conversations
                        ORDER BY id DESC
                        LIMIT 1
                        """,
                        Long.class
                );

        if (conversationId == null) {
            throw new RuntimeException(
                    "Failed to create conversation"
            );
        }

        // -------------------------------------------------
        // Add both members
        // -------------------------------------------------

        db.update(
                """
                INSERT INTO conversation_members
                (
                    conversation_id,
                    user_id
                )
                VALUES (?, ?), (?, ?)
                """,
                conversationId,
                userA,
                conversationId,
                userB
        );

        return conversationId;
    }

    // =====================================================
    // GET USER ID
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
