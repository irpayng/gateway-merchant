package com.tms.gateway.modules.audit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.tms.gateway.core.dto.ApiResponse;
import com.tms.gateway.core.security.GatewayMerchantUserDetails;
import com.tms.gateway.modules.audit.annotation.Auditable;
import com.tms.gateway.modules.audit.annotation.NoAudit;
import com.tms.gateway.modules.audit.entity.AuditLogEntity;
import com.tms.gateway.modules.audit.service.AuditLogService;
import com.tms.gateway.modules.audit.service.AuditPayloadSanitizer;
import com.tms.gateway.modules.audit.web.AuditBodyCaptureAdvice;
import com.tms.gateway.modules.audit.web.AuditLogInterceptor;
import io.swagger.v3.oas.annotations.Operation;
import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.json.JsonMapper;

class AuditLogInterceptorTest {

    private final AuditLogService service = mock(AuditLogService.class);
    private MockMvc mvc;

    @RestController
    @RequestMapping("/things")
    static class ThingController {

        @GetMapping("/{id}")
        public ApiResponse<String> show(@PathVariable String id) {
            return ApiResponse.success(id);
        }

        @Auditable("Rotate thing secret")
        @PostMapping("/{id}/rotate")
        public ApiResponse<Map<String, String>> rotate(@PathVariable String id, @RequestBody Map<String, Object> body) {
            return ApiResponse.success(Map.of("id", id, "secret", "sk_live_123"), "Rotated");
        }

        @Operation(summary = "Close thing")
        @PatchMapping("/{id}/close")
        public ResponseEntity<ApiResponse<Void>> close(@PathVariable String id) {
            return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
                    .body(ApiResponse.error(422, "Thing already closed"));
        }

        @NoAudit
        @PostMapping("/lookup")
        public ApiResponse<String> lookup(@RequestBody Map<String, Object> body) {
            return ApiResponse.success("found");
        }

        @DeleteMapping("/{id}")
        public ApiResponse<Void> deleteThing(@PathVariable String id) {
            return ApiResponse.success(null, "Deleted");
        }

        @PutMapping(value = "/{id}/document", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
        public ApiResponse<Void> upload(@PathVariable String id, @RequestPart("data") String data,
                @RequestPart("file") MultipartFile file) {
            return ApiResponse.success(null, "Uploaded");
        }

        @Auditable(value = "Login", module = "auth")
        @PostMapping("/login")
        public ApiResponse<Map<String, String>> login(@RequestBody Map<String, Object> body) {
            return ApiResponse.success(Map.of("token", "jwt-value"), "Login successful");
        }
    }

    @BeforeEach
    void setup() {
        JsonMapper mapper = JsonMapper.builder().propertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE).build();
        AuditPayloadSanitizer sanitizer = new AuditPayloadSanitizer(mapper);
        mvc = MockMvcBuilders.standaloneSetup(new ThingController())
                .addInterceptors(new AuditLogInterceptor(service, sanitizer))
                .setControllerAdvice(new AuditBodyCaptureAdvice()).build();
    }

    @AfterEach
    void cleanup() {
        SecurityContextHolder.clearContext();
    }

    private void authenticate() {
        var user = GatewayMerchantUserDetails.builder().id(4L).merchantId("m1").email("owner@example.com")
                .firstName("Ada").lastName("Obi").emailVerifiedAt(Instant.now()).build();
        SecurityContextHolder.getContext()
                .setAuthentication(new UsernamePasswordAuthenticationToken(user, null, user.getAuthorities()));
    }

    private AuditLogEntity recorded() {
        ArgumentCaptor<AuditLogEntity> captor = ArgumentCaptor.forClass(AuditLogEntity.class);
        verify(service).record(captor.capture());
        return captor.getValue();
    }

    @Test
    void recordsWriteWithActorResultAndRedactedPayloads() throws Exception {
        authenticate();
        mvc.perform(post("/things/42/rotate").header("User-Agent", "JUnit")
                .header("X-Forwarded-For", "10.0.0.9, 1.1.1.1").contentType(MediaType.APPLICATION_JSON)
                .content("{\"reason\":\"leak\",\"password\":\"hunter2\"}")).andExpect(status().isOk());

        AuditLogEntity e = recorded();
        assertThat(e.getAction()).isEqualTo("Rotate thing secret");
        assertThat(e.getModule()).isEqualTo("things");
        assertThat(e.getHttpMethod()).isEqualTo("POST");
        assertThat(e.getEndpoint()).isEqualTo("/things/{id}/rotate");
        assertThat(e.getRequestPath()).isEqualTo("/things/42/rotate");
        assertThat(e.getResourceId()).isEqualTo("42");
        assertThat(e.getUserId()).isEqualTo(4L);
        assertThat(e.getMerchantId()).isEqualTo("m1");
        assertThat(e.getActorEmail()).isEqualTo("owner@example.com");
        assertThat(e.getActorName()).isEqualTo("Ada Obi");
        assertThat(e.getStatus()).isEqualTo("success");
        assertThat(e.getStatusCode()).isEqualTo(200);
        assertThat(e.getResultMessage()).isEqualTo("Rotated");
        assertThat(e.getIpAddress()).isEqualTo("10.0.0.9");
        assertThat(e.getUserAgent()).isEqualTo("JUnit");
        assertThat(e.getCreatedAt()).isNotNull();
        assertThat(e.getDurationMs()).isNotNull();
        assertThat(e.getRequestPayload()).contains("\"reason\":\"leak\"").contains("[REDACTED]")
                .doesNotContain("hunter2");
        assertThat(e.getResponsePayload()).contains("[REDACTED]").doesNotContain("sk_live_123");
    }

    @Test
    void recordsFailedOutcomeAndFallsBackToOperationSummary() throws Exception {
        authenticate();
        mvc.perform(patch("/things/7/close")).andExpect(status().isUnprocessableEntity());

        AuditLogEntity e = recorded();
        assertThat(e.getAction()).isEqualTo("Close thing");
        assertThat(e.getStatus()).isEqualTo("failed");
        assertThat(e.getStatusCode()).isEqualTo(422);
        assertThat(e.getResultMessage()).isEqualTo("Thing already closed");
        assertThat(e.getRequestPayload()).isNull();
    }

    @Test
    void fallsBackToHumanisedMethodName() throws Exception {
        authenticate();
        mvc.perform(delete("/things/9")).andExpect(status().isOk());

        assertThat(recorded().getAction()).isEqualTo("Delete thing");
    }

    @Test
    void skipsFetchRequests() throws Exception {
        authenticate();
        mvc.perform(get("/things/1")).andExpect(status().isOk());

        verifyNoInteractions(service);
    }

    @Test
    void skipsNoAuditEndpoints() throws Exception {
        authenticate();
        mvc.perform(post("/things/lookup").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isOk());

        verifyNoInteractions(service);
    }

    @Test
    void identifiesUnauthenticatedActorByEmailAndRedactsToken() throws Exception {
        mvc.perform(post("/things/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"owner@example.com\",\"password\":\"secret!\"}")).andExpect(status().isOk());

        AuditLogEntity e = recorded();
        assertThat(e.getModule()).isEqualTo("auth");
        assertThat(e.getUserId()).isNull();
        assertThat(e.getActorEmail()).isEqualTo("owner@example.com");
        assertThat(e.getRequestPayload()).doesNotContain("secret!");
        assertThat(e.getResponsePayload()).doesNotContain("jwt-value");
    }

    @Test
    void recordsMultipartPartsAndFileMetadataOnly() throws Exception {
        authenticate();
        var data = new MockMultipartFile("data", "", "application/json",
                "{\"name\":\"Acme\",\"api_key\":\"k\"}".getBytes());
        var file = new MockMultipartFile("file", "cac.pdf", "application/pdf", "PDF-BYTES".getBytes());
        mvc.perform(multipart("/things/3/document").file(data).file(file).with(r -> {
            r.setMethod("PUT");
            return r;
        })).andExpect(status().isOk());

        AuditLogEntity e = recorded();
        assertThat(e.getRequestPayload()).contains("\"name\":\"Acme\"").contains("cac.pdf").contains("application/pdf")
                .doesNotContain("PDF-BYTES").doesNotContain("\"k\"");
    }
}
