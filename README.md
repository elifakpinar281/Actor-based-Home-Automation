# Home Automation System – Lab 02 (Actors)

The task was to implement a smart home system using Apache Pekko, consisting of two separate actor systems communicating via gRPC:

- **HomeAutomationSystem** – sensors, actuators, environment simulation, REST/HTTP API
- **OrderProcessorSystem** – external ordering system with H2 persistence, accessible via gRPC

---

## Starting the System

The Java systems must be started in the following order:

````bash
# Start the FHV VPN for the MQTT source (optional, only required for EXTERNAL_MQTT mode).

# Start the external Order Processor system (gRPC server on 127.0.0.1:50051):
./gradlew runOrderProcessor

# Start the Home Automation System (localhost:8084):
./gradlew run

# Start the frontend (Next.js on localhost:3000):
cd frontend && npm install && npm run dev
````


The MQTT connection to the external weather simulation is attempted automatically when the HomeAutomationSystem starts. If it fails, the system continues to run. The EXTERNAL_MQTT mode will simply not deliver any values in that case.

### Access Points

- Frontend: http://localhost:3000
- Backend API: http://localhost:8084
- gRPC OrderProcessor: 127.0.0.1:50051

## Frontend

The frontend exposes all required actions:

- Live status of temperature, weather, AC, blinds, and media station states
- Floorplan with live status of AC, blinds, and media station
- Controls: temperature via slider, weather selection, simulation mode switching
- Media Station card: start/stop movies
- Fridge Management: view and consume current products, place orders, view order history

---

## Architecture Overview

### Actors – HomeAutomationSystem

### Class Model

---

## Actor Discovery: Receptionist Everywhere, Except Per-Session Children

Actor discovery uses the Receptionist as recommended in the assignment, 
which keeps actors decoupled from one another. 
The one exception is the Fridge, which spawns its own child actors directly.

Discovery relationships:

1. EnvironmentCoordinator → TemperatureSensor: Receptionist
2. EnvironmentCoordinator → WeatherSensor: Receptionist
3. TemperatureSensor → AirCondition: Receptionist
4. WeatherSensor → Blinds: Receptionist
5. MediaStation → Blinds: Receptionist
6. Fridge → OrderProcessor: child spawn
7. Routes → all actors: direct reference (bind)

The HTTP routes hold direct references to actors because they are not actors themselves - they are the boundary to the outside world.

### Two Separate Actor Systems via gRPC

The ordering system lives in a separate actor system. 
There are two guardian-based ActorSystems (HomeAutomationController and OrderProcessorGuardian), 
each running in its own JVM process and communicating via gRPC. The Protobuf schema is located at src/main/proto/orderprocessing.proto.

The OrderServiceClient is created once in HomeAutomationController and passed to the Fridge, 
which forwards it to its per-session child actors. A new gRPC channel is not created per order.

---

## Design Decisions

### 1. EnvironmentCoordinator – Blackboard Pattern

The EnvironmentCoordinator handles four distinct update messages (InternalTemperatureUpdate, MqttTemperatureUpdate, SetFixedTemperature, and mode switch). 
Values are only propagated to sensors when the active mode matches the source. 
Each source may fire at any time, but the coordinator decides whether the update passes through.

On a mode switch, no new value is pushed immediately. The next tick handles propagation - pushing immediately would mix the mode switch with a temperature update.

### 2. EnvironmentSnapshot as a Status Cache

Instead of querying all actors individually on every /status call, the EnvironmentCoordinator keeps 
the last pushed value in an EnvironmentSnapshot and returns it via ask. 
This avoids inconsistent snapshots and reduces the number of hops required.

### 3. Single MessageAdapter per Class

Pekko allows only one messageAdapter per message class per actor. Registering a second adapter for the 
same class replaces the first. The EnvironmentCoordinator needs to process Receptionist.Listing for two service keys 
(TemperatureSensor and WeatherSensor). The solution is a single adapter that internally inspects listing.isForKey() 
to determine which command variant to produce.

### 4. Blocking-IO Dispatcher for PersistenceActor

JDBC calls block the executing thread. If the PersistenceActor ran on the default dispatcher, 
its synchronous DB inserts would occupy threads from the shared pool and slow down other actors. 
It is therefore spawned on a dedicated blocking-io-dispatcher (see src/main/resources/orderprocessor.conf). 
Four threads in the pool are sufficient for this environment.

### 5. JDBC Instead of EventSourcedBehavior

Plain JDBC is used rather than an ORM. Event sourcing would conceptually fit since orders are immutable 
and never modified, but was considered overkill for this scope.

H2 runs in AUTO_SERVER mode so the database can also be inspected externally.

### 6. Per-Session OrderProcessor (Child) Instead of a Shared Worker

For each order, the Fridge spawns a new OrderProcessor child that handles exactly one 
ProcessOrder message and then stops itself. This gives every order its own lifecycle with no shared state. 
This is the Per-Session Child pattern.

### 7. Auto-Reorder on Empty Stock

When a product's quantity in the Fridge drops to zero, the Fridge triggers an automatic reorder using 
the same mechanism as a user-initiated order, but without a replyTo reference. 
If the auto-reorder would exceed capacity limits, it is rejected with a warning log and the product 
remains out of stock.

### 8. Reorder in the Frontend

When the last unit of a product is consumed, the product row would normally disappear from the UI (due to a quantity > 0 filter) until the auto-reorder 
completes a few seconds later. Instead, such products are shown as pending reorder in the frontend with a "Reordering..." pill. Once the backend reports a quantity greater than zero again, the marker is automatically cleared via the pendingReorders.has(id) && quantity === 0 condition.

### 9. Shutdown and Shutdown Hook

Both actor systems register a JVM shutdown hook that calls system.terminate() and waits up to 5 seconds 
for a clean shutdown. This ensures the gRPC server, HTTP binding, and MQTT connection are closed 
gracefully on Ctrl+C rather than being abruptly terminated.

---

## Further Documentation

[INTERACTION_PATTERNS.md](INTERACTION_PATTERNS.md)