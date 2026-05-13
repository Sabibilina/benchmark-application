package com.musicstreaming.auth.unit;

import com.musicstreaming.auth.config.JwtConfig;
import com.musicstreaming.auth.dto.AuthResponse;
import com.musicstreaming.auth.dto.LoginRequest;
import com.musicstreaming.auth.dto.RegisterRequest;
import com.musicstreaming.auth.entity.User;
import com.musicstreaming.auth.exception.EmailAlreadyExistsException;
import com.musicstreaming.auth.exception.InvalidCredentialsException;
import com.musicstreaming.auth.exception.UsernameAlreadyExistsException;
import com.musicstreaming.auth.repository.UserRepository;
import com.musicstreaming.auth.service.AuthService;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock private UserRepository userRepository;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private JwtConfig jwtConfig;

    @InjectMocks
    private AuthService authService;

    private static RSAPrivateKey testPrivateKey;

    @BeforeAll
    static void generateTestKeyPair() throws Exception {
        KeyPairGenerator gen = KeyPairGenerator.getInstance("RSA");
        gen.initialize(2048);
        KeyPair pair = gen.generateKeyPair();
        testPrivateKey = (RSAPrivateKey) pair.getPrivate();
    }

    @BeforeEach
    void configureMocks() {
        // lenient: only the two tests that reach buildToken() will consume these stubs;
        // the four tests that throw early (duplicate / bad creds) must not fail strict-stub checks
        lenient().when(jwtConfig.getPrivateKey()).thenReturn(testPrivateKey);
        lenient().when(jwtConfig.getExpirationMs()).thenReturn(3_600_000L);
    }

    // --- register ---

    @Test
    void register_success_returnsTokenAndSavesUser() {
        when(userRepository.existsByUsername("alice")).thenReturn(false);
        when(userRepository.existsByEmail("alice@example.com")).thenReturn(false);
        when(passwordEncoder.encode("password123")).thenReturn("$2a$hashed");

        User saved = userWithId("alice", "alice@example.com");
        when(userRepository.save(any(User.class))).thenReturn(saved);

        AuthResponse response = authService.register(
                new RegisterRequest("alice", "alice@example.com", "password123"));

        assertNotNull(response.token());
        verify(userRepository).save(any(User.class));
    }

    @Test
    void register_duplicateUsername_throwsConflict() {
        when(userRepository.existsByUsername("alice")).thenReturn(true);

        assertThrows(UsernameAlreadyExistsException.class, () ->
                authService.register(new RegisterRequest("alice", "alice@example.com", "password123")));

        verify(userRepository, never()).save(any());
    }

    @Test
    void register_duplicateEmail_throwsConflict() {
        when(userRepository.existsByUsername("alice")).thenReturn(false);
        when(userRepository.existsByEmail("alice@example.com")).thenReturn(true);

        assertThrows(EmailAlreadyExistsException.class, () ->
                authService.register(new RegisterRequest("alice", "alice@example.com", "password123")));

        verify(userRepository, never()).save(any());
    }

    // --- login ---

    @Test
    void login_success_returnsToken() {
        User user = userWithId("alice", "alice@example.com");
        user.setPasswordHash("$2a$hashed");

        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("password123", "$2a$hashed")).thenReturn(true);

        AuthResponse response = authService.login(new LoginRequest("alice", "password123"));

        assertNotNull(response.token());
    }

    @Test
    void login_wrongPassword_throwsInvalidCredentials() {
        User user = userWithId("alice", "alice@example.com");
        user.setPasswordHash("$2a$hashed");

        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("wrongpass", "$2a$hashed")).thenReturn(false);

        assertThrows(InvalidCredentialsException.class, () ->
                authService.login(new LoginRequest("alice", "wrongpass")));
    }

    @Test
    void login_unknownUser_throwsInvalidCredentials() {
        when(userRepository.findByUsername("nobody")).thenReturn(Optional.empty());

        assertThrows(InvalidCredentialsException.class, () ->
                authService.login(new LoginRequest("nobody", "password123")));
    }

    private static User userWithId(String username, String email) {
        User u = new User();
        u.setId(UUID.randomUUID());
        u.setUsername(username);
        u.setEmail(email);
        return u;
    }
}
