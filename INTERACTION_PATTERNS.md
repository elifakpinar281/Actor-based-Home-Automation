# Interaction Patterns
---

## 1. Scheduling Messages to Self
Periodisches Senden einer Message an sich selbst über einen Timer.

**Wo verwendet:**

- environment/TemperatureEnvironment: interner Temperatur-Simulator, der alle 5 s einen Tick an sich selbst sendet und seine simulierte Temperatur leicht verändert
- environment/WeatherEnvironment: analog für Wettersimulation

**Aufbau:**


Beim Empfang von Tick gibt der Actor einen kleinen random Delta-Wert zurück, addiert ihn auf die aktuelle
Temperatur und pusht das Ergebnis als InternalTemperatureUpdate zum EnvironmentCoordinator.

Die interne Simulation Werte sollen über die Zeit leicht verändern. Es gibt kein eigner Thread. Timer wird beim Stop automatisch gecancelt.

---

## 2. Fire and Forget (Tell)
Eine Message wird gesendet, ohne dass eine Antwort erwartet oder verarbeitet wird.



**Wo verwendet:** 
Wird sehr häufig verwendet. Im Grunde überall, wo es keine Antwort braucht.
Einpaar Beispiele:

1. EnvironmentCoordinator -> TemperatureSensor: TemperatureMeasured
2. EnvironmentCoordinator -> WeatherSensor: WeatherMeasured
3. TemperatureSensor -> AirCondition: EnrichedTemperature
4. WeatherSensor -> Blinds: WeatherUpdate
5. MediaStation -> Blinds: MovieStatusChanged
6. MqttEnvironmentClient -> EnvironmentCoordinator: MqttTemperatureUpdate / MqttWeatherUpdate
sehr häufig – im Grunde überall, wo es keine Antwort braucht. Beispiele:

**Aufbau (Beispiel TemperatureSensor -> AirCondition):**


Sensoren broadcasten Messwerte an alle interessierten Aktuatoren. Es gibt keinen sinnvolle Antwort.
"AC hat das Update gelesen"-Acknowledgement ergibt fachlich kein Sinn.
Auch zwischen Coordinator und Sensoren passt Fire-and-Forget.

---

## 3. Request-Response with Ask (between Actor and outside/HTTP)

Ein Aufrufer schickt eine Anfrage und wartet (mit Timeout) auf genau eine Antwort. Der ActorRef der Antwort steht direkt im Request.

**Wo verwendet:** 
Überall, wo die HTTP-Routen Daten aus einem Actor brauchen, um eine HTTP-Antwort zu formen.

**Aufbau (Beispiel /status aggregiert 4 Aktoren parallel):




Die Aktoren bekommen einen replyTo: ActorRef<...Response> im Request mit. Beispiel AirCondition:


HTTP ist synchron-blockierend (Client wartet auf Response). Um aus Aktoren eine HTTP-Antwort zu formen, brauchen wir genau ein Future pro Aktor-Aufruf, mit Timeout.
Ask hat einen Timeout-Mechanismus und legt intern einen kurzlebigen Adapter-Actor an. Für Status-Queries vom HTTP-Layer ist das passend.

---

## 4. Send Future Result to Self (pipeToSelf)
Ein Actor ruft eine API auf, die ein `CompletionStage` zurückgibt, und will mit dem Ergebnis als interne Message weiterarbeiten – im Aktor-Kontext, nicht im Future-Callback-Thread.

**Wo verwendet:** 
devices/OrderProcessor.java. Der Per-Session-Child ruft den gRPC-Client und will die Antwort als Pekko-Message zurückbekommen, damit er weiter im Aktor-Lifecycle bleibt.

**Aufbau:**


pipeToSelf ist die Future-zu-Self-Message-Adaption. Das CompletionStage<OrderResponse> vom gRPC-Client wird in eine interne Command-Variante (GrpcResponse oder GrpcFailure) gewickelt
und an self gesendet. Damit bleibt der Actor stateless gegenüber dem Future-Thread, alle weiteren Schritte (gRPC-Success -> Receipt erzeugen -> an Fridge melden) laufen sauber in der Mailbox.

Den gRPC-Client direkt zu blockieren oder den Erfolg-Branch im Future-Callback zu behandeln würde das Aktor-Modell brechen (Thread-Safety, kein Zugriff auf internen Aktor-State erlaubt).

---

## 5. Ignoring Replies (Tell ohne replyTo)
Wie Fire-and-Forget, aber bewusst auch im Kontext einer Anfrage, bei der eine Antwort theoretisch möglich, aber nicht erwünscht ist.

**Wo verwendet:**

- Fridge.ConsumeProduct: der User klickt „Consume", die HTTP-Route sendet tell(...) an den Fridge und antwortet sofort mit 202 Accepted. Keine replyTo im Command:


- Fridge.OrderProducts.autoOrder(...): Der Fridge sendet sich selbst eine Bestellung, wenn ein Produkt auf 0 fällt. Hier ist die replyTo als Optional<ActorRef<...>> modelliert und im
  Auto-Order-Fall leer:



Im Response-Handler wird die optionale replyTo mit ifPresent(...) behandelt. Fehlt sie, wird nicht geantwortet.

„Consume" ist eine reine Befehlsaktion ohne sinnvollen Rückgabewert. Die Aufgabe nennt
Der zweite Anwendungsfall (Auto-Order) zeigt eine  Wiederverwendung des gleichen Commands mit und ohne replyTo (über Optional).

---

## 6. Per-Session Child Actor
Ein Aktor spawnt für jeden Request einen kurzlebigen Child-Actor, der den State des Requests isoliert hält und nach Abschluss stirbt.


**Wo verwendet:** 
Fridge.onOrderProducts: pro eingehender Bestellung wird ein eigener OrderProcessor als anonymes Child gespawnt.

**Aufbau (Fridge-Seite):**


**Aufbau (Child-Seite – stoppt sich selbst nach Antwort):**



**Warum hier passend:**

- Wird in der Aufgabe verlangt
- Jede Bestellung hat ihren eigenen Zustand (welche Order, an wen antworten). Den im Fridge-State zu halten würde eine Map<OrderId, replyTo> erfordern, der State würde mit jeder Order wachsen
  und müsste explizit angepasst werden. Der Per-Session-Child kapselt diesen State vollständig.
- gRPC ist asynchron. Der Child kann auf die gRPC-Antwort warten, ohne dass der Fridge währenddessen blockiert wird. Mehrere parallele Bestellungen funktionieren mit dem Pattern natürlich.
- Beim erfolgreichen oder fehlgeschlagenen Abschluss returnt der Child Behaviors.stopped() und räumt sich selbst weg. Kein Memory-Leak.

Der gRPC-Client wird nicht pro Session erstellt, sondern einmal im HomeAutomationController aufgemacht und an alle Per-Session-Children durchgereicht. 
Ein neuer gRPC-Channel pro Order wäre für die Aufgabe verschwenderisch.

---

## 7. Receptionist (Service Discovery)

Aktoren registrieren sich unter Service-Keys. Andere Aktoren abonnieren diese Keys und bekommen automatisch Updates, wenn Aktoren hinzukommen oder verschwinden.

**Wo verwendet:** 
Für jede inter-Aktor-Beziehung im HomeAutomationSystem, mit Ausnahme der Per-Session-Children des Fridge. 

- `EnvironmentCoordinator` abonniert `TemperatureSensor.SERVICE_KEY` und `WeatherSensor.SERVICE_KEY`
- `TemperatureSensor` abonniert `AirCondition.SERVICE_KEY`
- `WeatherSensor` abonniert `Blinds.SERVICE_KEY`
- `MediaStation` abonniert `Blinds.SERVICE_KEY`

**Aufbau (Beispiel WeatherSensor):**



Auf der Gegenseite (z.B. `HomeAutomationController`):




Konkrete Vorteile gegenüber direkter Referenz-Injektion via Constructor:
    - Keine Reihenfolge-Abhängigkeit beim Spawn (Actor A kann vor Actor B existieren, ohne dass A explizit eine Ref auf B braucht).
    - Ein zweiter AC oder zweiter Blinds-Actor könnte zur Laufzeit dazukommen, ohne dass Sensoren-Code geändert werden müsste.
    - In Unit-Tests können Probes unter dem gleichen Key registriert werden.

Pekko erlaubt nur einen messageAdapter pro Message-Klasse pro Actor. 
Wenn man wie der EnvironmentCoordinator Listings für mehrere Service-Keys braucht, muss ein einziger Adapter verwendet werden, der intern per
listing.isForKey(...) dispatcht. Eine zweite Registrierung des Adapters für dieselbe Klasse ersetzt die erste, was einen Bug erzeugen würde (Listings für den ersten Key kämen nie mehr an).

---

## 8. gRPC-Bridge zwischen zwei Actor-Systemen
Zwei eigenständige ActorSystems kommunizieren über ein streng typisiertes Request-Response-Protokoll.

Brücke zwischen HomeAutomationSystem (Fridge / Per-Session-OrderProcessor) und OrderProcessorSystem (OrderServiceActorImpl).
**Schema:** `src/main/proto/orderprocessing.proto`

```protobuf
service OrderService {
  rpc ProcessOrder(OrderRequest) returns (OrderResponse);
}
```

**Client-Seite (HomeAutomationSystem):**
Client wird einmalig im Controller erzeugt.


- Per-Session-OrderProcessor ruft grpcClient.processOrder(...) und adaptiert das Future per pipeToSelf (siehe 4.).

**Server-Seite (OrderProcessorSystem):** Der OrderServiceActorImpl implementiert das generierte OrderService-Interface. Eingehende gRPC-Calls werden per AskPattern.ask an den ValidationActor weitergegeben:

Damit ist die Verbindungen außerhalb des Aktor-Models verbunden mit der "innerhalb" (Validation -> Persistence Aktor-Pipeline).
Damit ist die Brücke „außerhalb" der Aktor-Welt (gRPC-Endpoint) cleanly verbunden mit der
„innerhalb" (Validation → Persistence Aktor-Pipeline).


- Pekko gRPC generiert aus dem .proto typsichere Server- und Client-Stubs, sodass wir keine manuellen Serialisierungs-/Deserialisierungs-Schichten warten müssen.
- Die zwei Systeme sind durch das Proto-Schema verbunden. Sie können unabhängig voneinander gestartet, deployed und versioniert werden.
