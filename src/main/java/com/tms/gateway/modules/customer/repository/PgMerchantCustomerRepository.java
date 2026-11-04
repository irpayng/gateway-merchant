package com.tms.gateway.modules.customer.repository;

import com.tms.gateway.modules.customer.entity.PgMerchantCustomerEntity;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface PgMerchantCustomerRepository extends JpaRepository<PgMerchantCustomerEntity, String> {

    Optional<PgMerchantCustomerEntity> findByMerchantIdAndCustomerRef(String merchantId, String customerRef);

    Optional<PgMerchantCustomerEntity> findByMerchantIdAndNormalizedEmail(String merchantId, String normalizedEmail);

    List<PgMerchantCustomerEntity> findAllByMerchantId(String merchantId);
}
