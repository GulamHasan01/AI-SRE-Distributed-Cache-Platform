package com.community.notification_service.services;

import com.community.notification_service.dtos.request.SystemNotificationRequest;
import com.community.notification_service.dtos.response.NotificationResponse;
import com.community.notification_service.entity.Notification;
import com.community.notification_service.repository.NotificationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Service
@RequiredArgsConstructor
public class NotificationService {

    private final NotificationRepository notificationRepository;

    public Notification create(SystemNotificationRequest request) {
        return notificationRepository.save(Notification.builder()
                .userId(request.getUserId())
                .type(request.getType())
                .title(request.getTitle())
                .message(request.getMessage())
                .actionUrl(request.getActionUrl())
                .actorId(request.getActorId())
                .actorName(request.getActorName())
                .read(false)
                .createdAt(Instant.now())
                .build());
    }

    public List<NotificationResponse> getAll(String userId) {
        return notificationRepository
                .findAllByUserIdOrderByCreatedAtDesc(userId)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    public List<NotificationResponse> getUnread(String userId) {
        return notificationRepository
                .findAllByUserIdAndReadFalseOrderByCreatedAtDesc(userId)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    public long getUnreadCount(String userId) {
        return notificationRepository.countByUserIdAndReadFalse(userId);
    }

    @Transactional
    public void markRead(String notificationId, String userId) {
        notificationRepository.findById(notificationId).ifPresent(n -> {
            if (n.getUserId().equals(userId)) {
                n.setRead(true);
                n.setReadAt(Instant.now());
                notificationRepository.save(n);
            }
        });
    }

    @Transactional
    public void markAllRead(String userId) {
        List<Notification> unread = notificationRepository.findAllByUserIdAndReadFalse(userId);
        unread.forEach(n -> {
            n.setRead(true);
            n.setReadAt(Instant.now());
        });
        notificationRepository.saveAll(unread);
    }

    public void delete(String notificationId, String userId) {
        notificationRepository.findById(notificationId).ifPresent(n -> {
            if (n.getUserId().equals(userId)) {
                notificationRepository.delete(n);
            }
        });
    }

    private NotificationResponse toResponse(Notification n) {
        return NotificationResponse.builder()
                .id(n.getId())
                .type(n.getType())
                .title(n.getTitle())
                .message(n.getMessage())
                .actionUrl(n.getActionUrl())
                .actorName(n.getActorName())
                .read(n.isRead())
                .createdAt(n.getCreatedAt())
                .build();
    }

}
