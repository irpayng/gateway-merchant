package com.tms.gateway.modules.compliance.dto;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.*;
public record CreateDocumentUploadRequest(@NotBlank String purpose,
        @JsonProperty("file_name") @NotBlank String fileName,
        @JsonProperty("content_type") @NotBlank String contentType,
        @JsonProperty("size_bytes") @Min(1) @Max(10485760) long sizeBytes) {
}
