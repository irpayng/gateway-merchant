package com.tms.gateway.modules.compliance.controller;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tms.gateway.core.dto.ApiResponse;
import com.tms.gateway.core.exception.AppException;
import com.tms.gateway.core.security.GatewayMerchantUserDetails;
import com.tms.gateway.modules.compliance.dto.*;
import com.tms.gateway.modules.compliance.schema.ComplianceSchema;
import com.tms.gateway.modules.compliance.schema.ComplianceSchemaDefinitions;
import com.tms.gateway.modules.compliance.service.ComplianceService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Valid;
import jakarta.validation.Validator;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.multipart.MultipartHttpServletRequest;

@RestController
@RequestMapping("/compliance")
@RequiredArgsConstructor
public class ComplianceController {
    private final ComplianceService service;
    private final ObjectMapper objectMapper;
    private final Validator validator;

    /**
     * Returns the compliance schema for a given business type. The schema defines
     * the steps, fields, and validation rules needed to complete compliance.
     *
     * @param businessType
     *            "starter" or "registered"
     * @return compliance schema with steps and field definitions
     */
    @GetMapping("/schema")
    public ResponseEntity<ApiResponse<ComplianceSchema>> getSchema(@RequestParam("business_type") String businessType) {
        ComplianceSchema schema = ComplianceSchemaDefinitions.forBusinessType(businessType);
        return ResponseEntity.ok().cacheControl(CacheControl.maxAge(1, TimeUnit.HOURS).cachePublic())
                .body(ApiResponse.success(schema, "Schema retrieved"));
    }

    @PostMapping("/start")
    public ResponseEntity<ApiResponse<ComplianceResponse>> start(
            @AuthenticationPrincipal GatewayMerchantUserDetails user, @RequestHeader("Idempotency-Key") String key) {
        return ok(service.startCompliance(service.mutation(user, key, 0)), "Compliance started");
    }
    @GetMapping
    public ResponseEntity<ApiResponse<ComplianceResponse>> get(
            @AuthenticationPrincipal GatewayMerchantUserDetails user) {
        return ok(service.getCompliance(service.context(user, null, null)), "Compliance retrieved");
    }
    @PutMapping("/profile")
    public ResponseEntity<ApiResponse<ComplianceResponse>> saveProfile(
            @AuthenticationPrincipal GatewayMerchantUserDetails user, @RequestHeader("Idempotency-Key") String key,
            @RequestHeader("X-Expected-Version") long version, @Valid @RequestBody SaveProfileRequest request) {
        return ok(service.saveProfile(service.mutation(user, key, version), request), "Draft saved");
    }
    @PutMapping("/contact")
    public ResponseEntity<ApiResponse<ComplianceResponse>> saveContact(
            @AuthenticationPrincipal GatewayMerchantUserDetails user, @RequestHeader("Idempotency-Key") String key,
            @RequestHeader("X-Expected-Version") long version, @Valid @RequestBody SaveContactRequest request) {
        return ok(service.saveContact(service.mutation(user, key, version), request), "Draft saved");
    }
    @PutMapping("/owners")
    public ResponseEntity<ApiResponse<ComplianceResponse>> saveOwners(
            @AuthenticationPrincipal GatewayMerchantUserDetails user, @RequestHeader("Idempotency-Key") String key,
            @RequestHeader("X-Expected-Version") long version, @Valid @RequestBody SaveOwnersRequest request) {
        return ok(service.saveOwners(service.mutation(user, key, version), request), "Draft saved");
    }
    @PutMapping("/bank-accounts")
    public ResponseEntity<ApiResponse<ComplianceResponse>> saveBankAccounts(
            @AuthenticationPrincipal GatewayMerchantUserDetails user, @RequestHeader("Idempotency-Key") String key,
            @RequestHeader("X-Expected-Version") long version, @Valid @RequestBody SaveBankAccountsRequest request) {
        return ok(service.saveBankAccounts(service.mutation(user, key, version), request), "Draft saved");
    }
    @PutMapping("/registration")
    public ResponseEntity<ApiResponse<ComplianceResponse>> saveRegistration(
            @AuthenticationPrincipal GatewayMerchantUserDetails user, @RequestHeader("Idempotency-Key") String key,
            @RequestHeader("X-Expected-Version") long version, @Valid @RequestBody SaveRegistrationRequest request) {
        return ok(service.saveRegistration(service.mutation(user, key, version), request), "Draft saved");
    }

    // ── Multipart Endpoints for Document Upload ─────────────────

    private static final Set<String> ALLOWED_CONTENT_TYPES = Set.of("application/pdf", "image/jpeg", "image/png");
    private static final long MAX_FILE_SIZE = 5 * 1024 * 1024; // 5MB

    /**
     * Profile with CAC certificate (for registered business).
     */
    @Operation(summary = "Save profile with CAC certificate", description = "Save business profile details with optional CAC certificate document. "
            + "Use Content-Type: multipart/form-data. The 'data' part contains the JSON profile, "
            + "and 'cac_certificate' part contains the PDF/image file.")
    @PutMapping(value = "/profile", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<ComplianceResponse>> saveProfileWithDocs(
            @AuthenticationPrincipal GatewayMerchantUserDetails user, @RequestHeader("Idempotency-Key") String key,
            @RequestHeader("X-Expected-Version") long version, @RequestPart("data") @Valid SaveProfileRequest request,
            @Parameter(description = "CAC certificate file (PDF, JPEG, or PNG, max 5MB)") @RequestPart(value = "cac_certificate", required = false) MultipartFile cacCertificate) {
        validateFile(cacCertificate);
        return ok(service.saveProfileWithFile(service.mutation(user, key, version), request, cacCertificate),
                "Draft saved");
    }

    /**
     * Contact with business address proof.
     */
    @Operation(summary = "Save contact with address proof", description = "Save contact information with optional business address proof document. "
            + "Use Content-Type: multipart/form-data. The 'data' part contains the JSON contact info, "
            + "and 'business_address_proof' part contains the PDF/image file.")
    @PutMapping(value = "/contact", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<ComplianceResponse>> saveContactWithDocs(
            @AuthenticationPrincipal GatewayMerchantUserDetails user, @RequestHeader("Idempotency-Key") String key,
            @RequestHeader("X-Expected-Version") long version, @RequestPart("data") @Valid SaveContactRequest request,
            @Parameter(description = "Business address proof file (PDF, JPEG, or PNG, max 5MB)") @RequestPart(value = "business_address_proof", required = false) MultipartFile addressProof) {
        validateFile(addressProof);
        return ok(service.saveContactWithFile(service.mutation(user, key, version), request, addressProof),
                "Draft saved");
    }

    /**
     * Owners with identity and address documents.
     */
    @Operation(summary = "Save owners with documents", description = "Save owner details with identity and address documents. "
            + "Use Content-Type: multipart/form-data. The 'data' part contains the JSON with owners array. "
            + "File parts use naming convention: owner_{n}_identity and owner_{n}_address where {n} is the "
            + "0-based index matching the owner's position in the JSON array. "
            + "Example: owner_0_identity, owner_0_address for the first owner.")
    @PutMapping(value = "/owners", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<ComplianceResponse>> saveOwnersWithDocs(
            @AuthenticationPrincipal GatewayMerchantUserDetails user, @RequestHeader("Idempotency-Key") String key,
            @RequestHeader("X-Expected-Version") long version, @RequestPart("data") @Valid SaveOwnersRequest request,
            HttpServletRequest httpRequest) {
        Map<Integer, OwnerFiles> ownerFilesMap = extractOwnerFiles(httpRequest);
        return ok(service.saveOwnersWithFiles(service.mutation(user, key, version), request, ownerFilesMap),
                "Draft saved");
    }

    /**
     * Shareholders with identity and address documents (registered business).
     */
    @Operation(summary = "Save shareholders with documents", description = "Save shareholder details with identity and address documents for registered businesses. "
            + "Use Content-Type: multipart/form-data. The 'data' part contains the JSON with shareholders array. "
            + "File parts use naming convention: owner_{n}_identity and owner_{n}_address where {n} is the "
            + "0-based index matching the shareholder's position in the JSON array. "
            + "Example: owner_0_identity, owner_0_address for the first shareholder, "
            + "owner_1_identity, owner_1_address for the second, etc.")
    @PutMapping(value = "/business/shareholders", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<ComplianceResponse>> saveShareholdersWithDocs(
            @AuthenticationPrincipal GatewayMerchantUserDetails user, @RequestHeader("Idempotency-Key") String key,
            @RequestHeader("X-Expected-Version") long version,
            @RequestPart("data") @Valid SaveRegisteredShareholdersRequest request, HttpServletRequest httpRequest) {
        Map<Integer, OwnerFiles> ownerFilesMap = extractOwnerFiles(httpRequest);
        return ok(service.saveShareholdersWithFiles(service.mutation(user, key, version), request, ownerFilesMap),
                "Shareholders saved");
    }

    /**
     * Record to hold files for one owner.
     */
    public record OwnerFiles(MultipartFile identity, MultipartFile address) {
    }

    private void validateFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            return;
        }
        if (file.getSize() > MAX_FILE_SIZE) {
            throw new AppException("File exceeds maximum size of 5MB", HttpStatus.BAD_REQUEST);
        }
        String contentType = file.getContentType();
        if (!ALLOWED_CONTENT_TYPES.contains(contentType)) {
            throw new AppException("File type not allowed. Allowed: PDF, JPEG, PNG", HttpStatus.BAD_REQUEST);
        }
    }

    private Map<Integer, OwnerFiles> extractOwnerFiles(HttpServletRequest request) {
        if (!(request instanceof MultipartHttpServletRequest multipartRequest)) {
            return Map.of();
        }
        Map<Integer, OwnerFiles> result = new HashMap<>();
        Pattern pattern = Pattern.compile("owner_(\\d+)_(identity|address)");
        multipartRequest.getFileMap().forEach((name, file) -> {
            Matcher matcher = pattern.matcher(name);
            if (matcher.matches()) {
                int index = Integer.parseInt(matcher.group(1));
                String type = matcher.group(2);
                validateFile(file);
                OwnerFiles existing = result.getOrDefault(index, new OwnerFiles(null, null));
                if ("identity".equals(type)) {
                    result.put(index, new OwnerFiles(file, existing.address()));
                } else {
                    result.put(index, new OwnerFiles(existing.identity(), file));
                }
            }
        });
        return result;
    }

    @PutMapping("/business/details")
    public ResponseEntity<ApiResponse<ComplianceResponse>> saveRegisteredBusinessDetails(
            @AuthenticationPrincipal GatewayMerchantUserDetails user, @RequestHeader("Idempotency-Key") String key,
            @RequestHeader("X-Expected-Version") long version,
            @Valid @RequestBody RegisteredBusinessDetailsRequest request) {
        return ok(service.saveRegisteredBusinessDetails(service.mutation(user, key, version), request),
                "Business details saved");
    }

    /**
     * Business details with business address proof (registered business).
     */
    @Operation(summary = "Save business details with address proof", description = "Save registered business details (profile + contact) with optional business address proof document. "
            + "Use Content-Type: multipart/form-data. The 'data' part contains the JSON business details, "
            + "and 'business_address_proof' part contains the PDF/image file (max 10MB).")
    @PutMapping(value = "/business/details", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<ComplianceResponse>> saveRegisteredBusinessDetailsWithDocs(
            @AuthenticationPrincipal GatewayMerchantUserDetails user, @RequestHeader("Idempotency-Key") String key,
            @RequestHeader("X-Expected-Version") long version,
            @Parameter(description = "JSON business details", schema = @Schema(implementation = RegisteredBusinessDetailsRequest.class)) @RequestPart("data") String dataJson,
            @Parameter(description = "Business address proof file (PDF, JPEG, or PNG, max 10MB)") @RequestPart(value = "business_address_proof", required = false) MultipartFile businessAddressProof) {
        RegisteredBusinessDetailsRequest request = parseAndValidate(dataJson, RegisteredBusinessDetailsRequest.class);
        validateFile(businessAddressProof);
        return ok(service.saveRegisteredBusinessDetailsWithFile(service.mutation(user, key, version), request,
                businessAddressProof), "Business details saved");
    }

    private <T> T parseAndValidate(String json, Class<T> clazz) {
        try {
            T request = objectMapper.readValue(json, clazz);
            Set<ConstraintViolation<T>> violations = validator.validate(request);
            if (!violations.isEmpty()) {
                String errors = violations.stream().map(v -> v.getPropertyPath() + ": " + v.getMessage())
                        .collect(Collectors.joining(", "));
                throw new AppException("Validation failed: " + errors, HttpStatus.UNPROCESSABLE_ENTITY);
            }
            return request;
        } catch (AppException e) {
            throw e;
        } catch (Exception e) {
            throw new AppException("Invalid JSON in 'data' field: " + e.getMessage(), HttpStatus.BAD_REQUEST);
        }
    }
    @PutMapping("/business/registration")
    public ResponseEntity<ApiResponse<ComplianceResponse>> saveRegisteredBusinessRegistration(
            @AuthenticationPrincipal GatewayMerchantUserDetails user, @RequestHeader("Idempotency-Key") String key,
            @RequestHeader("X-Expected-Version") long version,
            @Valid @RequestBody RegisteredBusinessRegistrationRequest request) {
        return ok(service.saveRegisteredBusinessRegistration(service.mutation(user, key, version), request),
                "Registration details saved");
    }
    @PutMapping("/business/shareholders")
    public ResponseEntity<ApiResponse<ComplianceResponse>> saveRegisteredBusinessShareholders(
            @AuthenticationPrincipal GatewayMerchantUserDetails user, @RequestHeader("Idempotency-Key") String key,
            @RequestHeader("X-Expected-Version") long version,
            @Valid @RequestBody SaveRegisteredShareholdersRequest request) {
        return ok(service.saveRegisteredBusinessShareholders(service.mutation(user, key, version), request),
                "Shareholders saved");
    }
    @PostMapping("/submit")
    public ResponseEntity<ApiResponse<ComplianceResponse>> submit(
            @AuthenticationPrincipal GatewayMerchantUserDetails user, @RequestHeader("Idempotency-Key") String key,
            @RequestHeader("X-Expected-Version") long version, @Valid @RequestBody SubmitComplianceRequest request,
            HttpServletRequest http) {
        return ok(service.submitCompliance(service.mutation(user, key, version), request, http.getRemoteAddr(),
                http.getHeader("User-Agent")), "Compliance submitted for review");
    }
    private ResponseEntity<ApiResponse<ComplianceResponse>> ok(ComplianceResponse data, String message) {
        var response = ResponseEntity.ok().cacheControl(org.springframework.http.CacheControl.noStore());
        if (data != null)
            response.eTag(Long.toString(data.version()));
        return response.body(ApiResponse.success(data, message));
    }
}
