package dev.erkut.authservice.user;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Locale;

@Component
@Profile("dev")
@ConditionalOnProperty(name = "auth.local-admin.enabled", havingValue = "true")
class LocalAdminBootstrap implements ApplicationRunner {

    private final AuthUserRepository authUserRepository;
    private final PasswordEncoder passwordEncoder;
    private final String email;
    private final String password;

    LocalAdminBootstrap(
            AuthUserRepository authUserRepository,
            PasswordEncoder passwordEncoder,
            @Value("${auth.local-admin.email}") String email,
            @Value("${auth.local-admin.password}") String password
    ) {
        this.authUserRepository = authUserRepository;
        this.passwordEncoder = passwordEncoder;
        this.email = email;
        this.password = password;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        String normalizedEmail = email.trim().toLowerCase(Locale.ROOT);
        authUserRepository.findByEmail(normalizedEmail).ifPresentOrElse(
                this::verifyExistingAdmin,
                () -> authUserRepository.save(AuthUser.createLocalAdmin(
                        normalizedEmail,
                        passwordEncoder.encode(password),
                        Instant.now()
                ))
        );
    }

    private void verifyExistingAdmin(AuthUser existingUser) {
        if (existingUser.getRole() != Role.ADMIN
                || !existingUser.isEnabled()
                || !passwordEncoder.matches(password, existingUser.getPasswordHash())) {
            throw new IllegalStateException(
                    "Configured local ADMIN already exists with a different role, status, or password"
            );
        }
    }
}
