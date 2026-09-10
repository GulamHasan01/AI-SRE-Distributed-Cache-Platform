package com.community.iam_service.repository;

import com.community.iam_service.entity.EmailOtp;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.Optional;

public interface EmailOtpRepository extends MongoRepository<EmailOtp, String> {

    Optional<EmailOtp> findByEmail(String email);

    void deleteByEmail(String email);
}