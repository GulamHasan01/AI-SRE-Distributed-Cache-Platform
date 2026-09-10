package com.community.notification_service.dtos.request;

import com.community.notification_service.entity.Enum.NotificationType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class SystemNotificationRequest {

    @NotBlank(message = "Target userId is required")
    private String userId;

    @NotNull(message = "Notification type is required")
    private NotificationType type;

    @NotBlank(message = "Title is required")
    private String title;

    @NotBlank(message = "Message is required")
    private String message;

    private String actionUrl;
    private String actorId;
    private String actorName;
}
