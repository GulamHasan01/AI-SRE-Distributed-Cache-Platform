package com.community.notification_service.repository;

import com.community.notification_service.entity.EmailLog;
import com.community.notification_service.entity.Enum.EmailStatus;
import com.community.notification_service.entity.Enum.EmailType;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.time.Instant;
import java.util.List;

public interface EmailLogRepository extends MongoRepository<EmailLog, String> {

    long countByRecipientAndCreatedAtAfter(String recipient, Instant since);

    List<EmailLog> findAllByStatusAndRetryCountLessThan(EmailStatus status, int maxRetries);

    List<EmailLog> findAllByUserIdOrderByCreatedAtDesc(String userId);

    boolean existsByUserIdAndType(String userId, EmailType type);
}
