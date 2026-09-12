# Wallet & P2P Transfer Service

A simple wallet service that allows users to maintain a balance and transfer money to other users.

The application is built using Spring Boot 3, Java 21, and PostgreSQL.

All amounts are stored in paise as integers. The service does not use floating point or decimal values for money, which avoids rounding issues during transfers.

## Running the Application

The application can be started locally with Docker Compose.

```bash
docker compose up --build
```

Once started, the application will be available at:

```text
http://localhost:8080
```

PostgreSQL runs on port `5432`.

The database is initialized automatically when the application starts. Flyway handles the database migrations, so there is no need to run any SQL scripts manually.

## API

Each API request requires the following header:

```text
Authorization: Bearer <userId>
```

This project does not implement real authentication. The value in the bearer token is simply treated as the user ID. This keeps the focus on the wallet and transfer functionality.

### Create or Get Wallet

```http
POST /wallets
```

Creates a wallet for the current user if one does not already exist.

If the user already has a wallet, the existing wallet is returned.

### Get Wallet

```http
GET /wallets/{id}
```

Returns the wallet and its current balance.

### Create Transfer

```http
POST /transfers
```

Transfers money from one wallet to another.

Request body:

```json
{
  "from": 1,
  "to": 2,
  "amountPaise": 1000,
  "idempotencyKey": "unique-key"
}
```

The transfer checks the available balance and prevents the same request from being processed more than once.

### Get Transfer

```http
GET /transfers/{id}
```

Returns the current status and details of a transfer.

### Reverse Transfer

```http
POST /transfers/{id}/reverse
```

Reverses a completed transfer and returns the money to the original sender.

Request body:

```json
{
  "idempotencyKey": "unique-key"
}
```

### Seed Wallet

```http
POST /admin/wallets/{id}/seed
```

Adds money directly to a wallet for testing purposes.

Request body:

```json
{
  "amountPaise": 10000
}
```

This endpoint is provided only to make local testing easier. It is not part of the normal wallet API.

## Testing

The project includes scripts that send concurrent requests to the application and verify the main consistency guarantees.

Run them using:

```bash
scripts/burst-get-or-create.sh http://localhost:8080 50
scripts/burst-idempotent-retry.sh http://localhost:8080 30
scripts/burst-conservation.sh http://localhost:8080 50
```

The scripts test scenarios such as concurrent wallet creation, repeated requests with the same idempotency key, and balance conservation during concurrent transfers.

Each script reports `PASS` or `FAIL` when it finishes.

## Monitoring

The application exposes structured JSON logs through standard output.

Each request receives an `X-Correlation-Id`, which can be used to follow a request across the application logs.

Prometheus metrics are available at:

```text
GET /actuator/prometheus
```

Along with standard application metrics such as request rate and response time, the service exposes custom metrics related to wallet transfers:

```text
wallet.transfers.created
wallet.transfers.declined_insufficient_funds
wallet.transfers.idempotent_replay
```

The application health status is available at:

```text
GET /actuator/health
```

## Design Notes

The main design decisions around concurrency, transaction handling, locking, idempotency, and transfer consistency are documented in `WRITEUP.md`.
