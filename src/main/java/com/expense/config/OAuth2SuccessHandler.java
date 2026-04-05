package com.expense.config;

import com.expense.model.User;
import com.expense.repository.UserRepository;
import com.expense.util.JwtUtil;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
@RequiredArgsConstructor
@Slf4j
public class OAuth2SuccessHandler extends SimpleUrlAuthenticationSuccessHandler {

    private final JwtUtil jwtUtil;
    private final UserRepository userRepository;

    // Must be a full absolute URL — e.g. http://localhost:3000/oauth2/redirect
    @Value("${app.frontend.redirect-url:http://localhost:3000/oauth2/redirect}")
    private String frontendRedirectUrl;

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request,
                                        HttpServletResponse response,
                                        Authentication authentication) throws IOException {
        try {
            OAuth2User oAuth2User = (OAuth2User) authentication.getPrincipal();
            String email = oAuth2User.getAttribute("email");

            log.info("OAuth2 success handler: email={}", email);

            User user = userRepository.findByEmail(email)
                    .orElseThrow(() -> new RuntimeException("User not found: " + email));

            String token = jwtUtil.generateToken(user);

            // Build absolute URL explicitly — never use sendRedirect with a relative path
            // as it will redirect to localhost:8080 instead of localhost:3000
            String targetUrl = frontendRedirectUrl + "?token=" + token;

            log.info("Sending OAuth2 redirect to: {}", targetUrl.substring(0, Math.min(80, targetUrl.length())));

            // Clear the auth attributes to avoid session reuse
            clearAuthenticationAttributes(request);

            // Use HttpServletResponse directly to guarantee the absolute URL is used
            response.sendRedirect(targetUrl);

        } catch (Exception e) {
            log.error("OAuth2 success handler failed: {}", e.getMessage(), e);
            response.sendRedirect("http://localhost:3000/login?error=oauth_failed");
        }
    }
}