package com.community.iam_service.repository;

import com.community.iam_service.entity.EmailVerificationToken;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.Optional;

public interface EmailVerificationTokenRepository
        extends MongoRepository<EmailVerificationToken, String> {

    Optional<EmailVerificationToken> findByTokenHash(String tokenHash);
    Optional<EmailVerificationToken> findByUserId(String userId);

    void deleteAllByUserId(String userId);
}