package com.musicstreaming.auth.service;

import com.musicstreaming.auth.config.JwtConfig;
import com.musicstreaming.auth.dto.AuthResponse;
import com.musicstreaming.auth.dto.LoginRequest;
import com.musicstreaming.auth.dto.RegisterRequest;
import com.musicstreaming.auth.entity.User;
import com.musicstreaming.auth.exception.EmailAlreadyExistsException;
import com.musicstreaming.auth.exception.InvalidCredentialsException;
import com.musicstreaming.auth.exception.UsernameAlreadyExistsException;
import com.musicstreaming.auth.repository.UserRepository;
import io.jsonwebtoken.Jwts;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Date;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtConfig jwtConfig;

    public AuthService(UserRepository userRepository,
                       PasswordEncoder passwordEncoder,
                       JwtConfig jwtConfig) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtConfig = jwtConfig;
    }

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        if (userRepository.existsByUsername(request.username())) {
            throw new UsernameAlreadyExistsException(request.username());
        }
        if (userRepository.existsByEmail(request.email())) {
            throw new EmailAlreadyExistsException(request.email());
        }

        User user = new User();
        user.setUsername(request.username());
        user.setEmail(request.email());
        user.setPasswordHash(passwordEncoder.encode(request.password()));

        User saved = userRepository.save(user);
        return new AuthResponse(buildToken(saved));
    }

    @Transactional(readOnly = true)
    public AuthResponse login(LoginRequest request) {
        User user = userRepository.findByUsername(request.username())
                .orElseThrow(InvalidCredentialsException::new);

        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new InvalidCredentialsException();
        }

        return new AuthResponse(buildToken(user));
    }

    private String buildToken(User user) {
        Date now    = new Date();
        Date expiry = new Date(now.getTime() + jwtConfig.getExpirationMs());

        return Jwts.builder()
                .subject(user.getId().toString())
                .claim("username", user.getUsername())
                .issuedAt(now)
                .expiration(expiry)
                .signWith(jwtConfig.getPrivateKey(), Jwts.SIG.RS256)
                .compact();
    }
}
