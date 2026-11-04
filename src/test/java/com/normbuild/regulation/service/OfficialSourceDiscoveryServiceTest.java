package com.normbuild.regulation.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.normbuild.regulation.dto.OfficialSourceCandidateResponse;
import com.normbuild.regulation.dto.OfficialSourceSearchRequest;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

class OfficialSourceDiscoveryServiceTest {

    @Test
    void searchesAnyColombianMunicipalityAndReturnsReviewableCandidates() {
        OfficialSourceSearchProvider provider = name -> CompletableFuture.completedFuture(List.of(
                new OfficialSourceCandidateResponse("POT", "https://municipio.gov.co/pot", "POT vigente",
                        "municipio.gov.co", "REQUIRES_LEGAL_REVIEW")));
        OfficialSourceDiscoveryService service = new OfficialSourceDiscoveryService(provider);

        var response = service.search(new OfficialSourceSearchRequest("  San José del Guaviare  ")).join();

        assertThat(response.municipality()).isEqualTo("San José del Guaviare");
        assertThat(response.candidates()).hasSize(1);
        assertThat(response.candidates().getFirst().reviewStatus()).isEqualTo("REQUIRES_LEGAL_REVIEW");
        assertThat(response.searchedAt()).isNotNull();
    }

    @Test
    void rejectsQueriesThatAreNotMunicipalityNames() {
        OfficialSourceSearchProvider provider = name -> CompletableFuture.completedFuture(List.of());
        OfficialSourceDiscoveryService service = new OfficialSourceDiscoveryService(provider);

        assertThatThrownBy(() -> service.search(new OfficialSourceSearchRequest("<script>alert(1)</script>")))
                .isInstanceOf(ResponseStatusException.class);
    }
}
