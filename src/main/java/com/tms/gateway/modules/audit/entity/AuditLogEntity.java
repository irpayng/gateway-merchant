package com.tms.gateway.modules.audit.entity;

import jakarta.persistence.*;
import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * One recorded merchant activity (a state-changing API request).
 */
@Entity
@Table(name = "audit_logs")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuditLogEntity {

    public static final String STATUS_SUCCESS = "success";
    public static final String STATUS_FAILED = "failed";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id")
    private Long userId;

    @Column(name = "merchant_id", length = 50)
    private String merchantId;

    @Column(name = "actor_email")
    private String actorEmail;

    @Column(name = "actor_name")
    private String actorName;

    @Column(nullable = false, length = 150)
    private String action;

    @Column(nullable = false, length = 100)
    private String module;

    @Column(name = "http_method", nullable = false, length = 10)
    private String httpMethod;

    @Column(nullable = false)
    private String endpoint;

    @Column(name = "request_path", nullable = false, length = 500)
    private String requestPath;

    @Column(name = "resource_id")
    private String resourceId;

    @Column(nullable = false, length = 20)
    private String status;

    @Column(name = "status_code", nullable = false)
    private Integer statusCode;

    @Column(name = "result_message", columnDefinition = "text")
    private String resultMessage;

    @Column(name = "error_message", columnDefinition = "text")
    private String errorMessage;

    @Column(name = "request_payload", columnDefinition = "jsonb")
    @JdbcTypeCode(SqlTypes.JSON)
    private String requestPayload;

    @Column(name = "response_payload", columnDefinition = "jsonb")
    @JdbcTypeCode(SqlTypes.JSON)
    private String responsePayload;

    @Column(name = "ip_address", length = 64)
    private String ipAddress;

    @Column(name = "user_agent", length = 500)
    private String userAgent;

    @Column(name = "duration_ms")
    private Long durationMs;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
}
