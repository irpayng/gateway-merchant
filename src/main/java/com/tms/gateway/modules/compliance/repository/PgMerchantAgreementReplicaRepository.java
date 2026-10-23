package com.tms.gateway.modules.compliance.repository;

import com.tms.gateway.modules.compliance.entity.PgMerchantAgreementReplicaEntity;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface PgMerchantAgreementReplicaRepository extends JpaRepository<PgMerchantAgreementReplicaEntity, Long> {

    Optional<PgMerchantAgreementReplicaEntity> findByComplianceId(Long complianceId);

    void deleteByComplianceId(Long complianceId);
}
