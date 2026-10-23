package com.tms.gateway.modules.auth.service;

import com.tms.gateway.core.security.GatewayMerchantUserDetails;
import com.tms.gateway.core.security.JwtService;
import com.tms.gateway.grpc.user.AuthPgMerchantResponse;
import com.tms.gateway.modules.auth.dto.LoginRequest;
import com.tms.gateway.modules.auth.dto.LoginResponse;
import com.tms.gateway.modules.auth.dto.UserData;
import com.tms.gateway.modules.grpc.service.UserGrpcClient;
import com.tms.gateway.modules.user.entity.PgMerchantUserEntity;
import com.tms.gateway.modules.user.repository.PgMerchantUserRepository;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Authentication service for pg_merchant dashboard.
 *
 * <p>
 * Authenticates users via gRPC to tms-user (same credentials as registration),
 * then issues local JWTs for session management.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserGrpcClient userGrpcClient;
    private final JwtService jwtService;
    private final PgMerchantUserRepository userRepository;

    @Value("${app.jwt.expiration-ms}")
    private long jwtExpirationMs;

    /**
     * Authenticate a pg_merchant user and issue a JWT.
     *
     * @param request
     *            the login request with email and password
     * @return the login response with token and user data
     */
    @Transactional
    public LoginResponse login(LoginRequest request) {
        // Authenticate via gRPC to tms-user
        AuthPgMerchantResponse authResponse = userGrpcClient.authPgMerchant(request.getEmail().trim().toLowerCase(),
                request.getPassword());

        // Generate JWT token
        String token = jwtService.generateToken(authResponse.getUserId(), authResponse.getMerchantId(),
                authResponse.getEmail());

        // Update or create local replica entry
        upsertLocalReplica(authResponse);

        // Build user data (compliance status will be added when compliance module is
        // implemented)
        UserData userData = UserData.builder().id(authResponse.getUserId()).merchantId(authResponse.getMerchantId())
                .email(authResponse.getEmail()).phoneNumber(authResponse.getPhoneNumber())
                .businessName(authResponse.getBusinessName()).firstName(authResponse.getFirstName())
                .lastName(authResponse.getLastName()).displayName(buildDisplayName(authResponse))
                .complianceStatus("none") // TODO: fetch from compliance replica
                .complianceStep(0).complianceRequired(true).build();

        log.info("Login successful for userId={} merchantId={}", authResponse.getUserId(),
                authResponse.getMerchantId());

        return LoginResponse.builder().token(token).tokenType("Bearer").expiresIn(jwtExpirationMs / 1000).user(userData)
                .build();
    }

    /**
     * Get current user data from local replica.
     *
     * @param userDetails
     *            the authenticated user principal
     * @return the user data with compliance status
     */
    public UserData getCurrentUser(GatewayMerchantUserDetails userDetails) {
        // TODO: fetch compliance status from compliance replica
        return UserData.builder().id(userDetails.getId()).merchantId(userDetails.getMerchantId())
                .email(userDetails.getEmail()).phoneNumber(userDetails.getPhoneNumber())
                .businessName(userDetails.getBusinessName()).firstName(userDetails.getFirstName())
                .lastName(userDetails.getLastName()).displayName(userDetails.getDisplayName()).complianceStatus("none") // TODO:
                                                                                                                        // fetch
                                                                                                                        // from
                                                                                                                        // compliance
                                                                                                                        // replica
                .complianceStep(0).complianceRequired(true).build();
    }

    /**
     * Update or create local user replica from auth response. This ensures the
     * local replica is up-to-date after a successful login, even if Kafka
     * replication is delayed.
     */
    private void upsertLocalReplica(AuthPgMerchantResponse auth) {
        try {
            var existing = userRepository.findById(auth.getUserId());
            Instant now = Instant.now();

            if (existing.isPresent()) {
                PgMerchantUserEntity user = existing.get();
                user.setEmail(auth.getEmail());
                user.setPhoneNumber(auth.getPhoneNumber());
                user.setBusinessName(auth.getBusinessName());
                user.setFirstName(auth.getFirstName());
                user.setLastName(auth.getLastName());
                user.setUpdatedAt(now);
                user.setSyncedAt(now);
                userRepository.save(user);
            } else {
                // Create new replica entry (first login before Kafka sync)
                PgMerchantUserEntity user = PgMerchantUserEntity.builder().id(auth.getUserId())
                        .merchantId(auth.getMerchantId()).email(auth.getEmail()).phoneNumber(auth.getPhoneNumber())
                        .businessName(auth.getBusinessName()).firstName(auth.getFirstName())
                        .lastName(auth.getLastName()).emailVerifiedAt(now) // Must be verified to login
                        .createdAt(now).updatedAt(now).syncedAt(now).build();
                userRepository.save(user);
            }
        } catch (Exception e) {
            // Don't fail login if replica update fails
            log.warn("Failed to update local replica for userId={}: {}", auth.getUserId(), e.getMessage());
        }
    }

    private String buildDisplayName(AuthPgMerchantResponse auth) {
        if (auth.getFirstName() != null && !auth.getFirstName().isBlank()) {
            String name = auth.getFirstName();
            if (auth.getLastName() != null && !auth.getLastName().isBlank()) {
                name += " " + auth.getLastName();
            }
            return name;
        }
        return auth.getBusinessName() != null ? auth.getBusinessName() : auth.getEmail();
    }
}
