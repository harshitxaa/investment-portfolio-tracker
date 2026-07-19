# Portfolio Tracker API

A medium-complexity **Java / Spring Boot backend** for tracking investment portfolios,
transactions, and watchlists, with basic performance analytics (returns, P/L,
volatility, Sharpe ratio).

This project is inspired by the idea behind a "QuantFolio" style app, but the
focus has been deliberately shifted **away from quantitative finance math**
(no optimizers, no Monte Carlo, no options pricing) and **onto backend
engineering fundamentals**: REST API design, layered architecture, JPA/Hibernate,
Spring Security with JWT, validation, global exception handling, pagination,
filtering/search, and Swagger documentation. The analytics endpoints are simple
and well-documented on purpose, so every formula is something you can explain
line by line.

---

## Tech stack

| Concern              | Technology                                  |
|-----------------------|---------------------------------------------|
| Language / runtime    | Java 17                                      |
| Framework             | Spring Boot 3.3 (Web, Security, Validation)  |
| Persistence           | Spring Data JPA + Hibernate                  |
| Database              | MySQL 8                                      |
| Auth                  | JWT (jjwt), BCrypt password hashing          |
| API docs              | springdoc-openapi (Swagger UI)               |
| Build                 | Maven                                        |
| Boilerplate reduction | Lombok                                       |

---

## Architecture

Classic layered architecture, one package per responsibility:

```
com.portfoliotracker
├── config          # SecurityConfig, OpenApiConfig
├── security        # JwtUtil, JwtAuthFilter, UserDetailsServiceImpl, SecurityUtils
├── entity          # JPA entities (User, Portfolio, Transaction, WatchlistItem, MarketPrice)
├── repository      # Spring Data JPA repositories
├── dto             # Request/response objects — entities never leave the service layer
├── specification   # JPA Specifications for dynamic search/filter
├── service         # Business logic
├── controller      # REST endpoints — thin, delegate to services
└── exception       # Custom exceptions + @RestControllerAdvice global handler
```

**Why DTOs instead of returning entities directly?** It decouples the API
contract from the database schema, avoids leaking JPA proxies/lazy-loading
issues into JSON, and lets request/response shapes evolve independently of
the entity model — a very standard practice worth being able to explain.

**Why Specifications for transaction search?** `TransactionSpecification`
builds a `WHERE` clause dynamically depending on which optional filters
(`symbol`, `type`, `from`, `to`) are actually supplied, instead of writing a
combinatorial explosion of repository methods or fragile string-concatenated
JPQL.

**Why a global exception handler?** So controllers stay free of `try/catch`
and every error - validation failure, not-found, duplicate, bad credentials,
unexpected exception - is translated into the same consistent JSON shape
(`timestamp`, `status`, `error`, `message`, `path`, and optionally
`validationErrors`).

---

## Data model

```
User 1---* Portfolio 1---* Transaction
User 1---* WatchlistItem
MarketPrice   (symbol, priceDate, closePrice) - independent price history table
```

- A **Transaction** is a single BUY or SELL of a symbol at a price/quantity/date.
- **Holdings are not stored** — they're derived on demand from the transaction
  history using the **weighted-average cost method**: each BUY updates a
  running average cost; each SELL reduces quantity but leaves the average
  cost basis unchanged. This is the same approach many brokerages use for
  cost-basis reporting, and it's implemented in
  `PortfolioService.getSummary()` / the `HoldingAccumulator` helper class.
- **MarketPrice** holds a simulated daily close price feed (see below) —
  it's what "current price" and the analytics calculations are computed
  against.

### About the simulated price feed

There's no dependency on a paid market-data API. The first time a symbol is
referenced (via a transaction, a watchlist entry, or an analytics call),
`MarketPriceService` seeds ~90 days of daily closing prices using a simple
random walk (`price_t = price_(t-1) * (1 + small random % change)`), seeded
per-symbol so it's reproducible. Everything downstream (current price,
market value, analytics) is computed off that stored history. Swapping in a
real provider (Alpha Vantage, Twelve Data, IEX Cloud, etc.) would only mean
changing this one class — every other layer just calls
`getCurrentPrice(symbol)` / `getPriceHistory(symbol, days)`.

This is a deliberate, documented simplification appropriate for a backend
learning project — it keeps the project self-contained and runnable offline
while still exercising real time-series calculations.

---

## Analytics — how each metric is computed

All in `AnalyticsService`. **Simplifying assumption** (also called out in
code comments): the portfolio's *current* holding quantities are treated as
constant across the whole lookback window when building the historical value
series, rather than replaying each buy/sell on the date it happened. That
keeps the math easy to trace through while still being genuine time-series
analysis.

1. **Daily portfolio value series** — for each date in the lookback window,
   `value(date) = Σ (quantity_held(symbol) × closePrice(symbol, date))`.
2. **Daily returns** — `r_t = (value_t − value_(t-1)) / value_(t-1)`.
3. **Total return %** — `(endValue − startValue) / startValue × 100`.
4. **Unrealized P/L** — `endValue − startValue` (in the summary endpoint,
   P/L is computed properly against cost basis instead).
5. **Annualized volatility** — sample standard deviation of daily returns,
   annualized by `× √252` (252 ≈ trading days/year).
6. **Sharpe ratio** — `(annualizedReturn − riskFreeRate) / annualizedVolatility`,
   where `annualizedReturn = meanDailyReturn × 252`.

None of this requires a math library — it's plain `BigDecimal`/`double`
arithmetic, which is intentional so you can walk through every line in an
interview.

---

## Auth flow

1. `POST /api/auth/register` — creates a user (BCrypt-hashed password),
   returns a JWT.
2. `POST /api/auth/login` — authenticates via Spring Security's
   `AuthenticationManager`, returns a JWT.
3. Every other endpoint requires `Authorization: Bearer <token>`.
   `JwtAuthFilter` (a `OncePerRequestFilter`) validates the token and
   populates the `SecurityContext` on each request; `SecurityUtils` reads
   the current user's id out of that context so services never trust a
   client-supplied user id.
4. All portfolio/transaction/watchlist queries are scoped to
   `owner_id = currentUser` at the repository level — one user can never
   read or modify another user's data, even by guessing an id.

---

## Running locally

### 1. Start MySQL

```bash
docker compose up -d
```

(or point `application.yml` / env vars at an existing MySQL instance)

### 2. Configure environment (optional — sensible dev defaults exist)

```bash
export DB_USERNAME=root
export DB_PASSWORD=root
export JWT_SECRET=change-this-to-a-long-random-string-in-real-deployments
```

### 3. Run

```bash
mvn spring-boot:run
```

(requires Maven 3.9+ installed locally; a Maven wrapper wasn't bundled in
this export, so `mvn` is used directly — running `mvn -N wrapper:wrapper`
once will generate `./mvnw` if you'd prefer that)

The app starts on `http://localhost:8080`.
Swagger UI: `http://localhost:8080/swagger-ui.html`

---

## Example requests

```bash
# Register
curl -X POST http://localhost:8080/api/auth/register \
  -H "Content-Type: application/json" \
  -d '{"username":"alex","email":"alex@example.com","password":"password123"}'

# Login (grab the token from the response)
curl -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"alex","password":"password123"}'

TOKEN="paste-jwt-here"

# Create a portfolio
curl -X POST http://localhost:8080/api/portfolios \
  -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" \
  -d '{"name":"Growth Portfolio","description":"Long-term tech holdings"}'

# Record a BUY transaction
curl -X POST http://localhost:8080/api/portfolios/1/transactions \
  -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" \
  -d '{"symbol":"AAPL","type":"BUY","quantity":10,"price":180.50,"transactionDate":"2026-05-01"}'

# Search/filter/sort/paginate transaction history
curl "http://localhost:8080/api/portfolios/1/transactions?symbol=AAPL&type=BUY&sort=transactionDate,desc&page=0&size=10" \
  -H "Authorization: Bearer $TOKEN"

# Portfolio summary (holdings, market value, P/L)
curl http://localhost:8080/api/portfolios/1/summary -H "Authorization: Bearer $TOKEN"

# Analytics (returns, volatility, Sharpe ratio)
curl "http://localhost:8080/api/portfolios/1/analytics?lookbackDays=90&riskFreeRatePct=2.0" \
  -H "Authorization: Bearer $TOKEN"

# Watchlist
curl -X POST http://localhost:8080/api/watchlist \
  -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" \
  -d '{"symbol":"TSLA","notes":"Watching for a pullback"}'
```

---

## Full endpoint list

| Method | Path | Description |
|---|---|---|
| POST | `/api/auth/register` | Register + receive JWT |
| POST | `/api/auth/login` | Login + receive JWT |
| POST | `/api/portfolios` | Create portfolio |
| GET | `/api/portfolios` | List my portfolios |
| GET | `/api/portfolios/{id}` | Get one portfolio |
| PUT | `/api/portfolios/{id}` | Update portfolio |
| DELETE | `/api/portfolios/{id}` | Delete portfolio (cascades transactions) |
| GET | `/api/portfolios/{id}/summary` | Computed holdings, market value, P/L |
| POST | `/api/portfolios/{id}/transactions` | Record BUY/SELL |
| GET | `/api/portfolios/{id}/transactions` | Search/filter/sort/paginate history |
| GET | `/api/portfolios/{id}/transactions/{txId}` | Get one transaction |
| DELETE | `/api/portfolios/{id}/transactions/{txId}` | Delete a transaction |
| GET | `/api/portfolios/{id}/analytics` | Returns, volatility, Sharpe ratio |
| POST | `/api/watchlist` | Add a symbol |
| GET | `/api/watchlist` | List watchlist with live prices |
| DELETE | `/api/watchlist/{id}` | Remove from watchlist |

---

## Things worth knowing if you extend this project

- `ddl-auto: update` is used for convenience. A production-grade version of
  this project would swap to **Flyway or Liquibase** migrations with
  `ddl-auto: validate`.
- Passwords are hashed with **BCrypt** via Spring Security's
  `PasswordEncoder` — never stored or logged in plain text.
- JWT secret and DB credentials are read from environment variables with
  dev-only fallback defaults in `application.yml` — never commit real
  secrets.
- Natural next steps if you want to push this further: refresh tokens,
  role-based admin endpoints (the `Role` enum already exists), rate
  limiting, caching the market price lookups, integration tests with
  Testcontainers, and replacing the simulated price feed with a real
  market-data API.
