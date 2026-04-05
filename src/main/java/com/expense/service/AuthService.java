package com.expense.service;

import com.expense.dto.LoginRequest;
import com.expense.dto.RegisterRequest;
import com.expense.model.Role;
import com.expense.model.User;
import com.expense.repository.UserRepository;
import com.expense.util.JwtUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;
    private final AuthenticationManager authenticationManager;

    public Map<String, Object> register(RegisterRequest request) {
        if (userRepository.existsByEmail(request.getEmail())) {
            // If the email exists as a Google account, give a helpful message
            userRepository.findByEmail(request.getEmail()).ifPresent(u -> {
                if (u.isOAuthUser()) {
                    throw new IllegalArgumentException(
                            "This email is already linked to a Google account. Please use 'Continue with Google' to sign in.");
                }
            });
            throw new IllegalArgumentException("An account with this email already exists.");
        }

        User user = User.builder()
                .name(request.getName())
                .email(request.getEmail())
                .password(passwordEncoder.encode(request.getPassword()))
                .provider("local")
                .role(Role.ROLE_USER)
                .build();

        userRepository.save(user);
        String token = jwtUtil.generateToken(user);

        return Map.of(
                "token", token,
                "user", Map.of(
                        "id", user.getId(),
                        "name", user.getName(),
                        "email", user.getEmail(),
                        "role", user.getRole()
                )
        );
    }

    public Map<String, Object> login(LoginRequest request) {
        // Find the user first to give a clear error for OAuth accounts
        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new UsernameNotFoundException("No account found with this email."));

        // Block Google/OAuth users from logging in with password
        if (user.isOAuthUser()) {
            throw new IllegalArgumentException(
                    "This account uses Google sign-in. Please click 'Continue with Google' instead.");
        }

        // Now authenticate with password
        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.getEmail(), request.getPassword())
        );

        String token = jwtUtil.generateToken(user);

        return Map.of(
                "token", token,
                "user", Map.of(
                        "id", user.getId(),
                        "name", user.getName(),
                        "email", user.getEmail(),
                        "role", user.getRole(),
                        "pictureUrl", user.getPictureUrl() != null ? user.getPictureUrl() : ""
                )
        );
    }

    public Map<String, Object> getCurrentUser(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new UsernameNotFoundException("User not found"));

        return Map.of(
                "id", user.getId(),
                "name", user.getName(),
                "email", user.getEmail(),
                "role", user.getRole(),
                "provider", user.getProvider() != null ? user.getProvider() : "local",
                "pictureUrl", user.getPictureUrl() != null ? user.getPictureUrl() : "",
                "createdAt", user.getCreatedAt()
        );
    }
}