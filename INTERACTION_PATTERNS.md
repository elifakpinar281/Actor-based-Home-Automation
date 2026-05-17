# Interaction Patterns

---

## 1. Scheduling Messages to Self

Periodically sending a message to itself via a timer.

**Used in:**

- environment/TemperatureEnvironment: internal temperature simulator that sends a tick to itself every 5 seconds and slightly adjusts its simulated temperature
- environment/WeatherEnvironment: analogous for weather simulation

**How it works:**

On receiving a Tick, the actor applies a small random delta to the current temperature and pushes the result as an InternalTemperatureUpdate to the EnvironmentCoordinator.

The internal simulation values change gradually over time. No dedicated thread is involved. The timer is automatically cancelled on stop.

---

## 2. Fire and Forget (Tell)

A message is sent without expecting or handling a reply.

**Used in:**

This pattern is used very frequently — essentially everywhere a response is not needed. Some examples:

1. EnvironmentCoordinator -> TemperatureSensor: TemperatureMeasured
2. EnvironmentCoordinator -> WeatherSensor: WeatherMeasured
3. TemperatureSensor -> AirCondition: EnrichedTemperature
4. WeatherSensor -> Blinds: WeatherUpdate
5. MediaStation -> Blinds: MovieStatusChanged
6. MqttEnvironmentClient -> EnvironmentCoordinator: MqttTemperatureUpdate / MqttWeatherUpdate

**Why it fits:**

Sensors broadcast measurements to all interested actuators. There is no meaningful reply — an "AC has received the update" acknowledgement makes no domain sense. Fire-and-forget also fits the coordinator-to-sensor direction for the same reason.

---

## 3. Request-Response with Ask (between Actor and HTTP)

A caller sends a request and waits (with a timeout) for exactly one reply. The reply ActorRef is included directly in the request.

**Used in:**

Everywhere the HTTP routes need data from an actor to form an HTTP response.

**Example (/status aggregates 4 actors in parallel):**

Actors receive a replyTo: ActorRef<...Response> in the request. HTTP is synchronous — the client waits for a response. To form an HTTP response from actor data, exactly one Future per actor call is needed, with a timeout. Ask provides a timeout mechanism and internally creates a short-lived adapter actor. This is the right fit for status queries from the HTTP layer.

---

## 4. Send Future Result to Self (pipeToSelf)

An actor calls an API that returns a `CompletionStage` and wants to continue working with the result as an internal message — inside the actor context, not inside a future callback thread.

**Used in:**

devices/OrderProcessor.java. The per-session child calls the gRPC client and wants the response back as a Pekko message so it stays within the actor lifecycle.

**How it works:**

`pipeToSelf` is the future-to-self-message adapter. The `CompletionStage<OrderResponse>` from the gRPC client is wrapped into an internal command variant (GrpcResponse or GrpcFailure) and sent to self. This keeps the actor stateless with respect to the future thread — all subsequent steps (gRPC success → create receipt → notify Fridge) run cleanly through the mailbox.

Blocking the gRPC client directly or handling the success branch inside a future callback would break the actor model (thread safety, no access to internal actor state allowed).

---

## 5. Ignoring Replies (Tell without replyTo)

Like fire-and-forget, but intentionally used in contexts where a reply would be technically possible but is not desired.

**Used in:**

- Fridge.ConsumeProduct: the user clicks "Consume", the HTTP route sends a tell(...) to the Fridge and immediately responds with 202 Accepted. No replyTo in the command.
- Fridge.OrderProducts.autoOrder(...): the Fridge sends itself an order when a product drops to zero. Here replyTo is modelled as `Optional<ActorRef<...>>` and is empty in the auto-order case.

In the response handler, the optional replyTo is handled with `ifPresent(...)`. If absent, no reply is sent.

"Consume" is a pure command action with no meaningful return value. The auto-order case demonstrates reuse of the same command with and without replyTo via Optional.

---

## 6. Per-Session Child Actor

An actor spawns a short-lived child actor for each request. The child isolates the request's state and stops itself after completion.

**Used in:**

Fridge.onOrderProducts: for each incoming order, a dedicated OrderProcessor is spawned as an anonymous child.

**Fridge side:** spawns a new child per order.

**Child side:** stops itself after sending the reply (`Behaviors.stopped()`).

**Why it fits:**

- Required by the assignment
- Each order has its own state (which order, who to reply to). Keeping this in the Fridge state would require a `Map<OrderId, replyTo>` that grows with every order and must be cleaned up explicitly. The per-session child encapsulates this state entirely.
- gRPC is asynchronous. The child can wait for the gRPC response without blocking the Fridge in the meantime. Multiple parallel orders work naturally with this pattern.
- On successful or failed completion, the child returns `Behaviors.stopped()` and cleans itself up. No memory leak.

The gRPC client is not created per session — it is opened once in HomeAutomationController and passed down to all per-session children. A new gRPC channel per order would be wasteful.

---

## 7. Receptionist (Service Discovery)

Actors register under service keys. 
Other actors subscribe to these keys and automatically receive updates when actors are added or removed.

**Used in:**

For every inter-actor relationship in the HomeAutomationSystem, except the per-session children of the Fridge.

- `EnvironmentCoordinator` subscribes to `TemperatureSensor.SERVICE_KEY` and `WeatherSensor.SERVICE_KEY`
- `TemperatureSensor` subscribes to `AirCondition.SERVICE_KEY`
- `WeatherSensor` subscribes to `Blinds.SERVICE_KEY`
- `MediaStation` subscribes to `Blinds.SERVICE_KEY`

**Concrete advantages over direct reference injection via constructor:**

- No spawn-order dependency (actor A can exist before actor B without needing an explicit ref to B).
- A second AC or Blinds actor could be added at runtime without changing any sensor code.
- In unit tests, probes can be registered under the same key.

Pekko allows only one messageAdapter per message class per actor. 
When an actor like EnvironmentCoordinator needs listings for multiple service keys, 
a single adapter must be used that internally dispatches via `listing.isForKey(...)`. 
Registering a second adapter for the same class replaces the first, which would cause a bug - 
listings for the first key would never arrive.

---

## 8. gRPC Bridge between Two Actor Systems

Two independent ActorSystems communicate via a strictly typed request-response protocol.

Bridge between HomeAutomationSystem (Fridge / per-session OrderProcessor) and 
OrderProcessorSystem (OrderServiceActorImpl).

**Schema:** `src/main/proto/orderprocessing.proto`

```protobuf
service OrderService {
  rpc ProcessOrder(OrderRequest) returns (OrderResponse);
}
```

**Client side (HomeAutomationSystem):**

The client is created once in the controller. The per-session OrderProcessor calls 
grpcClient.processOrder(...) and adapts the future via pipeToSelf (see section 4).

**Server side (OrderProcessorSystem):**

The OrderServiceActorImpl implements the generated OrderService interface. 
Incoming gRPC calls are forwarded to the ValidationActor via AskPattern.ask.
This cleanly connects the "outside" of the actor world (gRPC endpoint) with the "inside" (Validation → Persistence actor pipeline).

**Why it fits:**

- Pekko gRPC generates type-safe server and client stubs from the .proto file, so no manual serialization/deserialization layers need to be maintained.
- The two systems are coupled only through the proto schema. They can be started, deployed, and versioned independently.