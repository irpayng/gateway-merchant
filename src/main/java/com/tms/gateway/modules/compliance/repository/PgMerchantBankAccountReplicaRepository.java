package com.tms.gateway.modules.compliance.repository;

import com.tms.gateway.modules.compliance.entity.PgMerchantBankAccountReplicaEntity;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface PgMerchantBankAccountReplicaRepository
        extends
            JpaRepository<PgMerchantBankAccountReplicaEntity, Long> {

    List<PgMerchantBankAccountReplicaEntity> findByComplianceId(Long complianceId);

    void deleteByComplianceId(Long complianceId);
}
