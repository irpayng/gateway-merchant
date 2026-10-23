package com.tms.gateway.core.security;

import com.tms.gateway.modules.user.repository.PgMerchantUserRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Validates JWTs and populates the Spring Security context with the pg_merchant
 * user principal. Loads the user from the local replica each request so that
 * blocked/suspended accounts stop authenticating immediately.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtService jwtService;
    private final PgMerchantUserRepository userRepository;

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request, @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain) throws ServletException, IOException {
        String authHeader = request.getHeader("Authorization");

        String jwt = null;
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            jwt = authHeader.substring(7);
        }

        if (jwt == null) {
            filterChain.doFilter(request, response);
            return;
        }

        try {
            String email = jwtService.extractUsername(jwt);
            Long userId = jwtService.extractUserId(jwt);
            String merchantId = jwtService.extractMerchantId(jwt);

            if (email != null && SecurityContextHolder.getContext().getAuthentication() == null) {
                // Load user from local replica to check current blocked/suspended status
                var userOpt = userRepository.findByEmail(email);

                if (userOpt.isPresent()) {
                    var user = userOpt.get();

                    // Verify token claims match the user record
                    if (user.getId().equals(userId) && user.getMerchantId().equals(merchantId)) {
                        var userDetails = GatewayMerchantUserDetails.builder().id(user.getId())
                                .merchantId(user.getMerchantId()).email(user.getEmail())
                                .phoneNumber(user.getPhoneNumber()).businessName(user.getBusinessName())
                                .firstName(user.getFirstName()).lastName(user.getLastName())
                                .emailVerifiedAt(user.getEmailVerifiedAt()).blockedAt(user.getBlockedAt())
                                .suspendedAt(user.getSuspendedAt()).build();

                        // Only authenticate if user is enabled (not blocked, not suspended, email
                        // verified)
                        if (userDetails.isEnabled() && jwtService.isTokenValid(jwt, email)) {
                            var authToken = new UsernamePasswordAuthenticationToken(userDetails, null,
                                    userDetails.getAuthorities());
                            authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                            SecurityContextHolder.getContext().setAuthentication(authToken);
                        }
                    }
                }
            }
        } catch (Exception e) {
            // Invalid/expired token or user not in replica — proceed unauthenticated
            log.debug("JWT authentication failed: {}", e.getMessage());
        }

        filterChain.doFilter(request, response);
    }
}
