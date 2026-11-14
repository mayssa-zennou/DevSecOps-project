# DevSecOps Project: Order Saga

Order management with stock reservation and user notification, built as a set of Spring Boot microservices to practice the problems that distributed systems frequently raise: data consistency across services, partial failures, idempotency, as well as **DevSecOps** best practices: securing service-to-service communication (identity, mTLS, encrypted broker, network isolation) and proving it with reproducible attack scenarios.

The business domain is deliberately simple (ordering products and reserving stock + user notifivation) so that the effort goes into engineering rather than business logic.

## Tech stack

Java 25, Spring Boot 4.1.2, Maven (wrapper included), PostgreSQL 16 (Order, Inventory, Notification), Spring Cloud Gateway (WebFlux), Kafka, Mailpit, Flyway, JUnit 5, Testcontainers, Docker Compose.

## Quick start

**Prerequisite:** Docker with Compose. No local Java needed to run the stack.

```bash
git clone https://github.com/mayssa-zennou/DevSecOps-project.git
cd DevSecOps-project

docker compose up -d --build
docker compose ps        # check that everything is up and databases are "healthy"
```

The first build downloads Java and the Maven dependencies, so it takes a few minutes. The API Gateway is included in this stack and is available on port 8080.

Stop everything:

```bash
docker compose down        
```

Useful:

```bash
docker compose logs -f order-service       # follow the logs of one service
docker compose up -d --build order-service # rebuild and restart one service
```

Get the unsecured baseline:

```bash
git checkout v0-unsecured
```

## Services and ports

| Service | Port | Storage |
|---|---|---|
| Order Service | 8081 | PostgreSQL `orders` (host port 5432) |
| Inventory Service | 8082 | PostgreSQL `inventory` (host port 5433) |
| Notification Service | 8083 | PostgreSQL `notification` (host port 5434) |
| API Gateway | 8080 | none |
| Kafka | 9092 | none |
| Mailpit | web UI 8025 and SMTP 1025 | none |

## API

### Order Service (`:8081`)

| Method | Path | Description | Responses |
|---|---|---|---|
| `POST` | `/orders` | Create an order (starts as `PENDING`) | `201`, `400` invalid body |
| `GET` | `/orders/{id}` | Read one order | `200`, `404` unknown id, `400` malformed id |
| `GET` | `/orders` | List orders | `200` |

Body of `POST /orders`: `{"productId": "SKU-42", "quantity": 3}` (`productId` not blank, `quantity` at least 1).

Order statuses: `PENDING`, `CONFIRMED`, `REJECTED`.

### Inventory Service (`:8082`)

| Method | Path | Description | Responses |
|---|---|---|---|
| `GET` | `/stock/{productId}` | Available quantity of a product | `200`, `404` unknown product |
| `POST` | `/stock/{productId}/reservations` | Reserve a quantity | `200` reserved, `409` insufficient stock, `404` unknown product, `400` invalid quantity |

Body of a reservation: `{"quantity": 30}`. Demo data: `SKU-42` is seeded with 100 units.

### Notification Service (`:8083`)

The Kafka listeners handle messages from `order.created` and `stock.low`. For `order.created`, the service sends an email and saves an `ORDER_CREATED` notification. For `stock.low`, it saves a `STOCK_LOW` notification for user `admin`; this handler does not send an email. The repository contains no publisher for `stock.low`, so another producer must publish that event. The service skips an event whose `eventId` is already stored, and the database enforces uniqueness for that field.

| Method | Path | Description |
|---|---|---|
| `GET` | `/notifications` | Return all stored notifications |
| `GET` | `/notifications/user/{userId}` | Return notifications for the specified user |

The order service currently publishes provisional user data (`alice`, `alice@test.com`), which is the recipient used by the notification email handler. Mailpit's web interface is available at `http://localhost:8025` when the Compose stack is running.

### API Gateway (`:8080`)

The Spring Cloud Gateway WebFlux application forwards these paths without changing them. In Docker Compose, it uses the service names on the internal network:

| Path | Configured upstream |
|---|---|
| `/orders` and `/orders/**` | `order-service:8081` |
| `/stock` and `/stock/**` | `inventory-service:8082` |
| `/notifications` and `/notifications/**` | `notification-service:8083` |

When running the gateway directly from your IDE or Maven, the default upstream URLs point to `localhost:8081`, `:8082`, and `:8083`. To rebuild and start only the gateway container after changing it:

```bash
docker compose up -d --build api-gateway
docker compose logs -f api-gateway
```

To start the gateway locally instead of in Docker, with the other services already running:

```bash
cd services/api-gateway
./mvnw spring-boot:run
```

On Windows, use:

```powershell
cd services/api-gateway
.\mvnw.cmd spring-boot:run
```

## Try it with curl

```bash
# Create an order through the gateway
curl -i -X POST http://localhost:8080/orders \
  -H "Content-Type: application/json" \
  -d '{"productId":"SKU-42","quantity":3}'

# Read / list orders through the gateway
curl -i http://localhost:8080/orders
curl -i http://localhost:8080/orders/<order-id>

# Check the stock through the gateway
curl -i http://localhost:8080/stock/SKU-42

# List notifications through the gateway
curl -i http://localhost:8080/notifications

# Reserve 30 units
curl -i -X POST http://localhost:8080/stock/SKU-42/reservations \
  -H "Content-Type: application/json" \
  -d '{"quantity":30}'
```

> On Windows PowerShell, use `curl.exe` and escape the JSON quotes (`-d '{\"quantity\":30}'`), or use Postman or Bruno.

## Tests

Docker must be running: Testcontainers starts its own throwaway databases, independent from the Compose ones.

```bash
cd services/order-service && ./mvnw test
cd services/inventory-service && ./mvnw test
cd services/notification-service && ./mvnw test
```

On Windows use `.\mvnw.cmd test`.

## Local development

Run a service from the IDE: start its dependencies, then launch the application. Order and inventory use `application.yml`; notification and the gateway use `application.properties`. Notification's properties point to PostgreSQL at `localhost:5434`, Kafka at `localhost:9092`, and SMTP at `localhost:1025`.

```bash
docker compose up -d orders-db inventory-db notification-db kafka mailpit
```

Inspect a database:

| Service | Database | Host port | User | Password |
|---|---|---|---|---|
| orders-db | `orders` | 5432 | mayssa | orders |
| inventory-db | `inventory` | 5433 | mayssa | inventory |
| notification-db | `notification` | 5434 | nazha | notification |

```bash
docker compose exec <service> psql -U <user> -d <db>
```

In `psql`: `\dt` lists tables, `\d stock` describes a table, `SELECT * FROM flyway_schema_history;` shows applied migrations, `\q` quits.
