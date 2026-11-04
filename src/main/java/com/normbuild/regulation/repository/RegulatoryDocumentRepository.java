package com.normbuild.regulation.repository;

import com.normbuild.regulation.domain.RegulatoryDocument;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RegulatoryDocumentRepository extends JpaRepository<RegulatoryDocument, UUID> {

    @Query(value = """
            SELECT
                id,
                title,
                regulation_code AS regulationCode,
                article_reference AS articleReference,
                source_url AS sourceUrl,
                content,
                1 - (embedding <=> CAST(:embedding AS vector)) AS similarity
            FROM regulatory_documents
            WHERE jurisdiction = :jurisdiction
              AND 1 - (embedding <=> CAST(:embedding AS vector)) >= :threshold
            ORDER BY embedding <=> CAST(:embedding AS vector)
            LIMIT :limit
            """, nativeQuery = true)
    List<RegulatoryDocumentProjection> findNearestRegulations(
            @Param("jurisdiction") String jurisdiction,
            @Param("embedding") String embedding,
            @Param("threshold") double threshold,
            @Param("limit") int limit
    );
}
