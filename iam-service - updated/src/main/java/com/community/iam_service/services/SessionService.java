package com.community.iam_service.services;

import com.community.iam_service.entity.Session;
import com.community.iam_service.exception.AppException;
import com.community.iam_service.repository.SessionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Service
@RequiredArgsConstructor
public class SessionService {

    private final SessionRepository sessionRepository;

    public Session createSession(String userId, String refreshTokenId,
                                 String ipAddress, String userAgent) {
        DeviceInfo device = parseUserAgent(userAgent);

        List<Session> existingSessions = sessionRepository.findAllByUserIdAndActiveTrue(userId);
        boolean suspicious = false;
        String suspiciousReason = null;

        if (!existingSessions.isEmpty() && device.country != null) {
            boolean newCountry = existingSessions.stream()
                    .noneMatch(s -> device.country.equals(s.getCountry()));
            if (newCountry) {
                suspicious = true;
                suspiciousReason = "Login from new location: " + device.country;
            }
        }

        return sessionRepository.save(Session.builder()
                .userId(userId)
                .refreshTokenId(refreshTokenId)
                .ipAddress(ipAddress)
                .userAgent(userAgent)
                .deviceType(device.deviceType)
                .os(device.os)
                .browser(device.browser)
                .country(device.country)
                .suspicious(suspicious)
                .suspiciousReason(suspiciousReason)
                .active(true)
                .createdAt(Instant.now())
                .lastAccessedAt(Instant.now())
                .build());
    }

    public List<Session> getActiveSessions(String userId) {
        return sessionRepository.findAllByUserIdAndActiveTrue(userId);
    }

    @Transactional
    public void revokeSession(String sessionId, String userId) {
        Session session = sessionRepository.findByIdAndUserId(sessionId, userId)
                .orElseThrow(() -> new AppException.UserNotFoundException(sessionId));
        session.setActive(false);
        sessionRepository.save(session);
    }

    @Transactional
    public void revokeAllSessions(String userId) {
        List<Session> sessions = sessionRepository.findAllByUserIdAndActiveTrue(userId);
        sessions.forEach(s -> s.setActive(false));
        sessionRepository.saveAll(sessions);
    }

    private DeviceInfo parseUserAgent(String userAgent) {
        if (userAgent == null) return new DeviceInfo("UNKNOWN", "UNKNOWN", "UNKNOWN", null);

        String ua = userAgent.toLowerCase();
        String deviceType = ua.contains("mobile") ? "MOBILE" :
                ua.contains("tablet") ? "TABLET" : "DESKTOP";
        String os = ua.contains("windows") ? "Windows" :
                ua.contains("mac") ? "macOS" :
                        ua.contains("android") ? "Android" :
                                ua.contains("iphone") || ua.contains("ipad") ? "iOS" :
                                        ua.contains("linux") ? "Linux" : "Unknown";
        String browser = ua.contains("chrome") ? "Chrome" :
                ua.contains("firefox") ? "Firefox" :
                        ua.contains("safari") ? "Safari" :
                                ua.contains("edge") ? "Edge" : "Unknown";

        return new DeviceInfo(deviceType, os, browser, null);
    }

    private record DeviceInfo(String deviceType, String os, String browser, String country) {}
}