package com.normbuild.regulation.service;

import com.normbuild.regulation.dto.OfficialSourceCandidateResponse;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public interface OfficialSourceSearchProvider {

    CompletableFuture<List<OfficialSourceCandidateResponse>> search(String municipality);
}
