package com.community.iam_service.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

@Document(collection = "email_otp")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EmailOtp {

    @Id
    private String id;

    private String email;

    private String otp;

    private Instant expiryTime;
}