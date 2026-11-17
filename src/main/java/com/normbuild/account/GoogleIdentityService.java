package com.normbuild.account;

import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.List;
import java.util.Locale;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class GoogleIdentityService {

    private final String clientId;
    private final GoogleIdTokenVerifier verifier;

    public GoogleIdentityService(@Value("${GOOGLE_OAUTH_CLIENT_ID:}") String clientId) {
        this.clientId = clientId.strip();
        this.verifier = this.clientId.isEmpty() ? null : new GoogleIdTokenVerifier.Builder(
                new NetHttpTransport(), GsonFactory.getDefaultInstance())
                .setAudience(List.of(this.clientId)).build();
    }

    public String clientId() {
        return clientId;
    }

    public VerifiedGoogleIdentity verify(String credential) {
        if (verifier == null) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "El acceso con Google aún no está configurado.");
        }
        if (credential == null || credential.isBlank() || credential.length() > 10_000) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "La credencial de Google no es válida.");
        }
        try {
            GoogleIdToken token = verifier.verify(credential);
            if (token == null || token.getPayload() == null
                    || !Boolean.TRUE.equals(token.getPayload().getEmailVerified())) {
                throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,
                        "Google no pudo verificar esta cuenta.");
            }
            String subject = token.getPayload().getSubject();
            String email = token.getPayload().getEmail();
            if (subject == null || subject.isBlank() || email == null || email.isBlank()) {
                throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,
                        "La cuenta de Google no proporcionó una identidad válida.");
            }
            return new VerifiedGoogleIdentity(subject, email.toLowerCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,
                    "La credencial de Google no es válida.");
        } catch (GeneralSecurityException | IOException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                    "No fue posible verificar la identidad con Google. Intenta nuevamente.");
        }
    }

    public record VerifiedGoogleIdentity(String subject, String email) { }
}
