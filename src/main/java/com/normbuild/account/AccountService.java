package com.normbuild.account;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.OffsetDateTime;
import java.time.Duration;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class AccountService {

    private final JdbcTemplate jdbc;
    private final BCryptPasswordEncoder passwords = new BCryptPasswordEncoder(12);
    private final SecureRandom random = new SecureRandom();
    private final Cache<String, Integer> failedLogins = Caffeine.newBuilder()
            .maximumSize(10_000).expireAfterWrite(Duration.ofMinutes(10)).build();

    public AccountService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional
    public AccountSession register(AccountCredentials credentials) {
        String email = normalizeEmail(credentials.email());
        validatePassword(credentials.password());
        UUID id = UUID.randomUUID();
        try {
            jdbc.update("INSERT INTO app_users (id, email, password_hash, role) VALUES (?, ?, ?, 'CITIZEN')",
                    id, email, passwords.encode(credentials.password()));
        } catch (DuplicateKeyException exception) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Ya existe una cuenta con ese correo.");
        }
        return createSession(new AccountProfile(id, email, "CITIZEN"));
    }

    @Transactional
    public AccountSession login(AccountCredentials credentials) {
        String email = normalizeEmail(credentials.email());
        Integer failures = failedLogins.getIfPresent(email);
        if (failures != null && failures >= 5) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,
                    "Demasiados intentos de acceso. Espera unos minutos.");
        }
        List<UserWithPassword> users = jdbc.query(
                "SELECT id, email, role, password_hash FROM app_users WHERE email = ?",
                (row, number) -> new UserWithPassword(
                        (UUID) row.getObject("id"), row.getString("email"),
                        row.getString("role"), row.getString("password_hash")), email);
        if (users.isEmpty() || users.getFirst().passwordHash() == null
                || !passwords.matches(credentials.password(), users.getFirst().passwordHash())) {
            failedLogins.asMap().merge(email, 1, Integer::sum);
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Correo o contraseña incorrectos.");
        }
        failedLogins.invalidate(email);
        UserWithPassword user = users.getFirst();
        return createSession(new AccountProfile(user.id(), user.email(), user.role()));
    }

    @Transactional
    public AccountSession loginWithGoogle(GoogleIdentityService.VerifiedGoogleIdentity identity) {
        List<AccountProfile> linked = jdbc.query(
                "SELECT id, email, role FROM app_users WHERE google_subject = ?",
                (row, number) -> new AccountProfile((UUID) row.getObject("id"),
                        row.getString("email"), row.getString("role")), identity.subject());
        if (!linked.isEmpty()) return createSession(linked.getFirst());

        Integer existingEmail = jdbc.queryForObject(
                "SELECT COUNT(*) FROM app_users WHERE email = ?", Integer.class, identity.email());
        if (existingEmail != null && existingEmail > 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Este correo ya tiene una cuenta. Inicia sesión con tu contraseña.");
        }
        UUID id = UUID.randomUUID();
        try {
            jdbc.update("INSERT INTO app_users (id, email, google_subject, role) VALUES (?, ?, ?, 'CITIZEN')",
                    id, identity.email(), identity.subject());
        } catch (DuplicateKeyException exception) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "La cuenta cambió durante el acceso. Intenta nuevamente.");
        }
        return createSession(new AccountProfile(id, identity.email(), "CITIZEN"));
    }

    public AccountProfile requireSession(String token) {
        if (token == null || token.isBlank()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Inicia sesión para continuar.");
        }
        List<AccountProfile> users = jdbc.query("""
                SELECT u.id, u.email, u.role
                FROM user_sessions s JOIN app_users u ON u.id = s.user_id
                WHERE s.token_hash = ? AND s.expires_at > NOW()
                """, (row, number) -> new AccountProfile(
                (UUID) row.getObject("id"), row.getString("email"), row.getString("role")), hashToken(token));
        if (users.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "La sesión expiró. Inicia sesión nuevamente.");
        }
        return users.getFirst();
    }

    public void logout(String token) {
        if (token != null && !token.isBlank()) {
            jdbc.update("DELETE FROM user_sessions WHERE token_hash = ?", hashToken(token));
        }
    }

    private AccountSession createSession(AccountProfile profile) {
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        jdbc.update("INSERT INTO user_sessions (token_hash, user_id, expires_at) VALUES (?, ?, ?)",
                hashToken(token), profile.id(), OffsetDateTime.now().plusHours(8));
        return new AccountSession(profile, token);
    }

    private String normalizeEmail(String email) {
        return email.strip().toLowerCase(Locale.ROOT);
    }

    private void validatePassword(String password) {
        if (password.getBytes(StandardCharsets.UTF_8).length > 72) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "La contraseña supera el límite de seguridad.");
        }
    }

    private String hashToken(String token) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(token.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private record UserWithPassword(UUID id, String email, String role, String passwordHash) {
    }
}
