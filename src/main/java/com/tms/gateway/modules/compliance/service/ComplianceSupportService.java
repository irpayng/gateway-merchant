package com.tms.gateway.modules.compliance.service;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.tms.gateway.core.exception.AppException;
import com.tms.gateway.modules.compliance.dto.CreateDocumentUploadRequest;
import com.tms.gateway.modules.grpc.compliance.*;
import com.tms.gateway.modules.grpc.service.ComplianceGrpcClient;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
@Service
@RequiredArgsConstructor
public class ComplianceSupportService {
    private final ComplianceGrpcClient client;
    public record Option(String code, String label, @JsonProperty("parent_code") String parentCode, String currency,
            String minimum, String maximum) {
    }
    public record Upload(@JsonProperty("document_id") String documentId, @JsonProperty("upload_url") String uploadUrl,
            @JsonProperty("expires_at") String expiresAt, Map<String, String> headers, String method) {
    }
    public record Document(@JsonProperty("document_id") String documentId, String status,
            @JsonProperty("download_url") String downloadUrl, @JsonProperty("expires_at") String expiresAt) {
    }
    public List<Option> referenceData(String kind, String parent, String country, String currency) {
        if (!Set.of("industries", "categories", "business-types", "countries", "states", "lgas", "sales-ranges",
                "currencies", "owner-roles", "id-document-types").contains(kind))
            throw new AppException("Unknown reference data kind");
        return client
                .referenceData(ReferenceDataRequest.newBuilder().setKind(kind).setParentCode(parent).setCountry(country)
                        .setCurrency(currency).build())
                .getOptionsList().stream().map(o -> new Option(o.getCode(), o.getLabel(), o.getParentCode(),
                        o.getCurrency(), o.getMinimum(), o.getMaximum()))
                .toList();
    }
    public Upload upload(MutationContext context, CreateDocumentUploadRequest r) {
        if (!Set.of("identity", "owner_address", "business_address").contains(r.purpose()))
            throw new AppException("Invalid document purpose");
        if (!Set.of("application/pdf", "image/jpeg", "image/png").contains(r.contentType()))
            throw new AppException("Only PDF, JPEG and PNG documents are accepted");
        if (r.fileName().contains("/") || r.fileName().contains("\\")
                || r.fileName().chars().anyMatch(Character::isISOControl))
            throw new AppException("Invalid file name");
        var d = client.createUpload(DocumentUploadRequest.newBuilder().setContext(context).setPurpose(r.purpose())
                .setFileName(r.fileName()).setContentType(r.contentType()).setSizeBytes(r.sizeBytes()).build());
        return new Upload(d.getDocumentId(), d.getUploadUrl(), d.getExpiresAt(), d.getHeadersMap(), d.getMethod());
    }
    public Document document(MutationContext context, String id, String action) {
        var request = DocumentRequest.newBuilder().setContext(context).setDocumentId(id).build();
        var d = switch (action) {
            case "complete" -> client.completeUpload(request);
            case "delete" -> client.deleteDocument(request);
            default -> client.getDocument(request);
        };
        return new Document(d.getDocumentId(), d.getStatus(), d.getDownloadUrl(), d.getExpiresAt());
    }
}
