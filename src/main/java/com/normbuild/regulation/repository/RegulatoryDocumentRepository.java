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
            WHERE jurisdiction IN (:jurisdiction, 'Colombia')
              AND source_url IS NOT NULL
              AND (NOT :prioritizeLicensing OR content ~* 'licencias?|licenciamiento')
              AND (:includeSpecializedHousing OR content !~* '\\mVIP\\M')
              AND source_url ~* '^https://([a-z0-9-]+[.])*gov[.]co(/|$)'
              AND 1 - (embedding <=> CAST(:embedding AS vector)) >= :threshold
            ORDER BY (embedding <=> CAST(:embedding AS vector))
                     + CASE WHEN :prioritizeBuildingRules AND :jurisdiction = 'Pasto'
                                 AND content ~* 'art[ií]culo\\s+250'
                            THEN -0.40 ELSE 0 END
                     + CASE WHEN :prioritizeLicensing AND regulation_code ~* '1077' THEN -0.35 ELSE 0 END
                     + CASE WHEN :prioritizeBuildingRules AND content ~* 'edificabilidad.{0,40}equipamientos'
                            THEN 0.25 ELSE 0 END
            LIMIT :limit
            """, nativeQuery = true)
    List<RegulatoryDocumentProjection> findNearestRegulations(
            @Param("jurisdiction") String jurisdiction,
            @Param("embedding") String embedding,
            @Param("threshold") double threshold,
            @Param("prioritizeBuildingRules") boolean prioritizeBuildingRules,
            @Param("includeSpecializedHousing") boolean includeSpecializedHousing,
            @Param("prioritizeLicensing") boolean prioritizeLicensing,
            @Param("limit") int limit
    );
}
