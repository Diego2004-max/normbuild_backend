package com.normbuild.regulation.repository;

import com.normbuild.regulation.dto.DocumentIngestionRequest;
import java.sql.PreparedStatement;
import java.time.OffsetDateTime;
import java.util.UUID;
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
}
