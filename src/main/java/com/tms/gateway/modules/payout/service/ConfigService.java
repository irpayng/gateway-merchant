package com.tms.gateway.modules.payout.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tms.gateway.core.exception.AppException;
import com.tms.gateway.modules.grpc.config.GrpcProperties;
import com.tms.gateway.modules.payout.dto.BankResponse;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

/**
 * Service for fetching reference data from tms-config. Results are cached since
 * bank codes rarely change.
 */
@Slf4j
@Service
public class ConfigService {

    private final GrpcProperties properties;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    public ConfigService(GrpcProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(Duration.ofSeconds(10)).build();
    }

    /**
     * Get all bank codes from tms-config. Results are cached for 1 hour.
     *
     * @return list of all bank codes with names
     */
    @Cacheable(value = "banks", unless = "#result == null || #result.isEmpty()")
    public List<BankResponse> getBanks() {
        log.debug("Fetching bank codes from config service at {}:{}", properties.getConfigServiceHost(),
                properties.getConfigServicePort());

        try {
            String url = String.format("http://%s:%d/bank-codes/all", properties.getConfigServiceHost(),
                    properties.getConfigServicePort());

            HttpRequest request = HttpRequest.newBuilder().uri(URI.create(url)).timeout(Duration.ofSeconds(30))
                    .header("Accept", "application/json").GET().build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() >= 400) {
                log.error("Config service returned error: status={}, body={}", response.statusCode(), response.body());
                throw new AppException("Failed to fetch banks", HttpStatus.BAD_GATEWAY);
            }

            // Response format: {"code":200,"message":"success","data":[{...}]}
            Map<String, Object> result = objectMapper.readValue(response.body(),
                    new TypeReference<Map<String, Object>>() {
                    });

            Object data = result.get("data");
            if (data instanceof List<?> list) {
                return list.stream().filter(item -> item instanceof Map).map(item -> {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> bank = (Map<String, Object>) item;
                    return BankResponse.fromMap(bank);
                }).toList();
            }

            throw new AppException("Unexpected response format", HttpStatus.BAD_GATEWAY);

        } catch (AppException e) {
            throw e;
        } catch (Exception e) {
            log.error("Failed to fetch banks: {}", e.getMessage(), e);
            throw new AppException("Failed to fetch banks: " + e.getMessage(), HttpStatus.BAD_GATEWAY);
        }
    }

    /**
     * Get most commonly used banks. Returns a curated list of top Nigerian banks.
     *
     * @return list of most used banks
     */
    @Cacheable(value = "banks-most-used", unless = "#result == null || #result.isEmpty()")
    public List<BankResponse> getMostUsedBanks() {
        log.debug("Fetching most used banks from config service at {}:{}", properties.getConfigServiceHost(),
                properties.getConfigServicePort());

        try {
            String url = String.format("http://%s:%d/banks/most-used", properties.getConfigServiceHost(),
                    properties.getConfigServicePort());

            HttpRequest request = HttpRequest.newBuilder().uri(URI.create(url)).timeout(Duration.ofSeconds(30))
                    .header("Accept", "application/json").GET().build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() >= 400) {
                log.error("Config service returned error: status={}, body={}", response.statusCode(), response.body());
                throw new AppException("Failed to fetch banks", HttpStatus.BAD_GATEWAY);
            }

            Map<String, Object> result = objectMapper.readValue(response.body(),
                    new TypeReference<Map<String, Object>>() {
                    });

            Object data = result.get("data");
            if (data instanceof List<?> list) {
                return list.stream().filter(item -> item instanceof Map).map(item -> {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> bank = (Map<String, Object>) item;
                    return BankResponse.fromMap(bank);
                }).toList();
            }

            throw new AppException("Unexpected response format", HttpStatus.BAD_GATEWAY);

        } catch (AppException e) {
            throw e;
        } catch (Exception e) {
            log.error("Failed to fetch most used banks: {}", e.getMessage(), e);
            throw new AppException("Failed to fetch banks: " + e.getMessage(), HttpStatus.BAD_GATEWAY);
        }
    }
}
