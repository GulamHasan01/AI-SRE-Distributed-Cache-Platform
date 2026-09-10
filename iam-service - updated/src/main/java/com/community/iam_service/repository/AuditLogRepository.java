package com.community.iam_service.repository;

import com.community.iam_service.entity.AuditLog;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

public interface AuditLogRepository extends MongoRepository<AuditLog, String> {

    List<AuditLog> findAllByUserId(String userId);
}