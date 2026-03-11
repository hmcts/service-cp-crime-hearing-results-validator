package uk.gov.hmcts.cp.integration;

import jakarta.annotation.Resource;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.web.client.HttpClientErrorException;
import uk.gov.hmcts.cp.filters.jwt.JWTService;

import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static uk.gov.hmcts.cp.filters.jwt.JWTFilter.JWT_TOKEN_HEADER;

@SpringBootTest(properties = {"jwt.filter.enabled=true"})
class JWTFilterIntegrationTest extends IntegrationTestBase {

    @Resource
    private JWTService jwtService;

    @Test
    void filter_should_pass_when_good_token() throws Exception {
        final String jwtToken = jwtService.createToken();

        mockMvc.perform(
                        MockMvcRequestBuilders.get("/")
                                .header(JWT_TOKEN_HEADER, jwtToken)
                )
                .andExpectAll(
                        status().isOk(),
                        content().string(containsString("Hello"))
                );
    }

    @Test
    void filter_should_fail_when_missing_token() {
        assertThatExceptionOfType(HttpClientErrorException.class)
                .isThrownBy(() -> performGet("/"))
                .withMessageContaining("No jwt token passed");
    }

    private MvcResult performGet(String path) throws Exception {
        return mockMvc.perform(MockMvcRequestBuilders.get(path)).andReturn();
    }
}
