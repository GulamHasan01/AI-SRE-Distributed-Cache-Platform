package com.community.iam_service.repository;

import com.community.iam_service.entity.TwoFactorAuth;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.Optional;

public interface TwoFactorAuthRepository extends MongoRepository<TwoFactorAuth, String> {
    Optional<TwoFactorAuth> findByUserId(String userId);
    void deleteByUserId(String userId);
}