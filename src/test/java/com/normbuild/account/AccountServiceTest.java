package com.normbuild.account;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import org.mockito.ArgumentCaptor;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.server.ResponseStatusException;

import static org.mockito.Mockito.mock;

class AccountServiceTest {

    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    private final AccountService service = new AccountService(jdbc);

    @Test
    void rejectsMissingSessionBeforeQueryingDatabase() {
        ResponseStatusException failure = assertThrows(ResponseStatusException.class,
                () -> service.requireSession(null));
        assertEquals(401, failure.getStatusCode().value());
    }

    @Test
    void rejectsPasswordBeyondBcryptLimit() {
        AccountCredentials credentials = new AccountCredentials("citizen@example.org", "á".repeat(40));
        ResponseStatusException failure = assertThrows(ResponseStatusException.class,
                () -> service.register(credentials));
        assertEquals(400, failure.getStatusCode().value());
    }

    @Test
    void logoutRemovesOnlyHashedToken() {
        service.logout("private-session-token");
        ArgumentCaptor<String> tokenHash = ArgumentCaptor.forClass(String.class);
        verify(jdbc).update(eq("DELETE FROM user_sessions WHERE token_hash = ?"), tokenHash.capture());
        assertEquals(64, tokenHash.getValue().length());
        assertNotEquals("private-session-token", tokenHash.getValue());
        assertTrue(tokenHash.getValue().matches("[0-9a-f]{64}"));
    }
}
