package co.edu.uniquindio.auth_service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
		"jwt.secret=clave-de-pruebas-de-al-menos-32-bytes-0123456789",
		"admin.default.password=Admin-de-pruebas-1",
		"spring.datasource.url=jdbc:h2:mem:authdb;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
		"spring.datasource.driver-class-name=org.h2.Driver",
		"spring.datasource.username=sa",
		"spring.datasource.password=",
		"spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
		"spring.rabbitmq.listener.simple.auto-startup=false",
		"spring.rabbitmq.listener.direct.auto-startup=false"
})
@AutoConfigureMockMvc
class AuthServiceApplicationTests {

	@jakarta.annotation.Resource
	private MockMvc mockMvc;

	private final ObjectMapper objectMapper = new ObjectMapper();

	@Test
	void contextLoads() {
	}

	@Test
	void openApiDocumentsAllAuthEndpointsAndProtectsOnlyPasswordChange() throws Exception {
		String response = mockMvc.perform(get("/v3/api-docs"))
				.andExpect(status().isOk())
				.andReturn()
				.getResponse()
				.getContentAsString();
		JsonNode document = objectMapper.readTree(response);
		JsonNode paths = document.path("paths");

		assertEquals("/", document.path("servers").get(0).path("url").asText());
		assertTrue(paths.has("/auth/login"));
		assertTrue(paths.has("/auth/recover-password"));
		assertTrue(paths.has("/auth/reset-password"));
		assertTrue(paths.has("/auth/change-password"));

		assertFalse(paths.path("/auth/login").path("post").has("security"));
		assertFalse(paths.path("/auth/recover-password").path("post").has("security"));
		assertFalse(paths.path("/auth/reset-password").path("post").has("security"));
		assertEquals("bearerAuth", paths.path("/auth/change-password").path("post")
				.path("security").get(0).fieldNames().next());
		assertEquals("bearer", document.path("components").path("securitySchemes")
				.path("bearerAuth").path("scheme").asText());
		assertTrue(paths.path("/auth/login").path("post").path("requestBody")
				.path("content").path("application/json").path("schema").path("$ref").asText().endsWith("/LoginDTO"));
		assertTrue(paths.path("/auth/login").path("post").path("responses").path("200")
				.toString().contains("TokenDTO"));
		assertTrue(paths.path("/auth/recover-password").path("post").path("responses").path("200")
				.toString().contains("ResponseDTO"));
		assertTrue(paths.path("/auth/change-password").path("post").path("parameters").toString()
				.contains("X-User-Id"));
		assertEquals(8, document.path("components").path("schemas").path("ResetPasswordDTO")
				.path("properties").path("newPassword").path("minLength").asInt());
		assertEquals(72, document.path("components").path("schemas").path("ResetPasswordDTO")
				.path("properties").path("newPassword").path("maxLength").asInt());
		assertTrue(document.path("components").path("schemas").has("ValidationErrorResponse"));
		assertTrue(document.path("components").path("schemas").has("ValidationDTO"));
		assertTrue(paths.path("/auth/login").path("post").path("responses").has("401"));
		assertTrue(paths.path("/auth/reset-password").path("post").path("responses").has("403"));
	}
}
