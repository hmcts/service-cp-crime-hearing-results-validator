package uk.gov.hmcts.cp.integration;

import org.junit.jupiter.api.Test;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class RootControllerIntegrationTest extends IntegrationTestBase {

    @Test
    void root_endpoint_should_be_ok() throws Exception {
        mockMvc.perform(get("/"))
                .andExpect(status().isOk());
    }
}
