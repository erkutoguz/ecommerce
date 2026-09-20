package dev.erkut.authservice.user;

import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.Locale;

@Service
public class JpaUserDetailsService implements UserDetailsService {

    private final AuthUserRepository authUserRepository;
    public JpaUserDetailsService(AuthUserRepository authUserRepository) {
        this.authUserRepository = authUserRepository;
    }


    @Override
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        if (email == null || email.isBlank()) {
            throw new UsernameNotFoundException("User not found");
        }

        String normalizedEmail = email.trim().toLowerCase(Locale.ROOT);

        AuthUser authUser = authUserRepository.findByEmail(normalizedEmail)
                .orElseThrow(() -> new UsernameNotFoundException("User not found"));

        return User.withUsername(authUser.getEmail())
                .password(authUser.getPasswordHash())
                .roles(authUser.getRole().name())
                .disabled(!authUser.isEnabled())
                .build();
    }
}
