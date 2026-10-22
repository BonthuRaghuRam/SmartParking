# Smart Parking Lot – Backend

A Spring Boot REST API for managing a multi-floor parking lot. It automatically assigns the best parking spot based on vehicle size, tracks entry and exit times, calculates fees by duration and vehicle type, and provides real-time spot availability.

**Design documents:** [`docs/DESIGN.md`](docs/DESIGN.md) (architecture, data model, flows, algorithms, concurrency) and [`docs/ClassDiagram.md`](docs/ClassDiagram.md) (class-level view)

## 🚀 Features

- **Smart Spot Allocation:** `NearestFirstSpotStrategy` picks the best-fit spot type first (so large spots stay free for buses), then the lowest floor and spot number.
- **Check-In / Check-Out:** Records entry/exit times and generates unique ticket IDs (e.g. `TKT-A1B2C3D4`). Check-out is idempotent.
- **Fee Calculation:** `HourlyFeeStrategy` bills per started hour (minimum 1 hour) using a base rate and a per-vehicle multiplier. All rates are configurable in `application.properties`.
- **Vehicle Tracking:** A `Vehicle` entity (unique license plate + type) is linked to every ticket, so every visit is tied to a known vehicle.
- **Real-Time Availability:** Spot status changes in the same transaction as the ticket, so reads always see current state.
- **Concurrency Safety:**
    - Spot claiming uses a pessimistic row lock with `SKIP LOCKED`, so simultaneous check-ins receive *different* spots instead of fighting over the same one.
    - `@Version` optimistic locking on `ParkingSpot` and `Ticket` acts as a safety net.
    - `@Retryable` re-runs a transaction on lock or optimistic-lock failure (5 attempts, randomised backoff).
    - A unique `active_plate` column on `Ticket` guarantees a plate can hold only one active ticket, even under a race.
- **Clean Architecture:** Strategy pattern for swappable allocation and pricing, DTOs at the API boundary, injectable `Clock` for testable time, and `@ControllerAdvice` for uniform error responses.

## 🛠️ Tech Stack

- **Language:** Java 21
- **Framework:** Spring Boot 3.5 (Web, Data JPA, Validation, AOP), Spring Retry
- **Database:** H2 in-memory (schema is portable to any SQL database)
- **Tools:** Lombok, Maven, JUnit 5, Mockito, MockMvc

## 📂 Project Structure

```plaintext
src/main/java/com/airtribe/smartparking
├── SmartparkingApplication.java      # Entry point, Clock bean, seed data generator
├── controller/ParkingController.java # REST API endpoints
├── dto/                              # CheckInRequest, TicketResponseDto
├── service/ParkingServiceImpl.java   # Business logic, transactions, retry
├── strategy/                         # Pluggable algorithms (spot assignment, fees)
├── repository/                       # Spring Data JPA (ParkingSpot, Ticket, Vehicle)
├── model/                            # Entities (ParkingSpot, Vehicle, Ticket) & enums
└── exception/                        # TicketNotFoundException, GlobalExceptionHandler
docs/
├── DESIGN.md                         # Low-level design
└── ClassDiagram.md                   # Class diagram
```

## ⚙️ Run Locally

**Prerequisites:** JDK 21 (the project is compiled with `java.version=21`).

Build and test the project:

```bash
./mvnw clean install
```

Start the server:

```bash
./mvnw spring-boot:run
```

- **Server URL:** http://localhost:8050
- **H2 Database Console:** http://localhost:8050/h2-console (JDBC URL: `jdbc:h2:mem:parkingdb`, User: `sa`, Password: *blank*)

> **Note:** On startup the database is seeded with 60 parking spots across 3 floors. Each floor has 20 spots: 4 SMALL, 12 COMPACT and 4 LARGE. Data is in-memory and is lost on restart.

## 📡 API Endpoints

| Method | Path | Description |
|---|---|---|
| GET | `/api/v1/parking/availability` | Free-spot counts grouped by floor and spot type. |
| POST | `/api/v1/parking/check-in` | Allocate a spot and create a ticket (`201 Created`). |
| POST | `/api/v1/parking/check-out/{ticketNumber}` | Calculate the fee and free the spot (`200 OK`, idempotent). |

**Error responses**

| Status | When |
|---|---|
| 400 | Blank plate, missing vehicle type, malformed JSON, or an unknown vehicle type (the message lists the allowed values) |
| 404 | Unknown ticket number |
| 409 | Vehicle already parked, no spot available, or the system is under heavy contention (retry) |

## ⚡ Quick Try (cURL)

**1. Check In**

```bash
curl -X POST http://localhost:8050/api/v1/parking/check-in \
  -H "Content-Type: application/json" \
  -d '{"licensePlate":"TS09-EX-1234","vehicleType":"CAR"}'
```

**2. Check Availability**

```bash
curl http://localhost:8050/api/v1/parking/availability
```

**3. Check Out** (replace with your actual ticket number)

```bash
curl -X POST http://localhost:8050/api/v1/parking/check-out/TKT-XXXXXXXX
```

License plates are normalised (trimmed, upper-cased, whitespace removed) before use.

## 🅿️ Spot Compatibility

| Vehicle | Spot types tried, in order |
|---|---|
| MOTORCYCLE | SMALL → COMPACT → LARGE |
| CAR | COMPACT → LARGE |
| BUS | LARGE |

## 💰 Fee Rules

- **Formula:** `max(1, ceil(seconds / 3600)) × Base Rate × Vehicle Multiplier`, rounded to 2 decimals (HALF_UP).
- **Defaults:** Base rate ₹20/hour. Multipliers: Motorcycle 0.5, Car 1.0, Bus 2.5.
- **Examples:** 1 hour: Motorcycle ₹10, Car ₹20, Bus ₹50. 60 min 1 s: Car ₹40 (starts a 2nd hour).
- **Configuration** (`application.properties`):

```properties
parking.rates.base-hourly=20.00
parking.rates.multiplier.motorcycle=0.5
parking.rates.multiplier.car=1.0
parking.rates.multiplier.bus=2.5
```

## 🧪 Tests

Run the suite with `./mvnw test`.

| Test class | Type | Covers |
|---|---|---|
| `HourlyFeeStrategyTest` | Unit | Minimum charge, per-vehicle multiplier, rounding up (including 60 min 1 s), exit before entry |
| `ParkingServiceIntegrationTest` | Integration | Allocation with fallback, duplicate check-in, full lot, check-out frees spot |
| `ConcurrencyTest` | Integration | 15 simultaneous check-ins for 5 spots (no double booking), same-plate race (only one succeeds), simultaneous check-outs of one ticket (idempotent) |
| `ParkingControllerTest` | Web layer (MockMvc) | 201 check-in, 200 check-out and availability, 400 on missing plate or invalid vehicle type, 409 on already parked, 404 on unknown ticket |
| `SmartparkingApplicationTests` | Smoke | Application context loads |