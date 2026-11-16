package com.normbuild.account;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.normbuild.regulation.dto.ComplianceChecklistResponse;
import com.normbuild.regulation.dto.ComplianceQueryRequest;
import com.normbuild.regulation.dto.ChecklistGenerationMode;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class QueryHistoryService {

    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;

    public QueryHistoryService(JdbcTemplate jdbc, ObjectMapper mapper) {
        this.jdbc = jdbc;
        this.mapper = mapper;
    }

    public void save(UUID userId, ComplianceQueryRequest request, ComplianceChecklistResponse response) {
        if (response.answer() == null || response.answer().isBlank()
                || response.generationMode() != ChecklistGenerationMode.GENERATIVE
                && response.generationMode() != ChecklistGenerationMode.WEB_RESEARCH) return;
        try {
            String citations = mapper.writeValueAsString(response.citations());
            jdbc.update("""
                    INSERT INTO query_history
                    (id, user_id, jurisdiction, project_description, answer, risk_level, generation_mode, citations)
                    VALUES (?, ?, ?, ?, ?, ?, ?, CAST(? AS jsonb))
                    """, UUID.randomUUID(), userId, request.jurisdiction(), request.projectDescription(),
                    response.answer(), response.riskLevel(), response.generationMode().name(), citations);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Could not persist checklist history", exception);
        }
    }

    public List<QueryHistoryItem> list(UUID userId) {
        return jdbc.query("""
                SELECT id, jurisdiction, project_description, answer, risk_level, generation_mode, created_at
                FROM query_history WHERE user_id = ? ORDER BY created_at DESC LIMIT 30
                """, this::mapItem, userId);
    }

    public boolean delete(UUID userId, UUID historyId) {
        return jdbc.update("DELETE FROM query_history WHERE id = ? AND user_id = ?", historyId, userId) > 0;
    }

    private QueryHistoryItem mapItem(ResultSet row, int number) throws SQLException {
        return new QueryHistoryItem((UUID) row.getObject("id"), row.getString("jurisdiction"),
                row.getString("project_description"), row.getString("answer"),
                row.getString("risk_level"), row.getString("generation_mode"),
                row.getObject("created_at", OffsetDateTime.class));
    }
}
