# Running Travel Microservices in IntelliJ IDEA — Step-by-Step Guide

This guide walks you from zero to a fully running stack in IntelliJ. By the end you'll be able to set breakpoints in any of the four services, hit the `/api/v1/trips` endpoint, and step through the entire booking flow including the serverless cancellation function.

---

## Table of Contents

1. [Prerequisites](#1-prerequisites)
2. [Open the Project](#2-open-the-project-in-intellij)
3. [Configure the SDK and Plugins](#3-configure-the-sdk-and-plugins)
4. [Enable Lombok](#4-enable-lombok-critical)
5. [Import & Build with Maven](#5-import--build-with-maven)
6. [Create Run Configurations](#6-create-run-configurations-for-each-service)
7. [Start the Services in Order](#7-start-the-services-in-the-right-order)
8. [Verify Each Service is Up](#8-verify-each-service-is-up)
9. [Test the Booking Flow](#9-test-the-end-to-end-booking-flow)
10. [Debugging](#10-debugging-step-through-the-flow)
11. [Common Issues](#11-common-issues--fixes)
12. [Optional: Run as a Compound Configuration](#12-optional-run-everything-with-one-click)

---

## 1. Prerequisites

Install these before opening IntelliJ:

| Tool | Version | Where to get it |
|---|---|---|
| **IntelliJ IDEA** | 2023.3+ (Community Edition is fine) | https://www.jetbrains.com/idea/download/ |
| **JDK 17** | Any vendor — Temurin, Oracle, Amazon Corretto | https://adoptium.net/ |
| **Maven** | Bundled with IntelliJ — no separate install needed | — |

Verify Java from a terminal:
```bash
java -version
# should print "17.x.x" or higher
```

> **Why JDK 17?** Spring Boot 3.x requires Java 17 minimum. The project's POMs explicitly target it.

---

## 2. Open the Project in IntelliJ

1. **Unzip** `travel-microservices.zip` somewhere stable, e.g. `~/projects/travel-microservices`.
2. Launch IntelliJ → **File → Open** (or *Open* from the welcome screen).
3. Navigate to the **`travel-microservices`** folder (the top-level one containing `pom.xml`, `Jenkinsfile`, the four service folders, and `k8s/`).
4. Click **OK**.
5. When IntelliJ asks "*Trust and Open Maven Project?*" → click **Trust Project**.

IntelliJ now reads the root `pom.xml` and detects the four Maven modules. You'll see this in the Project tool window (left side):

```
travel-microservices
├── cancellation-function
├── flight-service
├── hotel-service
├── travel-service
├── k8s
├── scripts
├── docs
└── docker-compose.yml
```

---

## 3. Configure the SDK and Plugins

### 3.1 Set Project SDK to JDK 17

1. **File → Project Structure** (or `Ctrl+Alt+Shift+S` / `⌘;` on macOS).
2. Under **Project Settings → Project**:
   - **SDK** → pick or add JDK 17. If it's not listed, click **Add SDK → JDK** and point at your JDK 17 home folder.
   - **Language level** → **17 - Sealed types, always-strict floating-point semantics**.
3. Under **Project Settings → Modules**:
   - Select each of the four modules (`hotel-service`, `flight-service`, `travel-service`, `cancellation-function`) and confirm **Language level** is **17**.
4. Click **Apply → OK**.

### 3.2 Verify Maven plugin is enabled

1. **File → Settings → Plugins** (or `⌘,` on macOS).
2. Under **Installed**, confirm **Maven** is enabled. (It is by default — only check if Maven menus don't appear.)

---

## 4. Enable Lombok (CRITICAL)

The entire codebase uses Lombok annotations (`@Data`, `@Builder`, `@RequiredArgsConstructor`, `@Slf4j`, etc.). Without Lombok set up properly, **every file will show red errors**.

### 4.1 Install the Lombok plugin (if not already installed)

1. **File → Settings → Plugins**.
2. **Marketplace** tab → search **"Lombok"**.
3. The official **Lombok** plugin (by JetBrains) is bundled with recent IntelliJ versions — confirm it shows **Installed**. If not, install it and **restart IntelliJ**.

### 4.2 Enable annotation processing

This is the step everyone forgets and then wonders why nothing compiles.

1. **File → Settings → Build, Execution, Deployment → Compiler → Annotation Processors**.
2. Check **✅ Enable annotation processing**.
3. Leave **Obtain processors from project classpath** selected (this is the default and what Lombok needs).
4. Click **Apply → OK**.

> **Quick check**: open any DTO file like `travel-service/src/main/java/com/travel/dto/TripRequest.java`. The `@Data` annotation should NOT be underlined red, and methods like `getCustomerName()` should be reachable via Ctrl-click. If they're red, annotation processing is still off — repeat 4.2.

---

## 5. Import & Build with Maven

### 5.1 Trigger a Maven reload

1. Open the **Maven tool window** (right sidebar, or **View → Tool Windows → Maven**).
2. Click the **🔄 Reload All Maven Projects** button (top-left of the Maven panel).
3. IntelliJ downloads ~300MB of Spring Boot, Spring Cloud, and supporting dependencies the first time. Watch the progress bar at the bottom.

### 5.2 Build the project

Once Maven sync finishes:

1. **Build → Build Project** (`Ctrl+F9` / `⌘F9`).
2. The Build tool window appears. Wait for "Build completed successfully."
3. If you see "*X errors found*" — check:
   - Lombok plugin + annotation processing both enabled (Section 4)
   - JDK 17 is set for the project AND each module (Section 3.1)
   - Maven sync actually completed (Section 5.1)

---

## 6. Create Run Configurations for Each Service

Each microservice has its own `*Application.java` class with a `main` method. IntelliJ can usually auto-detect these — but creating explicit configurations gives you cleaner control over names, ports, and environment variables.

### 6.1 Hotel Service

1. **Run → Edit Configurations…**
2. Click **+** (top-left) → **Spring Boot**.
3. Fill in:
   - **Name**: `Hotel Service`
   - **Main class**: `com.hotel.HotelServiceApplication` (click the folder icon → search)
   - **Module**: `hotel-service`
   - **JRE**: 17
   - **Active profiles**: *(leave blank)*
   - **Working directory**: `$MODULE_DIR$` (default)
4. Click **Apply**.

### 6.2 Flight Service

Repeat the steps:

- **Name**: `Flight Service`
- **Main class**: `com.flight.FlightServiceApplication`
- **Module**: `flight-service`

### 6.3 Travel Service

This one needs environment variables so it knows where to find the other services:

- **Name**: `Travel Service`
- **Main class**: `com.travel.TravelServiceApplication`
- **Module**: `travel-service`
- **Environment variables** (click the icon next to the field, then add each):

  | Name | Value |
  |---|---|
  | `HOTEL_SERVICE_URL` | `http://localhost:8081` |
  | `FLIGHT_SERVICE_URL` | `http://localhost:8082` |
  | `CANCELLATION_FUNCTION_URL` | `http://localhost:8083/cancelTrip` |
  | `FUNCTION_DISPATCH_MODE` | `http` |

  > These have defaults in `application.yml`, so the env vars are technically optional for local dev — but setting them explicitly avoids confusion when you start changing things.

### 6.4 Cancellation Function

- **Name**: `Cancellation Function`
- **Main class**: `com.cancellation.CancellationFunctionApplication`
- **Module**: `cancellation-function`
- **Environment variables**:

  | Name | Value |
  |---|---|
  | `HOTEL_SERVICE_URL` | `http://localhost:8081` |
  | `FLIGHT_SERVICE_URL` | `http://localhost:8082` |

---

## 7. Start the Services in the Right Order

**Order matters** because the travel-service uses Feign to call the others on startup health checks. Start them like this:

| Step | Service | Port | Why first |
|---|---|---|---|
| 1 | **Hotel Service** | 8081 | Independent — no dependencies |
| 2 | **Flight Service** | 8082 | Independent — no dependencies |
| 3 | **Cancellation Function** | 8083 | Called by travel-service when cancellations happen |
| 4 | **Travel Service** | 8080 | Calls hotel-service + flight-service + cancellation-function |

**To start each one:**

1. In the top-right of IntelliJ, pick the run configuration from the dropdown.
2. Click the green ▶ **Run** button (or `Shift+F10`).
3. Wait for the Spring Boot banner and the line: `Started <App> in <X> seconds`.
4. Switch to the next run configuration in the dropdown and repeat.

After all four are running, you'll have four tabs in the **Run** tool window at the bottom — one per service. Click any tab to see that service's logs.

---

## 8. Verify Each Service is Up

Open a terminal (IntelliJ's built-in terminal works: **View → Tool Windows → Terminal**) and curl each service's health endpoint:

```bash
curl http://localhost:8081/actuator/health   # hotel-service
curl http://localhost:8082/actuator/health   # flight-service
curl http://localhost:8083/actuator/health   # cancellation-function
curl http://localhost:8080/actuator/health   # travel-service
```

Each should return:
```json
{"status":"UP"}
```

You can also pop open the H2 web console for each service to see the seeded data:

| Service | URL | JDBC URL | User |
|---|---|---|---|
| Hotel | http://localhost:8081/h2-console | `jdbc:h2:mem:hoteldb` | `sa` (blank password) |
| Flight | http://localhost:8082/h2-console | `jdbc:h2:mem:flightdb` | `sa` (blank password) |
| Travel | http://localhost:8080/h2-console | `jdbc:h2:mem:traveldb` | `sa` (blank password) |

Run `SELECT * FROM HOTELS;` in the hotel console to see the 8 seeded hotels across 5 cities.

---

## 9. Test the End-to-End Booking Flow

### 9.1 Scenario 1 — Happy path

Pick a `startDate` ~30 days in the future (the validation requires future dates). In the IntelliJ terminal:

```bash
curl -X POST http://localhost:8080/api/v1/trips \
    -H "Content-Type: application/json" \
    -d '{
        "customerName":"Alice",
        "customerEmail":"alice@example.com",
        "originCity":"New York",
        "destinationCity":"Paris",
        "startDate":"2026-06-15",
        "endDate":"2026-06-20",
        "numberOfTravelers":2,
        "numberOfRooms":1
    }'
```

Expected: `201 Created` with status `CONFIRMED` and both `hotelBookingReference` + `flightBookingReference` populated.

Watch the four Run tabs — you'll see:
- **Travel Service** logs: "Starting trip booking workflow", "Hotel booking OK", "Flight booking OK", "Trip CONFIRMED"
- **Hotel Service** logs: "Processing hotel booking", "Hotel booking confirmed"
- **Flight Service** logs: "Processing flight booking", "Flight booking confirmed"

### 9.2 Scenario 2 — Hotel unavailable

```bash
curl -X POST http://localhost:8080/api/v1/trips \
    -H "Content-Type: application/json" \
    -d '{
        "customerName":"Bob",
        "customerEmail":"bob@example.com",
        "originCity":"New York",
        "destinationCity":"Atlantis",
        "startDate":"2026-06-15",
        "endDate":"2026-06-20",
        "numberOfTravelers":1,
        "numberOfRooms":1
    }'
```

Expected: `422 Unprocessable Entity` with `status: "FAILED"`. **The flight-service logs will show no activity** — confirming the flight booking was not even attempted.

### 9.3 Cancellation — triggers the serverless function

Grab the `tripReference` from Scenario 1's response (e.g. `TRIP-A1B2C3D4`):

```bash
curl -X DELETE http://localhost:8080/api/v1/trips/TRIP-A1B2C3D4
```

Expected: `200 OK` with status `CANCELLED`.

Now watch the **Cancellation Function** tab in IntelliJ:
```
=== Serverless cancellation triggered === trip=TRIP-A1B2C3D4 ...
Calling DELETE http://localhost:8081/api/v1/hotels/bookings/HB-...
Hotel booking HB-... cancelled successfully
Calling DELETE http://localhost:8082/api/v1/flights/bookings/FB-...
Flight booking FB-... cancelled successfully
Cancellation processed: ...
```

This is the **serverless function being invoked automatically** by the travel-service — exactly what the spec asks for.

---

## 10. Debugging: Step Through the Flow

The whole point of using IntelliJ is breakpoint debugging. Here's how to step through the entire booking workflow:

### 10.1 Set breakpoints

Open these files and click the gutter (left of the line numbers) to set breakpoints:

| File | Line to break on | What you'll see |
|---|---|---|
| `travel-service/.../TripBookingService.java` | First line of `bookTrip()` method | Trip request arrives |
| `travel-service/.../TripBookingService.java` | The `hotelServiceClient.bookHotel(…)` call | About to call hotel-service |
| `hotel-service/.../HotelBookingService.java` | First line of `bookHotel()` | Hotel-service receives the call |
| `travel-service/.../TripBookingService.java` | The `flightServiceClient.bookFlight(…)` call | About to call flight-service |
| `flight-service/.../FlightBookingService.java` | First line of `bookFlight()` | Flight-service receives the call |
| `cancellation-function/.../CancellationFunction.java` | Inside the lambda — first `log.info` line | Function triggered after cancellation |

### 10.2 Stop and restart in debug mode

For each service, instead of clicking ▶ **Run**, click 🐞 **Debug** (the bug icon next to the green play button, or `Shift+F9`).

Now run `curl` to POST a trip — IntelliJ will pause at your first breakpoint. Use:
- **F8** — Step over
- **F7** — Step into
- **F9** — Resume to next breakpoint
- **Alt+F8** — Evaluate expression on the fly

You can switch between Run tool window tabs to follow execution across services. When the travel-service does `hotelServiceClient.bookHotel(...)`, execution will hop into the **Hotel Service** debugger pane.

---

## 11. Common Issues & Fixes

| Symptom | Likely cause | Fix |
|---|---|---|
| Every `@Data`, `@Builder`, `@Slf4j` is red and won't compile | Lombok plugin not enabled or annotation processing off | Section 4 — both have to be done |
| `java.lang.UnsupportedClassVersionError: ... class file version 61.0` | IntelliJ is using JDK 8/11 | Section 3.1 — set JDK 17 at *Project* AND *Modules* level |
| `Port 8080 already in use` | Another app (or another instance from a previous run) is on that port | `lsof -i :8080` to find it, kill it; or change `TRAVEL_SERVICE_PORT` env var in the run configuration |
| Maven sync says `Cannot resolve org.springframework.boot:...` | Network/firewall, or corporate proxy | **File → Settings → Build Tools → Maven → Importing** — point to your settings.xml with corporate mirror; or run `mvn -B clean install` from terminal to inspect the real error |
| `Connection refused` when travel-service calls hotel-service | Hotel service not started yet, or started on a different port | Confirm hotel-service Run tab shows "Started"; confirm `HOTEL_SERVICE_URL` in travel-service run config matches the actual port |
| `Cannot find symbol: builder()` errors after first build | Lombok generated code was lost on rebuild | **Build → Rebuild Project**; if it persists, re-tick annotation processing in Section 4.2 |
| `Travel-service starts but logs Feign 404s on cancellation` | Cancellation function not running, or `CANCELLATION_FUNCTION_URL` is wrong | Start cancellation-function first; confirm URL ends with `/cancelTrip` |
| Cancellation succeeds but bookings stay in CONFIRMED | Cancellation function isn't reaching hotel/flight services | Check cancellation-function logs — usually it's `HOTEL_SERVICE_URL` pointing somewhere wrong inside that service's env vars |
| `@Future` / `@FutureOrPresent` validation rejects your dates | You used a startDate in the past | Use a date at least 1 day in the future. The smoke-test.sh script handles this automatically. |
| Booking succeeds the first time, fails the second time with "no rooms available" | Yes — the H2 DB is per-process and persists for the life of the Spring Boot process; multiple bookings of the same hotel on the same dates use up the room inventory | Either book different cities, or restart the hotel-service to wipe the in-memory data |
| You restart IntelliJ and now you can't find your old trips | H2 is in-memory — data is gone on every restart | Expected behavior. For real persistence, swap H2 for Postgres in each `application.yml` |
| `RabbitMQ connection refused` warnings | RabbitMQ no longer used | These are ignorable warnings if you still have RabbitMQ dependencies; everything works via Kafka or HTTP |

---

## 12. Optional: Run Everything With One Click

After creating the four run configurations, you can chain them into a **Compound Configuration** so a single ▶ click starts all four.

1. **Run → Edit Configurations…**
2. Click **+** → **Compound**.
3. **Name**: `All Travel Services`
4. Click **+** in the configurations list and add, in this order:
   - Hotel Service
   - Flight Service
   - Cancellation Function
   - Travel Service
5. **Apply → OK**.

Now select **All Travel Services** from the dropdown and click ▶ — IntelliJ starts all four with one shortcut. The Run tool window will have four tabs side-by-side.

> **Tip**: Compound configurations start all children simultaneously, not sequentially. The four services tolerate this because each one only contacts the others on the first user request, not at startup. If you ever add a hard startup dependency (e.g. travel-service refusing to start without hotel-service responding), you'd need a different approach — `Before launch` hooks or a startup-order extension plugin.

---

## 13. Going Further

Once you have the local IntelliJ flow working, you can:

- **Switch to Docker Compose** — `docker-compose up --build` from the project root brings up the same stack plus RabbitMQ, all containerized. Useful for testing the broker-driven `FUNCTION_DISPATCH_MODE=stream` path.
- **Deploy to Kubernetes** — see the main `README.md` for AWS EKS and GCP GKE instructions using the provided `scripts/deploy.sh`.
- **Run the smoke test** — `./scripts/smoke-test.sh` exercises Scenario 1, Scenario 2, and the cancellation flow against your local stack in one shot.
- **Tweak the cron** — to see the itinerary scheduler fire without waiting until 6 AM, set the env var `ITINERARY_CRON="0/30 * * * * *"` in the Travel Service run configuration (every 30 seconds), book a trip with `startDate` = today, and watch the logs.

That's it — you now have a fully debuggable, breakpointable, hot-reloadable Travel Microservices development environment in IntelliJ.
