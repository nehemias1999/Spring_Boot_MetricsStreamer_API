# MetricsStreamer API

A reactive microservices system built with **Spring WebFlux** and **Project Reactor** that simulates real-time server telemetry monitoring. The system continuously generates server performance metrics, streams them via Server-Sent Events (SSE), applies configurable alert rules, and re-exposes qualifying alerts to connected clients — all without blocking a single thread.

---

## Table of Contents

- [Purpose](#purpose)
- [Architecture Overview](#architecture-overview)
- [Microservices](#microservices)
- [Tech Stack](#tech-stack)
- [Project Structure](#project-structure)
- [How It Works](#how-it-works)
- [Data Flow](#data-flow)
- [Configuration](#configuration)
- [Running the Project](#running-the-project)
- [API Endpoints](#api-endpoints)
- [Testing](#testing)

---

## Purpose

MetricsStreamer API demonstrates how to build a **reactive, event-driven microservices pipeline** using modern Java and the Spring ecosystem. The key goals are:

- Stream infinite data between services without blocking threads
- Apply business rules reactively using functional operators (`.filter()`, `.map()`, `.retryWhen()`)
- Enforce clean architectural boundaries using **Hexagonal Architecture** with **Vertical Slicing**
- Show resilience patterns: retry with exponential backoff, element timeout, and configurable thresholds
- Provide observability out of the box: health checks, metrics, distributed tracing, and structured logging

---

## Architecture Overview

The system follows **Hexagonal Architecture** (Ports & Adapters) organized with **Vertical Slicing** — code is grouped by domain feature first, then by layer inside that feature.

```
feature/
├── domain/              → Pure business entities (no framework dependencies)
├── application/
│   ├── port/in/         → Inbound ports  (interfaces the adapters call INTO the core)
│   ├── port/out/        → Outbound ports (interfaces the core calls OUT to infrastructure)
│   └── service/         → Use-case implementations (business logic lives here)
└── infrastructure/
    ├── config/          → Spring configuration beans (@Configuration)
    ├── client/          → Outbound adapters (WebClient HTTP clients)
    └── web/             → Inbound adapters  (REST controllers)
```

**Dependency rule:** Infrastructure depends on Application ports. Application never depends on Infrastructure. This means the business logic can be unit-tested without starting a Spring context.

---

## Microservices

### `telemetry-producer` — Port `8080`

Simulates a monitored server. Generates randomised CPU and RAM usage metrics every second and streams them as an infinite SSE feed to any connected consumer.

### `metrics-dashboard` — Port `8081`

Consumes the producer's stream via a reactive WebClient. Applies configurable alert threshold rules and re-exposes only the qualifying high-usage events as a new SSE feed for downstream clients (browsers, monitoring tools, other services).

---

## Tech Stack

| Category | Technology |
|---|---|
| Language | Java 21 |
| Framework | Spring Boot 4.0.3 |
| Reactive Web | Spring WebFlux |
| Reactive Streams | Project Reactor (`Flux`, `Mono`) |
| HTTP Server | Netty (embedded, non-blocking) |
| HTTP Client | WebClient (reactive, non-blocking) |
| Streaming Protocol | Server-Sent Events (SSE) |
| Serialisation | Jackson (JSON) |
| Validation | Jakarta Bean Validation |
| Observability | Spring Boot Actuator + Micrometer |
| Distributed Tracing | Micrometer Tracing (Brave bridge) |
| Build Tool | Apache Maven 3.9 (Maven Wrapper) |
| Boilerplate Reduction | Lombok (`@Slf4j`, `@ConfigurationProperties`) |
| Testing | JUnit 5, Mockito, StepVerifier (reactor-test), MockWebServer (OkHttp) |

---

## Project Structure

```
MetricsStreamer_API/
├── telemetry-producer/
│   └── src/main/java/.../
│       └── metrics/
│           ├── domain/
│           │   └── ServerMetric.java             ← immutable record (serverId, cpuUsage, ramUsage, timestamp)
│           ├── application/
│           │   ├── port/in/
│           │   │   └── ITelemetryStreamUseCase.java
│           │   └── service/
│           │       └── TelemetryService.java      ← Flux.interval() + .map() to generate metrics
│           └── infrastructure/
│               └── web/
│                   └── TelemetryController.java   ← GET /api/v1/telemetry/stream (SSE)
│
└── metrics-dashboard/
    └── src/main/java/.../
        └── dashboard/
            ├── domain/
            │   └── ServerMetricDto.java           ← mirrors producer JSON exactly
            ├── application/
            │   ├── port/in/
            │   │   └── IAlertStreamUseCase.java
            │   ├── port/out/
            │   │   └── ITelemetryProducerClient.java
            │   └── service/
            │       └── AlertService.java          ← .filter(cpu > threshold OR ram > threshold)
            └── infrastructure/
                ├── config/
                │   ├── AlertProperties.java       ← @ConfigurationProperties (thresholds)
                │   └── WebClientConfig.java       ← WebClient bean pointing to :8080
                ├── client/
                │   └── TelemetryProducerWebClient.java  ← SSE consumer + retry + timeout
                └── web/
                    └── DashboardController.java   ← GET /api/v1/dashboard/alerts (SSE)
```

---

## How It Works

### `telemetry-producer`

```
Flux.interval(1 second)
    └── .map(tick → ServerMetric { serverId, cpuUsage (random), ramUsage (random), timestamp })
                │
                ▼
        GET /api/v1/telemetry/stream
        Content-Type: text/event-stream
        (one JSON event per second, connection stays open)
```

`TelemetryService` creates a cold, infinite reactive stream. Each subscriber gets their own independent stream. The controller simply returns the `Flux` — Spring WebFlux and Netty handle serialisation and SSE framing automatically.

### `metrics-dashboard`

```
WebClient → GET /api/v1/telemetry/stream   (producer at :8080)
                │
                │  Flux<ServerMetricDto>   (infinite, raw stream)
                ▼
            .timeout(10s)                  (error if no event received within 10 seconds)
            .retryWhen(backoff)            (reconnect on failure: 2s → 4s → 8s, max 30s, jitter 50%)
                │
                ▼
            .filter(cpu > cpuThreshold OR ram > ramThreshold)
                │
                │  Flux<ServerMetricDto>   (only alert events)
                ▼
        GET /api/v1/dashboard/alerts
        Content-Type: text/event-stream
        (only high-usage events forwarded to client)
```

When a client disconnects from the dashboard, the cancellation signal propagates back through the reactive chain to the producer connection — no resources are held unnecessarily.

---

## Data Flow

```
┌──────────────────────────────────────────────────────────────────────┐
│  [Simulated Server]                                                  │
│       │  random metrics every 1 second                               │
│       ▼                                                              │
│  ┌─────────────────────┐                                             │
│  │  telemetry-producer │  :8080                                      │
│  │  ─────────────────  │                                             │
│  │  TelemetryService   │  Flux.interval + .map                       │
│  │  TelemetryController│  GET /api/v1/telemetry/stream (SSE)         │
│  └──────────┬──────────┘                                             │
│             │  ALL metrics (JSON, 1/sec)                             │
│             ▼                                                        │
│  ┌──────────────────────────────┐                                    │
│  │      metrics-dashboard       │  :8081                             │
│  │  ────────────────────────── │                                     │
│  │  TelemetryProducerWebClient  │  WebClient + timeout + retry       │
│  │  AlertService                │  .filter(cpu > 80 OR ram > 85)     │
│  │  DashboardController         │  GET /api/v1/dashboard/alerts (SSE)│
│  └──────────────┬───────────────┘                                    │
│                 │  ALERT metrics only                                 │
│                 ▼                                                     │
│     [curl / browser / monitoring tool]                               │
└──────────────────────────────────────────────────────────────────────┘
```

---

## Configuration

### `telemetry-producer` — `application.yaml`

| Property | Default | Description |
|---|---|---|
| `server.port` | `8080` | HTTP port |
| `spring.profiles.active` | `dev` | Active profile (`dev` / `prod`) |
| `management.tracing.sampling.probability` | `1.0` | 100% trace sampling |

### `metrics-dashboard` — `application.yaml`

| Property | Default | Description |
|---|---|---|
| `server.port` | `8081` | HTTP port |
| `telemetry.producer.base-url` | `http://localhost:8080` | Producer URL (override per environment) |
| `dashboard.alerts.cpu-threshold` | `80.0` | CPU % above which an alert is triggered |
| `dashboard.alerts.ram-threshold` | `85.0` | RAM % above which an alert is triggered |
| `spring.profiles.active` | `dev` | Active profile (`dev` / `prod`) |

### Environment Profiles

| Profile | Log level | Actuator endpoints |
|---|---|---|
| `dev` | `DEBUG` | `health`, `info`, `metrics`, `loggers` |
| `prod` | `INFO` / `WARN` | `health`, `info`, `metrics` |

Override any property at runtime with environment variables, e.g.:
```bash
TELEMETRY_PRODUCER_BASE_URL=http://producer-host:8080
DASHBOARD_ALERTS_CPU_THRESHOLD=75.0
```

---

## Running the Project

### Prerequisites

- Java 21+
- No other tools required — Maven Wrapper is included in each project

### Start `telemetry-producer`

```bash
cd telemetry-producer
./mvnw spring-boot:run          # Linux / macOS
mvnw.cmd spring-boot:run        # Windows
```

### Start `metrics-dashboard`

```bash
cd metrics-dashboard
./mvnw spring-boot:run          # Linux / macOS
mvnw.cmd spring-boot:run        # Windows
```

### Verify the pipeline

```bash
# See all metrics (1 per second)
curl http://localhost:8080/api/v1/telemetry/stream

# See only alerts (cpu > 80% or ram > 85%)
curl http://localhost:8081/api/v1/dashboard/alerts
```

Expected output (one line per second, connection stays open):
```
data:{"serverId":"server-01","cpuUsage":91.34,"ramUsage":47.82,"timestamp":"2024-06-01T14:23:05.123Z"}

data:{"serverId":"server-01","cpuUsage":88.67,"ramUsage":90.11,"timestamp":"2024-06-01T14:23:06.124Z"}
```

---

## API Endpoints

### `telemetry-producer` — `:8080`

| Method | Path | Content-Type | Description |
|---|---|---|---|
| `GET` | `/api/v1/telemetry/stream` | `text/event-stream` | Infinite SSE stream of all server metrics |
| `GET` | `/actuator/health` | `application/json` | Service health status |
| `GET` | `/actuator/info` | `application/json` | Application metadata |
| `GET` | `/actuator/metrics` | `application/json` | JVM and server metrics |

### `metrics-dashboard` — `:8081`

| Method | Path | Content-Type | Description |
|---|---|---|---|
| `GET` | `/api/v1/dashboard/alerts` | `text/event-stream` | Filtered SSE stream — only high-usage alert events |
| `GET` | `/actuator/health` | `application/json` | Service health status |
| `GET` | `/actuator/info` | `application/json` | Application metadata |
| `GET` | `/actuator/metrics` | `application/json` | JVM and server metrics |

---

## Testing

Each microservice has three layers of tests:

| Layer | Tool | What it validates |
|---|---|---|
| **Unit** | JUnit 5 + Mockito + `StepVerifier` | Business logic in isolation (no Spring context) |
| **Integration (web)** | `@SpringBootTest` + `WebTestClient` | Full HTTP pipeline with mocked service ports |
| **Integration (HTTP client)** | MockWebServer (OkHttp) | Real HTTP + SSE deserialisation + retry behaviour |

```bash
# Run all tests
./mvnw test

# Run a single test class
./mvnw test -Dtest=AlertServiceTest

# Run a single test method
./mvnw test -Dtest=AlertServiceTest#streamAlerts_cpuFilter_blocksAtAndBelowThreshold
```

### Test count

| Project | Tests |
|---|---|
| `telemetry-producer` | 8 |
| `metrics-dashboard` | 15 |
| **Total** | **23** |
