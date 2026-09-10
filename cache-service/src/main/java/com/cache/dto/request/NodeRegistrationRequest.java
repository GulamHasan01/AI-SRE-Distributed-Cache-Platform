package com.cache.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

@Schema(description = "Request body for registering a cache node with the cluster")
public record NodeRegistrationRequest(

        @Schema(description = "Unique node identifier — must be unique across the cluster",
                example = "node-1")
        @NotBlank(message = "Node ID must not be blank")
        @Size(max = 64, message = "Node ID must not exceed 64 characters")
        @Pattern(regexp = "[a-zA-Z0-9_:\\-]+",
                message = "Node ID may only contain letters, digits, underscores, colons, and hyphens")
        String nodeId,

        @Schema(description = "Hostname or IP address of the node",
                example = "cache-node-1.internal")
        @NotBlank(message = "Host must not be blank")
        @Size(max = 253, message = "Host must not exceed 253 characters (DNS limit)")
        String host,

        @Schema(description = "Port the node is listening on",
                example = "8081")
        @Min(value = 1, message = "Port must be >= 1")
        @Max(value = 65535, message = "Port must be <= 65535")
        int port

) {}
