package com.expense.controller;

import com.expense.dto.LoginRequest;
import com.expense.dto.RegisterRequest;
import com.expense.service.AuthService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @Value("${BACKEND_BASE_URL:https://expense-tracker-backend-j36l.onrender.com}")
    private String backendBaseUrl;

    @Value("${app.frontend.redirect-url:NOT_SET}")
    private String frontendRedirectUrl;

    @Value("${GOOGLE_CLIENT_ID:NOT_SET}")
    private String googleClientId;

    @PostMapping("/register")
    public ResponseEntity<Map<String, Object>> register(@Valid @RequestBody RegisterRequest request) {
        return ResponseEntity.ok(authService.register(request));
    }

    @PostMapping("/login")
    public ResponseEntity<Map<String, Object>> login(@Valid @RequestBody LoginRequest request) {
        return ResponseEntity.ok(authService.login(request));
    }

    @GetMapping("/me")
    public ResponseEntity<Map<String, Object>> getCurrentUser(
            @AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntity.ok(authService.getCurrentUser(userDetails.getUsername()));
    }

    /**
     * Open in browser to verify config:
     * https://expense-tracker-backend-aydi.onrender.com/api/auth/oauth-debug
     *
     * Copy "redirectUriToAddInGoogleConsole" and paste it into:
     * Google Cloud Console → APIs & Services → Credentials → Authorized redirect URIs
     */
    @GetMapping("/oauth-debug")
    public ResponseEntity<Map<String, Object>> oauthDebug(HttpServletRequest req) {
        String computedRedirectUri = backendBaseUrl + "/login/oauth2/code/google";
        Map<String, Object> info = new LinkedHashMap<>();
        info.put("✅ redirectUriToAddInGoogleConsole", computedRedirectUri);
        info.put("BACKEND_BASE_URL",        backendBaseUrl);
        info.put("frontendRedirectUrl",     frontendRedirectUrl);
        info.put("googleClientId_prefix",   googleClientId.length() > 10
                ? googleClientId.substring(0, 10) + "..." : googleClientId);
        info.put("requestScheme",           req.getScheme());
        info.put("xForwardedProto",         req.getHeader("X-Forwarded-Proto"));
        info.put("xForwardedHost",          req.getHeader("X-Forwarded-Host"));
        info.put("serverName",              req.getServerName());
        return ResponseEntity.ok(info);
    }
}