# Phsar Digital — Multi-Vendor E-Commerce API

A production-oriented REST API for a multi-vendor marketplace built for local Cambodian
businesses. Sellers apply for verification, list products, and fulfil orders; buyers browse,
add to cart, order, chat with sellers, and leave reviews.

Built with **Spring Boot 4 · Java 25 · PostgreSQL · Keycloak · MinIO · WebSocket (STOMP)**

**66 REST endpoints across 14 modules**, plus a real-time messaging channel.

---

## Table of Contents

- [What is this project?](#what-is-this-project)
- [Key design decisions](#key-design-decisions)
- [Features](#features)
- [Tech stack](#tech-stack)
- [Architecture](#architecture)
- [Getting started](#getting-started)
- [Core flows](#core-flows)
- [API reference](#api-reference)
- [Real-time messaging](#real-time-messaging)
- [Project status](#project-status)

---

## What is this project?

Phsar Digital is a **multi-vendor marketplace** — many independent sellers, one platform.
It is not a single-store shop: every seller runs their own storefront, manages their own
inventory, and fulfils their own orders. The platform provides discovery, communication,
and governance.

The API models a marketplace that operates the way local commerce actually works in
Cambodia: **buyers pay cash on delivery**, and the details of that delivery are arranged
directly between buyer and seller over in-app chat. There is no payment gateway in the
transaction path.

---

## Key design decisions

These shaped the whole system, and understanding them explains most of the API surface.

### Keycloak owns identity; this database owns domain data

No passwords, no role columns, no local credential storage. `users.id` **is** the Keycloak
`sub` claim, so every foreign key in the schema points directly at the token subject with no
translation layer. Roles are read from `realm_access.roles` on each request, meaning
authorization is always current and can never drift from a stale database copy.

### One cart per shop, enforced by the database

A buyer shopping from three sellers has three carts. This is guaranteed by a unique
constraint on `(buyer_id, seller_id)` — items from different vendors *cannot* mix, because
`addItem` resolves the shop from the listing and routes to that shop's cart. Checkout is
therefore per-shop, and every order belongs to exactly one seller. No order-splitting logic
is needed, because the split happens naturally in the cart.

### Prices are snapshotted at checkout

`order_items.unit_price` is frozen when the order is created. If a seller raises the price
tomorrow, existing orders are unaffected — the buyer owes what they saw. The cart shows the
*live* price; the order shows the *agreed* price.

### Stock decrements on seller confirmation, not at checkout

Because there is no online payment, a `PENDING` order is a **request**, not a commitment.
Decrementing stock at checkout would let abandoned orders lock up real inventory. Stock is
reserved when the seller accepts, and restored if a confirmed order is later cancelled.

*Accepted trade-off:* two buyers can hold `PENDING` orders for the last item. Whoever the
seller confirms first gets it; the second confirmation fails with an explicit stock error.
For a marketplace where sellers confirm manually and coordinate over chat, this is more
honest than a silent race at checkout.

### Sellers are verified, not self-declared

Anyone registers as a normal `USER`. Becoming a seller requires submitting an application
with business details and verification documents, which an admin reviews. Only on approval
does the API grant the `SELLER` role in Keycloak and create the `SellerProfile`. A separate
application table (rather than a flag on the profile) preserves rejection history and allows
re-application.

---

## Features

### Buyer
- Browse listings with category filtering, pagination, and a nested category tree
- Per-vendor cart with quantity management and live stock validation
- Checkout per shop → order with frozen prices
- Order history and detail, cancellation
- Favorites / wishlist
- Real-time chat with sellers
- Review listings and read seller replies
- Profile management (name synced to Keycloak, avatar, phone, DOB)

### Seller
- Apply for verification with document upload; track application status
- Full listing CRUD: images, thumbnails, custom attributes, stock, DRAFT → ACTIVE lifecycle
- Incoming order queue with confirm / complete / cancel fulfilment actions
- Automatic stock management, including `SOLD_OUT` transitions and restock on cancel
- Public shop profile with storefront listing feed
- Reply to reviews
- Real-time chat with buyers

### Admin
- Review seller applications with attached documents
- Approve (grants the Keycloak role and provisions the shop) or reject with a reason
- Category management: create, update, reorder, nest, soft-delete

### Platform
- Two-step file pipeline (upload → reference by object name) backed by MinIO
- JWT resource-server security with role-based route guards
- WebSocket real-time message delivery with JWT-authenticated STOMP handshake
- OpenAPI documentation via Swagger UI and Scalar

---

## Tech stack

| Layer | Technology |
| --- | --- |
| Language | Java 25 |
| Framework | Spring Boot 4.1 (Web MVC, Data JPA, Security, WebSocket) |
| Auth | Keycloak 26 (OAuth2 Resource Server + Admin Client) |
| Database | PostgreSQL 18 |
| Object storage | MinIO |
| Real-time | WebSocket + STOMP |
| Mapping | MapStruct |
| Docs | SpringDoc OpenAPI (Swagger UI + Scalar) |
| Infra | Docker Compose, Nginx Proxy Manager |

---

## Architecture

Feature-sliced packages — each module owns its entity, repository, service, controller, and DTOs.

```
co.istad.projectpracticum.phsardigital
├── config
│   ├── config/          BasedEntity, JPA auditing, Utils
│   ├── security/        SecurityConfig, AuthUtils, Keycloak props
│   └── websocket/       WebSocketConfig, JwtChannelInterceptor
└── features
    ├── auth/            registration → Keycloak Admin API
    ├── user/            local profile (mirrors Keycloak identity)
    ├── categories/      nested tree, slug resolution
    ├── listings/        + listing_images, listing_attributes
    ├── cart/            per-vendor Cart + CartItem
    ├── purchases/       Purchase + PurchaseItem, order lifecycle
    ├── review/          reviews + threaded replies
    ├── favorite/        wishlist
    ├── messaging/       Conversation + Message, WebSocket push
    ├── seller/          SellerProfile
    │   └── application/ verification workflow (+ admin controller)
    └── file/            MinIO upload/preview/delete
```

**Security model.** Public reads (`GET /listings`, `GET /categories`) require no token.
Everything else is role-guarded: `USER` for buyer actions, `SELLER` for shop and fulfilment
actions, `ADMIN` for governance. `AuthUtils.extractUserId()` resolves the caller's Keycloak
`sub` from the validated JWT, which is the identity used for every ownership check.

---

## Getting started

### Prerequisites
- JDK 25
- Docker & Docker Compose

### 1. Start infrastructure

```bash
cd phsardigital-docker
docker compose up -d
```

Brings up PostgreSQL, Keycloak, MinIO, and Nginx Proxy Manager.

### 2. Configure Keycloak

1. Create realm `phsardigital`
2. Create realm roles: `USER`, `SELLER`, `ADMIN`
3. Create a confidential client for the API and copy its secret
4. Grant the client's **service account** these `realm-management` roles:
   `manage-users`, `view-users`, `query-users`, `view-realm`
   *(without these, registration fails when assigning roles)*

### 3. Environment variables

```bash
export URL=jdbc:postgresql://localhost:5432/phsardigital
export USERNAME=your_db_user
export PASSWORD=your_db_password
```

Update `src/main/resources/application.yaml` with your Keycloak URL, client id, and secret.

### 4. Run

```bash
./gradlew bootRun
```

| Service | URL |
| --- | --- |
| API | http://localhost:8999 |
| Swagger UI | http://localhost:8999/swagger-ui.html |
| Scalar docs | http://localhost:8999/scalar |
| WebSocket | ws://localhost:8999/ws |

### 5. Get a token

```bash
curl -X POST "http://localhost:8082/realms/phsardigital/protocol/openid-connect/token" \
  -d "grant_type=password" \
  -d "client_id=phsar_digital_client" \
  -d "username=<user>" \
  -d "password=<password>"
```

Send as `Authorization: Bearer <access_token>` on protected endpoints.

---

## Core flows

### Becoming a seller

```
POST /auth/register              → Keycloak user + USER role + local profile
POST /seller-applications        → business details, status PENDING
POST /seller-applications/me/documents   → attach verification docs
   ── admin reviews ──
PATCH /admin/seller-applications/{uuid}/approve
                                 → SELLER role granted in Keycloak
                                 → SellerProfile created
   ── user must re-login for a token carrying the new role ──
```

### Listing a product

```
POST /files/upload               → returns objectName
POST /listings                   → created as DRAFT, seller taken from token
POST /listings/{uuid}/images     → attach images by objectName
PATCH /listings/{uuid}           → { "status": "ACTIVE" }  ← now purchasable
```

### Buying

```
POST  /carts/items                       → routed to that shop's cart
POST  /purchases/checkout/{sellerId}     → PENDING order, prices frozen, cart cleared
   ── seller ──
GET   /purchases/seller/orders
PATCH /purchases/{uuid}/confirm          → stock decremented, CONFIRMED
   ── delivery arranged over chat, cash collected ──
PATCH /purchases/{uuid}/complete         → COMPLETED
```

**Order lifecycle:** `PENDING → CONFIRMED → COMPLETED`, or `CANCELLED` from either
non-terminal state. Cancelling a `CONFIRMED` order restores stock and reverts `SOLD_OUT`
listings to `ACTIVE`.

---

## API reference

66 endpoints. Full interactive documentation at `/swagger-ui.html`.

| Module | Count | Base path |
| --- | --- | --- |
| Auth | 1 | `/api/v1/auth` |
| User Profiles | 2 | `/api/v1/user-profiles` |
| Files | 4 | `/api/v1/files` |
| Categories | 8 | `/api/v1/categories` |
| Listings | 8 | `/api/v1/listings` |
| Listing Attributes | 3 | `/api/v1/listing_attributes` |
| Cart | 6 | `/api/v1/carts` |
| Purchases | 7 | `/api/v1/purchases` |
| Reviews | 8 | `/api/v1/reviews` |
| Favorites | 3 | `/api/v1/favorites` |
| Messaging | 5 | `/api/v1/conversations` |
| Seller Profile | 4 | `/api/v1/sellers` |
| Seller Applications | 3 | `/api/v1/seller-applications` |
| Admin — Applications | 4 | `/api/v1/admin/seller-applications` |

<details>
<summary><b>Full endpoint list</b></summary>

**Auth**
```
POST   /api/v1/auth/register
```

**User Profiles**
```
GET    /api/v1/user-profiles/me
PATCH  /api/v1/user-profiles/me
```

**Files**
```
POST   /api/v1/files/upload
POST   /api/v1/files/upload-multiple
GET    /api/v1/files/{name}/preview
DELETE /api/v1/files/{name}
```

**Categories**
```
GET    /api/v1/categories
POST   /api/v1/categories
GET    /api/v1/categories/{uuid}
PATCH  /api/v1/categories/{uuid}
GET    /api/v1/categories/{uuid}/children
GET    /api/v1/categories/tree
GET    /api/v1/categories/slug/{slug}
DELETE /api/v1/categories/{slug}
```

**Listings**
```
GET    /api/v1/listings
POST   /api/v1/listings
GET    /api/v1/listings/{uuid}
PATCH  /api/v1/listings/{uuid}
DELETE /api/v1/listings/{uuid}
PATCH  /api/v1/listings/{uuid}/thumbnail
POST   /api/v1/listings/{uuid}/images
DELETE /api/v1/listings/{uuid}/images/{imageUuid}
```

**Listing Attributes**
```
POST   /api/v1/listing_attributes/{listingUuid}
PATCH  /api/v1/listing_attributes/update/{listingUuid}
DELETE /api/v1/listing_attributes/{listingUuid}
```

**Cart**
```
GET    /api/v1/carts
GET    /api/v1/carts/{sellerId}
POST   /api/v1/carts/items
PATCH  /api/v1/carts/{sellerId}/items/{itemUuid}
DELETE /api/v1/carts/{sellerId}/items/{itemUuid}
DELETE /api/v1/carts/{sellerId}
```

**Purchases**
```
POST   /api/v1/purchases/checkout/{sellerId}
GET    /api/v1/purchases
GET    /api/v1/purchases/{uuid}
GET    /api/v1/purchases/seller/orders
PATCH  /api/v1/purchases/{uuid}/confirm
PATCH  /api/v1/purchases/{uuid}/complete
PATCH  /api/v1/purchases/{uuid}/cancel
```

**Reviews**
```
GET    /api/v1/reviews/listings/{listingUuid}
POST   /api/v1/reviews/listings/{listingUuid}
GET    /api/v1/reviews/me
PATCH  /api/v1/reviews/{reviewUuid}
DELETE /api/v1/reviews/{reviewUuid}
GET    /api/v1/reviews/sellers/me
POST   /api/v1/reviews/{reviewUuid}/replies
GET    /api/v1/reviews/{reviewUuid}/replies
```

**Favorites**
```
GET    /api/v1/favorites
POST   /api/v1/favorites/{listingUuid}
DELETE /api/v1/favorites
```

**Messaging**
```
GET    /api/v1/conversations
POST   /api/v1/conversations
GET    /api/v1/conversations/{uuid}/messages
POST   /api/v1/conversations/{uuid}/messages
PATCH  /api/v1/conversations/{uuid}/read
```

**Seller Profile**
```
GET    /api/v1/sellers/{sellerId}
GET    /api/v1/sellers/{sellerId}/listings
GET    /api/v1/sellers/me
PATCH  /api/v1/sellers/me
```

**Seller Applications**
```
POST   /api/v1/seller-applications
GET    /api/v1/seller-applications/me
POST   /api/v1/seller-applications/me/documents
```

**Admin — Seller Applications**
```
GET    /api/v1/admin/seller-applications
GET    /api/v1/admin/seller-applications/{uuid}
PATCH  /api/v1/admin/seller-applications/{uuid}/approve
PATCH  /api/v1/admin/seller-applications/{uuid}/reject
```

</details>

---

## Real-time messaging

REST is the source of truth; WebSocket is a delivery channel layered on top. Messages are
always persisted, so offline recipients lose nothing — they fetch history on next load.

One conversation per user pair, normalised (`participantA < participantB`) so that
buyer→seller and seller→buyer resolve to the same thread rather than creating duplicates.

| Concern | Handled by |
| --- | --- |
| Persist message | REST / JPA |
| Send message | `POST /conversations/{uuid}/messages` |
| Deliver to online recipient | WebSocket push |
| Chat history, inbox, unread counts | REST |

**Connecting.** Browsers cannot set headers on a WebSocket handshake, so the JWT travels in
the **STOMP CONNECT frame** and is validated by a `ChannelInterceptor` before the principal
is bound:

```js
const client = new StompJs.Client({
  brokerURL: 'ws://localhost:8999/ws',
  connectHeaders: { Authorization: 'Bearer ' + token },
  onConnect: () => {
    client.subscribe('/user/queue/messages', frame => {
      const message = JSON.parse(frame.body);
      // render into the open thread
    });
  }
});
client.activate();
```

---

## Project status

**Implemented (66 endpoints)** — authentication, user profiles, file storage, categories,
listings with images and attributes, per-vendor cart, full order lifecycle, reviews with
replies, favorites, real-time messaging, seller profiles, seller verification workflow, and
admin application review.

**Planned**

| Module | Endpoints | Notes |
| --- | --- | --- |
| Notifications | 4 | Order and message events currently have no notification channel |
| Subscriptions | 11 | Seller→platform billing; requires a payment decision |
| Admin governance | 13 | Dashboard, user management, moderation, reports |
| Seller dashboard | 1 | KPI aggregation |
| Receipt PDF | 1 | `GET /purchases/{uuid}/receipt` |

**Known limitations, stated honestly**

- Concurrent `confirm` calls on the last unit of stock could oversell. Acceptable at current
  scale; a pessimistic lock on the listing read would close it.
- Nothing notifies a seller that an order has arrived — they must check the orders page.
  This is the most significant functional gap and the next planned work.
- Registration creates a Keycloak user and a local profile in sequence; a failure between
  the two leaves an account without a profile. There is no compensating rollback, since
  Keycloak is an external system outside the JPA transaction.

---

## License

Academic project. Not licensed for production use.
