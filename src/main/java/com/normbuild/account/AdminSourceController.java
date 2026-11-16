package com.normbuild.account;

import com.normbuild.regulation.dto.OfficialSourceImportRequest;
import com.normbuild.regulation.dto.PdfDocumentIngestionResponse;
import com.normbuild.regulation.service.OfficialPdfSourceImportService;
import jakarta.validation.Valid;
import java.util.concurrent.CompletableFuture;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/account/admin")
public class AdminSourceController {

    private final AccountService accounts;
    private final OfficialPdfSourceImportService sources;

    public AdminSourceController(AccountService accounts, OfficialPdfSourceImportService sources) {
        this.accounts = accounts;
        this.sources = sources;
    }

    @PostMapping("/sources")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public CompletableFuture<PdfDocumentIngestionResponse> importSource(
            @CookieValue(value = "NORMBUILD_SESSION", required = false) String sessionToken,
            @Valid @RequestBody OfficialSourceImportRequest request) {
        if (!"ADMIN".equals(accounts.requireSession(sessionToken).role())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Se requiere una cuenta administradora para incorporar fuentes.");
        }
        return sources.importReviewedSource(request);
    }
}
