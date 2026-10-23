package com.tms.gateway.core.exception;

import java.util.HashMap;
import java.util.Map;
import lombok.Getter;

/**
 * Exception thrown when a gRPC call to a microservice fails.
 */
@Getter
public class GrpcException extends RuntimeException {

    private final String service;
    private final String method;
    private final String grpcStatus;

    public GrpcException(String service, String method, String grpcStatus, String message) {
        super(message);
        this.service = service;
        this.method = method;
        this.grpcStatus = grpcStatus;
    }

    public Map<String, Object> toMap() {
        Map<String, Object> map = new HashMap<>();
        map.put("service", service);
        map.put("method", method);
        map.put("grpc_status", grpcStatus);
        map.put("message", getMessage());
        return map;
    }
}
