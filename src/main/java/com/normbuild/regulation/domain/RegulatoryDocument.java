package com.normbuild.regulation.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "regulatory_documents")
public class RegulatoryDocument {

    @Id
    private UUID id;

    @Column(nullable = false)
    private String title;

    @Column(nullable = false)
    private String jurisdiction;

    @Column(name = "regulation_code", nullable = false)
    private String regulationCode;

    @Column(name = "article_reference", nullable = false)
    private String articleReference;

    @Column(name = "source_url")
    private String sourceUrl;

    @Column(nullable = false, columnDefinition = "text")
    private String content;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    protected RegulatoryDocument() {
    }

    public RegulatoryDocument(UUID id, String title, String jurisdiction, String regulationCode, String articleReference, String sourceUrl, String content, OffsetDateTime createdAt) {
        this.id = id;
        this.title = title;
        this.jurisdiction = jurisdiction;
        this.regulationCode = regulationCode;
        this.articleReference = articleReference;
        this.sourceUrl = sourceUrl;
        this.content = content;
        this.createdAt = createdAt;
    }

    public UUID getId() {
        return id;
    }

    public String getTitle() {
        return title;
    }

    public String getJurisdiction() {
        return jurisdiction;
    }

    public String getRegulationCode() {
        return regulationCode;
    }

    public String getArticleReference() {
        return articleReference;
    }

    public String getSourceUrl() {
        return sourceUrl;
    }

    public String getContent() {
        return content;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }
}
