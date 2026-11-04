package com.normbuild.regulation.service;

import com.normbuild.regulation.dto.OfficialSourceSearchRequest;
import com.normbuild.regulation.dto.OfficialSourceSearchResponse;
import java.time.OffsetDateTime;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Pattern;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class OfficialSourceDiscoveryService {

    private static final Pattern MUNICIPALITY_NAME = Pattern.compile("[\\p{L}][\\p{L}\\p{M} .'-]{1,98}");
    private final OfficialSourceSearchProvider searchProvider;

    public OfficialSourceDiscoveryService(OfficialSourceSearchProvider searchProvider) {
        this.searchProvider = searchProvider;
    }

    public CompletableFuture<OfficialSourceSearchResponse> search(OfficialSourceSearchRequest request) {
        String municipality = request.municipality().strip().replaceAll("\\s+", " ");
        if (!MUNICIPALITY_NAME.matcher(municipality).matches()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Escribe el nombre de un municipio colombiano válido.");
        }
        return searchProvider.search(municipality)
                .thenApply(candidates -> new OfficialSourceSearchResponse(
                        municipality, OffsetDateTime.now(), candidates));
    }
}
