package com.tms.gateway.modules.compliance.dto;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.*;
/**
 * Request to create a document upload session.
 *
 * <p>
 * For owner/shareholder documents, include {@code owner_id} to automatically
 * link the document to that owner when upload completes. The owner must exist
 * (save the owner first via PUT /compliance/owners).
 * </p>
 *
 * <p>
 * Supported purposes:
 * </p>
 * <ul>
 * <li>{@code identity} - Owner ID document (for starter business owner)</li>
 * <li>{@code owner_address} - Owner proof of address (for starter
 * business)</li>
 * <li>{@code business_address} - Business proof of address</li>
 * <li>{@code shareholder_identity} - Shareholder ID document (requires
 * owner_id)</li>
 * <li>{@code shareholder_address} - Shareholder proof of address (requires
 * owner_id)</li>
 * </ul>
 */
public record CreateDocumentUploadRequest(@NotBlank String purpose,
        @JsonProperty("file_name") @NotBlank String fileName,
        @JsonProperty("content_type") @NotBlank String contentType,
        @JsonProperty("size_bytes") @Min(1) @Max(10485760) long sizeBytes, @JsonProperty("owner_id") Long ownerId) {
}
