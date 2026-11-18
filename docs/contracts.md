# Interface contracts

This document is the source of truth between the services and between the two
owners (A: identity and JWT; B: network and broker). Any change to a name
below must go through this file before it reaches the code.

## 1. Kafka topics

| Topic             | Producer             | Consumer               | Message key | Event                |
|-------------------|----------------------|------------------------|-------------|----------------------|
| `order.created`   | order-service        | inventory-service      | `orderId`   | `OrderCreatedEvent`  |
| `stock.reserved`  | inventory-service    | order-service          | `orderId`   | `StockReservedEvent` |
| `stock.rejected`  | inventory-service    | order-service          | `orderId`   | `StockRejectedEvent` |
| `order.confirmed` | order-service        | notification-service   | `orderId`   | `OrderConfirmedEvent`|
| `order.rejected`  | order-service        | notification-service   | `orderId`   | `OrderRejectedEvent` |

Consumer groups: `inventory-service`, `order-service`,
`notification-service` (one group per service, same name as the service).

Format: JSON as a `String`, key = `orderId` to guarantee the ordering of the
events of a given order on a single partition.

Topic declaration: each topic is declared by its producing service
(`NewTopic` bean, 1 partition, replication factor 1). If the broker disables
auto-creation or restricts the `Create` permission, the topics must instead be
created by the broker setup, with exactly the names above.

## 2. Event schemas

All fields are `String` except `quantity` (int) and `occurredAt`
(ISO-8601, UTC). `eventId` is a unique UUID per event.

- **OrderCreatedEvent**: eventId, orderId, userId, userEmail, productId, quantity, occurredAt
- **StockReservedEvent**: eventId, orderId, occurredAt
- **StockRejectedEvent**: eventId, orderId, reason (`INSUFFICIENT_STOCK` or `UNKNOWN_PRODUCT`), occurredAt
- **OrderConfirmedEvent**: eventId, orderId, userId, userEmail, occurredAt
- **OrderRejectedEvent**: eventId, orderId, userId, userEmail, reason (copied from `StockRejectedEvent`), occurredAt

Note: `userId` and `userEmail` are **provisional** (hard-coded values
`alice` / `alice@test.com`) as long as there is no authentication. They
will come from the JWT in Phase 1.

## 3. Processing rules

- **At-least-once delivery**: order-service and inventory-service write their
  events to an `outbox` table in the same transaction as the business change;
  a relay then publishes them (message key = `orderId`). An event may be
  published more than once but is never lost.
- **Consumer idempotence**, one mechanism per service:

  | Service              | Idempotency mechanism                                                                  |
  |----------------------|----------------------------------------------------------------------------------------|
  | inventory-service    | `processed_events` table; the `eventId` is inserted in the same transaction as the stock reservation |
  | order-service        | state check: `confirm()` / `reject()` only applies if the order is `PENDING`, any other event is ignored |
  | notification-service | unique `event_id` on the `notification` row, saved before the email is sent, in one transaction |

- **Stable eventId** for `order.confirmed` and `order.rejected`: UUID v3
  derived from `topic:orderId`, so a republished event carries the same
  identifier and is deduplicated downstream. The other events use a random UUID.
- **Order states**: `PENDING` becomes `CONFIRMED` on `stock.reserved`, or
  `REJECTED` on `stock.rejected` (this is the compensation of the order
  creation). Both are terminal.
- **No unnecessary personal data**: only the events meant for
  notification-service carry the email.

## 4. OAuth2 scopes (Phase 1)

| Scope                 | Granted to                             | Protects                              |
|-----------------------|----------------------------------------|---------------------------------------|
| `order:write`         | end clients (through the gateway)      | `POST /orders`                        |
| `order:read`          | end clients (through the gateway)      | `GET /orders/**`                      |
| `stock:read`          | end clients (through the gateway)      | `GET /stock/{productId}`              |
| `stock:reserve`       | admin / internal tooling only          | `POST /stock/{productId}/reservations`|
| `notification:read`   | end clients (own notifications), admin | `GET /notifications/**`               |

Notes:

- Stock reservation inside the order flow happens through Kafka
  (`order.created`), not through an HTTP call, so no service needs
  `stock:reserve` at runtime.
- `GET /notifications/user/{userId}` is allowed only when `userId` equals the
  token `sub`, unless the caller is admin.
- `POST /stock/{productId}/reservations` is still exposed, but the saga no
longer uses it (Order and Inventory communicate only through Kafka). The
`stock:reserve` scope and the Order → Inventory client credentials
only make sense if Order keeps a synchronous call to Inventory; otherwise the
security of that link moves to the Kafka ACLs.

## 5. Identities and certificates

Convention: one certificate per service, `CN=<service-name>`, with no
special characters.

| Service              | CN                      |
|----------------------|-------------------------|
| order-service        | `CN=order-service`      |
| inventory-service    | `CN=inventory-service`  |
| notification-service | `CN=notification-service` |
| api-gateway          | `CN=api-gateway`        |

The CN also serves as the Kafka principal (B's ACLs) and as the Keycloak `client_id`.

## 6. Internal certificate authority

- **A generates** the internal CA (private key kept by A, never committed).
- **B consumes** the CA's public certificate (Kafka and network truststore).
- Service certificates are issued by A and placed in `certs/`
  (ignored by git), following the Phase 2 rotation script.

## 7. Synchronous links

The order flow itself is asynchronous (section 1). The HTTP links are:

| Caller      | Callee                                          | Protection                        |
|-------------|-------------------------------------------------|-----------------------------------|
| Client      | api-gateway                                     | user JWT                          |
| api-gateway | order-service, inventory-service, notification-service | forwarded JWT; **mTLS on api-gateway → order-service** (minimum viable link) |

Kafka ACLs follow directly from the producer and consumer columns of
section 1: a service may only produce and consume what that table gives it,
and read only its own consumer group.