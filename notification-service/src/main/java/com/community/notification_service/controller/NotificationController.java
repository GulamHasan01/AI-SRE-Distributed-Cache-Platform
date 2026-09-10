package com.community.notification_service.controller;

import com.community.notification_service.dtos.request.SystemNotificationRequest;
import com.community.notification_service.dtos.response.NotificationResponse;
import com.community.notification_service.services.NotificationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/notify")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notificationService;

    @PostMapping("/system")
    public ResponseEntity<Map<String, String>> sendSystem(
            @Valid @RequestBody SystemNotificationRequest request) {

        notificationService.create(request);
        return ResponseEntity.accepted().body(Map.of("status", "created"));
    }

    @GetMapping("/{userId}")
    public ResponseEntity<List<NotificationResponse>> getAll(
            @PathVariable String userId) {
        return ResponseEntity.ok(notificationService.getAll(userId));
    }

    @GetMapping("/{userId}/unread")
    public ResponseEntity<List<NotificationResponse>> getUnread(
            @PathVariable String userId) {
        return ResponseEntity.ok(notificationService.getUnread(userId));
    }

    @GetMapping("/{userId}/count")
    public ResponseEntity<Map<String, Long>> getUnreadCount(
            @PathVariable String userId) {
        return ResponseEntity.ok(Map.of(
                "unreadCount", notificationService.getUnreadCount(userId)
        ));
    }

    @PatchMapping("/{userId}/{notificationId}/read")
    public ResponseEntity<Void> markRead(
            @PathVariable String userId,
            @PathVariable String notificationId) {
        notificationService.markRead(notificationId, userId);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/{userId}/read-all")
    public ResponseEntity<Void> markAllRead(@PathVariable String userId) {
        notificationService.markAllRead(userId);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{userId}/{notificationId}")
    public ResponseEntity<Void> delete(
            @PathVariable String userId,
            @PathVariable String notificationId) {
        notificationService.delete(notificationId, userId);
        return ResponseEntity.noContent().build();
    }
}
