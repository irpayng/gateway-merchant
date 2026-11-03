package com.tms.gateway.modules.compliance.service;

import com.tms.gateway.core.exception.AppException;
import com.tms.gateway.modules.compliance.dto.ComplianceResponse.CorrectionData;
import java.util.List;
import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public class CompliancePartialSaveException extends RuntimeException {
    private final HttpStatus status;
    private final long savedVersion;
    private final List<CorrectionData> errors;

    public CompliancePartialSaveException(AppException cause, long savedVersion) {
        super(cause.getMessage(), cause);
        this.status = cause.getStatus();
        this.savedVersion = savedVersion;
        this.errors = List.of();
    }

    public CompliancePartialSaveException(ComplianceValidationException cause, long savedVersion) {
        super(cause.getMessage(), cause);
        this.status = HttpStatus.UNPROCESSABLE_ENTITY;
        this.savedVersion = savedVersion;
        this.errors = cause.getErrors();
    }
}
