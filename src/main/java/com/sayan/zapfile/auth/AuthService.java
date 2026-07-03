package com.sayan.zapfile.auth;

import com.sayan.zapfile.auth.AuthDtos.AuthResponse;
import com.sayan.zapfile.auth.AuthDtos.LoginRequest;
import com.sayan.zapfile.auth.AuthDtos.RefreshRequest;
import com.sayan.zapfile.auth.AuthDtos.RegisterRequest;
import com.sayan.zapfile.common.ApiException;
import com.sayan.zapfile.user.User;
import com.sayan.zapfile.user.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    public AuthService(UserRepository userRepository, PasswordEncoder passwordEncoder, JwtService jwtService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
    }

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        String email = request.email().trim().toLowerCase();
        if (userRepository.existsByEmail(email)) {
            throw ApiException.conflict("An account with this email already exists");
        }
        User user = new User(email, passwordEncoder.encode(request.password()), request.displayName().trim());
        user = userRepository.save(user);
        return tokensFor(user);
    }

    public AuthResponse login(LoginRequest request) {
        User user = userRepository.findByEmail(request.email().trim().toLowerCase())
                .orElseThrow(() -> ApiException.unauthorized("Invalid email or password"));
        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw ApiException.unauthorized("Invalid email or password");
        }
        return tokensFor(user);
    }

    public AuthResponse refresh(RefreshRequest request) {
        String userId = jwtService.extractUserId(request.refreshToken(), JwtService.TYPE_REFRESH)
                .orElseThrow(() -> ApiException.unauthorized("Invalid or expired refresh token"));
        User user = userRepository.findById(userId)
                .orElseThrow(() -> ApiException.unauthorized("Account no longer exists"));
        return tokensFor(user);
    }

    private AuthResponse tokensFor(User user) {
        return new AuthResponse(
                jwtService.createAccessToken(user.getId()),
                jwtService.createRefreshToken(user.getId()),
                user.getId(),
                user.getDisplayName());
    }
}
