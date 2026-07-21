[readme.md](https://github.com/user-attachments/files/30238444/readme.md)
# ⚙️ Phsar Digital — Multi-Vendor E-Commerce Platform (Backend Services)

Welcome to the official backend repository for **Phsar Digital** — a robust, scalable, multi-tenant Spring Boot REST API service powering a local Cambodian e-commerce marketplace. This repository houses the core business logic, database migrations, security layer, object storage integrations, and third-party payment gateway handlers[cite: 1, 2].

---

## 📌 Project Overview

* **Project Title:** Phsar Digital — Multi-Vendor E-Commerce Platform
* **Client / Owner:** Phsar Digital (Attn: Reksmey)
* **Development Agency:** G4 Agency
* **Target Delivery:** 16 Weeks (Agile / Sprint-Based)

---

## 🚀 Midterm Achievements & Progress (Week 10)

The backend service is ahead of core milestone metrics, successfully building **66 REST API endpoints** (~68% of the total estimated endpoint architecture across all platform phases).

### 📊 Comprehensive Module Status Summary

| Module | Built Endpoints | Remaining Endpoints | Status / Description |
| :--- | :---: | :---: | :--- |
| **Auth** | `1` | `0` | Keycloak JWT integration & core session validation |
| **Files** | `4` | `0` | Complete file attachment handler (MinIO Object Storage) |
| **Categories** | `8` | `0` | Complete category hierarchy CRUD & taxonomy management[cite: 2] |
| **Listings** | `8` | `0` | Complete product listing CRUD & stock management[cite: 1, 2] |
| **Listing Attributes** | `3` | `0` | Dynamic product specifications & variants[cite: 2] |
| **Cart** | `6` | `0` | Full buyer shopping cart lifecycle endpoints |
| **Purchases** | `7` | `1` | Order processing & receipt data generation[cite: 1, 2] |
| **Favorites** | `3` | `0` | Buyer wishlist / saved listings module |
| **Messaging** | `5` | `1` | Real-time buyer-seller communication channels[cite: 1] |
| **Reviews** | `8` | `0` | Complete buyer reviews & feedback moderation engine[cite: 1] |
| **Seller Profile** | `4` | `1` | Storefront metadata, branding, and policy settings[cite: 1] |
| **Seller Applications** | `3` | `0` | Merchant onboarding submission with document attachments[cite: 1] |
| **Admin Seller Applications** | `4` | `0` | Complete admin workflow for business verification[cite: 1] |
| **User Profiles** | `2` | `1` | User account information and avatar handling[cite: 1] |
| **Notifications** | `0` | `4` | Planned for upcoming sprint cycle[cite: 1] |
| **Subscriptions** | `0` | `11` | Planned for upcoming sprint cycle (billing & trial logic)[cite: 1] |
| **Admin (Dashboard/Moderation/Reports)** | `0` | `13` | Planned for upcoming sprint cycle[cite: 1] |
| **TOTAL** | **66** | **~32** | **Core Data Engine & Business Logic Fully Operational** |

---

## 🔑 Authentication & Security Model

The backend leverages **Keycloak Identity & Access Management (IAM)** to issue and validate secure **JSON Web Tokens (JWT)**[cite: 2].

* **Base API URL:** `/api/v1`
* **Header Format:** `Authorization: Bearer <JWT_TOKEN>`
* **Role Parsing:** Roles are mapped dynamically from `realm_access.roles` inside the JWT claims.

### 🛡️ Role Hierarchy & Permissions
* **`USER`** — Buyer role (Applies to all registered shoppers/buyers on the platform. *Note: There is no explicit `BUYER` string role; `USER` represents the buyer role.*)
* **`SELLER`** — Verified merchants managing storefront listings, stock, and orders[cite: 1].
* **`ADMIN`** — Platform administrators governing applications, moderation, and system settings[cite: 1].

---

## 📖 Interactive API Documentation

Interactive API specifications and endpoint schemas are generated directly from the codebase and can be explored live using **Swagger UI** or **Scalar API Reference**:

* **Swagger UI:** `http://localhost:8080/swagger-ui.html`
* **Scalar API Docs:** `http://localhost:8080/scalar`
* **OpenAPI Specs:** `http://localhost:8080/v3/api-docs`

---

## 🛠️ Technology Stack & Dependencies

* **Framework:** Java 25 / Spring Boot 4.1.0
* **Security:** Spring Security + Keycloak IAM (OAuth2 Resource Server)[cite: 2]
* **Database:** PostgreSQL (Relational Multi-Tenant Schema)[cite: 1, 2]
* **ORM / Persistence:** Spring Data JPA / Hibernate
* **Object Storage:** MinIO S3-Compatible API Engine[cite: 2]
* **Third-Party Integrations:** Bakong Payment Gateway API[cite: 2]
* **Documentation Tooling:** OpenAPI 3 / Swagger / Scalar UI
* **Search engine  Tooling:** Meilisearch
