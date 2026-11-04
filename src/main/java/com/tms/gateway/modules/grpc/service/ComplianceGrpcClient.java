package com.tms.gateway.modules.grpc.service;
import com.google.protobuf.ByteString;
import com.tms.gateway.core.exception.AppException;
import com.tms.gateway.modules.compliance.controller.ComplianceController.OwnerFiles;
import com.tms.gateway.modules.compliance.dto.*;
import com.tms.gateway.modules.compliance.dto.ComplianceResponse.CorrectionData;
import com.tms.gateway.modules.compliance.service.ComplianceValidationException;
import com.tms.gateway.modules.grpc.compliance.*;
import com.tms.gateway.modules.grpc.config.GrpcProperties;
import io.grpc.*;
import io.grpc.stub.MetadataUtils;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
/**
 * All mutations are merchant-scoped, versioned and idempotent at the source of
 * truth.
 */
@Service
public class ComplianceGrpcClient {
    private static final Logger log = LoggerFactory.getLogger(ComplianceGrpcClient.class);
    @Value("${grpc.debug-errors:false}")
    private boolean debugErrors;
    private final GrpcProperties properties;
    private ManagedChannel channel;
    private PgMerchantComplianceServiceGrpc.PgMerchantComplianceServiceBlockingStub stub;
    @Value("${grpc.api.key}")
    private String apiKey;
    public ComplianceGrpcClient(GrpcProperties properties) {
        this.properties = properties;
    }
    @PostConstruct
    public void init() {
        channel = ManagedChannelBuilder.forAddress(properties.getKycServiceHost(), properties.getKycServicePort())
                .usePlaintext().build();
        Metadata headers = new Metadata();
        headers.put(Metadata.Key.of("x-api-key", Metadata.ASCII_STRING_MARSHALLER), apiKey);
        stub = PgMerchantComplianceServiceGrpc.newBlockingStub(channel)
                .withInterceptors(MetadataUtils.newAttachHeadersInterceptor(headers));
    }
    private PgMerchantComplianceServiceGrpc.PgMerchantComplianceServiceBlockingStub timed() {
        return stub.withDeadlineAfter(properties.getTimeout(), TimeUnit.SECONDS);
    }
    @PreDestroy
    public void shutdown() {
        if (channel != null) {
            try {
                if (!channel.shutdown().awaitTermination(5, TimeUnit.SECONDS))
                    channel.shutdownNow();
            } catch (InterruptedException e) {
                channel.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
    }
    private <T> T invoke(String operation, Supplier<T> call) {
        long started = System.nanoTime();
        log.debug("KYC gRPC request operation={} target={}:{}", operation, properties.getKycServiceHost(),
                properties.getKycServicePort());
        try {
            return call.get();
        } catch (StatusRuntimeException e) {
            log.error("KYC gRPC failed operation={} target={}:{} status={} description={} elapsed_ms={}", operation,
                    properties.getKycServiceHost(), properties.getKycServicePort(), e.getStatus().getCode(),
                    e.getStatus().getDescription(), TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started), e);
            HttpStatus http = switch (e.getStatus().getCode()) {
                case INVALID_ARGUMENT -> HttpStatus.UNPROCESSABLE_ENTITY;
                case PERMISSION_DENIED -> HttpStatus.FORBIDDEN;
                case NOT_FOUND -> HttpStatus.NOT_FOUND;
                case ALREADY_EXISTS, ABORTED, FAILED_PRECONDITION -> HttpStatus.CONFLICT;
                case RESOURCE_EXHAUSTED -> HttpStatus.TOO_MANY_REQUESTS;
                case UNAVAILABLE, UNIMPLEMENTED -> HttpStatus.SERVICE_UNAVAILABLE;
                case DEADLINE_EXCEEDED -> HttpStatus.GATEWAY_TIMEOUT;
                default -> HttpStatus.BAD_GATEWAY;
            };
            String message = "KYC " + operation + " failed (" + e.getStatus().getCode().name() + ")";
            if (debugErrors && e.getStatus().getDescription() != null)
                message += ": " + e.getStatus().getDescription();
            var failure = new AppException(message, http);
            failure.initCause(e);
            throw failure;
        }
    }
    private ComplianceData result(com.tms.gateway.modules.grpc.compliance.ComplianceResponse r) {
        if (!r.getSuccess()) {
            log.warn("KYC rejected compliance operation error_code={} message={} field_error_count={}",
                    r.getErrorCode(), r.getMessage(), r.getErrorsCount());
            if (!r.getErrorsList().isEmpty())
                throw new ComplianceValidationException(r.getErrorsList().stream()
                        .map(e -> new CorrectionData(e.getStep(), e.getField(), e.getMessage())).toList());
            HttpStatus status = switch (r.getErrorCode()) {
                case "NOT_FOUND" -> HttpStatus.NOT_FOUND;
                case "FORBIDDEN" -> HttpStatus.FORBIDDEN;
                case "VERSION_CONFLICT", "IDEMPOTENCY_CONFLICT", "INVALID_STATE" -> HttpStatus.CONFLICT;
                case "VALIDATION_FAILED" -> HttpStatus.UNPROCESSABLE_ENTITY;
                default -> HttpStatus.BAD_GATEWAY;
            };
            throw new AppException(r.getMessage().isBlank() ? "KYC rejected the operation" : r.getMessage(), status);
        }
        if (!r.hasCompliance() || r.getCompliance().getMerchantId().isBlank()) {
            log.error("KYC returned an incomplete compliance response has_compliance={} merchant_id_present={}",
                    r.hasCompliance(), !r.getCompliance().getMerchantId().isBlank());
            throw new AppException("KYC returned an incomplete compliance response", HttpStatus.BAD_GATEWAY);
        }
        return r.getCompliance();
    }
    public ComplianceData startCompliance(MutationContext context) {
        return result(invoke("start", () -> timed()
                .startMerchantCompliance(MerchantComplianceRequest.newBuilder().setContext(context).build())));
    }
    public ComplianceData getCompliance(MutationContext context) {
        var r = invoke("read", () -> timed()
                .getMerchantCompliance(MerchantComplianceRequest.newBuilder().setContext(context).build()));
        if (!r.getSuccess() && "NOT_FOUND".equals(r.getErrorCode()))
            return null;
        // Handle case where merchant exists but hasn't started compliance yet
        if (r.getSuccess() && (!r.hasCompliance() || r.getCompliance().getMerchantId().isBlank()))
            return null;
        return result(r);
    }
    public ComplianceData saveProfile(MutationContext context,
            com.tms.gateway.modules.compliance.dto.SaveProfileRequest r) {
        var data = com.tms.gateway.modules.grpc.compliance.SaveProfileRequest.newBuilder();
        var supplied = new ArrayList<String>();
        data.setUserId(context.getActorUserId());
        if (r.country() != null) {
            supplied.add("country");
            data.setCountry(r.country());
        }
        if (r.industry() != null) {
            supplied.add("industry");
            data.setIndustry(r.industry());
        }
        if (r.category() != null) {
            supplied.add("category");
            data.setCategory(r.category());
        }
        if (r.tradingName() != null) {
            supplied.add("trading_name");
            data.setTradingName(r.tradingName());
        }
        if (r.salesVolume() != null) {
            supplied.add("sales_volume");
            data.setSalesVolume(r.salesVolume());
        }
        if (r.businessType() != null) {
            supplied.add("business_type");
            data.setBusinessType(r.businessType());
        }
        if (r.businessDescription() != null) {
            supplied.add("business_description");
            data.setBusinessDescription(r.businessDescription());
        }
        return result(invoke("saveProfile", () -> timed().saveMerchantProfileDraft(MerchantProfileDraftRequest
                .newBuilder().setContext(context).setData(data).addAllSuppliedFields(supplied).build())));
    }
    public ComplianceData saveContact(MutationContext context,
            com.tms.gateway.modules.compliance.dto.SaveContactRequest r) {
        var data = com.tms.gateway.modules.grpc.compliance.SaveContactRequest.newBuilder();
        var supplied = new ArrayList<String>();
        data.setUserId(context.getActorUserId());
        if (r.email() != null) {
            supplied.add("email");
            data.setEmail(r.email());
        }
        if (r.phone() != null) {
            supplied.add("phone");
            data.setPhone(r.phone());
        }
        if (r.address() != null) {
            supplied.add("address");
            data.setAddress(r.address());
        }
        if (r.supportEmail() != null) {
            supplied.add("support_email");
            data.setSupportEmail(r.supportEmail());
        }
        if (r.disputeEmail() != null) {
            supplied.add("dispute_email");
            data.setDisputeEmail(r.disputeEmail());
        }
        if (r.state() != null) {
            supplied.add("state");
            data.setState(r.state());
        }
        if (r.lga() != null) {
            supplied.add("lga");
            data.setLga(r.lga());
        }
        if (r.sameAsSupportEmail() != null) {
            supplied.add("same_as_support_email");
            data.setSameAsSupportEmail(r.sameAsSupportEmail());
        }
        if (r.onlineLinks() != null) {
            supplied.add("online_links");
            data.addAllOnlineLinks(r.onlineLinks().stream()
                    .map(l -> OnlineLinkData.newBuilder().setPlatform(l.platform() == null ? "" : l.platform())
                            .setUrl(l.url() == null ? "" : l.url()).build())
                    .toList());
        }
        return result(invoke("saveContact", () -> timed().saveMerchantContactDraft(MerchantContactDraftRequest
                .newBuilder().setContext(context).setData(data).addAllSuppliedFields(supplied).build())));
    }
    public ComplianceData saveRegistration(MutationContext context,
            com.tms.gateway.modules.compliance.dto.SaveRegistrationRequest r) {
        var data = RegistrationData.newBuilder();
        var supplied = new ArrayList<String>();
        if (r.businessType() != null) {
            supplied.add("business_type");
            data.setBusinessType(r.businessType());
        }
        if (r.registrationNumber() != null) {
            supplied.add("registration_number");
            data.setRegistrationNumber(r.registrationNumber());
        }
        if (r.taxIdentificationNumber() != null) {
            supplied.add("tax_identification_number");
            data.setTaxIdentificationNumber(r.taxIdentificationNumber());
        }
        return result(
                invoke("saveRegistration", () -> timed().saveMerchantRegistrationDraft(MerchantRegistrationDraftRequest
                        .newBuilder().setContext(context).setData(data).addAllSuppliedFields(supplied).build())));
    }
    public ComplianceData saveOwners(MutationContext context,
            List<com.tms.gateway.modules.compliance.dto.SaveOwnerRequest> items) {
        var request = MerchantOwnersDraftRequest.newBuilder().setContext(context);
        for (var r : items) {
            var data = OwnerData.newBuilder();
            var supplied = FieldPresence.newBuilder();
            if (r.id() != null) {
                data.setId(r.id());
                supplied.addFields("id");
            }
            if (r.role() != null) {
                data.setRole(r.role());
                supplied.addFields("role");
            }
            if (r.dateOfBirth() != null) {
                data.setDateOfBirth(r.dateOfBirth());
                supplied.addFields("date_of_birth");
            }
            if (r.address() != null) {
                data.setAddress(r.address());
                supplied.addFields("address");
            }
            if (r.idDocumentType() != null) {
                data.setIdDocumentType(r.idDocumentType());
                supplied.addFields("id_document_type");
            }
            if (r.fullName() != null) {
                data.setFullName(r.fullName());
                supplied.addFields("full_name");
            }
            if (r.nationality() != null) {
                data.setNationality(r.nationality());
                supplied.addFields("nationality");
            }
            if (r.state() != null) {
                data.setState(r.state());
                supplied.addFields("state");
            }
            if (r.lga() != null) {
                data.setLga(r.lga());
                supplied.addFields("lga");
            }
            if (r.sameAsBusinessAddress() != null) {
                data.setSameAsBusinessAddress(r.sameAsBusinessAddress());
                supplied.addFields("same_as_business_address");
            }
            request.addOwners(data).addOwnerFields(supplied);
        }
        return result(invoke("saveOwners", () -> timed().saveMerchantOwnersDraft(request.build())));
    }

    // ── Methods with file uploads ───────────────────────────────

    public ComplianceData saveProfileWithFile(MutationContext context,
            com.tms.gateway.modules.compliance.dto.SaveProfileRequest r, MultipartFile cacCertificate) {
        var data = com.tms.gateway.modules.grpc.compliance.SaveProfileRequest.newBuilder();
        var supplied = new ArrayList<String>();
        data.setUserId(context.getActorUserId());
        if (r.country() != null) {
            supplied.add("country");
            data.setCountry(r.country());
        }
        if (r.industry() != null) {
            supplied.add("industry");
            data.setIndustry(r.industry());
        }
        if (r.category() != null) {
            supplied.add("category");
            data.setCategory(r.category());
        }
        if (r.tradingName() != null) {
            supplied.add("trading_name");
            data.setTradingName(r.tradingName());
        }
        if (r.salesVolume() != null) {
            supplied.add("sales_volume");
            data.setSalesVolume(r.salesVolume());
        }
        if (r.businessType() != null) {
            supplied.add("business_type");
            data.setBusinessType(r.businessType());
        }
        if (r.businessDescription() != null) {
            supplied.add("business_description");
            data.setBusinessDescription(r.businessDescription());
        }
        var builder = MerchantProfileDraftRequest.newBuilder().setContext(context).setData(data)
                .addAllSuppliedFields(supplied);
        if (cacCertificate != null && !cacCertificate.isEmpty()) {
            builder.setCacCertificate(toFileData(cacCertificate));
        }
        return result(invoke("saveProfileWithFile", () -> timed().saveMerchantProfileDraft(builder.build())));
    }

    public ComplianceData saveContactWithFile(MutationContext context,
            com.tms.gateway.modules.compliance.dto.SaveContactRequest r, MultipartFile addressProof) {
        var data = com.tms.gateway.modules.grpc.compliance.SaveContactRequest.newBuilder();
        var supplied = new ArrayList<String>();
        data.setUserId(context.getActorUserId());
        if (r.email() != null) {
            supplied.add("email");
            data.setEmail(r.email());
        }
        if (r.phone() != null) {
            supplied.add("phone");
            data.setPhone(r.phone());
        }
        if (r.address() != null) {
            supplied.add("address");
            data.setAddress(r.address());
        }
        if (r.supportEmail() != null) {
            supplied.add("support_email");
            data.setSupportEmail(r.supportEmail());
        }
        if (r.disputeEmail() != null) {
            supplied.add("dispute_email");
            data.setDisputeEmail(r.disputeEmail());
        }
        if (r.state() != null) {
            supplied.add("state");
            data.setState(r.state());
        }
        if (r.lga() != null) {
            supplied.add("lga");
            data.setLga(r.lga());
        }
        if (r.sameAsSupportEmail() != null) {
            supplied.add("same_as_support_email");
            data.setSameAsSupportEmail(r.sameAsSupportEmail());
        }
        if (r.onlineLinks() != null) {
            supplied.add("online_links");
            data.addAllOnlineLinks(r.onlineLinks().stream()
                    .map(l -> OnlineLinkData.newBuilder().setPlatform(l.platform() == null ? "" : l.platform())
                            .setUrl(l.url() == null ? "" : l.url()).build())
                    .toList());
        }
        var builder = MerchantContactDraftRequest.newBuilder().setContext(context).setData(data)
                .addAllSuppliedFields(supplied);
        if (addressProof != null && !addressProof.isEmpty()) {
            builder.setBusinessAddressProof(toFileData(addressProof));
        }
        return result(invoke("saveContactWithFile", () -> timed().saveMerchantContactDraft(builder.build())));
    }

    public ComplianceData saveOwnersWithFiles(MutationContext context,
            List<com.tms.gateway.modules.compliance.dto.SaveOwnerRequest> items, Map<Integer, OwnerFiles> ownerFiles) {
        var request = MerchantOwnersDraftRequest.newBuilder().setContext(context);
        for (int i = 0; i < items.size(); i++) {
            var r = items.get(i);
            var data = OwnerData.newBuilder();
            var supplied = FieldPresence.newBuilder();
            if (r.id() != null) {
                data.setId(r.id());
                supplied.addFields("id");
            }
            if (r.role() != null) {
                data.setRole(r.role());
                supplied.addFields("role");
            }
            if (r.dateOfBirth() != null) {
                data.setDateOfBirth(r.dateOfBirth());
                supplied.addFields("date_of_birth");
            }
            if (r.address() != null) {
                data.setAddress(r.address());
                supplied.addFields("address");
            }
            if (r.idDocumentType() != null) {
                data.setIdDocumentType(r.idDocumentType());
                supplied.addFields("id_document_type");
            }
            if (r.fullName() != null) {
                data.setFullName(r.fullName());
                supplied.addFields("full_name");
            }
            if (r.nationality() != null) {
                data.setNationality(r.nationality());
                supplied.addFields("nationality");
            }
            if (r.state() != null) {
                data.setState(r.state());
                supplied.addFields("state");
            }
            if (r.lga() != null) {
                data.setLga(r.lga());
                supplied.addFields("lga");
            }
            if (r.sameAsBusinessAddress() != null) {
                data.setSameAsBusinessAddress(r.sameAsBusinessAddress());
                supplied.addFields("same_as_business_address");
            }
            request.addOwners(data).addOwnerFields(supplied);

            // Build owner files for this index
            OwnerFileData.Builder fileBuilder = OwnerFileData.newBuilder();
            OwnerFiles files = ownerFiles.get(i);
            if (files != null) {
                if (files.identity() != null && !files.identity().isEmpty()) {
                    fileBuilder.setIdentityDocument(toFileData(files.identity()));
                }
                if (files.address() != null && !files.address().isEmpty()) {
                    fileBuilder.setAddressProof(toFileData(files.address()));
                }
            }
            request.addOwnerFiles(fileBuilder);
        }
        return result(invoke("saveOwnersWithFiles", () -> timed().saveMerchantOwnersDraft(request.build())));
    }

    private FileData toFileData(MultipartFile file) {
        try {
            return FileData.newBuilder().setContent(ByteString.copyFrom(file.getBytes()))
                    .setFileName(file.getOriginalFilename() != null ? file.getOriginalFilename() : "file")
                    .setContentType(file.getContentType() != null ? file.getContentType() : "application/octet-stream")
                    .build();
        } catch (IOException e) {
            throw new AppException("Failed to read uploaded file", HttpStatus.BAD_REQUEST);
        }
    }

    public ComplianceData saveBankAccounts(MutationContext context,
            List<com.tms.gateway.modules.compliance.dto.SaveBankAccountRequest> items) {
        var request = MerchantBankAccountsDraftRequest.newBuilder().setContext(context);
        boolean firstAccount = true;
        for (var r : items) {
            var data = BankAccountData.newBuilder();
            var supplied = FieldPresence.newBuilder();
            if (r.id() != null) {
                data.setId(r.id());
                supplied.addFields("id");
            }
            if (r.bankCode() != null) {
                data.setBankCode(r.bankCode());
                supplied.addFields("bank_code");
            }
            if (r.bankName() != null) {
                data.setBankName(r.bankName());
                supplied.addFields("bank_name");
            }
            if (r.accountNumber() != null) {
                data.setAccountNumber(r.accountNumber());
                supplied.addFields("account_number");
            }
            if (r.accountName() != null) {
                data.setAccountName(r.accountName());
                supplied.addFields("account_name");
            }
            // Auto-set is_primary=true for the first account (not exposed in API by design)
            if (firstAccount) {
                data.setIsPrimary(true);
                supplied.addFields("is_primary");
            }
            firstAccount = false;
            request.addBankAccounts(data).addAccountFields(supplied);
        }
        return result(invoke("saveBankAccounts", () -> timed().saveMerchantBankAccountsDraft(request.build())));
    }
    public ComplianceData submitCompliance(MutationContext context, boolean accepted, String ip, String userAgent) {
        return result(invoke("submit",
                () -> timed().acceptAgreementAndSubmit(MerchantSubmissionRequest.newBuilder().setContext(context)
                        .setAgreementAccepted(accepted).setIpAddress(ip == null ? "" : ip)
                        .setUserAgent(userAgent == null ? "" : userAgent).build())));
    }
    public ReferenceDataResponse referenceData(ReferenceDataRequest request) {
        return invoke("reference data", () -> timed().listComplianceReferenceData(request));
    }
    public DocumentUploadResponse createUpload(DocumentUploadRequest request) {
        return invoke("document upload", () -> timed().createComplianceDocumentUpload(request));
    }
    public DocumentResponse completeUpload(DocumentRequest request) {
        return invoke("complete upload", () -> timed().completeComplianceDocumentUpload(request));
    }
    public DocumentResponse getDocument(DocumentRequest request) {
        return invoke("document", () -> timed().getComplianceDocument(request));
    }
    public DocumentResponse deleteDocument(DocumentRequest request) {
        return invoke("delete document", () -> timed().deleteComplianceDocument(request));
    }
}
