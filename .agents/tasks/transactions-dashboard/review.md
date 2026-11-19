# Review: Transactions Dashboard Endpoint Implementation

**Commit:** eace4a2 - feat(transactions): add transactions module for merchant dashboard  
**Reviewer:** Kiro  
**Date:** 2026-11-03

## Evidence Summary

**Build verification (from commit message):**
- `mvn spotless:apply && spotless:check`: PASSED
- `mvn compile`: BUILD SUCCESS

## Review Checklist

### 1. Database Migration (V6__create_pg_merchant_transactions.sql) ✅

- Table schema correctly defines all required fields
- Proper indexes for dashboard queries:
  - `idx_pg_merchant_transactions_user_created` (user_id, created_at DESC) — optimal for list queries
  - `idx_pg_merchant_transactions_user_status` (user_id, status_code) — for status filtering
  - `idx_pg_merchant_transactions_reference` — for detail lookups
  - `idx_pg_merchant_transactions_user_payment_method` — for payment method filtering
- Unique constraint on reference column prevents duplicates
- JSONB metadata column for flexible storage

### 2. Kafka Consumer (TransactionEventConsumer.java) ✅

- Correctly listens to `transaction-result` topic (matching tms-transaction's TransactionResultPublisher)
- **pg_merchant user filtering**: Uses `userRepository.existsById(userId)` to filter only pg_merchant users
- Field mappings align with TransactionResultEvent from tms-transaction:
  - `user_id`, `reference`, `product_code`, `status`, `message`, `amount`, `service_fee`
  - `channel`, `provider_code`, `provider_reference`, `metadata`
- Status normalization handles all expected values: successful/success/completed → completed, failed/failure/error → failed, pending/processing/initiated → pending, reversed/refunded → reversed
- Payment method derivation from channel and metadata is implemented correctly
- Upsert pattern with findByReference for idempotent processing
- Follows existing ComplianceEventConsumer pattern with @Transactional and proper error logging

### 3. Entity (PgMerchantTransactionEntity.java) ✅

- All fields match migration schema
- Proper JPA annotations with correct column names
- JSONB metadata handling with @JdbcTypeCode(SqlTypes.JSON)
- Lombok @Builder enables clean construction in consumer

### 4. Repository (PgMerchantTransactionRepository.java) ✅

- `findByFilters` query handles all filter combinations correctly with optional parameters
- `getSummaryByStatus` efficiently aggregates counts and sums by status
- All queries properly scoped by userId
- Pagination supported via Spring Data Pageable

### 5. DTOs ✅

- **TransactionSummaryDto**: Includes all required dashboard card metrics (total, successful, failed, pending counts and values)
- **TransactionListItemDto**: Includes both raw status/payment_method and display variants
- **TransactionDetailDto**: Includes full details including bank_name, account_number, provider_reference
- **TransactionPageResponse**: Standard pagination metadata (page, size, total_elements, has_next, has_previous)

### 6. Service (TransactionService.java) ✅

- **Status mapping**: Correctly maps completed/successful → "Successful" for display
- **Payment method mapping**: card → "Card", bank_transfer → "Bank Transfer", wallet → "IRPay Wallet"
- Input normalization accepts both display names and codes for filtering
- Summary aggregation handles multiple status codes mapping to same category
- @Transactional(readOnly = true) for all read operations

### 7. Controller (TransactionController.java) ✅

- **Security**: All endpoints use `@AuthenticationPrincipal GatewayMerchantUserDetails` and pass `userDetails.getUserId()` to service — properly scoped to authenticated user
- Follows existing PayoutController pattern with ApiResponse wrapper
- Swagger annotations for API documentation
- Endpoints:
  - GET `/transactions/summary` — dashboard cards
  - GET `/transactions` — paginated list with filters
  - GET `/transactions/{reference}` — single transaction detail

### 8. Error Handling ✅

- `getDetail()` throws `IllegalArgumentException` for not-found transactions
- GlobalExceptionHandler converts this to HTTP 400 with message — acceptable behavior for user-facing API
- Consistent with existing patterns in the codebase

## Findings

**No blockers identified.**

The implementation correctly:
1. Replicates transactions from tms-transaction via Kafka (not from Merchant-Backend as specified)
2. Filters only pg_merchant users by checking the pg_merchant_users table
3. Provides all dashboard data requirements (summary stats, paginated list, detail view)
4. Follows existing patterns in gateway-merchant (ComplianceEventConsumer, PayoutController)
5. Uses proper security scoping via @AuthenticationPrincipal

## Verdict

**APPROVED** — Implementation meets all requirements with no correctness, security, or data issues.
