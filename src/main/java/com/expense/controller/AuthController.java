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

    @Value("${BACKEND_BASE_URL:NOT_SET}")
    private String backendBaseUrl;

    @Value("${spring.security.oauth2.client.registration.google.redirect-uri:NOT_SET}")
    private String registeredRedirectUri;

    @Value("${app.frontend.redirect-url:NOT_SET}")
    private String frontendRedirectUrl;

    @PostMapping("/register")
    public ResponseEntity<Map<String, Object>> register(@Valid @RequestBody RegisterRequest request) {
        return ResponseEntity.ok(authService.register(request));
    }

    @PostMapping("/login")
    public ResponseEntity<Map<String, Object>> login(@Valid @RequestBody LoginRequest request) {
        return ResponseEntity.ok(authService.login(request));
    }

    @GetMapping("/me")
    public ResponseEntity<Map<String, Object>> getCurrentUser(@AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntity.ok(authService.getCurrentUser(userDetails.getUsername()));
    }

    /**
     * Debug endpoint — shows exactly what redirect URI the backend is using.
     * Open in browser: https://your-backend.onrender.com/api/auth/oauth-debug
     * The value shown under "registeredRedirectUri" must EXACTLY match
     * what you have in Google Cloud Console.
     */
    @GetMapping("/oauth-debug")
    public ResponseEntity<Map<String, Object>> oauthDebug(HttpServletRequest request) {
        Map<String, Object> info = new LinkedHashMap<>();
        info.put("BACKEND_BASE_URL_env",     backendBaseUrl);
        info.put("registeredRedirectUri",    registeredRedirectUri);
        info.put("frontendRedirectUrl",      frontendRedirectUrl);
        info.put("requestScheme",            request.getScheme());
        info.put("requestServerName",        request.getServerName());
        info.put("requestServerPort",        request.getServerPort());
        info.put("X-Forwarded-Proto",        request.getHeader("X-Forwarded-Proto"));
        info.put("X-Forwarded-Host",         request.getHeader("X-Forwarded-Host"));
        info.put("instruction",
                "Copy the value of 'registeredRedirectUri' and add it EXACTLY to Google Cloud Console → Authorized redirect URIs");
        return ResponseEntity.ok(info);
    }
}