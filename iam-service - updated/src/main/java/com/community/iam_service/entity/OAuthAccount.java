package com.community.iam_service.entity;

import com.community.iam_service.entity.Enum.AuthProvider;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import java.time.Instant;

@Document(collection = "oauth_accounts")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@CompoundIndex(name = "provider_userId_idx", def = "{'provider': 1, 'providerUserId': 1}", unique = true)
public class OAuthAccount {

    @Id
    private String id;

    private String userId;

    private AuthProvider provider;

    @Indexed(unique = true)
    private String providerUserId;

    private Instant linkedAt;
}
