package com.peraerp.operations.config;

import com.peraerp.operations.freight.FreightRateController;
import com.peraerp.operations.freight.FreightRateService;
import com.peraerp.operations.logistics.ShipmentController;
import com.peraerp.operations.logistics.ShipmentDocumentService;
import com.peraerp.operations.logistics.ShipmentService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.context.support.PropertySourcesPlaceholderConfigurer;
import org.springframework.data.domain.Page;
import org.springframework.data.web.config.EnableSpringDataWebSupport;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.test.context.web.WebAppConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringJUnitConfig(SecurityConfigAuthorizationTest.TestConfiguration.class)
@WebAppConfiguration
@TestPropertySource(properties = "pera.jwt.secret=test-secret-that-is-long-enough-for-hs256")
class SecurityConfigAuthorizationTest {

    @Autowired WebApplicationContext context;
    @Autowired FreightRateService freightRateService;

    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        when(freightRateService.search(any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(Page.empty());
    }

    @Test
    void freightReadAndWriteAreSeparated() throws Exception {
        mvc.perform(get("/api/v1/freight-rates"))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/freight-rates").with(permission("logistics:read")))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/freight-rates").with(permission("freight:read")))
                .andExpect(status().isOk());
        mvc.perform(post("/api/v1/freight-rates").with(permission("freight:read")))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/freight-rates/simulate").with(permission("freight:read")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void documentWritesRequireLogisticsWriteAndFreightApplicationRequiresFreightWrite() throws Exception {
        mvc.perform(post("/api/v1/shipments/00000000-0000-0000-0000-000000000001/documents/upload")
                        .with(permission("logistics:read")))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/shipments/00000000-0000-0000-0000-000000000001/documents/upload")
                        .with(permission("logistics:write")))
                .andExpect(status().isUnsupportedMediaType());
        mvc.perform(post("/api/v1/shipments/00000000-0000-0000-0000-000000000001/freight/resolve")
                        .with(permission("logistics:write")))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/shipments/00000000-0000-0000-0000-000000000001/freight/resolve")
                        .with(permission("freight:write")))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/shipments/00000000-0000-0000-0000-000000000001/freight/resolve")
                        .with(jwt().authorities(new SimpleGrantedAuthority("freight:write"),
                                new SimpleGrantedAuthority("logistics:write"))))
                .andExpect(status().isBadRequest());
    }

    private org.springframework.test.web.servlet.request.RequestPostProcessor permission(String permission) {
        return jwt().authorities(new SimpleGrantedAuthority(permission));
    }

    @Configuration
    @EnableWebMvc
    @EnableWebSecurity
    @EnableSpringDataWebSupport
    @Import({SecurityConfig.class, FreightRateController.class, ShipmentController.class})
    static class TestConfiguration {

        @Bean
        static PropertySourcesPlaceholderConfigurer propertySourcesPlaceholderConfigurer() {
            return new PropertySourcesPlaceholderConfigurer();
        }

        @Bean
        FreightRateService freightRateService() {
            return mock(FreightRateService.class);
        }

        @Bean
        ShipmentService shipmentService() {
            return mock(ShipmentService.class);
        }

        @Bean
        ShipmentDocumentService shipmentDocumentService() {
            return mock(ShipmentDocumentService.class);
        }
    }
}
