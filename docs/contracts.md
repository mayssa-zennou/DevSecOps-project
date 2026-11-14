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

## 2. Event schemas

All fields are `String` except `quantity` (int) and `occurredAt`
(ISO-8601, UTC). `eventId` is a unique UUID per event.

- **OrderCreatedEvent**: eventId, orderId, userId, userEmail, productId, quantity, occurredAt
- **StockReservedEvent**: eventId, orderId, occurredAt
- **StockRejectedEvent**: eventId, orderId, reason (`INSUFFICIENT_STOCK` or `UNKNOWN_PRODUCT`), occurredAt
- **OrderConfirmedEvent**: eventId, orderId, userId, userEmail, occurredAt
- **OrderRejectedEvent**: eventId, orderId, userId, userEmail, reason, occurredAt

Note: `userId` and `userEmail` are **provisional** (hard-coded values
`alice` / `alice@test.com`) as long as there is no authentication. They
will come from the JWT in Phase 1.

## 3. Processing rules

- **Idempotency**: a consumer ignores any `eventId` that was already processed
  (`processed_events` table, inserted in the same transaction as the effect).
- **State transition**: order-service only applies `confirm()` or `reject()`
  if the order is `PENDING`; a replayed event has no effect.
- **No unnecessary personal data**: only the events meant for
  notification-service carry the email.

## 4. OAuth2 scopes (Phase 1)

| Scope                 | Granted to                             | Protects                              |
|-----------------------|----------------------------------------|---------------------------------------|
| `order:write`         | end clients (through the gateway)      | `POST /orders`                        |
| `order:read`          | end clients (through the gateway)      | `GET /orders/**`                      |
| `stock:read`          | end clients, order-service             | `GET /stock/{productId}`              |
| `stock:reserve`       | order-service (client credentials)     | `POST /stock/{productId}/reservations`|
| `notification:send`   | internal services only                 | notification sending API              |

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