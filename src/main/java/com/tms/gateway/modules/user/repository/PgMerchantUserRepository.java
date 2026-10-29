package com.tms.gateway.modules.user.repository;

import com.tms.gateway.modules.user.entity.PgMerchantUserEntity;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Repository for pg_merchant user replicas.
 */
@Repository
public interface PgMerchantUserRepository extends JpaRepository<PgMerchantUserEntity, Long> {

    Optional<PgMerchantUserEntity> findByEmail(String email);

    java.util.List<PgMerchantUserEntity> findAllByMerchantId(String merchantId);

    Optional<PgMerchantUserEntity> findByPhoneNumber(String phoneNumber);

    boolean existsByEmail(String email);

    boolean existsByMerchantId(String merchantId);
}
