package com.cache.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Represents a virtual node on the consistent hash ring")
public record VirtualNodeResponse(
        @Schema(description = "Hexadecimal string representation of the 64-bit ring hash position", example = "a2cfd01e4a3179ff")
        String positionHex,

        @Schema(description = "64-bit long integer representation of the ring hash position", example = "-672390128490234800")
        long position,

        @Schema(description = "The target physical node ID mapped to this position", example = "node-1")
        String nodeId
) {}
