package com.tms.gateway.modules.compliance.repository;

import com.tms.gateway.modules.compliance.entity.PgMerchantProfileReplicaEntity;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface PgMerchantProfileReplicaRepository extends JpaRepository<PgMerchantProfileReplicaEntity, Long> {

    Optional<PgMerchantProfileReplicaEntity> findByComplianceId(Long complianceId);

    void deleteByComplianceId(Long complianceId);
}
