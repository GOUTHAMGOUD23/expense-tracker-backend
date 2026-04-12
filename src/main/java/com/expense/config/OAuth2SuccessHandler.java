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

    // Resolves to: ${FRONTEND_BASE_URL}/oauth2/redirect
    // Local:      http://localhost:3000/oauth2/redirect
    // Production: https://expense-tracker-frontend-aydi.onrender.com/oauth2/redirect
    @Value("${app.frontend.redirect-url:https://expense-tracker-frontend-aydi.onrender.com/oauth2/redirect}")
    private String frontendRedirectUrl;

    @Value("${app.frontend.base-url:https://expense-tracker-frontend-aydi.onrender.com}")
    private String frontendBaseUrl;

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request,
                                        HttpServletResponse response,
                                        Authentication authentication) throws IOException {
        try {
            OAuth2User oAuth2User = (OAuth2User) authentication.getPrincipal();
            String email = oAuth2User.getAttribute("email");

            log.info("OAuth2 success for: {}", email);

            User user = userRepository.findByEmail(email)
                    .orElseThrow(() -> new RuntimeException("User not found: " + email));

            String token = jwtUtil.generateToken(user);
            String targetUrl = frontendRedirectUrl + "?token=" + token;

            log.info("Redirecting to frontend: {}",
                    targetUrl.substring(0, Math.min(80, targetUrl.length())));

            clearAuthenticationAttributes(request);
            response.sendRedirect(targetUrl);

        } catch (Exception e) {
            log.error("OAuth2 success handler failed: {}", e.getMessage(), e);
            // Use the configured frontend URL, not hardcoded localhost
            response.sendRedirect(frontendBaseUrl + "/login?error=oauth_failed");
        }
    }
}