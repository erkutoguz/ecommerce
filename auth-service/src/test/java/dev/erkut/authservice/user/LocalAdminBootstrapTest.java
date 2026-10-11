package dev.erkut.authservice.user;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Instant;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LocalAdminBootstrapTest {

    @Mock
    private AuthUserRepository authUserRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Test
    void createsHashedAdminWhenConfiguredAccountDoesNotExist() {
        when(authUserRepository.findByEmail("search-e2e-admin@example.com"))
                .thenReturn(Optional.empty());
        when(passwordEncoder.encode("local-password")).thenReturn("encoded-password");

        bootstrap().run(new DefaultApplicationArguments());

        ArgumentCaptor<AuthUser> userCaptor = ArgumentCaptor.forClass(AuthUser.class);
        verify(authUserRepository).save(userCaptor.capture());
        assertEquals(Role.ADMIN, userCaptor.getValue().getRole());
        assertEquals("encoded-password", userCaptor.getValue().getPasswordHash());
    }

    @Test
    void doesNotReplaceExistingAdmin() {
        AuthUser existing = AuthUser.createLocalAdmin(
                "search-e2e-admin@example.com", "encoded-password", Instant.now());
        when(authUserRepository.findByEmail("search-e2e-admin@example.com"))
                .thenReturn(Optional.of(existing));
        when(passwordEncoder.matches("local-password", "encoded-password")).thenReturn(true);

        bootstrap().run(new DefaultApplicationArguments());

        verify(authUserRepository, never()).save(existing);
    }

    @Test
    void refusesToReuseConfiguredEmailWithDifferentPassword() {
        AuthUser existing = AuthUser.createLocalAdmin(
                "search-e2e-admin@example.com", "encoded-password", Instant.now());
        when(authUserRepository.findByEmail("search-e2e-admin@example.com"))
                .thenReturn(Optional.of(existing));
        when(passwordEncoder.matches("local-password", "encoded-password")).thenReturn(false);

        assertThrows(IllegalStateException.class,
                () -> bootstrap().run(new DefaultApplicationArguments()));
        verify(authUserRepository, never()).save(existing);
    }

    private LocalAdminBootstrap bootstrap() {
        return new LocalAdminBootstrap(
                authUserRepository,
                passwordEncoder,
                " Search-E2E-Admin@Example.com ",
                "local-password");
    }
}
