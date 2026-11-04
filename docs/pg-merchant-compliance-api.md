# Payment Gateway Merchant Compliance API Documentation

**Version:** 2.0  
**Last Updated:** November 2026  
**Service:** gateway-merchant  

---

## Table of Contents

1. [Overview](#1-overview)
2. [Authentication](#2-authentication)
3. [Idempotency](#3-idempotency)
4. [Optimistic Concurrency Control](#4-optimistic-concurrency-control)
5. [Business Types](#5-business-types)
6. [API Endpoints Reference](#6-api-endpoints-reference)
7. [Starter Business Flow](#7-starter-business-flow)
8. [Registered Business Flow](#8-registered-business-flow)
9. [Document Upload](#9-document-upload)
10. [Error Handling](#10-error-handling)

---

## 1. Overview

The Payment Gateway Merchant Compliance API enables merchants to complete their KYC (Know Your Customer) compliance requirements for payment gateway access. The API supports two business types with different requirements:

- **Starter Business**: For unregistered businesses/sole proprietors (single owner)
- **Registered Business**: For CAC-registered companies (multiple shareholders)

### Architecture

```
┌─────────────────┐     REST/Multipart     ┌───────────────────┐      gRPC       ┌─────────────┐
│   Frontend      │ ◄───────────────────► │  gateway-merchant │ ◄─────────────► │   tms-kyc   │
│  (merchant-fe)  │                        │   (API Gateway)   │                 │  (Storage)  │
└─────────────────┘                        └───────────────────┘                 └─────────────┘
                                                    │
                                                    │ Internal Upload
                                                    ▼
                                               AWS S3
```

### Key Design: Multipart Form Upload

Documents are submitted **inline with form data** at each step using `multipart/form-data`. This reduces the total API calls to a maximum of **2 calls per step** (one save + optional submit), regardless of how many documents are attached.

### Base URL

```
Production: https://api.merchant.irpay.ng
Staging:    https://api.merchant.staging.irpay.ng
```

---

## 2. Authentication

All endpoints (except `/compliance/schema`) require Bearer token authentication.

### Required Headers

```http
Authorization: Bearer <access_token>
```

### Obtaining a Token

```http
POST /auth/login
Content-Type: application/json

{
  "email": "merchant@example.com",
  "password": "secure_password"
}
```

**Response:**
```json
{
  "success": true,
  "data": {
    "access_token": "eyJhbGciOiJIUzI1NiIs...",
    "refresh_token": "eyJhbGciOiJIUzI1NiIs...",
    "expires_in": 3600
  }
}
```

---

## 3. Idempotency

### What is Idempotency?

Idempotency ensures that if a request fails (network timeout, server error) and you retry it with the **same idempotency key**, the server will:
- Return the same response as the first successful attempt
- NOT create duplicate records or perform the action twice

### How to Use

All mutating endpoints (POST, PUT, PATCH, DELETE) require an `Idempotency-Key` header:

```http
Idempotency-Key: 550e8400-e29b-41d4-a716-446655440000
```

### Rules for Idempotency Keys

| Rule | Description |
|------|-------------|
| **Format** | UUID v4 or alphanumeric string (max 128 chars) |
| **Uniqueness** | Each distinct operation needs a unique key |
| **Scope** | Keys are scoped per merchant |
| **TTL** | Keys expire 24 hours after first use |
| **Retry** | Same key + same request = returns cached response |
| **Conflict** | Same key + different request = HTTP 409 error |

---

## 4. Optimistic Concurrency Control

### How It Works

1. **GET** compliance → response includes `version: 5`
2. **PUT** with header `X-Expected-Version: 5`
3. If current version is still 5 → update succeeds, version becomes 6
4. If current version changed → HTTP 409 Conflict (refresh and retry)

### Required Header

```http
X-Expected-Version: 5
```

---

## 5. Business Types

### Starter Business

For sole proprietors and unregistered small businesses.

**Steps:**
1. Profile Details
2. Contact Information  
3. Owner Details + Documents (ID + proof of address)
4. Bank Account
5. Service Agreement

**Documents Required:**
- Owner ID document (National ID, NIN, Passport, or Driver's License)
- Owner proof of address (Utility bill ≤ 3 months old)

### Registered Business

For CAC-registered companies (LLC, PLC, Business Name).

**Steps:**
1. Business Details + Business Address Proof
2. Registration Details (RC number, TIN)
3. Shareholders + Documents (1-10 people, each with ID + proof of address)
4. Bank Account
5. Service Agreement

**Documents Required:**
- Business proof of address
- Per shareholder: ID document + proof of address

---

## 6. API Endpoints Reference

### Public Endpoint

| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/compliance/schema?business_type=X` | Get form schema (no auth required) |

### Compliance Endpoints (JSON)

| Method | Endpoint | Description |
|--------|----------|-------------|
| POST | `/compliance/start` | Start new compliance |
| GET | `/compliance` | Get current compliance state |
| PUT | `/compliance/profile` | Save profile (no documents) |
| PUT | `/compliance/contact` | Save contact (no documents) |
| PUT | `/compliance/registration` | Save registration (registered only) |
| PUT | `/compliance/owners` | Save owners (no documents) |
| PUT | `/compliance/bank-accounts` | Save bank accounts |
| POST | `/compliance/submit` | Submit for review |

### Compliance Endpoints with File Upload (Multipart)

| Method | Endpoint | Content-Type | Description |
|--------|----------|--------------|-------------|
| PUT | `/compliance/profile/with-file` | `multipart/form-data` | Save profile + CAC certificate |
| PUT | `/compliance/contact/with-file` | `multipart/form-data` | Save contact + address proof |
| PUT | `/compliance/owners/with-files` | `multipart/form-data` | Save owners + documents |
| PUT | `/compliance/shareholders/with-files` | `multipart/form-data` | Save shareholders + documents |

---

## 7. Starter Business Flow

### Complete Step-by-Step

```
════════════════════════════════════════════════════════════════════════════════
                         STARTER BUSINESS FLOW
════════════════════════════════════════════════════════════════════════════════

STEP 1: GET SCHEMA
──────────────────────────────────────────────────────────────────────────────
GET /compliance/schema?business_type=starter

Response: Form schema with fields for each step


STEP 2: START COMPLIANCE
──────────────────────────────────────────────────────────────────────────────
POST /compliance/start
Headers:
  Authorization: Bearer <token>
  Idempotency-Key: <uuid-1>

Response:
{
  "success": true,
  "data": {
    "compliance_id": 100,
    "status": "draft",
    "version": 1
  }
}


STEP 3: SAVE PROFILE
──────────────────────────────────────────────────────────────────────────────
PUT /compliance/profile
Headers:
  Authorization: Bearer <token>
  Idempotency-Key: <uuid-2>
  X-Expected-Version: 1
  Content-Type: application/json

Body:
{
  "country": "NG",
  "industry": "retail",
  "category": "fashion",
  "trading_name": "ACME Store",
  "sales_volume": "100000-500000",
  "business_type": "starter",
  "business_description": "Fashion retail business"
}

Response: { "version": 2, ... }


STEP 4: SAVE CONTACT
──────────────────────────────────────────────────────────────────────────────
PUT /compliance/contact
Headers:
  Authorization: Bearer <token>
  Idempotency-Key: <uuid-3>
  X-Expected-Version: 2
  Content-Type: application/json

Body:
{
  "email": "contact@acme.com",
  "support_email": "support@acme.com",
  "dispute_email": "support@acme.com",
  "same_as_support_email": true,
  "phone": "+2348012345678",
  "address": "123 Victoria Island, Lagos",
  "state": "Lagos",
  "lga": "eti-osa",
  "online_links": [
    { "platform": "website", "url": "https://acme.com" }
  ]
}

Response: { "version": 3, ... }


STEP 5: SAVE OWNER WITH DOCUMENTS (Single Multipart Request)
──────────────────────────────────────────────────────────────────────────────
PUT /compliance/owners/with-files
Headers:
  Authorization: Bearer <token>
  Idempotency-Key: <uuid-4>
  X-Expected-Version: 3
  Content-Type: multipart/form-data

Form Parts:
┌─────────────────────────────────────────────────────────────────────────────┐
│ Part: "data" (application/json)                                             │
│ {                                                                           │
│   "owners": [{                                                              │
│     "full_name": "John Doe",                                                │
│     "date_of_birth": "15-03-1985",                                          │
│     "nationality": "Nigerian",                                              │
│     "id_document_type": "international_passport",                           │
│     "address": "456 Ikoyi, Lagos",                                          │
│     "same_as_business_address": false,                                      │
│     "state": "Lagos",                                                       │
│     "lga": "eti-osa"                                                        │
│   }]                                                                        │
│ }                                                                           │
├─────────────────────────────────────────────────────────────────────────────┤
│ Part: "owner_0_identity" (file)                                             │
│ → passport.pdf                                                              │
├─────────────────────────────────────────────────────────────────────────────┤
│ Part: "owner_0_address" (file)                                              │
│ → utility-bill.pdf                                                          │
└─────────────────────────────────────────────────────────────────────────────┘

File part naming convention:
  - owner_{index}_identity  → ID document for owner at index
  - owner_{index}_address   → Proof of address for owner at index

Response: { "version": 4, ... }


STEP 6: SAVE BANK ACCOUNT
──────────────────────────────────────────────────────────────────────────────
PUT /compliance/bank-accounts
Headers:
  Authorization: Bearer <token>
  Idempotency-Key: <uuid-5>
  X-Expected-Version: 4
  Content-Type: application/json

Body:
{
  "bank_accounts": [{
    "bank_code": "044",
    "account_number": "0123456789",
    "account_name": "John Doe"
  }]
}

Response: { "version": 5, ... }


STEP 7: SUBMIT FOR REVIEW
──────────────────────────────────────────────────────────────────────────────
POST /compliance/submit
Headers:
  Authorization: Bearer <token>
  Idempotency-Key: <uuid-6>
  X-Expected-Version: 5
  Content-Type: application/json

Body:
{
  "agreement_accepted": true
}

Response: { "version": 6, "status": "submitted" }


════════════════════════════════════════════════════════════════════════════════
                         STARTER FLOW COMPLETE!
                         Total API calls: 7 (including schema)
════════════════════════════════════════════════════════════════════════════════
```

---

## 8. Registered Business Flow

### Complete Step-by-Step

```
════════════════════════════════════════════════════════════════════════════════
                       REGISTERED BUSINESS FLOW
════════════════════════════════════════════════════════════════════════════════

STEP 1: GET SCHEMA
──────────────────────────────────────────────────────────────────────────────
GET /compliance/schema?business_type=registered


STEP 2: START COMPLIANCE
──────────────────────────────────────────────────────────────────────────────
POST /compliance/start
Headers:
  Idempotency-Key: <uuid>

Response: { "compliance_id": 200, "version": 1 }


STEP 3: SAVE BUSINESS DETAILS WITH ADDRESS PROOF (Single Multipart Request)
──────────────────────────────────────────────────────────────────────────────
PUT /compliance/contact/with-file
Headers:
  Authorization: Bearer <token>
  Idempotency-Key: <uuid>
  X-Expected-Version: 1
  Content-Type: multipart/form-data

Form Parts:
┌─────────────────────────────────────────────────────────────────────────────┐
│ Part: "data" (application/json)                                             │
│ {                                                                           │
│   "phone": "+2349012345678",                                                │
│   "address": "Plot 123 Tech Hub, Lekki",                                    │
│   "support_email": "support@techcorp.ng",                                   │
│   "dispute_email": "disputes@techcorp.ng",                                  │
│   "state": "Lagos",                                                         │
│   "lga": "eti-osa",                                                         │
│   "online_links": [                                                         │
│     { "platform": "website", "url": "https://techcorp.ng" }                 │
│   ]                                                                         │
│ }                                                                           │
├─────────────────────────────────────────────────────────────────────────────┤
│ Part: "address_proof" (file)                                                │
│ → office-lease.pdf                                                          │
└─────────────────────────────────────────────────────────────────────────────┘

Response: { "version": 2, ... }


STEP 4: SAVE REGISTRATION DETAILS
──────────────────────────────────────────────────────────────────────────────
PUT /compliance/registration
Headers:
  Idempotency-Key: <uuid>
  X-Expected-Version: 2
  Content-Type: application/json

Body:
{
  "registration_number": "RC1234567",
  "tax_identification_number": "12345678-0001"
}

Response: { "version": 3, ... }


STEP 5: SAVE SHAREHOLDERS WITH DOCUMENTS (Single Multipart Request)
──────────────────────────────────────────────────────────────────────────────
PUT /compliance/shareholders/with-files
Headers:
  Authorization: Bearer <token>
  Idempotency-Key: <uuid>
  X-Expected-Version: 3
  Content-Type: multipart/form-data

Form Parts:
┌─────────────────────────────────────────────────────────────────────────────┐
│ Part: "data" (application/json)                                             │
│ {                                                                           │
│   "shareholders": [                                                         │
│     {                                                                       │
│       "full_name": "Adaora Okafor",                                         │
│       "date_of_birth": "20-05-1980",                                        │
│       "nationality": "Nigerian",                                            │
│       "role": "director",                                                   │
│       "id_document_type": "international_passport",                         │
│       "address": "12 Executive Estate, Ikoyi",                              │
│       "same_as_business_address": false,                                    │
│       "state": "Lagos",                                                     │
│       "lga": "eti-osa"                                                      │
│     },                                                                      │
│     {                                                                       │
│       "full_name": "Chidi Eze",                                             │
│       "date_of_birth": "08-11-1975",                                        │
│       "nationality": "Nigerian",                                            │
│       "role": "shareholder",                                                │
│       "id_document_type": "national_id",                                    │
│       "address": "45 Business District, VI",                                │
│       "same_as_business_address": false,                                    │
│       "state": "Lagos",                                                     │
│       "lga": "eti-osa"                                                      │
│     }                                                                       │
│   ]                                                                         │
│ }                                                                           │
├─────────────────────────────────────────────────────────────────────────────┤
│ Part: "shareholder_0_identity" (file) → adaora-passport.pdf                 │
├─────────────────────────────────────────────────────────────────────────────┤
│ Part: "shareholder_0_address" (file) → adaora-utility.pdf                   │
├─────────────────────────────────────────────────────────────────────────────┤
│ Part: "shareholder_1_identity" (file) → chidi-national-id.pdf               │
├─────────────────────────────────────────────────────────────────────────────┤
│ Part: "shareholder_1_address" (file) → chidi-utility.pdf                    │
└─────────────────────────────────────────────────────────────────────────────┘

File part naming convention:
  - shareholder_{index}_identity  → ID document for shareholder at index
  - shareholder_{index}_address   → Proof of address for shareholder at index

Response: { "version": 4, ... }


STEP 6: SAVE BANK ACCOUNT
──────────────────────────────────────────────────────────────────────────────
PUT /compliance/bank-accounts
Headers:
  Idempotency-Key: <uuid>
  X-Expected-Version: 4
  Content-Type: application/json

Body:
{
  "bank_accounts": [{
    "bank_code": "058",
    "account_number": "1234567890",
    "account_name": "TECHCORP LIMITED"
  }]
}

Response: { "version": 5, ... }


STEP 7: SUBMIT FOR REVIEW
──────────────────────────────────────────────────────────────────────────────
POST /compliance/submit
Headers:
  Idempotency-Key: <uuid>
  X-Expected-Version: 5
  Content-Type: application/json

Body:
{
  "agreement_accepted": true
}

Response: { "version": 6, "status": "submitted" }


════════════════════════════════════════════════════════════════════════════════
                      REGISTERED FLOW COMPLETE!
                      Total API calls: 7 (including schema)
════════════════════════════════════════════════════════════════════════════════
```

---

## 9. Document Upload

### Multipart Upload Design

Documents are submitted **inline with form data** using `multipart/form-data`. This approach:

- **Reduces API calls**: One request per step, regardless of document count
- **Simplifies client code**: No separate presigned URL dance
- **Atomic operations**: Form data and files saved together or fail together

### File Part Naming Convention

| Endpoint | File Part Name | Description |
|----------|---------------|-------------|
| `/compliance/profile/with-file` | `cac_certificate` | CAC registration certificate |
| `/compliance/contact/with-file` | `address_proof` | Business address proof |
| `/compliance/owners/with-files` | `owner_{n}_identity` | Owner's ID document (0-indexed) |
| `/compliance/owners/with-files` | `owner_{n}_address` | Owner's proof of address (0-indexed) |
| `/compliance/shareholders/with-files` | `shareholder_{n}_identity` | Shareholder's ID document (0-indexed) |
| `/compliance/shareholders/with-files` | `shareholder_{n}_address` | Shareholder's proof of address (0-indexed) |

### Supported File Types

| Content Type | Extensions | Max Size |
|-------------|------------|----------|
| `application/pdf` | .pdf | 10 MB |
| `image/jpeg` | .jpg, .jpeg | 10 MB |
| `image/png` | .png | 10 MB |

### JavaScript Example

```javascript
async function saveOwnersWithDocuments(owners, ownerFiles, token, version) {
  const formData = new FormData();
  
  // Add JSON data as a part
  formData.append('data', new Blob([JSON.stringify({ owners })], {
    type: 'application/json'
  }));
  
  // Add files for each owner
  ownerFiles.forEach((files, index) => {
    if (files.identity) {
      formData.append(`owner_${index}_identity`, files.identity);
    }
    if (files.address) {
      formData.append(`owner_${index}_address`, files.address);
    }
  });
  
  const response = await fetch('/compliance/owners/with-files', {
    method: 'PUT',
    headers: {
      'Authorization': `Bearer ${token}`,
      'Idempotency-Key': crypto.randomUUID(),
      'X-Expected-Version': version
      // Note: Don't set Content-Type - browser sets it with boundary
    },
    body: formData
  });
  
  return response.json();
}
```

---

## 10. Error Handling

### HTTP Status Codes

| Code | Meaning | Action |
|------|---------|--------|
| 200 | Success | Process response |
| 400 | Bad Request | Fix request data |
| 401 | Unauthorized | Refresh token or re-login |
| 409 | Conflict | Version mismatch - refresh and retry |
| 413 | Payload Too Large | Reduce file size |
| 422 | Validation Error | Fix field errors |
| 500 | Server Error | Retry with exponential backoff |

### Error Response Format

```json
{
  "success": false,
  "message": "Validation failed",
  "error_code": "VALIDATION_FAILED",
  "errors": [
    { "step": "owners", "field": "full_name", "message": "Full name is required" }
  ]
}
```

### Common Error Codes

| Code | Description |
|------|-------------|
| `VALIDATION_FAILED` | Field validation errors |
| `VERSION_CONFLICT` | X-Expected-Version doesn't match |
| `IDEMPOTENCY_CONFLICT` | Same key, different request body |
| `FILE_TOO_LARGE` | File exceeds 10 MB limit |
| `UNSUPPORTED_FILE_TYPE` | File type not allowed |
| `ALREADY_SUBMITTED` | Cannot modify submitted compliance |

---

## Appendix: Field Constraints

| Field | Max Length | Notes |
|-------|-----------|-------|
| country | 3 | ISO code (e.g., "NG") |
| trading_name | 200 | |
| business_description | 500 | |
| email | 255 | Valid email |
| phone | 20 | Include country code |
| address | 500 | |
| state | 100 | Nigerian state (e.g., "Lagos") |
| lga | 100 | Local Government Area |
| full_name | 200 | |
| date_of_birth | 10 | Format: DD-MM-YYYY |
| registration_number | 50 | RC number |
| account_number | 10 | Exactly 10 digits |

---

## Migration from v1.x

If you were using the previous presigned URL approach:

| v1.x (Deprecated) | v2.0 (Current) |
|-------------------|----------------|
| `POST /compliance/documents/uploads` | Not needed |
| `PUT <presigned_url>` (to S3) | Not needed |
| `POST /compliance/documents/{id}/complete` | Not needed |
| `PUT /compliance/owners` with `id_document_id` | `PUT /compliance/owners/with-files` with file parts |

The new multipart approach is simpler and requires fewer API calls.

---

**End of Document**
