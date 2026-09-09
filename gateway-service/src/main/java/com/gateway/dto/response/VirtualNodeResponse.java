package com.gateway.dto.response;

public record VirtualNodeResponse(
        String positionHex,
        long position,
        String nodeId
) {}
