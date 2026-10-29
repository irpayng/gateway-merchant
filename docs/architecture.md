# Gateway Merchant - Architecture Guide

> Compliance implementation update: see [Merchant compliance API](compliance-api.md)
> for the current Starter/Registered contract and required upstream rollout.

> A scalable backend for Payment Gateway merchants, designed to handle millions of users through event-driven data replication.

## Table of Contents
- [Quick Overview](#quick-overview)
- [System Architecture](#system-architecture)
- [Service Interactions](#service-interactions)
- [Data Flow Patterns](#data-flow-patterns)
- [Compliance Module](#compliance-module)
- [Authentication Flow](#authentication-flow)
- [Database Design](#database-design)
- [Kafka Events](#kafka-events)
- [API Reference](#api-reference)
- [Local Development](#local-development)

---

## Quick Overview

**What is gateway-merchant?**  
A Spring Boot API for PG (Payment Gateway) merchants to complete KYC compliance, manage their accounts, and access dashboard features.

**Why a separate service?**  
- Scale independently to millions of merchants
- Fast reads from local replicas (no network hops)
- Service isolation from core TMS microservices

**Key Principle: Kafka for reads, gRPC for writes**
```
┌─────────────────────────────────────────────────────────────┐
│                     READ PATH (fast)                        │
│  Client → gateway-merchant → Local PostgreSQL               │
│                                    ↑                        │
│                              Kafka Consumer                 │
└─────────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────────┐
│                    WRITE PATH (consistent)                  │
│  Client → gateway-merchant → gRPC → tms-kyc/tms-user        │
│                                         ↓                   │
│                                    Kafka Event              │
│                                         ↓                   │
│                              gateway-merchant consumer      │
└─────────────────────────────────────────────────────────────┘
```

---

## System Architecture

```
                                   ┌─────────────────────────────────┐
                                   │        Mobile/Web Client        │
                                   └───────────────┬─────────────────┘
                                                   │ REST API
                                                   ▼
┌──────────────────────────────────────────────────────────────────────────────┐
│                           GATEWAY-MERCHANT (Spring Boot)                      │
│                                                                              │
│  ┌────────────────┐  ┌────────────────┐  ┌────────────────┐                 │
│  │ AuthController │  │ ComplianceCtrl │  │ DashboardCtrl  │  ← REST Layer   │
│  └───────┬────────┘  └───────┬────────┘  └───────┬────────┘                 │
│          │                   │                   │                          │
│  ┌───────┴────────┐  ┌───────┴────────┐  ┌───────┴────────┐                 │
│  │  AuthService   │  │ComplianceServ  │  │ DashboardServ  │  ← Service Layer│
│  └───────┬────────┘  └───────┬────────┘  └───────┬────────┘                 │
│          │                   │                   │                          │
│          │           ┌───────┴────────┐          │                          │
│          │           │  Hybrid Read   │          │                          │
│          │           │ Local → gRPC   │          │                          │
│          │           └───────┬────────┘          │                          │
│          │                   │                   │                          │
│  ┌───────┴───────────────────┴───────────────────┴───────┐                  │
│  │              LOCAL DATABASE (PostgreSQL)               │                  │
│  │  ┌─────────────┐ ┌─────────────┐ ┌─────────────┐      │                  │
│  │  │pg_merchant_ │ │pg_merchant_ │ │pg_merchant_ │      │  ← Replica      │
│  │  │   users     │ │ compliance  │ │transactions │      │    Tables       │
│  │  └─────────────┘ └─────────────┘ └─────────────┘      │                  │
│  └────────────────────────────────────────────────────────┘                  │
│                              ▲                                               │
│  ┌───────────────────────────┴───────────────────────────┐                  │
│  │              KAFKA CONSUMERS                           │                  │
│  │  UserEventConsumer  │  ComplianceEventConsumer        │                  │
│  └───────────────────────────────────────────────────────┘                  │
│                                                                              │
│  ┌────────────────────────────────────────────────────────┐                 │
│  │              gRPC CLIENTS (writes only)                │                 │
│  │  UserGrpcClient     │    ComplianceGrpcClient         │                 │
│  └───────────────────────────────────────────────────────┘                  │
└──────────────────────────────────────────────────────────────────────────────┘
         │                              │                         │
         │ gRPC                         │ gRPC                    │ Kafka
         ▼                              ▼                         │
┌─────────────────┐           ┌─────────────────┐                 │
│    TMS-USER     │           │    TMS-KYC      │                 │
│   (Quarkus)     │           │   (Quarkus)     │                 │
│                 │           │                 │                 │
│ • Auth PG users │           │ • Store KYC     │                 │
│ • User CRUD     │           │ • Compliance    │                 │
│ • Password mgmt │           │ • Documents     │                 │
└────────┬────────┘           └────────┬────────┘                 │
         │                             │                          │
         │        Kafka Events         │                          │
         └─────────────────────────────┴──────────────────────────┘
                            outbox.event.*
```

---

## Service Interactions

### Services Involved

| Service | Tech Stack | Role | Communication |
|---------|------------|------|---------------|
| **gateway-merchant** | Spring Boot 3.4 | API Gateway for PG merchants | Receives REST, sends gRPC |
| **tms-user** | Quarkus | User management, authentication | gRPC server, Kafka producer |
| **tms-kyc** | Quarkus | KYC/Compliance storage | gRPC server, Kafka producer |

### gRPC Contracts

**tms-user** (`user_service.proto`)
```protobuf
service UserService {
  // PG Merchant Authentication
  rpc AuthenticatePgMerchant(AuthPgMerchantRequest) returns (AuthPgMerchantResponse);
  rpc ValidatePgMerchantToken(ValidateTokenRequest) returns (ValidateTokenResponse);
  rpc ChangePgMerchantPassword(ChangePasswordRequest) returns (ChangePasswordResponse);
}
```

**tms-kyc** (`pg_merchant_compliance_service.proto`)
```protobuf
service PgMerchantComplianceService {
  rpc StartCompliance(StartComplianceRequest) returns (ComplianceResponse);
  rpc GetCompliance(GetComplianceRequest) returns (ComplianceResponse);
  rpc SaveProfile(SaveProfileRequest) returns (ComplianceResponse);
  rpc SaveContact(SaveContactRequest) returns (ComplianceResponse);
  rpc SaveOwners(SaveOwnersRequest) returns (ComplianceResponse);
  rpc SaveBankAccounts(SaveBankAccountsRequest) returns (ComplianceResponse);
  rpc SaveAgreement(SaveAgreementRequest) returns (ComplianceResponse);
  rpc SubmitCompliance(SubmitComplianceRequest) returns (ComplianceResponse);
}
```

---

## Data Flow Patterns

### Pattern 1: Read from Local Replica (Primary)

```
┌──────────┐    GET /compliance     ┌──────────────────┐
│  Client  │ ───────────────────→   │ ComplianceCtrl   │
└──────────┘                        └────────┬─────────┘
                                             │
                                             ▼
                                    ┌──────────────────┐
                                    │ComplianceService │
                                    │                  │
                                    │ 1. Check local   │
                                    │    replica DB    │
                                    └────────┬─────────┘
                                             │ found?
                              ┌──────────────┴──────────────┐
                              │ YES                         │ NO
                              ▼                             ▼
                     ┌─────────────┐               ┌─────────────┐
                     │Return local │               │ Call gRPC   │
                     │   replica   │               │  fallback   │
                     └─────────────┘               └─────────────┘
```

**Why this pattern?**
- 99% of reads hit the local database (sub-millisecond)
- gRPC fallback handles edge cases (new data not yet replicated)
- Service stays available even if tms-kyc is down

### Pattern 2: Write via gRPC → Replicate via Kafka

```
┌──────────┐  PUT /compliance/profile  ┌──────────────────┐
│  Client  │ ───────────────────────→  │ ComplianceCtrl   │
└──────────┘                           └────────┬─────────┘
                                                │
                                                ▼
                                       ┌──────────────────┐
                                       │ComplianceService │
                                       └────────┬─────────┘
                                                │ gRPC call
                                                ▼
┌───────────────────────────────────────────────────────────────────┐
│                           TMS-KYC                                 │
│  ┌─────────────────┐     ┌─────────────────┐                     │
│  │ gRPC Handler    │ ──→ │ ComplianceServ  │                     │
│  └─────────────────┘     └────────┬────────┘                     │
│                                   │                               │
│                          ┌────────┴────────┐                     │
│                          │  Save to DB     │                     │
│                          │  (pg_merchant_  │                     │
│                          │   compliance)   │                     │
│                          └────────┬────────┘                     │
│                                   │                               │
│                          ┌────────┴────────┐                     │
│                          │ Publish Kafka   │                     │
│                          │ Event           │                     │
│                          └────────┬────────┘                     │
└───────────────────────────────────┼───────────────────────────────┘
                                    │
                                    │  outbox.event.kyc
                                    │  type: pg_merchant.compliance.updated
                                    ▼
┌───────────────────────────────────────────────────────────────────┐
│                       GATEWAY-MERCHANT                            │
│  ┌─────────────────────────┐                                     │
│  │ComplianceEventConsumer  │                                     │
│  │                         │                                     │
│  │ • Parse event           │                                     │
│  │ • Upsert to replica DB  │                                     │
│  └─────────────────────────┘                                     │
└───────────────────────────────────────────────────────────────────┘
```

---

## Compliance Module

### Overview

PG merchants must complete a 5-step compliance process before accessing the dashboard.

### Steps

| Step | Endpoint | Data Collected |
|------|----------|----------------|
| 1 | `PUT /compliance/profile` | Business type, industry, country, trading name |
| 2 | `PUT /compliance/contact` | Email, phone, address, city, state, country |
| 3 | `PUT /compliance/owners` | Directors/shareholders (multiple allowed) |
| 4 | `PUT /compliance/bank-accounts` | Settlement accounts (multiple allowed) |
| 5 | `PUT /compliance/agreement` | Terms acceptance with IP/user-agent |

### Status Lifecycle

```
                    ┌─────────┐
                    │  none   │  (user registered, no compliance started)
                    └────┬────┘
                         │ POST /compliance/start
                         ▼
                    ┌─────────┐
             ┌─────→│  draft  │←────┐
             │      └────┬────┘     │
             │           │          │
    save any step        │          │ rejected + user edits
             │           │          │
             │           ▼          │
             │   POST /compliance/submit
             │           │          │
             │           ▼          │
             │    ┌───────────┐     │
             │    │ submitted │     │
             │    └─────┬─────┘     │
             │          │           │
             │    admin reviews     │
             │          │           │
             │    ┌─────┴─────┐     │
             │    ▼           ▼     │
          ┌──────────┐  ┌──────────┐
          │ approved │  │ rejected │
          └──────────┘  └────┬─────┘
                             │
                             └──────────┘
```

### Draft Mode

Users can save progress incrementally:

```java
// ComplianceService.java
public ComplianceResponse saveProfile(Long userId, SaveProfileRequest request) {
    // 1. Call gRPC to save in tms-kyc
    var response = complianceGrpcClient.saveProfile(userId, request);
    
    // 2. tms-kyc updates: current_step = max(current_step, 1)
    // 3. tms-kyc publishes Kafka event
    // 4. Our consumer updates local replica
    
    return response;
}
```

### Database Tables

**In tms-kyc (source of truth):**
```sql
-- Main compliance record
pg_merchant_compliance (
  id, user_id, merchant_id, status, current_step,
  submitted_at, reviewed_at, reviewed_by, created_at, updated_at
)

-- Step data (foreign key to compliance)
pg_merchant_profiles    (compliance_id, country, industry, ...)
pg_merchant_contacts    (compliance_id, email, phone, ...)
pg_merchant_owners      (compliance_id, name, ownership_pct, ...)
pg_merchant_bank_accounts (compliance_id, bank_name, account_no, ...)
pg_merchant_agreements  (compliance_id, accepted_at, ip_address, ...)
```

**In gateway-merchant (replicas):**
Same structure with additional sync columns:
```sql
pg_merchant_compliance_replica (
  ...,
  source_id,      -- ID from tms-kyc
  synced_at       -- Last Kafka event timestamp
)
```

---

## Authentication Flow

### Login Sequence

```
┌──────────┐                    ┌──────────────────┐                    ┌──────────┐
│  Client  │                    │ gateway-merchant │                    │ tms-user │
└────┬─────┘                    └────────┬─────────┘                    └────┬─────┘
     │                                   │                                   │
     │  POST /auth/login                 │                                   │
     │  {email, password}                │                                   │
     │──────────────────────────────────→│                                   │
     │                                   │                                   │
     │                                   │  gRPC: AuthenticatePgMerchant     │
     │                                   │  {email, password}                │
     │                                   │──────────────────────────────────→│
     │                                   │                                   │
     │                                   │                                   │ Verify credentials
     │                                   │                                   │ Check user_type = 'pg_merchant'
     │                                   │                                   │
     │                                   │  {user_id, merchant_id, email}    │
     │                                   │←──────────────────────────────────│
     │                                   │                                   │
     │                                   │ Generate JWT                      │
     │                                   │                                   │
     │  {token, user: {...}}             │                                   │
     │←──────────────────────────────────│                                   │
     │                                   │                                   │
```

### JWT Structure

```json
{
  "sub": "user_123",
  "merchant_id": "MERCH_456",
  "email": "merchant@example.com",
  "user_type": "pg_merchant",
  "iat": 1698307200,
  "exp": 1698393600
}
```

### Key Design Decision

**No local `merchant_users` table** (unlike Merchant-Backend)

Why?
- PG merchants register in tms-user (single source of truth)
- Same credentials for registration and login
- User profile data replicated via Kafka for display only
- Password changes go through tms-user gRPC

---

## Kafka Events

### Topics Consumed

| Topic | Event Types | Action |
|-------|-------------|--------|
| `outbox.event.user` | `user.created`, `user.updated` | Upsert `pg_merchant_users_replica` |
| `outbox.event.kyc` | `pg_merchant.compliance.*` | Upsert compliance replica tables |

### Event Schema

```json
{
  "id": "evt_abc123",
  "type": "pg_merchant.compliance.updated",
  "timestamp": "2024-10-23T12:00:00Z",
  "data": {
    "compliance_id": 456,
    "user_id": 123,
    "merchant_id": "MERCH_789",
    "status": "draft",
    "current_step": 2,
    "profile": { ... },
    "contact": { ... }
  }
}
```

### Consumer Logic

```java
@KafkaListener(topics = "outbox.event.kyc")
public void handleComplianceEvent(String message) {
    var event = objectMapper.readValue(message, ComplianceEvent.class);
    
    if (!event.getType().startsWith("pg_merchant.compliance.")) {
        return; // Ignore non-PG merchant events
    }
    
    // Upsert main compliance record
    complianceReplicaRepository.upsert(event.getData());
    
    // Upsert related entities if present
    if (event.getData().getProfile() != null) {
        profileReplicaRepository.upsert(event.getData().getProfile());
    }
    // ... etc
}
```

---

## API Reference

### Authentication

| Method | Endpoint | Description |
|--------|----------|-------------|
| POST | `/auth/login` | Login with email/password |
| GET | `/auth/me` | Get current user + compliance status |
| POST | `/auth/change-password` | Change password |

### Compliance

| Method | Endpoint | Description |
|--------|----------|-------------|
| POST | `/compliance/start` | Initialize draft compliance |
| GET | `/compliance` | Get current compliance status |
| PUT | `/compliance/profile` | Save step 1 (business details) |
| PUT | `/compliance/contact` | Save step 2 (contact info) |
| PUT | `/compliance/owners` | Save step 3 (directors/shareholders) |
| PUT | `/compliance/bank-accounts` | Save step 4 (settlement accounts) |
| PUT | `/compliance/agreement` | Save step 5 (terms acceptance) |
| POST | `/compliance/submit` | Submit for admin review |

### Response Format

All endpoints return:
```json
{
  "status": true,
  "message": "Profile saved successfully",
  "data": { ... }
}
```

Error responses:
```json
{
  "status": false,
  "message": "Validation failed",
  "errors": {
    "email": "Invalid email format"
  }
}
```

---

## Local Development

### Prerequisites
- Java 21
- PostgreSQL 15+
- Kafka (or use docker-compose)
- tms-user and tms-kyc running

### Environment Variables

```properties
# Database
spring.datasource.url=jdbc:postgresql://localhost:5432/gateway_merchant
spring.datasource.username=postgres
spring.datasource.password=postgres

# Kafka
spring.kafka.bootstrap-servers=localhost:9092
spring.kafka.consumer.group-id=gateway-merchant

# gRPC
grpc.client.tms-user.address=static://localhost:9000
grpc.client.tms-kyc.address=static://localhost:9001

# JWT
jwt.secret=your-256-bit-secret
jwt.expiration=86400000
```

### Running Locally

```bash
# Start dependencies
docker-compose up -d postgres kafka

# Run migrations
mvn flyway:migrate

# Start the application
mvn spring-boot:run
```

### Testing

```bash
# Run all tests
mvn test

# Run with coverage
mvn test jacoco:report
```

---

## Troubleshooting

### Common Issues

**1. Compliance data not appearing after save**
- Check Kafka consumer logs for errors
- Verify topic `outbox.event.kyc` has messages
- Check if tms-kyc event producer is working

**2. gRPC connection refused**
- Verify tms-user/tms-kyc are running
- Check gRPC port configuration
- Test with `grpcurl`

**3. JWT validation fails**
- Ensure same `jwt.secret` across services
- Check token expiration
- Verify user_type is `pg_merchant`

---

## Related Documentation

- [tms-user gRPC API](../../../tms-user/docs/grpc-api.md)
- [tms-kyc Compliance Module](../../../tms-kyc/docs/pg-merchant-compliance.md)
- [Kafka Event Schemas](../../../docs/kafka-events.md)
