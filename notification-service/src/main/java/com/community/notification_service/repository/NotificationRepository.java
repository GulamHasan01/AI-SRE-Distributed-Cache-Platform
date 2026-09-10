package com.community.notification_service.repository;

import com.community.notification_service.entity.Notification;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

public interface NotificationRepository extends MongoRepository<Notification, String> {

    List<Notification> findAllByUserIdOrderByCreatedAtDesc(String userId);

    List<Notification> findAllByUserIdAndReadFalseOrderByCreatedAtDesc(String userId);

    long countByUserIdAndReadFalse(String userId);

    List<Notification> findAllByUserIdAndReadFalse(String userId);
}
