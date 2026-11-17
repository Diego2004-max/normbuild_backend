package com.normbuild.account;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

class GoogleIdentityServiceTest {

    @Test
    void disablesGoogleLoginUntilClientIdIsConfigured() {
        GoogleIdentityService service = new GoogleIdentityService("");
        assertEquals("", service.clientId());
        ResponseStatusException failure = assertThrows(ResponseStatusException.class,
                () -> service.verify("token"));
        assertEquals(503, failure.getStatusCode().value());
    }

    @Test
    void rejectsMalformedCredentialBeforeContactingGoogle() {
        GoogleIdentityService service = new GoogleIdentityService("web-client.apps.googleusercontent.com");
        ResponseStatusException failure = assertThrows(ResponseStatusException.class,
                () -> service.verify(" "));
        assertEquals(400, failure.getStatusCode().value());
    }
}
