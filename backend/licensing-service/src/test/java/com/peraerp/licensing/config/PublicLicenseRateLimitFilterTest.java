package com.peraerp.licensing.config;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;

class PublicLicenseRateLimitFilterTest {
    @Test
    void rejectsRequestsBeyondLocalIpWindowWithFailClosedResponse() throws Exception {
        PublicLicenseRateLimitFilter filter = new PublicLicenseRateLimitFilter(1, 60);

        MockHttpServletRequest first = request();
        MockHttpServletResponse firstResponse = new MockHttpServletResponse();
        filter.doFilter(first, firstResponse, new MockFilterChain());

        MockHttpServletRequest second = request();
        MockHttpServletResponse secondResponse = new MockHttpServletResponse();
        filter.doFilter(second, secondResponse, new MockFilterChain());

        assertThat(firstResponse.getStatus()).isEqualTo(200);
        assertThat(secondResponse.getStatus()).isEqualTo(429);
        assertThat(secondResponse.getContentAsString()).contains("\"valid\":false", "RATE_LIMITED");
        assertThat(secondResponse.getHeader("Cache-Control")).isEqualTo("no-store");
        assertThat(secondResponse.getHeader("Retry-After")).isEqualTo("60");
    }

    private MockHttpServletRequest request() {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/public/v1/licenses/validate");
        request.setRemoteAddr("192.0.2.10");
        return request;
    }
}
