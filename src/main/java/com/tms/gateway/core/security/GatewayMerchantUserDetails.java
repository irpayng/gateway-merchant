package com.tms.gateway.core.security;

import java.time.Instant;
import java.util.Collection;
import java.util.Collections;
import lombok.Builder;
import lombok.Getter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

/**
 * Spring Security principal for a Payment Gateway merchant.
 *
 * <p>
 * Unlike Merchant-Backend (which has complex roles and operators),
 * gateway-merchant has a simpler model: each user is the business owner with
 * full access. The compliance gate (not this class) controls access to features
 * based on compliance status.
 */
@Getter
@Builder
public class GatewayMerchantUserDetails implements UserDetails {

    private final Long id;
    private final String merchantId;
    private final String email;
    private final String phoneNumber;
    private final String businessName;
    private final String firstName;
    private final String lastName;
    private final Instant emailVerifiedAt;
    private final Instant blockedAt;
    private final Instant suspendedAt;

    /**
     * Returns the user ID. Alias for getId() for compatibility.
     */
    public Long getUserId() {
        return id;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        // All pg_merchant users have the same base authority
        // Feature access is controlled by compliance status, not roles
        return Collections.emptyList();
    }

    @Override
    public String getPassword() {
        // Password authentication is delegated to tms-user via gRPC
        return "";
    }

    @Override
    public String getUsername() {
        return email;
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return blockedAt == null;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return emailVerifiedAt != null && blockedAt == null && suspendedAt == null;
    }

    /**
     * Returns the user's display name (first + last) or business name as fallback.
     */
    public String getDisplayName() {
        if (firstName != null && !firstName.isBlank()) {
            String name = firstName;
            if (lastName != null && !lastName.isBlank()) {
                name += " " + lastName;
            }
            return name;
        }
        return businessName != null ? businessName : email;
    }
}
