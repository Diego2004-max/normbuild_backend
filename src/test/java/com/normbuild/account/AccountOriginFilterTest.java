package com.normbuild.account;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class AccountOriginFilterTest {

    private final AccountOriginFilter filter = new AccountOriginFilter("https://normbuild-frontend.vercel.app");
    private final FilterChain chain = mock(FilterChain.class);

    @Test
    void rejectsCrossSiteMutation() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/account/checklists/stream");
        request.addHeader("Origin", "https://untrusted.example");
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, chain);
        assertEquals(403, response.getStatus());
        verifyNoInteractions(chain);
    }

    @Test
    void acceptsConfiguredFrontendOrigin() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/account/login");
        request.addHeader("Origin", "https://normbuild-frontend.vercel.app");
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, chain);
        verify(chain).doFilter(request, response);
    }
}
