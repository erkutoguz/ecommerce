package dev.erkut.authservice.authentication;

import dev.erkut.authservice.authentication.dto.AuthResponse;
import dev.erkut.authservice.authentication.dto.LoginRequest;
import dev.erkut.authservice.authentication.dto.RegisterRequest;
import dev.erkut.authservice.exception.EmailAlreadyExistsException;
import dev.erkut.authservice.exception.InvalidCredentialsException;
import dev.erkut.authservice.message.command.CreateCustomerCommand;
import dev.erkut.authservice.outbox.application.OutboxService;
import dev.erkut.authservice.token.AccessTokenService;
import dev.erkut.authservice.user.AuthUser;
import dev.erkut.authservice.user.AuthUserRepository;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Locale;

@Service
public class AuthService {

    private final AuthUserRepository authUserRepository;
    private final PasswordEncoder passwordEncoder;
    private final AccessTokenService accessTokenService;
    private final AuthenticationManager authenticationManager;
    private final OutboxService outboxService;

    public AuthService(
            AuthUserRepository authUserRepository,
            PasswordEncoder passwordEncoder,
            AccessTokenService accessTokenService,
            AuthenticationManager authenticationManager,
            OutboxService outboxService
    ) {
        this.authUserRepository = authUserRepository;
        this.passwordEncoder = passwordEncoder;
        this.accessTokenService = accessTokenService;
        this.authenticationManager = authenticationManager;
        this.outboxService = outboxService;
    }

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        String normalizedEmail = request.email().trim().toLowerCase(Locale.ROOT);

        if (authUserRepository.existsByEmail(normalizedEmail)) {
            throw new EmailAlreadyExistsException("User with email " + normalizedEmail + " already exists");
        }

        Instant now = Instant.now();

        String passwordHash = passwordEncoder.encode(request.password());

        AuthUser user = AuthUser.create(
                normalizedEmail,
                passwordHash,
                now
        );

        AuthUser savedUser;
        try {
            savedUser = authUserRepository.saveAndFlush(user);
        } catch (DataIntegrityViolationException exception) {
            if (!isEmailUniquenessViolation(exception)) {
                throw exception;
            }

            throw new EmailAlreadyExistsException(
                    "User with email " + normalizedEmail + " already exists"
            );
        }

        String accessToken = accessTokenService.generate(savedUser, now);

        CreateCustomerCommand command = new CreateCustomerCommand(savedUser.getId(), savedUser.getEmail());
        outboxService.createCustomerCommand(command, now);

        return new AuthResponse(
                accessToken,
                "Bearer",
                accessTokenService.getExpiresInSeconds()
        );
    }

    private boolean isEmailUniquenessViolation(DataIntegrityViolationException exception) {
        Throwable cause = exception;
        while (cause != null) {
            if (cause instanceof ConstraintViolationException constraintViolation) {
                return "uq_auth_users_email".equals(constraintViolation.getConstraintName());
            }
            cause = cause.getCause();
        }
        return false;
    }

    public AuthResponse login(LoginRequest request) {
        String normalizedEmail = request.email().trim().toLowerCase(Locale.ROOT);

        try {
            Authentication authentication = authenticationManager.authenticate(
                    UsernamePasswordAuthenticationToken.unauthenticated(
                            normalizedEmail,
                            request.password()
                    )
            );

            String authenticatedEmail = authentication.getName()
                    .trim()
                    .toLowerCase(Locale.ROOT);

            AuthUser user = authUserRepository.findByEmail(authenticatedEmail)
                    .orElseThrow(() -> new InvalidCredentialsException("Invalid email or password"));

            Instant now = Instant.now();
            String accessToken = accessTokenService.generate(user, now);

            return new AuthResponse(
                    accessToken,
                    "Bearer",
                    accessTokenService.getExpiresInSeconds()
            );
        } catch (AuthenticationException exception) {
            throw new InvalidCredentialsException("Invalid email or password");
        }
    }
}
