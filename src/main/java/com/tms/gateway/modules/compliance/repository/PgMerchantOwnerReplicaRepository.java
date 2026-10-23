package com.tms.gateway.modules.compliance.repository;

import com.tms.gateway.modules.compliance.entity.PgMerchantOwnerReplicaEntity;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface PgMerchantOwnerReplicaRepository extends JpaRepository<PgMerchantOwnerReplicaEntity, Long> {

    List<PgMerchantOwnerReplicaEntity> findByComplianceId(Long complianceId);

    void deleteByComplianceId(Long complianceId);
}
