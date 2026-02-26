# RideApp — High Level Design and API Reference

This README is the single canonical documentation for the RideApp project. It contains a high-level architecture diagram, design notes, how Kafka and Redis are used, all API endpoints with request/response shapes, and operational notes (Docker and environment configuration).

---

## High-Level Design (HLD)

Overview
- RideApp is a lightweight dispatch system where riders submit ride requests, drivers publish location updates, and the dispatch service matches riders to nearby drivers.
- Core components:
  - API (Spring Boot application) — exposes HTTP endpoints for riders and drivers
  - PostgreSQL — persistent storage for `RideRequest`, `Driver`, and `Assignment` entities
  - Redis — transient storage for geo-indexing driver locations and short-lived idempotency keys
  - Kafka — event bus for async notifications (driver-assigned, driver-location-updated, analytics)

HLD Diagram (textual)

```
            +--------------------+       +----------------+
            |                    |       |                |
            |  Mobile Clients    | <---> |  API Gateway   |
            | (Rider & Driver)   |       |  (Spring Boot) |
            +--------------------+       +----------------+
                     |                            |
                     | HTTP/JSON                  | persists
                     |                            v
                     |                     +--------------+
                     |                     | PostgreSQL DB|
                     |                     +--------------+
                     |                            ^
                     |                            |
                     v                            | read/write
                +----------------------+           |
                |  Redis (Geo Index)   |<----------+
                |  - driver:locations  |  used for
                |  - idempotency keys  |  nearest-nearby lookups
                +----------------------+   and TTL ops
                     |
                     | publishes events
                     v
                +----------------------+   Kafka topics
                |   Kafka Broker(s)    |<------------------
                +----------------------+                   |
                       |                                   |
                       v                                   v
             consumers: analytics, notifications, microservices

```

Key Flows
- Driver location updates flow:
  1. Driver POSTs location to /api/dispatch/driver/location
  2. API writes geo point to Redis geo-index (driver:locations)
  3. Optionally publish `driver-location-updated` to Kafka for analytics

- Rider request and matching flow:
  1. Rider POSTs /api/dispatch/ride/request with requestId (idempotency key)
  2. API checks short-lived idempotency key in Redis (key: req:<requestId>)
      - If present, return conflict (already processed)
      - If absent, store temporary key with TTL
  3. Persist RideRequest in PostgreSQL
  4. Query Redis geo-index for nearby drivers
  5. Rank/select best driver and create Assignment in DB
  6. Update Driver.available flag and RideRequest status
  7. Publish `driver-assigned` event to Kafka

Why Redis + Kafka
- Redis: low-latency spatial queries (GEO commands) to find nearest drivers; also used for short-lived idempotency keys to prevent duplicate processing of client retries.
- Kafka: decouples synchronous dispatch processing from downstream consumers (notifications, analytics, billing). Kafka ensures events are broadcast reliably to multiple systems.

---

## Data Model (brief)
- RideRequest (ride_requests)
  - id (UUID), riderId, pickupLat, pickupLong, destLat, destLong, tier, paymentMethod, status
- Assignment (assignments)
  - id (UUID), rideRequest (FK), driver (FK), estimatedMinutes, status
- Driver (drivers)
  - id (UUID), name, rating, vehicleTier, currentLat, currentLong, available

---

## Matching logic (how we find nearby drivers)

This section explains the exact runtime logic used by the service to locate nearby drivers and select the best candidate.

1) Overview
- We use Redis GEO (a geo-index) as the fast, low-latency spatial index. Drivers publish their current location to a Redis GEO key (e.g., `driver:locations`).
- For each incoming ride request the service performs a radius search around the pickup coordinates to gather candidate drivers within a configurable radius (default in code: 5.0 km).
- Candidates from Redis are then joined with persistent driver data in PostgreSQL to get availability, rating, vehicle tier and other profile attributes.
- We filter out non-available drivers, optionally filter by vehicle tier (if rider requested e.g. `premium`), and compute a score combining distance and driver rating to pick the best driver.

2) Steps (detailed)
- Idempotency: before matching, the service writes `req:<requestId>` to Redis with TTL (e.g., 5 minutes) and rejects duplicate requestIds.

- Geo search (Redis):
  - Use GEOSEARCH or GEORADIUS (depending on your Redis version) with parameters:
    - key: `driver:locations`
    - center: pickup longitude, pickup latitude
    - radius: e.g., 5 km
    - options: WITHDIST (returns distance)
  - This gives a list of driverIds with distances in kilometers.

- Load drivers from DB: for each Redis result, load the corresponding `Driver` entity from PostgreSQL (via `DriverRepository.findById(...)`).

- Filter: remove drivers where `driver.available == false` or that don't support the requested `tier`.

- Rank / scoring: compute a score for each candidate and pick the minimum score as best driver.
  - Example scoring formula used in code (simple, replaceable):

    score = distanceKm * w_distance + (5.0 - rating) * w_rating

    where typical weights are w_distance = 0.6 and w_rating = 0.4.
    - Lower score indicates better candidate (closer and higher-rated).

- ETA: estimate ETA using driver stored coordinates and pickup coordinates. If driver coordinates are missing we return a defensive default (e.g., 5 minutes). The simple ETA estimator in code:

    ETA_minutes = (distance_km / average_speed_km_per_minute) + buffer_minutes

    In code we use a placeholder: average 48 km/h (0.8 km/min) and a +2 minute buffer.

- Assignment: persist `Assignment` linking `RideRequest` and `Driver`, set `rideRequest.status = "ASSIGNED"`, set `driver.available = false`, and save both.

- Event: publish a small event to Kafka (`driver-assigned`) containing { tripId, driverId, eta }.

3) Fallbacks and edge cases
- No drivers found in Redis: set ride status to `NO_DRIVERS`, persist it and return a 409 Conflict with message "No drivers nearby".
- Redis down: the idempotency check is wrapped in try/catch — we fall back and proceed without Redis idempotency, but matching (which relies on Redis) will likely find no candidates; the request will still be saved. A healthy system should have Redis available.
- Driver accepted/declined: if driver declines, we mark assignment as declined and re-run the matching logic to find the next best candidate (this is handled by a re-match routine; the current app logs and returns a decline; a production system should re-queue for the next best candidate).
- Concurrency: assignment saves and driver availability updates should ideally occur in a transaction or using optimistic locking so two simultaneous matches don't assign the same driver. The current simple implementation saves assignment then sets driver.available = false; consider adding DB-level locking or idempotency for production.

4) Pseudo-code

```
# Inputs: pickupLat, pickupLong, tier
candidates = redis.geoSearch("driver:locations", center=(pickupLong, pickupLat), radiusKm=5, withDistance=True)

detailedCandidates = []
for (driverId, distanceKm) in candidates:
    driver = driverRepo.findById(driverId)
    if driver is null: continue
    if not driver.available: continue
    if tier specified and driver.vehicleTier != tier: continue
    score = distanceKm * 0.6 + (5.0 - driver.rating) * 0.4
    eta = estimateEta(driver.currentLat, driver.currentLong, pickupLat, pickupLong)
    detailedCandidates.add({driver, distanceKm, score, eta})

if detailedCandidates.empty():
    mark ride.status = 'NO_DRIVERS'
    return 409 No drivers

best = min(detailedCandidates, key=lambda c: c.score)
assignment = new Assignment(rideRequest=ride, driver=best.driver, estimatedMinutes=best.eta)
assignmentRepo.save(assignment)
best.driver.available = false
driverRepo.save(best.driver)
rideRequest.status = 'ASSIGNED'
rideRequestRepo.save(rideRequest)
kafka.send('driver-assigned', {"tripId": rideRequest.id, "driverId": best.driver.id, "eta": best.eta})
return DispatchResponseDTO(tripId=rideRequest.id, driverId=best.driver.id, etaMinutes=best.eta, status='ASSIGNED')
```

5) Redis commands examples (manual)
- Add a driver location:
```
# GEOADD key longitude latitude member
GEOADD driver:locations 77.5946 12.9716 550e8400-e29b-41d4-a716-446655440001
```
- Find drivers within 5 km of pickup (example with GEORADIUS):
```
GEORADIUS driver:locations 77.5946 12.9716 5 km WITHDIST
```
- (Newer Redis versions) GEOSEARCH example:
```
GEOSEARCH driver:locations FROMLONLAT 77.5946 12.9716 BYRADIUS 5 km WITHDIST
```

6) Complexity and tuning
- Redis geo search is O(log N) on the index for the search; the heavy cost is loading driver entities from the DB for the returned candidates. To optimize at scale:
  - Keep driver metadata needed for filtering (available flag, tier, rating) in Redis as a hash so the matching service can avoid many DB lookups.
  - Use batching to fetch many driver rows in a single DB call (e.g., `findAllById(...)`).
  - Increase the select radius gradually if no candidates found (graceful expansion) or use a nearest-k search.

7) Safety & Production suggestions
- Use DB transactions or optimistic locking when creating assignments and flipping driver availability.
- Use a small circuit-breaker/retry logic around Redis/Kafka connections.
- Add metrics for match latency, assignment success rate, and idempotency key hits.

---

## How Kafka is used
- Topics (examples used in code):
  - `driver-assigned` — published when a driver is assigned to a ride
  - `driver-location-updated` — (optional) published on each driver location update
- Producers: The Dispatch service publishes assignment and other domain events.
- Consumers: Downstream systems may be configured to consume and process events (notifications, analytics, or long-term persistence).
- Guarantees: Kafka provides durability and the ability to replay events for consumers that need eventual consistency.

Best practices in this project:
- Keep event payloads small (IDs + minimal metadata) and enrich consumers by joining with DB if needed.
- Use separate topics for high-volume updates (e.g., driver-location-updated) vs lower-volume important events (driver-assigned).

---

## How Redis is used
- Geo indexing: Driver locations are stored using Redis GEO commands under the key `driver:locations`. This allows fast radius searches by (lon, lat).
- Idempotency keys: Each ride request includes a `requestId`. The API stores `req:<requestId>` in Redis with a short TTL (e.g., 5 minutes) to avoid duplicate processing of retried requests.
- Notes: Redis is not used for persistent ride history. It is the low-latency tactical store for real-time matching.

---

## API Endpoints (concise)

Base path: `/api/dispatch`

1) Driver sends location
- POST /driver/location
- Body (JSON): { "driverId": "<uuid>", "lat": <number>, "lon": <number> }
- Response 200: { driverId, status: "UPDATED", message, receivedAt, lat, lon }

2) Driver accepts ride
- POST /ride/{rideId}/accept
- Body: { "driverId": "<uuid>" }
- Response 200: { rideId, status: "ACCEPTED", message, timestamp }

3) Driver declines ride
- POST /ride/{rideId}/decline
- Body: { "driverId": "<uuid>", "reason": "..." }
- Response 200: { rideId, status: "DECLINED", message, timestamp }

4) Driver updates trip state
- PATCH /ride/{rideId}/state
- Body: { "newState": "ARRIVED_AT_PICKUP", "notes": "..." }
- Response 200: { rideId, status: <newState>, message, timestamp }

5) Rider requests ride
- POST /ride/request
- Body: { "riderId": "<uuid>", "pickupLat": <num>, "pickupLong": <num>, "destLat": <num>, "destLong": <num>, "tier": "economy", "paymentMethod": "card", "requestId": "<string>" }
- Response 200 (assigned): { tripId, driverId, driverName, etaMinutes, status: "ASSIGNED" }
- Response 409 (no drivers): { error: "Conflict", message: "No drivers nearby" }

6) Rider cancels ride
- POST /ride/{rideId}/cancel
- Body: { "riderId": "<uuid>", "reason": "..." }
- Response 200: { rideId, status: "CANCELLED", message, timestamp }

7) Get ride status
- GET /ride/{rideId}/status
- Response 200: { rideId, currentState, driverId?, etaSeconds?, surgeApplied? }
- Response 404: { error: "Not Found", message: "Ride not found: <id>" }

---

## Dockerfile (how to run)
- The repository ships a `Dockerfile` (see project root) which is a simple container image for running the Spring Boot app. The image expects runtime configuration to come from environment variables (see `.env`).

Example run (after building):

```
docker build -t rideapp:latest .
docker run --env-file .env -p 8081:8081 rideapp:latest
```

---

## Environment configuration
- All runtime secrets and service endpoints are centralized in `.env` (DB credentials, Redis host/port, Kafka bootstrap servers, etc.).
- Example `.env` fields (present in repo):
  - DB_HOST, DB_PORT, DB_NAME, DB_USER, DB_PASS
  - REDIS_HOST, REDIS_PORT
  - KAFKA_BOOTSTRAP_SERVERS
  - SPRING_PROFILES_ACTIVE, SERVER_PORT

---

## Operational notes
- Ensure Redis is running and reachable before performing ride matching. If Redis is unavailable, idempotency is skipped (request proceeds) but geo matching will fail.
- Ensure PostgreSQL is configured and reachable; persistent writes use JPA (ddl-auto: update in `application.yaml` for dev only).
- Kafka is optional for basic flow but recommended for production notification & analytics.

---

## Developer notes
- Data model: `RideRequest` (rider), `Assignment` (driver assignment), `Driver` (driver profile/state).
- Transient stores: Redis for geo and idempotency.
- Events: Kafka for decoupled downstream processing.

---

## Where to look in the code
- Controller: `src/main/java/com/example/rideapp/controller/DispatchController.java`
- Service: `src/main/java/com/example/rideapp/service/DispatchService.java`
- Entities: `src/main/java/com/example/rideapp/entity/`
- Repositories: `src/main/java/com/example/rideapp/repository/`
- Redis config: `src/main/java/com/example/rideapp/service/RedisConfig.java`
- Kafka usage: look for `kafkaTemplate.send(...)` in `DispatchService`

---

