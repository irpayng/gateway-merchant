package com.tms.gateway.modules.compliance.repository;

import com.tms.gateway.modules.compliance.entity.PgMerchantContactReplicaEntity;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface PgMerchantContactReplicaRepository extends JpaRepository<PgMerchantContactReplicaEntity, Long> {

    Optional<PgMerchantContactReplicaEntity> findByComplianceId(Long complianceId);

    void deleteByComplianceId(Long complianceId);
}
