package com.community.iam_service.repository;

import com.community.iam_service.entity.ApiKey;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.Optional;

public interface ApiKeyRepository extends MongoRepository<ApiKey, String> {
    List<ApiKey> findAllByUserIdAndActiveTrue(String userId);
    Optional<ApiKey> findByKeyHash(String keyHash);
    List<ApiKey> findAllByKeyPrefix(String keyPrefix);
    void deleteAllByUserId(String userId);
}