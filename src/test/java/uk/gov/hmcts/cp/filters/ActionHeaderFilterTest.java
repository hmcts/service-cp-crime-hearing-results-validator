package uk.gov.hmcts.cp.filters;

import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletResponse;
import java.util.Collections;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;

import jakarta.servlet.http.HttpServletRequest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;

/**
 * Unit tests for {@link ActionHeaderFilter}.
 */
@ExtendWith(MockitoExtension.class)
class ActionHeaderFilterTest {

    private final ActionHeaderFilter filter = new ActionHeaderFilter();

    @Mock
    private HttpServletResponse response;

    @Mock
    private FilterChain filterChain;

    /**
     * Verifies validation requests without an explicit action header are tagged as
     * {@code validation-service.validate}.
     */
    @Test
    void should_set_validate_action_for_validate_path() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/validation/validate");
        request.setServletPath("/api/validation/validate");

        filter.doFilterInternal(request, response, filterChain);

        ArgumentCaptor<HttpServletRequest> captor = ArgumentCaptor.forClass(HttpServletRequest.class);
        verify(filterChain).doFilter(captor.capture(), any());
        assertThat(captor.getValue().getHeader("CPP-ACTION")).isEqualTo("validation-service.validate");
    }

    /**
     * Verifies the rules list endpoint receives the list action header when none is supplied.
     */
    @Test
    void should_set_rules_action_for_rules_path() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/validation/rules");
        request.setServletPath("/api/validation/rules");

        filter.doFilterInternal(request, response, filterChain);

        ArgumentCaptor<HttpServletRequest> captor = ArgumentCaptor.forClass(HttpServletRequest.class);
        verify(filterChain).doFilter(captor.capture(), any());
        assertThat(captor.getValue().getHeader("CPP-ACTION")).isEqualTo("validation-service.rules");
    }

    /**
     * Verifies the rule detail endpoint receives the detail action header when matching a rule id
     * path.
     */
    @Test
    void should_set_rules_detail_action_for_rules_id_path() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/validation/rules/DR-SENT-002");
        request.setServletPath("/api/validation/rules/DR-SENT-002");

        filter.doFilterInternal(request, response, filterChain);

        ArgumentCaptor<HttpServletRequest> captor = ArgumentCaptor.forClass(HttpServletRequest.class);
        verify(filterChain).doFilter(captor.capture(), any());
        assertThat(captor.getValue().getHeader("CPP-ACTION")).isEqualTo("validation-service.rules-detail");
    }

    /**
     * Verifies the filter preserves a caller-supplied action header instead of overwriting it.
     */
    @Test
    void should_not_override_existing_action_header() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/validation/validate");
        request.setServletPath("/api/validation/validate");
        request.addHeader("CPP-ACTION", "custom-action");

        filter.doFilterInternal(request, response, filterChain);

        ArgumentCaptor<HttpServletRequest> captor = ArgumentCaptor.forClass(HttpServletRequest.class);
        verify(filterChain).doFilter(captor.capture(), any());
        assertThat(captor.getValue().getHeader("CPP-ACTION")).isEqualTo("custom-action");
    }

    /**
     * Verifies unrelated endpoints pass through unchanged and do not get a synthetic action header.
     */
    @Test
    void should_pass_through_unknown_paths_without_action() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/actuator/health");
        request.setServletPath("/actuator/health");

        filter.doFilterInternal(request, response, filterChain);

        ArgumentCaptor<HttpServletRequest> captor = ArgumentCaptor.forClass(HttpServletRequest.class);
        verify(filterChain).doFilter(captor.capture(), any());
        assertThat(captor.getValue().getHeader("CPP-ACTION")).isNull();
    }

    /**
     * Verifies the injected action is accessible via getHeaders() (plural) as used by some
     * frameworks when iterating multi-valued headers.
     */
    @Test
    void should_expose_injected_action_via_get_headers() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/validation/validate");
        request.setServletPath("/api/validation/validate");

        filter.doFilterInternal(request, response, filterChain);

        ArgumentCaptor<HttpServletRequest> captor = ArgumentCaptor.forClass(HttpServletRequest.class);
        verify(filterChain).doFilter(captor.capture(), any());
        assertThat(Collections.list(captor.getValue().getHeaders("CPP-ACTION")))
                .containsExactly("validation-service.validate");
    }

    /**
     * Verifies CPP-ACTION appears in the header name enumeration so frameworks that iterate
     * getHeaderNames() can discover the synthetic header.
     */
    @Test
    void should_include_action_header_name_in_header_names() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/validation/validate");
        request.setServletPath("/api/validation/validate");

        filter.doFilterInternal(request, response, filterChain);

        ArgumentCaptor<HttpServletRequest> captor = ArgumentCaptor.forClass(HttpServletRequest.class);
        verify(filterChain).doFilter(captor.capture(), any());
        assertThat(Collections.list(captor.getValue().getHeaderNames()))
                .contains("CPP-ACTION");
    }

    /**
     * Verifies getHeader() on the wrapper is case-insensitive, matching the Servlet spec.
     */
    @Test
    void should_return_injected_action_header_case_insensitively() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/validation/validate");
        request.setServletPath("/api/validation/validate");

        filter.doFilterInternal(request, response, filterChain);

        ArgumentCaptor<HttpServletRequest> captor = ArgumentCaptor.forClass(HttpServletRequest.class);
        verify(filterChain).doFilter(captor.capture(), any());
        assertThat(captor.getValue().getHeader("cpp-action")).isEqualTo("validation-service.validate");
    }

    /**
     * Verifies a deep sub-path beyond the rule id still maps to rules-detail because the
     * startsWith(RULES_DETAIL_PREFIX) check matches any path under /api/validation/rules/.
     */
    @Test
    void should_set_rules_detail_action_for_deep_sub_path() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/validation/rules/DR-SENT-002/extra");
        request.setServletPath("/api/validation/rules/DR-SENT-002/extra");

        filter.doFilterInternal(request, response, filterChain);

        ArgumentCaptor<HttpServletRequest> captor = ArgumentCaptor.forClass(HttpServletRequest.class);
        verify(filterChain).doFilter(captor.capture(), any());
        assertThat(captor.getValue().getHeader("CPP-ACTION")).isEqualTo("validation-service.rules-detail");
    }

    /**
     * Verifies a path with a trailing slash and no rule-id segment is classified as rules-detail,
     * since it still matches the RULES_DETAIL_PREFIX startsWith check.
     */
    @Test
    void should_set_rules_detail_action_for_rules_trailing_slash_path() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/validation/rules/");
        request.setServletPath("/api/validation/rules/");

        filter.doFilterInternal(request, response, filterChain);

        ArgumentCaptor<HttpServletRequest> captor = ArgumentCaptor.forClass(HttpServletRequest.class);
        verify(filterChain).doFilter(captor.capture(), any());
        assertThat(captor.getValue().getHeader("CPP-ACTION")).isEqualTo("validation-service.rules-detail");
    }
}
