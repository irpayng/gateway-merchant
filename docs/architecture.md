# Gateway Merchant Dashboard - Architecture Documentation

## Overview

The Gateway Merchant Dashboard is a Spring Boot backend service for Payment Gateway merchants (`pg_merchant` user type). It provides a self-service dashboard where merchants can complete compliance verification, view transactions, manage customers, and handle payouts.

## Problem Statement

Build a scalable dashboard backend that can handle millions of users by:
- Replicating data locally via Kafka (no gRPC calls for reads)
- Using gRPC only for write operations to source-of-truth microservices
- Supporting a 5-step compliance wizard with draft mode and admin review

## Architecture Diagram

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                           Gateway Merchant Dashboard                         │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                             │
│   ┌─────────────┐    ┌─────────────┐    ┌─────────────┐                    │
│   │   Auth      │    │ Compliance  │    │  Dashboard  │                    │
│   │  Module     │    │   Module    │    │   (future)  │                    │
│   └──────┬──────┘    └──────┬──────┘    └─────────────┘                    │
│          │                  │                                               │
│   ┌──────┴──────────────────┴──────────────────────────────────────┐       │
│   │                    Local Database (Replicas)                    │       │
│   │  ┌──────────┐  ┌──────────┐  ┌──────────┐  ┌──────────┐       │       │
│   │  │  users   │  │compliance│  │  txns    │  │ sessions │       │       │
│   │  │(replica) │  │(replica) │  │(replica) │  │ (local)  │       │       │
│   │  └──────────┘  └──────────┘  └──────────┘  └──────────┘       │       │
│   └─────────────────────────────────────────────────────────────────┘       │
│                                                                             │
└─────────────────────────────────────────────────────────────────────────────┘
         │ Kafka (reads/replication)              │ gRPC (writes only)
         ▼                                        ▼
┌─────────────────┐  ┌─────────────────┐  ┌─────────────────┐
│    tms-user     │  │    tms-kyc      │  │ tms-transaction │
│  (pg_merchant   │  │  (compliance    │  │  (transactions) │
│   registration) │  │   source)       │  │                 │
└─────────────────┘  └─────────────────┘  └─────────────────┘
```

## Data Flow

### Writes (gRPC)
```
Frontend → gateway-merchant → gRPC → tms-kyc → DB → Outbox → Kafka
                                                              ↓
```

### Reads (Hybrid: Local Replica + gRPC Fallback)
```
Frontend → gateway-merchant → Local DB (if exists) ← Kafka Consumer ┘
                            → gRPC (fallback)
```

## Key Design Decisions

### 1. Separate Database
Gateway-merchant has its own PostgreSQL database, completely isolated from other services. This enables:
- Independent scaling to handle millions of users
- No cross-service query impact
- Fast reads from local replicas

### 2. Event-Driven Replication
All read data is replicated locally via Kafka:
- User/profile data from tms-user
- Compliance data from tms-kyc  
- Transaction data from tms-transaction (future)

Benefits:
- No gRPC latency on reads
- Service remains available even if upstream services are down
- Consistent read performance at scale

### 3. gRPC Only for Writes
Mutations go through gRPC to source-of-truth microservices:
- Authentication → tms-user
- Compliance CRUD → tms-kyc
- User updates → tms-user

This ensures data consistency and leverages existing business logic.

### 4. Same Credentials for Registration and Login
Unlike Merchant-Backend (which has a separate `merchant_users` table), gateway-merchant:
- Uses tms-user as the authentication authority
- Authenticates via `AuthPgMerchant` gRPC call
- Has no local user credentials storage
- Replicates user data for profile display only

### 5. Compliance in tms-kyc
Compliance data is stored in tms-kyc (source of truth) with:
- Normalized tables for each compliance step
- Draft mode support (save partial progress)
- Admin review workflow
- Kafka events for replication

## Compliance Flow

### Steps
1. **Profile Details** - Country, industry, category, trading name, sales volume, business type
2. **Contact Information** - Email, phone, address, city, state, postal code
3. **Owner Details** - Director/shareholder information (supports multiple)
4. **Bank Account** - Settlement account details (supports multiple)
5. **Service Agreement** - Terms acceptance with IP and user-agent capture

### Status Lifecycle
```
none → draft → submitted → under_review → approved
                                       → rejected
```

### Draft Mode
- User can save progress at any step
- `current_step` tracks highest completed step
- Submit validates all steps are complete
- Cannot modify after submission

## Database Schema

### gateway-merchant (Replicas)
- `pg_merchant_users` - User profile replica (V1 migration)
- `pg_merchant_compliance` - Compliance workflow replica (V2 migration)
- `pg_merchant_profiles` - Step 1 replica
- `pg_merchant_contacts` - Step 2 replica
- `pg_merchant_owners` - Step 3 replica
- `pg_merchant_bank_accounts` - Step 4 replica
- `pg_merchant_agreements` - Step 5 replica

### tms-kyc (Source of Truth)
Same tables without `synced_at` and `source_id` columns.

## API Endpoints

### Authentication
- `POST /auth/login` - Login with email/password (same as registration)
- `GET /auth/me` - Current user info + compliance status

### Compliance
- `POST /compliance/start` - Start draft compliance
- `GET /compliance` - Get current compliance (from replica)
- `PUT /compliance/profile` - Save step 1
- `PUT /compliance/contact` - Save step 2
- `PUT /compliance/owners` - Save step 3
- `PUT /compliance/bank-accounts` - Save step 4
- `PUT /compliance/agreement` - Save step 5
- `POST /compliance/submit` - Submit for review

## Security

### JWT Authentication
- Issued by gateway-merchant after successful tms-user authentication
- Claims: user_id, merchant_id, email
- Validated on every request

### Compliance Gate
- Non-auth/compliance endpoints require `compliance_status = 'approved'`
- Returns 403 with message if compliance not complete

## Kafka Topics

### Consumed
- `outbox.event.user` - User events (filter for pg_merchant type)
- `outbox.event.kyc` - Compliance events (filter for pg_merchant.compliance.*)

### Event Types
- `pg_merchant.compliance.created`
- `pg_merchant.compliance.updated`
- `pg_merchant.compliance.submitted`
- `pg_merchant.compliance.approved`
- `pg_merchant.compliance.rejected`
- `pg_merchant.compliance.snapshot` (full sync)

## Technology Stack

- **Framework**: Spring Boot 4.0.5
- **Language**: Java 21
- **Database**: PostgreSQL (separate instance)
- **Messaging**: Kafka
- **RPC**: gRPC
- **Migrations**: Flyway
- **Security**: Spring Security + JWT

## Implementation Status

### Phase 1: Auth + Foundation ✅
- [x] Project setup (pom.xml, application.properties)
- [x] Core infrastructure (ApiResponse, exceptions, JWT)
- [x] Security configuration
- [x] User replica table + Kafka consumer
- [x] AuthPgMerchant gRPC in tms-user
- [x] Auth module (login, /auth/me)

### Phase 2: Compliance Module ✅
- [x] Compliance entities in tms-kyc (6 tables)
- [x] PgMerchantComplianceService in tms-kyc
- [x] gRPC service for compliance operations
- [x] Kafka event producer in tms-kyc
- [x] Compliance replica tables in gateway-merchant (V2 migration)
- [x] Compliance replica entities + repositories
- [x] Kafka consumer for compliance events
- [x] ComplianceService with hybrid reads
- [x] ComplianceController with all endpoints

### Phase 3: Future
- [ ] Compliance gate middleware
- [ ] Admin review endpoints
- [ ] Transaction module
- [ ] Dashboard features
