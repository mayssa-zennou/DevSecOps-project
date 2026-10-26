# Order Saga

Order management with stock reservation, built as 3 microservices
(Spring Boot 4, PostgreSQL, RabbitMQ) to practice distributed-systems
patterns: choreographed saga, outbox, idempotency.

See [docs/adr](docs/adr) for architecture decisions.

## Local development

### Start the infrastructure
```bash
docker compose up -d     # start all databases
docker compose ps        # check they are running
docker compose down      # stop (data kept); add -v to wipe it
```

### Databases

| Service | Host port | Database | User | Password |
|---|---|---|---|---|
| order-service | 5432 | orders | mayssa | orders |
| inventory-service | 5433 | inventory | mayssa | inventory |

> Credentials are for local throwaway containers only. Never reuse them anywhere else.

### Inspect a database
```bash
docker compose exec <service> psql -U <user> -d <db>
docker exec -it <container> psql -U <user> -d <db>
```

Useful commands inside `psql`: `\dt` (list tables), `\d stock` (describe a table),
`SELECT * FROM flyway_schema_history;` (applied migrations), `\q` (quit).

A GUI client (DBeaver, IntelliJ Database tool) works too: connect to `localhost` on the host port above.