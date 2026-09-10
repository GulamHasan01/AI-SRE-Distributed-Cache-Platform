package com.community.notification_service.entity;

import com.community.notification_service.entity.Enum.NotificationType;
import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

@Document(collection = "notifications")
@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class Notification {

    @Id
    private String id;

    @Indexed
    private String userId;

    private NotificationType type;
    private String title;
    private String message;
    private String actionUrl;
    private String actorId;
    private String actorName;

    @Builder.Default
    private boolean read = false;

    @Indexed(expireAfterSeconds = 2592000)
    private Instant createdAt;
    private Instant readAt;
}
