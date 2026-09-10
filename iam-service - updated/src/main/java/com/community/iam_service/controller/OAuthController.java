package com.community.iam_service.controller;

import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/auth/oauth")
public class OAuthController {

    @GetMapping("/providers")
    public Map<String, String> getProviders() {
        return Map.of(
                "google", "/oauth2/authorization/google",
                "linkedin", "/oauth2/authorization/linkedin"
        );
    }
}