package com.tms.gateway.modules.compliance.service;
import com.tms.gateway.modules.compliance.dto.ComplianceResponse.CorrectionData;
import java.util.List;
import lombok.Getter;
@Getter
public class ComplianceValidationException extends RuntimeException {
    private final List<CorrectionData> errors;
    public ComplianceValidationException(List<CorrectionData> errors) {
        super("Compliance validation failed");
        this.errors = List.copyOf(errors);
    }
}
