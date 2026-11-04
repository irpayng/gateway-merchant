package com.tms.gateway.modules.customer.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.tms.gateway.core.exception.AppException;
import com.tms.gateway.modules.customer.dto.CreateCustomerRequest;
import com.tms.gateway.modules.customer.entity.PgMerchantCustomerEntity;
import com.tms.gateway.modules.customer.repository.PgMerchantCustomerRepository;
import com.tms.gateway.modules.transaction.entity.PgMerchantTransactionEntity;
import com.tms.gateway.modules.transaction.repository.PgMerchantTransactionRepository;
import com.tms.gateway.modules.transaction.repository.PgMerchantTransactionRepository.CustomerTransactionAggregate;
import com.tms.gateway.modules.user.entity.PgMerchantUserEntity;
import com.tms.gateway.modules.user.repository.PgMerchantUserRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

class CustomerServiceTest {
    private final PgMerchantCustomerRepository customerRepository = mock(PgMerchantCustomerRepository.class);
    private final PgMerchantTransactionRepository transactionRepository = mock(PgMerchantTransactionRepository.class);
    private final PgMerchantUserRepository userRepository = mock(PgMerchantUserRepository.class);
    private final CustomerService service = new CustomerService(customerRepository, transactionRepository,
            userRepository);

    @BeforeEach
    void setup() {
        when(userRepository.findAllByMerchantId("merchant-1"))
                .thenReturn(List.of(PgMerchantUserEntity.builder().id(9L).merchantId("merchant-1").build()));
    }

    @Test
    void listMergesSavedDetailsAndBlacklistWithTransactionAggregate() {
        Instant firstTransaction = Instant.parse("2025-03-01T10:00:00Z");
        var aggregate = aggregate("jane@example.com", firstTransaction, "Jane Transaction", "150.00", 2, 3);
        var saved = PgMerchantCustomerEntity.builder().customerRef("saved-ref").customerId("customer-1")
                .merchantId("merchant-1").normalizedEmail("jane@example.com").firstName("Jane").lastName("Doe")
                .phoneNumber("+2348012345678").blacklisted(true).createdAt(Instant.parse("2026-01-01T00:00:00Z"))
                .updatedAt(Instant.parse("2026-01-01T00:00:00Z")).build();
        when(transactionRepository.aggregateCustomers(List.of(9L))).thenReturn(List.of(aggregate));
        when(customerRepository.findAllByMerchantId("merchant-1")).thenReturn(List.of(saved));

        var response = service.list("merchant-1", "234801", 0, 20);

        assertEquals(1, response.totalElements());
        var customer = response.content().getFirst();
        assertEquals("customer-1", customer.customerId());
        assertEquals("Jane Doe", customer.fullName());
        assertEquals(firstTransaction, customer.dateAdded());
        assertTrue(customer.blacklisted());
    }

    @Test
    void addCustomerEnrichesTransactionDerivedIdentity() {
        Instant firstTransaction = Instant.parse("2025-03-01T10:00:00Z");
        var aggregate = aggregate("jane@example.com", firstTransaction, "Transaction Name", "150.00", 2, 3);
        when(customerRepository.findByMerchantIdAndNormalizedEmail("merchant-1", "jane@example.com"))
                .thenReturn(Optional.empty());
        when(customerRepository.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(transactionRepository.aggregateCustomers(List.of(9L))).thenReturn(List.of(aggregate));

        var customer = service.create("merchant-1",
                new CreateCustomerRequest(" Jane ", " Doe ", "JANE@example.com", "+2348012345678"));

        assertNotNull(customer.customerId());
        assertEquals("jane@example.com", customer.email());
        assertEquals("Jane Doe", customer.fullName());
        assertEquals(firstTransaction, customer.dateAdded());
        ArgumentCaptor<PgMerchantCustomerEntity> captor = ArgumentCaptor.forClass(PgMerchantCustomerEntity.class);
        verify(customerRepository).saveAndFlush(captor.capture());
        assertEquals(customer.customerRef(), captor.getValue().getCustomerRef());
        assertEquals("jane@example.com", captor.getValue().getNormalizedEmail());
    }

    @Test
    void addCustomerRejectsAlreadySavedEmail() {
        when(customerRepository.findByMerchantIdAndNormalizedEmail("merchant-1", "jane@example.com"))
                .thenReturn(Optional.of(PgMerchantCustomerEntity.builder().customerId("already-saved").build()));

        var failure = assertThrows(AppException.class,
                () -> service.create("merchant-1", new CreateCustomerRequest("Jane", "Doe", "jane@example.com", null)));

        assertEquals(409, failure.getStatus().value());
        verify(customerRepository, never()).saveAndFlush(any());
    }

    @Test
    void customerDetailsReturnNgAndSuccessfulOnlySpend() {
        Instant firstTransaction = Instant.parse("2025-03-01T10:00:00Z");
        var aggregate = aggregate("jane@example.com", firstTransaction, "Jane Doe", "150.25", 2, 4);
        when(transactionRepository.aggregateCustomers(List.of(9L))).thenReturn(List.of(aggregate));
        when(customerRepository.findByMerchantIdAndCustomerRef("merchant-1",
                customerRef("merchant-1", "jane@example.com"))).thenReturn(Optional.empty());

        var list = service.list("merchant-1", null, 0, 20);
        var details = service.get("merchant-1", list.content().getFirst().customerRef());

        assertEquals("150.25", details.totalSpend().toPlainString());
        assertEquals("NGN", details.currency());
        assertEquals(2, details.successfulCount());
        assertEquals(4, details.totalCount());
    }

    @Test
    void blacklistCanBeSetForDerivedCustomerWithoutAssigningCustomerId() {
        var aggregate = aggregate("jane@example.com", Instant.parse("2025-03-01T10:00:00Z"), "Jane Doe", "0", 0, 1);
        when(transactionRepository.aggregateCustomers(List.of(9L))).thenReturn(List.of(aggregate));
        when(customerRepository.findByMerchantIdAndCustomerRef("merchant-1",
                customerRef("merchant-1", "jane@example.com"))).thenReturn(Optional.empty());
        when(customerRepository.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));

        var response = service.setBlacklisted("merchant-1", customerRef("merchant-1", "jane@example.com"), true);

        assertTrue(response.blacklisted());
        assertNull(response.customerId());
        verify(customerRepository).saveAndFlush(
                argThat(c -> c.isBlacklisted() && c.getCustomerId() == null && "merchant-1".equals(c.getMerchantId())));
    }

    @Test
    void customerTransactionRowsArePagedAndUseNgCurrency() {
        String ref = customerRef("merchant-1", "jane@example.com");
        var aggregate = aggregate("jane@example.com", Instant.parse("2025-03-01T10:00:00Z"), "Jane Doe", "15.00", 1, 1);
        var transaction = PgMerchantTransactionEntity.builder().reference("txn-1").amount(new BigDecimal("15.00"))
                .paymentMethod("card").statusCode("completed").createdAt(Instant.parse("2025-03-01T10:00:00Z")).build();
        when(transactionRepository.aggregateCustomers(List.of(9L))).thenReturn(List.of(aggregate));
        when(customerRepository.findByMerchantIdAndCustomerRef("merchant-1", ref)).thenReturn(Optional.empty());
        when(transactionRepository.findCustomerTransactions(eq(List.of(9L)), eq("jane@example.com"), any()))
                .thenReturn(new PageImpl<>(List.of(transaction), PageRequest.of(0, 20), 1));

        var response = service.transactions("merchant-1", ref, 0, 20);

        assertEquals("NGN", response.content().getFirst().currency());
        assertEquals("Card", response.content().getFirst().paymentMethodDisplay());
        assertEquals("Successful", response.content().getFirst().statusDisplay());
    }

    @Test
    void rejectsInvalidPaging() {
        assertEquals(400,
                assertThrows(AppException.class, () -> service.list("merchant-1", null, -1, 20)).getStatus().value());
        assertEquals(400,
                assertThrows(AppException.class, () -> service.list("merchant-1", null, 0, 101)).getStatus().value());
        verifyNoInteractions(transactionRepository);
    }

    @Test
    void customerReferenceCannotResolveAcrossMerchantScope() {
        when(userRepository.findAllByMerchantId("merchant-2")).thenReturn(List.of());
        when(customerRepository.findByMerchantIdAndCustomerRef("merchant-2",
                customerRef("merchant-1", "jane@example.com"))).thenReturn(Optional.empty());

        var failure = assertThrows(AppException.class,
                () -> service.get("merchant-2", customerRef("merchant-1", "jane@example.com")));

        assertEquals(404, failure.getStatus().value());
        verifyNoInteractions(transactionRepository);
    }

    private CustomerTransactionAggregate aggregate(String email, Instant date, String name, String spend,
            long successful, long total) {
        var aggregate = mock(CustomerTransactionAggregate.class);
        when(aggregate.getNormalizedEmail()).thenReturn(email);
        when(aggregate.getEarliestTransactionAt()).thenReturn(date);
        when(aggregate.getTransactionName()).thenReturn(name);
        when(aggregate.getSuccessfulSpend()).thenReturn(new BigDecimal(spend));
        when(aggregate.getSuccessfulCount()).thenReturn(successful);
        when(aggregate.getTotalCount()).thenReturn(total);
        return aggregate;
    }

    private String customerRef(String merchantId, String email) {
        return java.util.UUID
                .nameUUIDFromBytes((merchantId + "\u0000" + email).getBytes(java.nio.charset.StandardCharsets.UTF_8))
                .toString();
    }
}
