package com.tms.gateway.modules.compliance.repository;

import com.tms.gateway.modules.compliance.entity.PgMerchantComplianceReplicaEntity;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface PgMerchantComplianceReplicaRepository extends JpaRepository<PgMerchantComplianceReplicaEntity, Long> {

    Optional<PgMerchantComplianceReplicaEntity> findByUserId(Long userId);

    Optional<PgMerchantComplianceReplicaEntity> findByMerchantId(String merchantId);

    Optional<PgMerchantComplianceReplicaEntity> findBySourceId(Long sourceId);
}
