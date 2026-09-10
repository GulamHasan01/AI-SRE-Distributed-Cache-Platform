package com.community.iam_service.dtos.request;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;
import org.openapitools.jackson.nullable.JsonNullable;

@Data
public class UpdateUserSelfRequest {

    private JsonNullable<String> name = JsonNullable.undefined();

    @Size(min = 3, max = 30, message = "Username must be 3-30 characters")
    @Pattern(regexp = "^[a-zA-Z0-9_.-]+$", message = "Username can only contain letters, numbers, underscores, dots, hyphens")
    private JsonNullable<String> username = JsonNullable.undefined();

}