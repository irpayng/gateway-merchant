# Payment Gateway Merchant Compliance API Documentation

**Version:** 1.1  
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
9. [Document Upload Flow](#9-document-upload-flow)
10. [Error Handling](#10-error-handling)

---

## 1. Overview

The Payment Gateway Merchant Compliance API enables merchants to complete their KYC (Know Your Customer) compliance requirements for payment gateway access. The API supports two business types with different requirements:

- **Starter Business**: For unregistered businesses/sole proprietors (single owner)
- **Registered Business**: For CAC-registered companies (multiple shareholders)

### Architecture

```
┌─────────────────┐     REST/JSON      ┌───────────────────┐      gRPC       ┌─────────────┐
│   Frontend      │ ◄───────────────► │  gateway-merchant │ ◄─────────────► │   tms-kyc   │
│  (merchant-fe)  │                    │   (API Gateway)   │                 │  (Storage)  │
└─────────────────┘                    └───────────────────┘                 └─────────────┘
        │                                                                           │
        │              Direct Upload (Presigned URL)                                │
        └───────────────────────────────────────────────────────────────────────────┘
                                      AWS S3
```

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
Content-Type: application/json
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

This is critical for financial applications where duplicate operations could cause serious problems.

### How to Use

All mutating endpoints (POST, PUT, PATCH, DELETE) require an `Idempotency-Key` header:

```http
Idempotency-Key: 550e8400-e29b-41d4-a716-446655440000
```

### Rules for Idempotency Keys

| Rule | Description |
|------|-------------|
| **Format** | UUID v4 (e.g., `550e8400-e29b-41d4-a716-446655440000`) |
| **Uniqueness** | Each distinct operation needs a unique key |
| **Scope** | Keys are scoped per merchant |
| **TTL** | Keys expire 24 hours after first use |
| **Retry** | Same key + same request body = returns cached response |
| **Conflict** | Same key + different request body = HTTP 409 error |

### When to Generate vs. Reuse Keys

```
User clicks "Save" first time    →  Generate NEW key
Request fails (network error)    →  REUSE same key for retry
Request succeeds                 →  DISCARD key
User modifies data, clicks Save  →  Generate NEW key (different intent)
```

### JavaScript Example

```javascript
import { v4 as uuidv4 } from 'uuid';

// Store key until operation succeeds
let currentKey = null;

async function saveProfile(data) {
  // Generate key only on first attempt
  if (!currentKey) {
    currentKey = uuidv4();
  }
  
  try {
    const response = await fetch('/compliance/profile', {
      method: 'PUT',
      headers: {
        'Authorization': `Bearer ${token}`,
        'Idempotency-Key': currentKey,
        'X-Expected-Version': version,
        'Content-Type': 'application/json'
      },
      body: JSON.stringify(data)
    });
    
    if (response.ok) {
      currentKey = null;  // Success! Clear key for next operation
      return await response.json();
    }
    // On failure, key is retained for retry
  } catch (error) {
    // Network error - same key will be used on retry
    throw error;
  }
}
```

---

## 4. Optimistic Concurrency Control

### What is It?

Prevents conflicts when multiple browser tabs or devices edit the same compliance record simultaneously.

### How It Works

1. **GET** compliance → response includes `version: 5`
2. **PUT** with header `X-Expected-Version: 5`
3. If current version is still 5 → update succeeds, version becomes 6
4. If current version changed → HTTP 409 Conflict (refresh and retry)

### Required Header

```http
X-Expected-Version: 5
```

### Handling Conflicts

```javascript
const response = await saveProfile(data, currentVersion);

if (response.status === 409) {
  // Another session modified the record
  // Fetch latest data and show user the conflict
  const latest = await getCompliance();
  alert("This record was updated elsewhere. Please review and try again.");
}
```

---

## 5. Business Types

### Starter Business

For sole proprietors and unregistered small businesses.

**Steps:**
1. Profile Details
2. Contact Information  
3. Owner Details (single owner with ID + proof of address)
4. Bank Account
5. Service Agreement

**Documents Required:**
- Owner ID document (National ID, NIN, Passport, or Driver's License)
- Owner proof of address (Utility bill ≤ 3 months old)

### Registered Business

For CAC-registered companies (LLC, PLC, Business Name).

**Steps:**
1. Business Details
2. Registration Details (RC number, TIN)
3. Shareholders (1-10 people, each with ID + proof of address)
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

### Compliance Endpoints

| Method | Endpoint | Description |
|--------|----------|-------------|
| POST | `/compliance/start` | Start new compliance |
| GET | `/compliance` | Get current compliance state |
| PUT | `/compliance/profile` | Save profile/business details |
| PUT | `/compliance/contact` | Save contact information |
| PUT | `/compliance/registration` | Save registration (registered only) |
| PUT | `/compliance/owners` | Save owners/shareholders |
| PUT | `/compliance/bank-accounts` | Save bank accounts |
| POST | `/compliance/submit` | Submit for review |

### Document Endpoints

| Method | Endpoint | Description |
|--------|----------|-------------|
| POST | `/compliance/documents/uploads` | Get presigned upload URL |
| POST | `/compliance/documents/{id}/complete` | Confirm upload complete |
| GET | `/compliance/documents/{id}` | Get document info/download URL |
| DELETE | `/compliance/documents/{id}` | Delete document |

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
  "compliance_id": 100,
  "status": "draft",
  "version": 1
}


STEP 3: SAVE PROFILE
──────────────────────────────────────────────────────────────────────────────
PUT /compliance/profile
Headers:
  Authorization: Bearer <token>
  Idempotency-Key: <uuid-2>
  X-Expected-Version: 1

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

Response: { "version": 2, "profile_complete": true }


STEP 4: SAVE CONTACT
──────────────────────────────────────────────────────────────────────────────
PUT /compliance/contact
Headers:
  Authorization: Bearer <token>
  Idempotency-Key: <uuid-3>
  X-Expected-Version: 2

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

Response: { "version": 3, "contact_complete": true }


STEP 5: SAVE OWNER (without documents first)
──────────────────────────────────────────────────────────────────────────────
PUT /compliance/owners
Headers:
  Authorization: Bearer <token>
  Idempotency-Key: <uuid-4>
  X-Expected-Version: 3

Body:
{
  "owners": [{
    "full_name": "John Doe",
    "date_of_birth": "1985-03-15",
    "nationality": "Nigerian",
    "role": "owner",
    "id_document_type": "international_passport",
    "address": "456 Ikoyi, Lagos",
    "lga": "eti-osa",
    "same_as_business_address": false
  }]
}

Response:
{
  "version": 4,
  "owners_complete": false,    ← Documents still missing!
  "owners": [
    { "id": 42, "full_name": "John Doe", ... }
  ]
}

IMPORTANT: Note the owner "id": 42 - you'll need this for document linking


STEP 6: UPLOAD OWNER ID DOCUMENT
──────────────────────────────────────────────────────────────────────────────

6a. Request presigned URL
POST /compliance/documents/uploads
Headers:
  Authorization: Bearer <token>
  Idempotency-Key: <uuid-5>

Body:
{
  "purpose": "identity",           ← For starter owner ID
  "file_name": "passport.pdf",
  "content_type": "application/pdf",
  "size_bytes": 524288
}

NOTE: For starter business, "identity" and "owner_address" purposes 
      do NOT require owner_id because there's only one owner.

Response:
{
  "document_id": "doc_id_001",
  "upload_url": "https://s3.amazonaws.com/...",
  "method": "PUT",
  "headers": { "Content-Type": "application/pdf" }
}


6b. Upload file directly to S3
PUT https://s3.amazonaws.com/...
Content-Type: application/pdf
Body: <binary file data>

Response: 200 OK


6c. Confirm upload complete → AUTO-LINKS to owner!
POST /compliance/documents/doc_id_001/complete
Headers:
  Authorization: Bearer <token>
  Idempotency-Key: <uuid-6>

Response: { "document_id": "doc_id_001", "status": "uploaded" }

The backend AUTOMATICALLY sets:
  owner.id_document_id = "doc_id_001"


STEP 7: UPLOAD OWNER PROOF OF ADDRESS
──────────────────────────────────────────────────────────────────────────────
(Same 3-step pattern as Step 6)

7a. Request presigned URL
POST /compliance/documents/uploads
Body:
{
  "purpose": "owner_address",      ← For starter owner address proof
  "file_name": "utility-bill.pdf",
  "content_type": "application/pdf",
  "size_bytes": 234567
}

Response: { "document_id": "doc_addr_001", "upload_url": "...", ... }

7b. Upload to S3
PUT <upload_url> with file data

7c. Confirm complete → AUTO-LINKS to owner!
POST /compliance/documents/doc_addr_001/complete

The backend AUTOMATICALLY sets:
  owner.proof_of_address_document_id = "doc_addr_001"


STEP 8: VERIFY (Optional)
──────────────────────────────────────────────────────────────────────────────
GET /compliance

Response shows owners_complete: true and documents auto-linked:
{
  "owners": [
    { 
      "id": 42, 
      "full_name": "John Doe",
      "id_document_id": "doc_id_001",              ← Auto-linked!
      "proof_of_address_document_id": "doc_addr_001"  ← Auto-linked!
    }
  ],
  "owners_complete": true
}


STEP 9: SAVE BANK ACCOUNT
──────────────────────────────────────────────────────────────────────────────
PUT /compliance/bank-accounts
Headers:
  Authorization: Bearer <token>
  Idempotency-Key: <uuid-7>
  X-Expected-Version: 4

Body:
{
  "bank_accounts": [{
    "bank_code": "044",
    "account_number": "0123456789",
    "account_name": "John Doe"
  }]
}

Response: { "version": 5, "bank_accounts_complete": true }


STEP 10: SUBMIT FOR REVIEW
──────────────────────────────────────────────────────────────────────────────
POST /compliance/submit
Headers:
  Authorization: Bearer <token>
  Idempotency-Key: <uuid-8>
  X-Expected-Version: 5

Body:
{
  "agreement_accepted": true
}

Response: { "version": 7, "status": "submitted" }


════════════════════════════════════════════════════════════════════════════════
                         STARTER FLOW COMPLETE!
════════════════════════════════════════════════════════════════════════════════
```

### Starter Business Document Purposes

| Purpose | Description | owner_id Required? | Auto-Links? |
|---------|-------------|-------------------|-------------|
| `identity` | Owner's ID document | NO | YES |
| `owner_address` | Owner's proof of address | NO | YES |

For starter business, there's only ONE owner, so the system automatically links documents to that owner on upload completion. No second call to `/compliance/owners` is needed.

---

## 8. Registered Business Flow

### Key Difference: Multiple Shareholders with Auto-Linking

For registered businesses with multiple shareholders, documents use **auto-linking** via `owner_id`:

1. Save shareholders first → get their IDs
2. Upload documents with `owner_id` → backend auto-links them
3. No need to re-save shareholders with document IDs!

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


STEP 3: SAVE BUSINESS DETAILS (Profile)
──────────────────────────────────────────────────────────────────────────────
PUT /compliance/profile
Headers:
  Idempotency-Key: <uuid>
  X-Expected-Version: 1

Body:
{
  "country": "NG",
  "industry": "technology",
  "category": "fintech",
  "trading_name": "TechCorp Limited",
  "sales_volume": "1000000-5000000",
  "business_type": "registered",
  "business_description": "Financial technology solutions"
}

Response: { "version": 2, "profile_complete": true }


STEP 4: SAVE CONTACT WITH BUSINESS ADDRESS DOCUMENT
──────────────────────────────────────────────────────────────────────────────

4a. Upload business address document first
POST /compliance/documents/uploads
Body:
{
  "purpose": "business_address",
  "file_name": "office-lease.pdf",
  "content_type": "application/pdf",
  "size_bytes": 456789
}

Response: { "document_id": "doc_biz_addr", ... }

4b. Upload to S3, then complete
POST /compliance/documents/doc_biz_addr/complete

4c. Save contact with document ID
PUT /compliance/contact
Headers:
  Idempotency-Key: <uuid>
  X-Expected-Version: 2

Body:
{
  "email": "info@techcorp.ng",
  "support_email": "support@techcorp.ng",
  "dispute_email": "disputes@techcorp.ng",
  "phone": "+2349012345678",
  "address": "Plot 123 Tech Hub, Lekki",
  "state": "Lagos",
  "lga": "eti-osa",
  "business_address_document_id": "doc_biz_addr",
  "online_links": [
    { "platform": "website", "url": "https://techcorp.ng" }
  ]
}

Response: { "version": 3, "contact_complete": true }


STEP 5: SAVE REGISTRATION DETAILS
──────────────────────────────────────────────────────────────────────────────
PUT /compliance/registration
Headers:
  Idempotency-Key: <uuid>
  X-Expected-Version: 3

Body:
{
  "registration_number": "RC1234567",
  "tax_identification_number": "12345678-0001"
}

Response: { "version": 4, "registration_complete": true }


STEP 6: SAVE SHAREHOLDERS (without documents)
──────────────────────────────────────────────────────────────────────────────
PUT /compliance/owners
Headers:
  Idempotency-Key: <uuid>
  X-Expected-Version: 4

Body:
{
  "owners": [
    {
      "full_name": "Adaora Okafor",
      "date_of_birth": "1980-05-20",
      "nationality": "Nigerian",
      "role": "director",
      "id_document_type": "international_passport",
      "address": "12 Executive Estate, Ikoyi",
      "lga": "eti-osa",
      "same_as_business_address": false
    },
    {
      "full_name": "Chidi Eze",
      "date_of_birth": "1975-11-08",
      "nationality": "Nigerian",
      "role": "shareholder",
      "id_document_type": "national_id",
      "address": "45 Business District, VI",
      "lga": "eti-osa",
      "same_as_business_address": false
    }
  ]
}

Response:
{
  "version": 5,
  "owners_complete": false,
  "owners": [
    { "id": 50, "full_name": "Adaora Okafor", ... },
    { "id": 51, "full_name": "Chidi Eze", ... }
  ]
}

IMPORTANT: Extract owner IDs → 50 and 51


STEP 7: UPLOAD DOCUMENTS FOR SHAREHOLDER 1 (Adaora, ID: 50)
──────────────────────────────────────────────────────────────────────────────

7a. Upload ID document WITH owner_id
POST /compliance/documents/uploads
Headers:
  Idempotency-Key: <uuid>

Body:
{
  "purpose": "shareholder_identity",
  "owner_id": 50,                        ← REQUIRED for shareholders!
  "file_name": "adaora-passport.pdf",
  "content_type": "application/pdf",
  "size_bytes": 512000
}

Response: { "document_id": "doc_sh1_id", ... }

7b. Upload to S3
PUT <upload_url> with file

7c. Complete upload → AUTO-LINKS to owner 50!
POST /compliance/documents/doc_sh1_id/complete

The backend AUTOMATICALLY sets:
  owner(50).id_document_id = "doc_sh1_id"


7d. Upload proof of address WITH owner_id
POST /compliance/documents/uploads
Body:
{
  "purpose": "shareholder_address",
  "owner_id": 50,                        ← Same owner
  "file_name": "adaora-utility.pdf",
  "content_type": "application/pdf",
  "size_bytes": 234000
}

7e. Upload to S3, then complete
POST /compliance/documents/doc_sh1_addr/complete

The backend AUTOMATICALLY sets:
  owner(50).proof_of_address_document_id = "doc_sh1_addr"


STEP 8: UPLOAD DOCUMENTS FOR SHAREHOLDER 2 (Chidi, ID: 51)
──────────────────────────────────────────────────────────────────────────────
(Same pattern as Step 7, but with owner_id: 51)

POST /compliance/documents/uploads
{ "purpose": "shareholder_identity", "owner_id": 51, ... }
→ Complete → auto-links to owner 51

POST /compliance/documents/uploads
{ "purpose": "shareholder_address", "owner_id": 51, ... }
→ Complete → auto-links to owner 51


STEP 9: VERIFY (Optional)
──────────────────────────────────────────────────────────────────────────────
GET /compliance

Response shows owners_complete: true and all documents linked:
{
  "owners": [
    { "id": 50, "id_document_id": "doc_sh1_id", "proof_of_address_document_id": "doc_sh1_addr" },
    { "id": 51, "id_document_id": "doc_sh2_id", "proof_of_address_document_id": "doc_sh2_addr" }
  ]
}


STEP 10: SAVE BANK ACCOUNT
──────────────────────────────────────────────────────────────────────────────
PUT /compliance/bank-accounts
Headers:
  Idempotency-Key: <uuid>
  X-Expected-Version: 5

Body:
{
  "bank_accounts": [{
    "bank_code": "058",
    "account_number": "1234567890",
    "account_name": "TECHCORP LIMITED"
  }]
}

Response: { "version": 6, "bank_accounts_complete": true }


STEP 11: SUBMIT FOR REVIEW
──────────────────────────────────────────────────────────────────────────────
POST /compliance/submit
Headers:
  Idempotency-Key: <uuid>
  X-Expected-Version: 6

Body:
{
  "agreement_accepted": true
}

Response: { "version": 7, "status": "submitted" }


════════════════════════════════════════════════════════════════════════════════
                      REGISTERED FLOW COMPLETE!
════════════════════════════════════════════════════════════════════════════════
```

### Registered Business Document Purposes

| Purpose | Description | owner_id Required? |
|---------|-------------|-------------------|
| `business_address` | Business proof of address | NO |
| `shareholder_identity` | Shareholder's ID document | **YES** |
| `shareholder_address` | Shareholder's proof of address | **YES** |

---

## 9. Document Upload Flow

### Three-Step Process

Every document upload follows these 3 steps:

```
┌─────────────────────────────────────────────────────────────────────────────┐
│  STEP 1: Request Presigned URL                                              │
│  POST /compliance/documents/uploads                                         │
│  Body: { purpose, file_name, content_type, size_bytes, owner_id? }          │
│  Response: { document_id, upload_url, headers, method }                     │
└────────────────────────────────────┬────────────────────────────────────────┘
                                     │
                                     ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│  STEP 2: Upload File to S3 (Direct)                                         │
│  PUT {upload_url}                                                           │
│  Headers: from step 1 response                                              │
│  Body: <binary file data>                                                   │
│                                                                             │
│  NOTE: This goes DIRECTLY to S3, not through the API server!                │
└────────────────────────────────────┬────────────────────────────────────────┘
                                     │
                                     ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│  STEP 3: Confirm Completion                                                 │
│  POST /compliance/documents/{document_id}/complete                          │
│                                                                             │
│  For shareholder docs: This step AUTO-LINKS the document to the owner!      │
└─────────────────────────────────────────────────────────────────────────────┘
```

### Document Purpose Reference

| Purpose | Business Type | Description | owner_id |
|---------|--------------|-------------|----------|
| `identity` | Starter | Owner ID document | Not needed |
| `owner_address` | Starter | Owner proof of address | Not needed |
| `business_address` | Both | Business address proof | Not needed |
| `shareholder_identity` | Registered | Shareholder ID | **Required** |
| `shareholder_address` | Registered | Shareholder address | **Required** |

### Supported File Types

| Content Type | Extensions | Max Size |
|-------------|------------|----------|
| `application/pdf` | .pdf | 10 MB |
| `image/jpeg` | .jpg, .jpeg | 10 MB |
| `image/png` | .png | 10 MB |

---

## 10. Error Handling

### HTTP Status Codes

| Code | Meaning | Action |
|------|---------|--------|
| 200 | Success | Process response |
| 400 | Bad Request | Fix request data |
| 401 | Unauthorized | Refresh token or re-login |
| 409 | Conflict | Version mismatch - refresh and retry |
| 422 | Validation Error | Fix field errors |
| 500 | Server Error | Retry with exponential backoff |

### Error Response Format

```json
{
  "success": false,
  "message": "Validation failed",
  "error_code": "VALIDATION_FAILED",
  "errors": [
    { "step": "owners", "field": "id_document_id", "message": "ID document is required" }
  ]
}
```

### Common Error Codes

| Code | Description |
|------|-------------|
| `VALIDATION_FAILED` | Field validation errors |
| `VERSION_CONFLICT` | X-Expected-Version doesn't match |
| `IDEMPOTENCY_CONFLICT` | Same key, different request body |
| `DOCUMENT_NOT_FOUND` | Document ID doesn't exist |
| `OWNER_NOT_FOUND` | Owner ID doesn't exist |
| `ALREADY_SUBMITTED` | Cannot modify submitted compliance |

---

## Appendix: Field Constraints

| Field | Max Length | Notes |
|-------|-----------|-------|
| country | 3 | ISO code (e.g., "NG") |
| trading_name | 200 | |
| business_description | 1000 | |
| email | 255 | Valid email |
| phone | 20 | Include country code |
| address | 500 | |
| state | 100 | Nigerian state (e.g., "Lagos") |
| lga | 100 | Local Government Area |
| full_name | 200 | |
| registration_number | 50 | RC number |
| account_number | 10 | Exactly 10 digits |

---

**End of Document**
