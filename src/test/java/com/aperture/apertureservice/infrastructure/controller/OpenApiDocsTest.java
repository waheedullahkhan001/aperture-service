package com.aperture.apertureservice.infrastructure.controller;

import com.aperture.apertureservice.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@IntegrationTest
@AutoConfigureMockMvc
class OpenApiDocsTest {

    @Autowired
    MockMvc mvc;

    @Test
    void apiDocsEndpointReturns200WithKnownPath() throws Exception {
        mvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("/api/v1/auth/login")));
    }

    @Test
    void swaggerUiRedirectsOrReturns200() throws Exception {
        // springdoc serves swagger-ui/index.html directly; the root swagger-ui.html
        // path redirects to it. Either 200 or 3xx is acceptable here.
        mvc.perform(get("/swagger-ui/index.html"))
                .andExpect(status().isOk());
    }
}
