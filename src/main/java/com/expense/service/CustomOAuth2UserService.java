package com.expense.service;

import com.expense.model.Role;
import com.expense.model.User;
import com.expense.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class CustomOAuth2UserService extends DefaultOAuth2UserService {

    private final UserRepository userRepository;

    @Override
    @Transactional
    public OAuth2User loadUser(OAuth2UserRequest userRequest) throws OAuth2AuthenticationException {
        OAuth2User oAuth2User;
        try {
            oAuth2User = super.loadUser(userRequest);
        } catch (Exception e) {
            log.error("Failed to load OAuth2 user from provider: {}", e.getMessage(), e);
            throw new OAuth2AuthenticationException(
                    new OAuth2Error("user_fetch_failed"), "Failed to fetch user from Google: " + e.getMessage(), e);
        }

        try {
            String provider   = userRequest.getClientRegistration().getRegistrationId();
            String providerId = oAuth2User.getAttribute("sub");
            String email      = oAuth2User.getAttribute("email");
            String name       = oAuth2User.getAttribute("name");
            String picture    = oAuth2User.getAttribute("picture");

            if (email == null || email.isBlank()) {
                throw new OAuth2AuthenticationException(
                        new OAuth2Error("missing_email"), "Google account did not provide an email address");
            }

            log.info("OAuth2 login: provider={}, email={}", provider, email);

            userRepository.findByEmail(email).ifPresentOrElse(
                    existingUser -> {
                        // Update profile info on each login
                        existingUser.setPictureUrl(picture);
                        existingUser.setName(name != null ? name : existingUser.getName());
                        if (existingUser.getProvider() == null) {
                            existingUser.setProvider(provider);
                            existingUser.setProviderId(providerId);
                        }
                        userRepository.save(existingUser);
                        log.info("Existing user updated via OAuth2: {}", email);
                    },
                    () -> {
                        // New user — create account without password (OAuth2 only)
                        User newUser = User.builder()
                                .email(email)
                                .name(name != null ? name : email)
                                .password("")          // empty string — no local login possible
                                .provider(provider)
                                .providerId(providerId)
                                .pictureUrl(picture)
                                .role(Role.ROLE_USER)
                                .enabled(true)
                                .build();
                        userRepository.save(newUser);
                        log.info("New OAuth2 user registered: {}", email);
                    }
            );

            return oAuth2User;

        } catch (OAuth2AuthenticationException e) {
            throw e; // rethrow OAuth2 exceptions as-is
        } catch (Exception e) {
            log.error("Error processing OAuth2 user: {}", e.getMessage(), e);
            throw new OAuth2AuthenticationException(
                    new OAuth2Error("processing_error"), "Error saving user: " + e.getMessage(), e);
        }
    }
}