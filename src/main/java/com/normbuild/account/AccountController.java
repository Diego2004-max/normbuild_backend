package com.normbuild.account;

import com.normbuild.regulation.dto.ComplianceChecklistResponse;
import com.normbuild.regulation.dto.ComplianceQueryRequest;
import com.normbuild.regulation.service.RegulatoryRagService;
import jakarta.validation.Valid;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.io.IOException;
import org.springframework.http.MediaType;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@RestController
@RequestMapping("/api/account")
public class AccountController {

    private static final String SESSION_COOKIE = "NORMBUILD_SESSION";
    private static final Logger LOGGER = LoggerFactory.getLogger(AccountController.class);
    private final AccountService accountService;
    private final QueryHistoryService historyService;
    private final RegulatoryRagService ragService;
    private final GoogleIdentityService googleIdentityService;
    private final boolean secureCookies;

    public AccountController(AccountService accountService, QueryHistoryService historyService,
            RegulatoryRagService ragService, GoogleIdentityService googleIdentityService,
            @Value("${NORMBUILD_SECURE_COOKIES:true}") boolean secureCookies) {
        this.accountService = accountService;
        this.historyService = historyService;
        this.ragService = ragService;
        this.googleIdentityService = googleIdentityService;
        this.secureCookies = secureCookies;
    }

    @PostMapping("/register")
    public ResponseEntity<AccountProfile> register(@Valid @RequestBody AccountCredentials credentials) {
        return authenticated(accountService.register(credentials));
    }

    @PostMapping("/login")
    public ResponseEntity<AccountProfile> login(@Valid @RequestBody AccountCredentials credentials) {
        return authenticated(accountService.login(credentials));
    }

    @GetMapping("/google/config")
    public ResponseEntity<GoogleConfig> googleConfig() {
        return ResponseEntity.ok().header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(new GoogleConfig(googleIdentityService.clientId()));
    }

    @PostMapping("/google")
    public ResponseEntity<AccountProfile> loginWithGoogle(@Valid @RequestBody GoogleLoginRequest request) {
        return authenticated(accountService.loginWithGoogle(googleIdentityService.verify(request.credential())));
    }

    private record GoogleConfig(String clientId) { }

    @GetMapping("/me")
    public ResponseEntity<AccountProfile> me(
            @CookieValue(value = SESSION_COOKIE, required = false) String sessionToken) {
        return ResponseEntity.ok().header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(accountService.requireSession(sessionToken));
    }

    @GetMapping("/history")
    public ResponseEntity<List<QueryHistoryItem>> history(
            @CookieValue(value = SESSION_COOKIE, required = false) String sessionToken) {
        return ResponseEntity.ok().header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(historyService.list(accountService.requireSession(sessionToken).id()));
    }

    @DeleteMapping("/history/{id}")
    public ResponseEntity<Void> deleteHistory(
            @CookieValue(value = SESSION_COOKIE, required = false) String sessionToken,
            @PathVariable UUID id) {
        UUID userId = accountService.requireSession(sessionToken).id();
        if (!historyService.delete(userId, id)) return ResponseEntity.notFound().build();
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/checklists")
    public CompletableFuture<ComplianceChecklistResponse> createSavedChecklist(
            @CookieValue(value = SESSION_COOKIE, required = false) String sessionToken,
            @Valid @RequestBody ComplianceQueryRequest request) {
        UUID userId = accountService.requireSession(sessionToken).id();
        return ragService.answerComplianceQuestion(request).thenApply(response -> {
            try {
                historyService.save(userId, request, response);
            } catch (RuntimeException exception) {
                LOGGER.error("Could not persist private query history", exception);
            }
            return response;
        });
    }

    @PostMapping(value = "/checklists/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public ResponseEntity<SseEmitter> streamSavedChecklist(
            @CookieValue(value = SESSION_COOKIE, required = false) String sessionToken,
            @Valid @RequestBody ComplianceQueryRequest request) {
        UUID userId = accountService.requireSession(sessionToken).id();
        SseEmitter emitter = new SseEmitter(300_000L);
        AtomicBoolean closed = new AtomicBoolean();
        emitter.onCompletion(() -> closed.set(true));
        emitter.onError(error -> closed.set(true));
        emitter.onTimeout(() -> { closed.set(true); emitter.complete(); });
        ragService.answerComplianceQuestion(request, progress -> sendEvent(emitter, closed, "progress", progress))
                .whenComplete((response, error) -> {
                    if (closed.get()) return;
                    if (error == null) {
                        try {
                            historyService.save(userId, request, response);
                        } catch (RuntimeException exception) {
                            LOGGER.error("Could not persist private query history", exception);
                        }
                        sendEvent(emitter, closed, "complete", response);
                    } else {
                        LOGGER.error("Could not complete saved checklist", error);
                        sendEvent(emitter, closed, "error", new ErrorMessage(
                                "No fue posible completar la revisión normativa. Intenta nuevamente."));
                    }
                    closed.set(true);
                    emitter.complete();
                });
        return ResponseEntity.ok().header(HttpHeaders.CACHE_CONTROL, "no-cache")
                .header("X-Accel-Buffering", "no").body(emitter);
    }

    private void sendEvent(SseEmitter emitter, AtomicBoolean closed, String event, Object data) {
        if (closed.get()) return;
        try {
            emitter.send(SseEmitter.event().name(event).data(data, MediaType.APPLICATION_JSON));
        } catch (IOException | IllegalStateException exception) {
            closed.set(true);
        }
    }

    private record ErrorMessage(String message) { }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(
            @CookieValue(value = SESSION_COOKIE, required = false) String sessionToken) {
        accountService.logout(sessionToken);
        return ResponseEntity.noContent()
                .header(HttpHeaders.SET_COOKIE, cookie("", Duration.ZERO).toString())
                .header(HttpHeaders.CACHE_CONTROL, "no-store").build();
    }

    private ResponseEntity<AccountProfile> authenticated(AccountSession session) {
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, cookie(session.token(), Duration.ofHours(8)).toString())
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(session.profile());
    }

    private ResponseCookie cookie(String token, Duration age) {
        return ResponseCookie.from(SESSION_COOKIE, token)
                .httpOnly(true).secure(secureCookies).sameSite("Lax")
                .path("/api").maxAge(age).build();
    }
}
