package com.example.vacaciones;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.vacaciones.config.OpenApiConfig;
import com.example.vacaciones.controller.HealthController;
import com.example.vacaciones.controller.ManualVacationController;
import com.example.vacaciones.controller.VacationController;
import com.example.vacaciones.service.VacationService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springdoc.core.configuration.SpringDocConfiguration;
import org.springdoc.core.properties.SpringDocConfigProperties;
import org.springdoc.core.properties.SwaggerUiConfigProperties;
import org.springdoc.core.properties.SwaggerUiOAuthProperties;
import org.springdoc.webmvc.core.configuration.SpringDocWebMvcConfiguration;
import org.springdoc.webmvc.ui.SwaggerConfig;

import javax.sql.DataSource;

@WebMvcTest(
        controllers = {
                VacationController.class,
                ManualVacationController.class,
                HealthController.class
        },
        properties = "vacaciones.scheduler.manual-enabled=true"
)
@Import(OpenApiConfig.class)
@ImportAutoConfiguration(classes = {
        SpringDocConfiguration.class,
        SpringDocWebMvcConfiguration.class,
        SwaggerConfig.class,
        SpringDocConfigProperties.class,
        SwaggerUiConfigProperties.class,
        SwaggerUiOAuthProperties.class
})
class OpenApiDocumentationTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private VacationService vacationService;

    @MockitoBean
    private DataSource dataSource;

    @Test
    void publishesRootOpenApiSpecWithOperationsSchemasAndBearerAuth() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.components.securitySchemes.BearerAuth.type").value("http"))
                .andExpect(jsonPath("$.components.securitySchemes.BearerAuth.scheme").value("bearer"))
                .andExpect(jsonPath("$.components.securitySchemes.BearerAuth.bearerFormat").value("JWT"))
                .andExpect(jsonPath("$.paths['/vacaciones'].post").exists())
                .andExpect(jsonPath("$.paths['/vacaciones'].post.security[0].BearerAuth").exists())
                .andExpect(jsonPath("$.paths['/vacaciones'].post.responses['400']").exists())
                .andExpect(jsonPath("$.paths['/vacaciones'].post.requestBody").exists())
                .andExpect(jsonPath("$.paths['/vacaciones'].get.responses['200'].content['*/*'].schema.type")
                        .value("array"))
                .andExpect(jsonPath("$.paths['/vacaciones/{id}'].delete.responses['409']").exists())
                .andExpect(jsonPath("$.paths['/vacaciones/{id}/forzar-inicio'].post").exists())
                .andExpect(jsonPath("$.paths['/vacaciones/{id}/forzar-fin'].post").exists())
                .andExpect(jsonPath("$.components.schemas.VacationRequest.properties.fechaInicio.format")
                        .value("date"))
                .andExpect(jsonPath("$.components.schemas.VacationResponse.properties.estado.enum").isArray())
                .andExpect(jsonPath("$.components.schemas.ApiErrorResponse.properties.conflicto").exists())
                .andExpect(jsonPath("$.paths['/health'].get").exists())
                .andExpect(jsonPath("$.paths['/health/live'].get").exists())
                .andExpect(jsonPath("$.paths['/health/ready'].get").exists())
                .andExpect(jsonPath("$.paths['/health/ready'].get.security").doesNotExist());
    }

    @Test
    void publishesSwaggerUiAtTheServiceRoot() throws Exception {
        mockMvc.perform(get("/swagger-ui.html"))
                .andExpect(status().is3xxRedirection());
    }
}
