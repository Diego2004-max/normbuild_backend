package com.normbuild.regulation.repository;

import java.util.UUID;

public interface RegulatoryDocumentProjection {

    UUID getId();

    String getJurisdiction();

    String getTitle();

    String getRegulationCode();

    String getArticleReference();

    String getSourceUrl();

    String getContent();

    Double getSimilarity();
}
