package com.community.iam_service.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import java.time.Instant;

@Document(collection = "email_verification_tokens")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EmailVerificationToken {

    @Id
    private String id;

    private String userId;

    @Indexed(unique = true)
    private String tokenHash;

    @Indexed(expireAfterSeconds = 0)
    private Instant expiryDate;

    private boolean used;

    private Instant createdAt;
}
