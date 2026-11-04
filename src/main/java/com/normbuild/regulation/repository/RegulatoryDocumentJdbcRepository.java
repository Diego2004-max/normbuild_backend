package com.normbuild.regulation.repository;

import com.normbuild.regulation.dto.DocumentIngestionRequest;
import java.sql.PreparedStatement;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import java.util.Map;
import java.util.LinkedHashMap;
import com.normbuild.regulation.dto.JurisdictionCoverageResponse;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class RegulatoryDocumentJdbcRepository {

    private final JdbcTemplate jdbcTemplate;

    public RegulatoryDocumentJdbcRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public int insertDocument(UUID id, DocumentIngestionRequest request, String embeddingLiteral) {
        return jdbcTemplate.update(connection -> {
            PreparedStatement statement = connection.prepareStatement("""
                    INSERT INTO regulatory_documents (
                        id, title, jurisdiction, regulation_code, article_reference, source_url, content, embedding, created_at
                    ) VALUES (?, ?, ?, ?, ?, ?, ?, CAST(? AS vector), ?)
                    ON CONFLICT (id) DO NOTHING
                    """);
            statement.setObject(1, id);
            statement.setString(2, request.title());
            statement.setString(3, request.jurisdiction());
            statement.setString(4, request.regulationCode());
            statement.setString(5, request.articleReference());
            statement.setString(6, request.sourceUrl());
            statement.setString(7, request.content());
            statement.setString(8, embeddingLiteral);
            statement.setObject(9, OffsetDateTime.now());
            return statement;
        });
    }

    public List<JurisdictionCoverageResponse> findJurisdictionCoverage() {
        List<JurisdictionCoverageResponse> indexed = jdbcTemplate.query("""
                SELECT jurisdiction,
                       COUNT(*) AS indexed_sections,
                       COUNT(DISTINCT source_url) AS official_sources,
                       MAX(created_at) AS last_indexed_at
                FROM regulatory_documents
                WHERE source_url IS NOT NULL
                  AND source_url ~* '^https://([a-z0-9-]+[.])*gov[.]co(/|$)'
                GROUP BY jurisdiction
                ORDER BY jurisdiction
                """, (result, rowNumber) -> {
            long sections = result.getLong("indexed_sections");
            long sources = result.getLong("official_sources");
            OffsetDateTime lastIndexedAt = result.getObject("last_indexed_at", OffsetDateTime.class);
            String status = sources > 0 ? "AVAILABLE" : "NO_OFFICIAL_SOURCE";
            return new JurisdictionCoverageResponse(result.getString("jurisdiction"), sections, sources, lastIndexedAt, status);
        });
        Map<String, JurisdictionCoverageResponse> coverage = new LinkedHashMap<>();
        for (String jurisdiction : List.of("Bogotá D.C.", "Pasto", "Medellín", "Cali", "Bucaramanga", "Barranquilla", "Cartagena", "Colombia")) {
            coverage.put(jurisdiction, new JurisdictionCoverageResponse(jurisdiction, 0, 0, null, "NO_OFFICIAL_SOURCE"));
        }
        indexed.forEach(item -> coverage.put(item.jurisdiction(), item));
        return List.copyOf(coverage.values());
    }
}
