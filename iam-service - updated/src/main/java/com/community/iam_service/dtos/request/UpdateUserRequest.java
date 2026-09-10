package com.community.iam_service.dtos.request;

import com.community.iam_service.entity.Enum.Role;
import com.community.iam_service.entity.Enum.UserStatus;
import jakarta.validation.constraints.Email;

import lombok.Data;
import org.openapitools.jackson.nullable.JsonNullable;

import java.util.List;

@Data
public class UpdateUserRequest {

    @Email(message = "Must be a valid email address")
    private String email;

        private JsonNullable<String> name = JsonNullable.undefined();

        private String username;

        private JsonNullable<List<Role>> roles = JsonNullable.undefined();

        private JsonNullable<Boolean> verified = JsonNullable.undefined();

        private JsonNullable<UserStatus> status = JsonNullable.undefined();

        private JsonNullable<Boolean> enabled = JsonNullable.undefined();

        private JsonNullable<Boolean> deactivated = JsonNullable.undefined();

}