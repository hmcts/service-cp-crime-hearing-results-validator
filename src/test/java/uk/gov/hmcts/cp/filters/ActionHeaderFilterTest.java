package uk.gov.hmcts.cp.filters;

import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletResponse;
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

@ExtendWith(MockitoExtension.class)
/**
 * Unit tests for {@link ActionHeaderFilter}.
 */
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
}
