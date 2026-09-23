# Power Grid

A real-time power grid simulation: generation, consumption, distribution, and a wallet-based
economy, wired together as five independent Spring Boot services on a shared simulated clock and
watched live from a React dashboard.

There is no player turn. The grid runs continuously — one tick every 5 real seconds, forever —
and every service reacts to that tick (or to an event derived from it) the way a real utility's
systems react to the physical world: independently, asynchronously, and without waiting for
anyone to click anything.

## Contents

- [What it simulates](#what-it-simulates)
- [Quick start](#quick-start)
- [Architecture](#architecture)
- [The seven services](#the-seven-services)
- [Data flow, tick by tick](#data-flow-tick-by-tick)
- [Data model](#data-model)
- [Features](#features)
- [Tech stack](#tech-stack)
- [Repository layout](#repository-layout)
- [Working on one service directly (optional)](#working-on-one-service-directly-optional)
- [Design notes worth knowing before you read the code](#design-notes-worth-knowing-before-you-read-the-code)

## What it simulates

- **Producer** runs a fleet of power plants (thermal, solar, wind) and — for the two renewables —
  a real physical model: solar follows a daylight curve with cloud noise, wind follows a slow
  weather front plus fast gusts, both further scaled by a simulated season. Thermal follows grid
  frequency directly, via droop-governor physics, the way a real turbine does.
- **Customer** models zones (North, Central, East, ...) full of houses, factories and commercial
  buildings, each with its own demand profile (residential/commercial/industrial/government) that
  varies by time of day, day of week, and season.
- **Distributor** aggregates each zone's demand against what's actually being supplied, tracks a
  per-zone capacity cap, and flags when a zone is drawing over it.
- **Grid** is the one clock in the system. It computes system-wide frequency deviation from
  Producer's and Distributor's own published figures — an automatic control loop, not a human
  turning a dial — and exposes an explicit "load exceeded" state when demand outruns supply.
- **Billing** turns consumption into money: a per-tick charge per zone, a wallet per zone plus one
  shared "Grid Treasury" wallet that plants and storage are bought from, progressive (tax-bracket
  style) pricing that gets steeper as a plant grows, a recurring maintenance charge per active
  plant, partial refunds on decommission, and unlock thresholds that gate SOLAR/WIND behind
  cumulative energy sold.
- Storage (battery/hydrogen) sits in Producer, charging on a surplus and discharging into a
  deficit automatically, reacting to the same frequency signal the thermal governor does.
- The **frontend** is a live system diagram (not a form-heavy admin panel) — plants, storage,
  zones, customers and billing as nodes and edges that move as the simulation runs, plus a
  production-mix bar, a goals panel, and a live scoreboard.

## Quick start

Everything runs in Docker. One command, nothing installed on your machine beyond Docker itself,
no service started by hand, no separate terminal for the frontend:

```bash
docker compose up -d
```

That single command builds and starts all nine containers — Postgres, Kafka, Redis, pgAdmin, the
five Spring Boot services, and the frontend — and wires them together automatically. There is no
manual step after this: no `mvnw` to run per service, no `npm install`/`npm run dev` to start by
hand, nothing to configure. The first run takes a few minutes (Maven and npm fetch each service's
dependencies inside their containers); every run after that is seconds, since Docker caches those
layers.

```bash
docker compose down     # stop everything
docker compose up -d    # start it again — same one command, every time
```

| URL | What |
|---|---|
| http://localhost:5173 | The dashboard |
| http://localhost:8090 – 8094 | Producer, Customer, Grid, Distributor, Billing REST APIs |
| http://localhost:5050 | pgAdmin (`admin@email.com` / `adminpassword`) |
| localhost:5433 | Postgres (`powergrid` / `powergrid`) |
| localhost:6380 / localhost:9092 | Redis / Kafka, for poking around directly (Redis is on 6380, not 6379, so it never collides with a Redis installed on your machine) |

Every container carries a `mem_limit` and `restart: unless-stopped` — five JVMs left uncapped
will each size a heap off the *host's* visible memory rather than their fair share, which is
exactly how one of them tips a small VM over the edge and takes the whole stack down at once.
If you're on a constrained Docker/WSL2 memory allocation (check `docker info`), give it at least
4 GB.

## Architecture

Seven services, not five: the two Spring Boot services get their usual credit, but the database
layer and the frontend are each just as much a service in their own right — one owns all
persistence and schema decisions, the other owns the one thing a human actually looks at.

```
                          +----------------------+
                          |   Grid (8092)        |   the one clock -- publishes grid.tick
                          |   auto frequency ctrl |   every 5s, forever
                          +-----------+----------+
                       grid.tick      |      grid.tick
                 +--------------------+--------------------+
                 v                                          v
   +--------------------------+              +---------------------------+
   |  Producer (8090)          |              |  Customer (8091)           |
   |  plants + storage,         |              |  zones + demand,           |
   |  generation strategies     |              |  publishes customer.demand |
   +-------------+-------------+              +--------------+-------------+
                 | producer.output                            | customer.demand
                 | producer.storage-output                     | (2 independent consumer groups)
                 v                                +------------+------------+
        (consumed back by Grid                     v                         v
         to close the frequency          +-------------------+     +-------------------+
         control loop)                   |  Distributor (8093)|     |  Billing (8094)    |
                                          |  demand vs supply,  |     |  wallets, pricing,  |
                                          |  zone capacity      |     |  unlocks, ledger    |
                                          +-------------------+     +-------------------+

   +----------------------------- Database ------------------------------+
   |  Postgres: one schema per owning service (producer / distributor /  |
   |  billing) -- no foreign keys across or within schemas, by design.   |
   |  Redis: Customer's zone/unit config + live demand snapshot, and     |
   |  Grid's own tick-resume state.                                     |
   +-----------------------------------------------------------------------+

   +----------------------------- Frontend -------------------------------+
   |  React game-style map (plants -> Grid -> zones and their houses)     |
   |  over a details panel for whatever you click -- polling every        |
   |  service over its own REST API via TanStack Query.                   |
   +-----------------------------------------------------------------------+
```

**Why Distributor and Billing both react to `customer.demand` instead of one calling the other:**
Kafka consumer groups are independent by design. Customer publishes one event; Distributor and
Billing each join as their own group and see every event, with no coupling between them and no
risk that one being slow or down affects the other.

## The seven services

| # | Service | Port | Owns | Paradigm |
|---|---|---|---|---|
| 1 | **Producer** | 8090 | Plants, storage units, generation history | Spring MVC, hexagonal core |
| 2 | **Customer** | 8091 | Zones, consumer units, demand | Spring **WebFlux** (the one reactive service) |
| 3 | **Distributor** | 8093 | Zone capacity, distribution history | Spring MVC, hexagonal core |
| 4 | **Grid** | 8092 | The simulation clock, frequency control | Spring MVC, hexagonal core |
| 5 | **Billing** | 8094 | Wallets, pricing, ledger, unlocks | Spring MVC, hexagonal core |
| 6 | **Database** | — | Postgres (3 schemas) + Redis (4 keys) | Schema-per-service, no FKs anywhere |
| 7 | **Frontend** | 5173 | The live dashboard | React 19 + TanStack Query, plain DOM (no canvas library) |

Four of the five backend services share one shape: a thin `@KafkaListener`/`@RestController`
adapter layer around a framework-free core (plain Java classes, no Spring annotations, tested
with fakes rather than a broker). Customer is the exception — it's WebFlux end to end, because
its own read path (`GET /api/demand`, `GET /api/units`) is genuinely async I/O over Redis, not
just an adapter wrapping a synchronous core.

## Data flow, tick by tick

1. **Grid** ticks — publishes `grid.tick` (`tickNumber`, `frequencyDeviation`) to Kafka. One
   partition, so every consumer sees ticks in the same order they were issued.
2. **Producer** consumes it: each active plant's `GenerationStrategy` computes output for this
   tick (thermal follows the frequency deviation via droop physics; solar/wind follow their own
   deterministic weather+season curves, ignoring frequency entirely), writes one row to
   `generation_record`, and publishes `producer.output`. Storage does the same in parallel,
   charging or discharging based on the same frequency deviation, publishing
   `producer.storage-output`.
3. **Customer** also consumes `grid.tick`: recomputes every zone's demand from its units' demand
   profiles (time of day, day of week, season), overwrites its Redis snapshot, and publishes
   `customer.demand`.
4. **Grid** consumes `producer.output` + `producer.storage-output` (as supply) and
   `distributor.zone-balance` (as demand) to compute next tick's frequency deviation — the
   feedback loop that makes this "automatic control," not open-loop.
5. **Distributor** and **Billing** each independently consume `customer.demand`: Distributor
   writes `distribution_record` and checks each zone's capacity cap; Billing writes
   `billing_record` (idempotent on `(zone_id, tick_number)`, since Kafka delivery is
   at-least-once) and debits that zone's wallet.
6. Raw per-tick tables in all three Postgres schemas age out on a schedule (15 simulated days for
   Distributor/Billing, 24 real hours for Producer) and compact into one row per zone/plant per
   simulated day (or real minute, for Producer) — see the `*_daily_rollup` / `generation_rollup`
   tables.

## Data model

**Postgres** — one schema per owning service, zero foreign keys anywhere (verified: every table
stands alone, so retention jobs can truncate history in any order without touching config).

| Schema | Table | What | Growth |
|---|---|---|---|
| `producer` | `power_plant` | The live fleet | CRUD only |
| | `storage_unit` | Battery/hydrogen units | CRUD only |
| | `generation_record` | Raw per-tick output | +1/active plant/tick, 24h retention |
| | `generation_rollup` | Compacted history | +1/plant/minute once raw ages out |
| `distributor` | `zone_capacity` | Per-zone demand cap | CRUD only |
| | `distribution_record` | Raw per-tick demand/supply | +1/zone/tick, 15 sim-day retention |
| | `distribution_daily_rollup` | Compacted history | +1/zone/sim-day |
| `billing` | `wallet` | Current balance per zone + Grid Treasury | new row per new zone only |
| | `wallet_transaction` | Insert-only ledger | +1/zone/tick — **never trimmed** |
| | `billing_record` | Raw per-tick charge | +1/zone/tick, 15 sim-day retention |
| | `billing_daily_rollup` | Compacted history | +1/zone/sim-day |

**Redis** — Customer's entire store (it has no Postgres schema at all), plus one key of Grid's own:

| Key | Type | What |
|---|---|---|
| `customer:zones` | hash | One field per zone |
| `customer:units` | hash | One field per house/factory/commercial unit |
| `customer:demand:latest` | hash | Whole current-tick snapshot, overwritten every tick |
| `grid:clock` | hash | Grid's own tick/day/deviation, so a restart resumes instead of rewinding |

## Features

- **Plants**: thermal (droop-governed), solar (daylight curve + cloud noise + season), wind
  (weather front + gusts + season) — create, upgrade, decommission (partial refund), forecast
  (solar/wind only — thermal has no forecastable basis, since it follows frequency at the moment
  of the tick).
- **Storage**: battery and hydrogen, auto charge/discharge off grid frequency deviation, its own
  pricing separate from plant pricing.
- **Zones & customers**: residential/commercial/industrial/government demand profiles, each with
  its own hourly shape and weekend factor; day/night peak tracking per zone; per-zone capacity
  caps with overage billing.
- **Economy**: progressive (tax-bracket) plant pricing, recurring maintenance charges, plant
  decommission refunds, a shared Grid Treasury wallet, and tech-tree unlocks that gate SOLAR/WIND
  behind cumulative grid-wide kWh sold.
- **Money flow**: `GET /api/billing/flow` reports revenue per second by zone (a 60-second average
  of what was actually billed), each plant's running cost per second (its maintenance rate spread
  over the upkeep interval), and lifetime spend on the fleet by category. The dashboard shows it
  per zone, per plant and per building -- a building's figure is its zone's revenue split by its
  share of the zone's demand, since Billing records charges per zone, not per customer.
- **Grid**: automatic frequency control from live supply/demand, an explicit "load exceeded"
  failure state (not just a frequency number to interpret).
- **Dashboard**: a game-style map -- power plants and storage on the left, the Grid hub in the
  middle, and the city on the right, where each zone is a plot with its houses, shops and
  factories inside it (power lines animate while energy flows). A bottom panel shows the Grid
  overview by default (frequency, supply/demand, production mix, goals, scoreboard, service
  health); click any plant, storage unit, zone or building and it shows that thing's live
  details and actions instead (edit, decommission, add a building, forecast and output charts).

## Tech stack

| Layer | Choice |
|---|---|
| Backend | Java 25, Spring Boot 4.1 |
| Messaging | Apache Kafka (KRaft mode, no ZooKeeper) |
| Persistence | Postgres 18 (schema-per-service), Redis |
| Frontend | React 19, Vite 8, TanStack Query, Tailwind CSS 4 (plain DOM + inline SVG, no canvas/graph library) |
| Containers | Docker Compose — multi-stage builds for each Java service, a dev-mode container for the frontend |

## Repository layout

```
Power-Grid/
|-- Producer/       # plants, storage, generation strategies       (:8090)
|-- Customer/       # zones, units, demand                         (:8091, WebFlux)
|-- Grid/           # the simulation clock, frequency control      (:8092)
|-- Distributor/    # zone capacity, demand-vs-supply               (:8093)
|-- Billing/        # wallets, pricing, ledger, unlocks             (:8094)
|-- Frontend/       # the dashboard                                 (:5173)
|   |-- src/Dashboard.jsx      # the whole UI -- one large, deliberately un-split file
|   `-- src/lib/               # one {service}Api.js + {service}Queries.js pair per backend
|-- docker-compose.yml
`-- README.md
```

Each Java service follows the same internal shape: `api/` (controllers + request/response DTOs),
`model/` (JPA entities + repositories), a domain package named after the service's core concern
(`generation/`, `billing/`, `distribution/`, `simulation/`), `event/` (local wire-copies of every
Kafka event this service consumes), `kafka/` (listeners/publishers), `config/` (Kafka wiring), and
`history/` (the raw→rollup retention job, where one exists).

## Working on one service directly (optional)

`docker compose up -d` is the only thing you need to run the whole app — nothing below this
line is required. It's here only for when you're actively changing one service's Java/JS and want
a faster edit loop than rebuilding that service's container on every change.

`docker-compose.yml`'s environment overrides are additive, not a replacement for each service's
own `application.yml`, so a service run this way still talks to the same Postgres/Kafka/Redis:

```bash
docker compose up -d postgres redis kafka   # just the infra, none of the app services

cd Producer && ./mvnw spring-boot:run        # or Customer/Grid/Distributor/Billing
cd Frontend && npm install && npm run dev
```

Each service's `./mvnw test` runs offline (no broker, no database) — the framework-free cores are
tested with fakes, and only the thin adapter layer touches Spring/Kafka/JPA directly.

## Design notes worth knowing before you read the code

- **Simulated time is the only clock.** 1 tick = 5 real seconds = 5 simulated minutes; 288 ticks
  = 1 simulated day. Every retention window, every seasonal boundary, every rate is counted in
  ticks or simulated days, not wall-clock time — a rollup job that used wall-clock time would
  drift out of sync with what the dashboard displays.
- **Kafka delivery is at-least-once**, so the two places a redelivered event could double-apply
  money (`billing_record`, `wallet_transaction`) are guarded by a unique `(zone_id, tick_number)`
  constraint checked before anything is written, inside one transaction.
- **Money is a `double`, not a `BigDecimal`.** Every physical quantity in this codebase (MW, kW,
  Hz) already is one; this is a simulated currency with no real settlement requirement, so it
  follows the same convention rather than being the one exception.
- **Cross-service event contracts are structural, not shared code.** Every consumer keeps its own
  local copy of an event class (e.g. `Billing.event.ZoneCapacityEvent` mirrors
  `Distributor.event.ZoneCapacityEvent` field-for-field) rather than depending on a shared jar —
  deliberately, so no service's deploy is ever blocked on another's.
- **`TimeZone.setDefault(UTC)`** is pinned in every service's `main()`. Windows resolves the JVM's
  default timezone to `Asia/Calcutta`, which the Postgres JDBC driver's handshake rejects outright
  on some driver/server combinations — this sidesteps it rather than depending on host
  configuration.
