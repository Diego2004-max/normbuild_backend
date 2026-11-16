package com.normbuild.account;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

class QueryHistoryServiceTest {

    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    private final QueryHistoryService service = new QueryHistoryService(jdbc, new ObjectMapper());

    @Test
    void deletionIsScopedToAuthenticatedUser() {
        UUID owner = UUID.randomUUID();
        UUID historyId = UUID.randomUUID();
        String sql = "DELETE FROM query_history WHERE id = ? AND user_id = ?";
        when(jdbc.update(sql, historyId, owner)).thenReturn(1);

        assertTrue(service.delete(owner, historyId));
        verify(jdbc).update(eq(sql), eq(historyId), eq(owner));
        assertFalse(service.delete(UUID.randomUUID(), historyId));
    }
}
