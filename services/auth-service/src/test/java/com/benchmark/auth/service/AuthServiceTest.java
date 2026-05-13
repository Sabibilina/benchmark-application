package com.benchmark.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.benchmark.auth.dto.LoginRequest;
import com.benchmark.auth.dto.RegisterRequest;
import com.benchmark.auth.entity.UserAccount;
import com.benchmark.auth.repository.UserAccountRepository;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

class AuthServiceTest {

    private final UserAccountRepository repository = org.mockito.Mockito.mock(UserAccountRepository.class);
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();
    private final JwtService jwtService = org.mockito.Mockito.mock(JwtService.class);
    private AuthService authService;

    @BeforeEach
    void setUp() {
        authService = new AuthService(repository, passwordEncoder, jwtService);
    }

    @Test
    void registerCreatesUserWithHashedPassword() {
        when(repository.existsByEmail("user@example.com")).thenReturn(false);
        when(repository.save(any(UserAccount.class))).thenAnswer(invocation -> {
            UserAccount user = invocation.getArgument(0);
            ReflectionTestUtils.setField(user, "id", UUID.randomUUID());
            return user;
        });
        when(jwtService.issueAccessToken(any(UserAccount.class))).thenReturn("token");

        var response = authService.register(new RegisterRequest("USER@example.com", "CorrectHorse123"));

        ArgumentCaptor<UserAccount> captor = ArgumentCaptor.forClass(UserAccount.class);
        verify(repository).save(captor.capture());
        UserAccount saved = captor.getValue();
        assertThat(saved.getEmail()).isEqualTo("user@example.com");
        assertThat(saved.getPasswordHash()).isNotEqualTo("CorrectHorse123");
        assertThat(passwordEncoder.matches("CorrectHorse123", saved.getPasswordHash())).isTrue();
        assertThat(response.accessToken()).isEqualTo("token");
    }

    @Test
    void registerRejectsDuplicateEmail() {
        when(repository.existsByEmail("user@example.com")).thenReturn(true);

        assertThatThrownBy(() -> authService.register(new RegisterRequest("user@example.com", "CorrectHorse123")))
                .isInstanceOf(DuplicateUserException.class);

        verify(repository, never()).save(any());
    }

    @Test
    void loginReturnsTokenForValidCredentials() {
        UserAccount user = new UserAccount("user@example.com", passwordEncoder.encode("CorrectHorse123"));
        ReflectionTestUtils.setField(user, "id", UUID.randomUUID());
        when(repository.findByEmail("user@example.com")).thenReturn(Optional.of(user));
        when(jwtService.issueAccessToken(user)).thenReturn("token");

        var response = authService.login(new LoginRequest("user@example.com", "CorrectHorse123"));

        assertThat(response.accessToken()).isEqualTo("token");
        assertThat(response.user().email()).isEqualTo("user@example.com");
    }

    @Test
    void loginRejectsInvalidCredentials() {
        UserAccount user = new UserAccount("user@example.com", passwordEncoder.encode("CorrectHorse123"));
        when(repository.findByEmail("user@example.com")).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> authService.login(new LoginRequest("user@example.com", "wrong-password")))
                .isInstanceOf(InvalidCredentialsException.class);
    }
}
