# Architecture & Flow Diagrams

## 1. High-level Architecture

```
                           ┌──────────────────────────────────┐
                           │           Customer               │
                           └──────────────┬───────────────────┘
                                          │ HTTP
                                          ▼
                           ┌──────────────────────────────────┐
                           │   Ingress (ALB on AWS / GCLB on  │
                           │   GCP)                           │
                           └──────────────┬───────────────────┘
                                          │
                                          ▼
                  ┌────────────────────────────────────────┐
                  │      travel-service (orchestrator)     │
                  │  POST   /api/v1/trips                  │
                  │  DELETE /api/v1/trips/{ref}            │
                  │  GET    /api/v1/trips/{ref}            │
                  │  POST   /api/v1/trips/{ref}/itinerary  │
                  └─────┬───────────────┬──────────────────┘
                        │ Feign         │ Feign
                        │ HTTP          │ HTTP
                        ▼               ▼
              ┌───────────────┐ ┌───────────────┐
              │ hotel-service │ │ flight-service│
              │   :8081       │ │    :8082      │
              │  H2 in-memory │ │  H2 in-memory │
              └───────────────┘ └───────────────┘
                        ▲               ▲
                        │ DELETE        │ DELETE
                        │ (cancel)      │ (cancel)
                        └───────┬───────┘
                                │
                  ┌─────────────┴────────────────┐
                  │    cancellation-function     │
                  │    (Spring Cloud Function    │
                  │     on Knative — scale-to-0) │
                  └──────────────▲───────────────┘
                                 │
                                 │ HTTP POST  (mode="http")
                                 │       OR
                                 │ Stream binding (mode="stream")
                                 │       via Kafka / Pub-Sub /
                                 │       Kinesis
                                 │
                  ┌──────────────┴───────────────┐
                  │   travel-service publishes   │
                  │   CancellationEvent on user  │
                  │   cancellation               │
                  └──────────────────────────────┘
```

## 2. Booking Flow — Scenario 1 (Happy Path)

```
Customer    travel-service     hotel-service    flight-service
   │              │                   │                  │
   │  POST /trips │                   │                  │
   ├─────────────►│                   │                  │
   │              │                   │                  │
   │              │  Save trip        │                  │
   │              │  status=INITIATED │                  │
   │              │                   │                  │
   │              │  POST bookHotel   │                  │
   │              ├──────────────────►│                  │
   │              │  201 (CONFIRMED)  │                  │
   │              │◄──────────────────┤                  │
   │              │                   │                  │
   │              │  status=HOTEL_BOOKED                  │
   │              │                   │                  │
   │              │  POST bookFlight                     │
   │              ├─────────────────────────────────────►│
   │              │  201 (CONFIRMED)                     │
   │              │◄─────────────────────────────────────┤
   │              │                                       │
   │              │  status=CONFIRMED                     │
   │  201 (trip)  │                                       │
   │◄─────────────┤                                       │
```

## 3. Booking Flow — Scenario 2 (Hotel Unavailable)

```
Customer    travel-service     hotel-service    flight-service
   │              │                   │                  │
   │  POST /trips │                   │                  │
   ├─────────────►│                   │                  │
   │              │  POST bookHotel   │                  │
   │              ├──────────────────►│                  │
   │              │  400 (no rooms)   │                  │
   │              │◄──────────────────┤                  │
   │              │                                       │
   │              │  status=FAILED                        │
   │              │  Flight NOT attempted                 │
   │   422 (FAIL) │                                       │
   │◄─────────────┤                                       │
```

## 4. Booking Flow — Scenario 3 (Flight Fails After Hotel Succeeds)

The travel-service compensates by cancelling the hotel.

```
Customer    travel-service     hotel-service    flight-service
   │              │                   │                  │
   │              │  bookHotel  OK    │                  │
   │              ├──────────────────►│                  │
   │              │◄──────────────────┤                  │
   │              │                                       │
   │              │  bookFlight  FAIL                     │
   │              ├─────────────────────────────────────►│
   │              │◄─────────────────────────────────────┤
   │              │                   │                  │
   │              │  COMPENSATE       │                  │
   │              │  cancelHotel      │                  │
   │              ├──────────────────►│                  │
   │              │◄──────────────────┤                  │
   │              │  status=FAILED                        │
   │   422 (FAIL) │                                       │
   │◄─────────────┤                                       │
```

## 5. Cancellation Flow (Serverless)

```
Customer       travel-service        cancellation-function   hotel-service  flight-service
   │                  │                       │                   │              │
   │  DELETE /trip    │                       │                   │              │
   ├─────────────────►│                       │                   │              │
   │                  │ Check startDate>today │                   │              │
   │                  │ Mark trip CANCELLED   │                   │              │
   │                  │                       │                   │              │
   │                  │  Publish CancellationEvent                │              │
   │                  ├──────────────────────►│                   │              │
   │  200 OK          │                       │                   │              │
   │◄─────────────────┤                       │                   │              │
   │                                          │                   │              │
   │                                          │ DELETE hotel ref  │              │
   │                                          ├──────────────────►│              │
   │                                          │◄──────────────────┤              │
   │                                          │                                  │
   │                                          │  DELETE flight ref               │
   │                                          ├─────────────────────────────────►│
   │                                          │◄─────────────────────────────────┤
```

The serverless function dispatch is **cloud-agnostic** — selected by env var:
- `FUNCTION_DISPATCH_MODE=http`  → HTTP POST to function URL (Knative/Cloud Run/Lambda URL)
- `FUNCTION_DISPATCH_MODE=stream` → Spring Cloud Stream binder (Kafka/PubSub/Kinesis/EventBridge)

## 6. Itinerary Issue Flow

On the morning of `startDate`, a cron job inside `travel-service` automatically issues itineraries for all CONFIRMED trips. The customer can no longer cancel once this has happened.

```
Cron (06:00 daily)
   │
   ▼
ItineraryScheduler.issueItinerariesForToday()
   │
   ├── For each Trip where status=CONFIRMED AND startDate=today
   │       │
   │       └── tripBookingService.issueItinerary(trip)
   │              status -> COMPLETED
   │              return Itinerary DTO
```

## 7. Cloud-Agnostic Deployment Picture

```
              ┌─────────────────────────────────────────────┐
              │  Jenkins Pipeline (Jenkinsfile)             │
              │  Parameter: CLOUD_PROVIDER = AWS | GCP      │
              └────────────────────┬────────────────────────┘
                                   │
                  ┌────────────────┴────────────────┐
                  ▼                                 ▼
        ┌──────────────────┐              ┌──────────────────┐
        │  CLOUD_PROVIDER  │              │  CLOUD_PROVIDER  │
        │      = AWS       │              │      = GCP       │
        ├──────────────────┤              ├──────────────────┤
        │ Registry: Docker Hub │              │ Registry: Docker Hub │
        │ Cluster:  EKS        │              │ Cluster:  GKE        │
        │ Ingress:  ALB        │              │ Ingress:  GCLB       │
        │ Func:     Knative    │              │ Func:     Knative    │
        │           or Lambda  │              │           or Cloud   │
        │                      │              │           Run        │
        └──────────────────────┘              └──────────────────────┘
                  │                                 │
                  └─────────────┬───────────────────┘
                                ▼
                      ┌──────────────────┐
                      │  Same Docker     │
                      │  images          │
                      │  Same K8s YAMLs  │
                      │  (common/)       │
                      │  Same Java code  │
                      └──────────────────┘
```
