# Home Automation System - Lab 02 (Actors)
Elif Akpinar & Helena Aldaloul - The assignment PDF: [02-Actors.pdf](documentation_assests/02-Actors.pdf)


The task was to implement a smart home system using Apache Pekko, consisting of two separate actor systems communicating via gRPC:

- **HomeAutomationSystem** - sensors, actuators, environment simulation, REST/HTTP API
- **OrderProcessorSystem** - external ordering system with H2 persistence, accessible via gRPC

The HomeAutomationSystem simulates temperature and weather, controls AC, blinds and media station and manages a fridge with ordering capabilities. 
The OrderProcessorSystem handles incoming orders, simulates processing time and persists order history in H2.

Prerequisited: Java 17+, Gradle, Node.js 18+ 

---

## Starting the System

The Java systems must be started in the following order:

```bash
# Optional: start the FHV VPN for the MQTT source (only required for EXTERNAL_MQTT mode).

# Start the external Order Processor system (gRPC server on 127.0.0.1:50051):
./gradlew runOrderProcessor

# Start the Home Automation System (localhost:8084):
./gradlew run

# Start the frontend (Next.js on localhost:3000):
cd frontend && npm install && npm run dev
```

The MQTT connection to the external weather simulation is attempted automatically when the HomeAutomationSystem starts. 
If it fails, the system continues to run. The EXTERNAL_MQTT mode will simply not deliver any values in that case.

### Access Points

- Frontend: http://localhost:3000
- Backend API: http://localhost:8084
- gRPC OrderProcessor: 127.0.0.1:50051

## Frontend

The frontend has all required actions:

- Live status of temperature, weather, AC, blinds and media station states
- Floorplan with live status of AC, blinds and media station
- Controls: temperature via slider, weather selection, simulation mode switching
- Media Station card: start/stop movies
- Fridge Management: view and consume current products, place orders, view order history

---

## Architecture Overview

[ClassDiagram.pdf](documentation_assests/ClassDiagram.pdf)


---

## Actor Discovery

Actor discovery uses the Receptionist as recommended in the assignment, which keeps actors decoupled from one another. 
The only exception is the Fridge, which spawns its own child actors directly.

Discovery relationships:

1. EnvironmentCoordinator -> TemperatureSensor: Receptionist
2. EnvironmentCoordinator -> WeatherSensor: Receptionist
3. TemperatureSensor -> AirCondition: Receptionist
4. WeatherSensor -> Blinds: Receptionist
5. MediaStation -> Blinds: Receptionist
6. Fridge -> OrderProcessor: child spawn
7. Fridge -> WeightSensor: child spawn
8. Fridge -> SpaceSensor: child spawn
9. Routes -> all actors: direct reference (bind)

The HTTP routes hold direct references to actors because they are not actors themselves. They are the boundary to the "outside".

### Two Separate Actor Systems via gRPC

The ordering system lives in a separate actor system. 
There are two guardian-based ActorSystems (HomeAutomationController and OrderProcessorGuardian). Each runs in its own JVM process and communicate via gRPC. 
The Protobuf schema is located at `src/main/proto/orderprocessing.proto`.

The `OrderServiceClient` is created once in HomeAutomationController and passed to the Fridge, which forwards it to its per-session child actors. 
A new gRPC channel is not created per order.

---


## Design Decisions

### 1. EnvironmentCoordinator – Blackboard Pattern

The EnvironmentCoordinator is the single point that handles all environment updates: ticks from the internal simulator, values from MQTT and manual overrides from the user (`InternalTemperatureUpdate`, `MqttTemperatureUpdate`, `SetFixedTemperature`, `SetFixedWeather`, `SetMode`).
Updates are only forwarded to the sensors if the current mode matches the source of the update. 
So an MQTT update is dropped while the system is in INTERNAL mode, and vice versa. This way the sources don't need to know anything about each other.

What happens when the mode is switched:

- Switching to INTERNAL or EXTERNAL_MQTT does not push a new value right away. The next tick from that source will deliver one. Pushing immediately would just send out an old value from before the switch.
- Switching to FIXED pushes the last fixed value right away, because there is no tick that would otherwise trigger it.
- DISABLED ignores every incoming update. The last known values are still returned for /status, but the sensors no longer get new data.
- SetFixedTemperature and SetFixedWeather automatically switch the mode to FIXED if it isn't already. If the user sets a value, they clearly want that value to take effect.


### 2. EnvironmentSnapshot as a Status Cache

Every `/status` call could ask all sensors and actuators individually. We didn't do that. 
Instead, the EnvironmentCoordinator keeps the last pushed values in an EnvironmentSnapshot and returns them via ask. 
One actor call per /status instead of four, and the snapshot is always consistent (no risk of getting the temperature from before an update and the weather from after).


### 3. Single MessageAdapter per Class

Pekko only allows one `messageAdapter` per message class per actor. 
If you register a second one for the same class, it replaces the first. 
That's a problem in EnvironmentCoordinator, which needs to receive `Receptionist.Listing` messages for two service keys (TemperatureSensor and WeatherSensor).

The fix is one shared adapter that checks `listing.isForKey(...)` and produces the right command for each key. 
Unrelated listings are turned into an `IgnoredListing`. 
MediaStation only subscribes to one key (Blinds), but uses the same defensive pattern anyway in case more subscriptions are added later.

### 4. Pekko Persistence JDBC for Order Persistence

The PersistenceActor uses EventSourcedBehavior from pekko-persistence-typed. 
Each successful order is persisted as an OrderPersisted event in the journal. 
On restart of the OrderProcessorSystem, all events are replayed and the state (list of processed order IDs) is automatically reconstructed. 
Event serialization uses Jackson JSON. The database is H2 in in-memory mode, connected via pekko-persistence-jdbc (Slick). 
The journal and snapshot tables are created automatically on connection startup (INIT=RUNSCRIPT in the JDBC URL).


### 5. Per-Session OrderProcessor (Child) Instead of a Shared Worker

For every order, the Fridge spawns a fresh OrderProcessor child. 
That child handles exactly one ProcessOrder message and then stops itself. 
Every order gets its own actor, its own state and its own lifecycle. Nothing is shared between orders. This is the Per-Session Child pattern.


### 6. Auto-Reorder on Empty Stock

When a product hits zero in the Fridge, the Fridge orders it again automatically. 
It uses the same OrderProducts message as a user-initiated order, just without a `replyTo` (no one is waiting for a response).

The amount that gets reordered is the product's initial quantity. So each product fills back up to whatever it started with, not a fixed default.
If the auto-reorder wouldn't fit (item count or weight), it's skipped with a warning log and the product stays empty. 
The Fridge can never end up over capacity from an auto-reorder.


### 7. Reorder in the Frontend

Normally, when a product hits zero it would just disappear from the UI (because we filter on quantity > 0) and only reappear a few seconds 
later when the auto-reorder finishes. 
That would be confusing when the product looks gone.

Instead, the frontend keeps such products visible and shows a "Reordering..." pill on them. 
As soon as the backend reports a quantity > 0 again, the pill disappears. The check is just `pendingReorders.has(id) && quantity === 0`.


### 8. Records for Messages and DTOs

Every actor command, every response and every HTTP DTO is a Java record. We used records instead of plain classes.

- Actor messages have to be immutable. Otherwise the share-nothing model breaks. Records make every field final automatically and there are no setters.
- Records generate equals, hashCode, and toString for free. This makes a huge difference in logging.
- HTTP DTOs serialize to JSON cleanly with Jackson, because records have a constructor and accessor methods that Jackson can use directly.
- Messages where the reply is sometimes wanted and sometimes not (like `Fridge.OrderProducts`) use `Optional<ActorRef<...>>` for the replyTo, plus two static factory methods: `fromUser(...)` (with reply) and `autoOrder(...)` (without). Both go through the same handler. We avoided two separate message types just to keep the code simple.


### 9. Exception Handling – Domain Hierarchy + Global Handler
All custom exceptions extend DomainException, which is a RuntimeException with an ErrorCode field.
Each concrete exception takes structured arguments and builds its own message. 

For example, `new InsufficientSpaceException(60, 80, 25)` produces "Insufficient space in fridge. Current: 60, Max: 80, Attempted addition: 25". 
The call site doesn't have to format anything. This keeps Fridge.validateAndBuildLineItems clean, and every occurrence of the same error reads the same way.

ErrorCode is an enum that categorizes the error (e.g. `FRIDGE_INSUFFICIENT_SPACE`). 

GlobalExceptionHandler is registered once with `handleExceptions(...)` in `HttpServer.createRoute()`. It maps exceptions to HTTP status codes using Pekko's `ExceptionHandler.newBuilder().match(...)`:
For every .match, the handler logs errorCode, message -> HTTP code and returns the same ErrorResponse JSON shape.
To add a new domain exception, you add one .match(...) line in the handler

Why not checked exceptions?
Pekko's actor handlers and the HTTP route directives can't easily declare throws clauses, so checked exceptions would force a try/catch around almost every line. 
Unchecked exceptions keep the route code readable. 
The trade-off is that you have to remember to handle domain errors in the global handler.


### 10. Fridge Sensors
We modelled the Sensors as two child actors of the Fridge (WeightSensor, SpaceSensor).
Both sensors are spawned by the Fridge in its constructor and are not registered with the Receptionist.
The Fridge pushes updates to them via fire-and-forget. 
External actors can read the current values via request-response.

### 11. Shutdown and Shutdown Hook

Both actor systems register a JVM shutdown hook (so Strg+C does the right thing). Both hooks call `system.terminate()`. 
The HomeAutomation hook additionally waits up to 5 seconds via `getWhenTerminated().get(5, TimeUnit.SECONDS)`, which gives the gRPC client, 
HTTP binding and MQTT connection time to shut down cleanly.
The OrderProcessor hook just calls `terminate()` without waiting.
___
## Further Documentation

[INTERACTION_PATTERNS.md](INTERACTION_PATTERNS.md) - walkthrough of the interaction patterns used in the system.

[ClassDiagram.pdf](documentation_assests/ClassDiagram.pdf) - detailed class diagram of the HomeAutomationSystem and OrderProcessor actors and messages.