package com.community.iam_service.repository;

import com.community.iam_service.entity.RefreshToken;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.Optional;

public interface RefreshTokenRepository extends MongoRepository<RefreshToken, String> {

    Optional<RefreshToken> findByTokenHash(String tokenHash);

    List<RefreshToken> findAllByUserId(String userId);

    List<RefreshToken> findAllByTokenPrefix(String tokenPrefix);

    void deleteAllByUserId(String userId);
}
