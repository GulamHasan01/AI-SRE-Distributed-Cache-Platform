package com.community.iam_service.repository;

import com.community.iam_service.entity.Enum.AuthProvider;
import com.community.iam_service.entity.OAuthAccount;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.Optional;

public interface OAuthAccountRepository extends MongoRepository<OAuthAccount, String> {

    Optional<OAuthAccount> findByProviderAndProviderUserId(
            AuthProvider provider,
            String providerUserId
    );

    Optional<OAuthAccount> findByUserId(String userId);
}