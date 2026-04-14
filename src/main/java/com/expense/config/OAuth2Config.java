package com.expense.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.registration.InMemoryClientRegistrationRepository;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.core.oidc.IdTokenClaimNames;

import jakarta.annotation.PostConstruct;

/**
 * Manually registers Google OAuth2 client with a fully hardcoded redirect URI.
 * This bypasses Spring's {baseUrl} template resolution which can generate
 * http:// instead of https:// behind Render's reverse proxy.
 */
@Configuration
@Slf4j
public class OAuth2Config {

    @Value("${GOOGLE_CLIENT_ID:YOUR_GOOGLE_CLIENT_ID}")
    private String clientId;

    @Value("${GOOGLE_CLIENT_SECRET:YOUR_GOOGLE_CLIENT_SECRET}")
    private String clientSecret;

    // Set this in Render env vars:
    // BACKEND_BASE_URL = https://expense-tracker-backend-aydi.onrender.com
    @Value("${BACKEND_BASE_URL:https://expense-tracker-backend-j36l.onrender.com}")
    private String backendBaseUrl;

    @PostConstruct
    public void logConfig() {
        String redirectUri = backendBaseUrl + "/login/oauth2/code/google";
        log.info("╔══════════════════════════════════════════════════════════════╗");
        log.info("║ OAuth2 Config");
        log.info("║ BACKEND_BASE_URL  : {https://expense-tracker-backend-j36l.onrender.com}", backendBaseUrl);
        log.info("║ Redirect URI      : {https://expense-tracker-backend-j36l.onrender.com/login/oauth2/code/google}", redirectUri);
        log.info("║");
        log.info("║ ADD THIS EXACT URI TO GOOGLE CLOUD CONSOLE:");
        log.info("║ → {https://expense-tracker-backend-j36l.onrender.com/login/oauth2/code/google}", redirectUri);
        log.info("╚══════════════════════════════════════════════════════════════╝");
    }

    @Bean
    public ClientRegistrationRepository clientRegistrationRepository() {
        // Build the redirect URI as a plain string — no template variables
        String redirectUri = backendBaseUrl + "/login/oauth2/code/google";

        log.info("Registering Google OAuth2 with redirect URI: {}", redirectUri);

        ClientRegistration googleRegistration = ClientRegistration
                .withRegistrationId("google")
                .clientId(clientId)
                .clientSecret(clientSecret)
                .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .redirectUri(redirectUri)   // ← plain string, no {baseUrl} template
                .scope("openid", "profile", "email")
                .authorizationUri("https://accounts.google.com/o/oauth2/v2/auth")
                .tokenUri("https://www.googleapis.com/oauth2/v4/token")
                .userInfoUri("https://www.googleapis.com/oauth2/v3/userinfo")
                .userNameAttributeName(IdTokenClaimNames.SUB)
                .jwkSetUri("https://www.googleapis.com/oauth2/v3/certs")
                .clientName("Google")
                .build();

        return new InMemoryClientRegistrationRepository(googleRegistration);
    }
}