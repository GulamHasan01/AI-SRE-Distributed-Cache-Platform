package com.community.notification_service.entity;

import com.community.notification_service.entity.Enum.EmailStatus;
import com.community.notification_service.entity.Enum.EmailType;
import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

@Document(collection = "email_logs")
@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class EmailLog {

    @Id
    private String id;

    @Indexed
    private String userId;

    @Indexed
    private String recipient;

    private EmailType  type;
    private EmailStatus status;

    private String subject;
    private String failureReason;

    private int    retryCount;
    @Builder.Default
    private int    maxRetries = 3;

    @Indexed(expireAfterSeconds = 7776000)
    private Instant createdAt;
    private Instant sentAt;
}
