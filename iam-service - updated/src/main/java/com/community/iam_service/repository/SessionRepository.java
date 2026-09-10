package com.community.iam_service.repository;

import com.community.iam_service.entity.Session;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.Optional;

public interface SessionRepository extends MongoRepository<Session, String> {

    List<Session> findAllByUserIdAndActiveTrue(String userId);
    Optional<Session> findByIdAndUserId(String id, String userId);
    void deleteAllByUserId(String userId);
}