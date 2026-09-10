package com.community.notification_service.dtos.response;

import com.community.notification_service.entity.Enum.NotificationType;
import lombok.Builder;
import lombok.Data;

import java.time.Instant;

@Data @Builder
public class NotificationResponse {
    private String           id;
    private NotificationType type;
    private String           title;
    private String           message;
    private String           actionUrl;
    private String           actorName;
    private boolean          read;
    private Instant          createdAt;
}
