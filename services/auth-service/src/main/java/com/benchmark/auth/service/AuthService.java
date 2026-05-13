package com.benchmark.auth.service;

import com.benchmark.auth.dto.AuthResponse;
import com.benchmark.auth.dto.LoginRequest;
import com.benchmark.auth.dto.RegisterRequest;
import com.benchmark.auth.dto.UserResponse;
import com.benchmark.auth.entity.UserAccount;
import com.benchmark.auth.repository.UserAccountRepository;
import java.util.Locale;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

    private final UserAccountRepository userAccountRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    public AuthService(
            UserAccountRepository userAccountRepository,
            PasswordEncoder passwordEncoder,
            JwtService jwtService
    ) {
        this.userAccountRepository = userAccountRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
    }

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        String normalizedEmail = normalizeEmail(request.email());
        if (userAccountRepository.existsByEmail(normalizedEmail)) {
            throw new DuplicateUserException("User already exists");
        }

        UserAccount user = new UserAccount(normalizedEmail, passwordEncoder.encode(request.password()));
        UserAccount saved = userAccountRepository.save(user);
        return AuthResponse.bearer(jwtService.issueAccessToken(saved), toResponse(saved));
    }

    @Transactional(readOnly = true)
    public AuthResponse login(LoginRequest request) {
        String normalizedEmail = normalizeEmail(request.email());
        UserAccount user = userAccountRepository.findByEmail(normalizedEmail)
                .filter(found -> passwordEncoder.matches(request.password(), found.getPasswordHash()))
                .orElseThrow(InvalidCredentialsException::new);
        return AuthResponse.bearer(jwtService.issueAccessToken(user), toResponse(user));
    }

    private UserResponse toResponse(UserAccount user) {
        return new UserResponse(user.getId(), user.getEmail());
    }

    private String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }
}
