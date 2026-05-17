# Home Automation System – Lab 02 (Actors)

Die Aufgabe war die Implementierung eines Smart-Home-Systems mit Apache Pekko, bestehend aus zwei separaten Actor-Systemen, die über gRPC kommunizieren:

- HomeAutomationSystem - Sensoren, Aktuatoren, Environment-Simulation, REST/HTTP-API
- OrderProcessorSystem - externes Bestellsystem mit H2-Persistenz, erreichbar via gRPC

---
## Starten
Die Java-Systeme müssen in dieser Reihenfolge gestartet werden:

````bash
# FHV-VPN starten für MQTT-Quelle (optional, nur für EXTERNAL_MQTT-Modus)

# Externes Order-Processor-System (gRPC-Server auf 127.0.0.1:50051)
./gradlew runOrderProcessor

# Home-Automation-System (localhost:8084)
./gradlew run

# Frontend (Next.js auf localhost:3000)
cd frontend && npm install && npm run dev
````

Die MQTT-Verbindung zur externen Wettersimulation wird beim Start des HomeAutomationSystem automatisch versucht. 
Schlägt sie fehl, läuft das System trotzdem. Der EXTERNAL_MQTT-Modus liefert dann keine Werte.

### Zugriffspunkte

- Frontend: http://localhost:3000
- Backend API: http://localhost:8084
- gRPC OrderProcessor: 127.0.0.1:50051

## Frontend
Im Frontend lassen sich alle geforderten Aktionen ausführen:
- Live-Status der States von Temperatur, Wetter, AC, Blinds und MediaStation
- Floorplan: live Status von AC, Blinds und MediaStation
- Controls: Temperatur per Slider, Wetterauswahl, Wechsel des Simulation-Modus
- Media Station Card: Filme starten/stoppen
- Fridge Management: aktuelle Produkte ansehen + konsumieren, Bestellungen aufgeben, Bestellhistorie

---

## Architekturübersicht & 

### Akteure HomeAutomationSystem
### Klassenmodell


___

### Akteurs-Discovery: Receptionist überall, Ausnahme Per-Session-Child
Für Actor-Discovery wird Receptionist benutzt, wie es in der Aufgabe empfohlen war. Dadurch werden die Akteure voneinander decoupled. 
Eine Ausnahme ist hier der Fridge, da dieser den eigenen Child Actors spawned.
Discovery-Beziehungen:
1. EnvironmentCoordinator -> TemperatureSensor: Receptionist
2. EnvironmentCoordinator -> WeatherSensor: Receptionist
3. TemperatureSensor -> AirCondition: Receptionist
4. WeatherSensor -> Blinds: Receptionist
5. MediaStation -> Blinds: Receptionist
6. Fridge -> OrderProcessor: Child-Spawn
7. Routen -> alle Aktoren: direkte Referenz (Bind)

Die HTTP-Routen kennen die Aktoren direkt, weil sie kein Aktoren sind, sondern die mit der "Außenwelt" kommunizieren.


### Zwei getrennte Actor-Systeme via gRPC
Das Bestellsystem lebt in einem separaten Actor-System. Wir haben zwei Guardian-basierte ActorSystems (HomeAutomationController und OrderProcessorGuardian),
die in eigenen JVM-Prozessen laufen und per gRPC kommunizieren. Das Protobuf-Schema liegt in src/main/proto/orderprocessing.proto.

Der OrderServiceClient wird im HomeAutomationController einmalig erstellt und an den Fridge weitergegeben, der ihn an seine Per-Session-Children durchreicht.
Ein neues gRPC-Channel pro Bestellung haben wir nicht.
---

## Design-Entscheidungen

### 1. EnvironmentCoordinator - Blackboard-Pattern
Der EnvironmentCoordinator kennt vier separate Update-Messages (InternalTemperatureUpdate, MqttTemperatureUpdate, SetFixedTemperature + Mode-Switch)
und Werte werden nur dann an die Sensoren propagiert, wenn der aktive Modus zur Quelle passt.
Jede Quelle darf jederzeit feuern, aber der Coordinator entscheidet, ob es durchkommt.

Beim Mode-Switch wird nicht sofort ein neuer Wert gepusht. Der nächste Tick übernimmt das, sonst würde der Mode-Switch mit dem TemperatureUpdate vermischen würde.

### 2. EnvironmentSnapshot als „Cache" für status
Statt bei jedem /status-Call alle Aktoren einzeln nach ihrem Environment-Wert zu fragen, hält der EnvironmentCoordinator seinen letzten gepushten Wert
in einem EnvironmentSnapshot und gibt diesen per Ask zurück. Dies vermeidet inkonsistente Snapshots und reduziert den Weg, den zurückgelegt werden muss.


### 3. Single messageAdapter pro Klasse
Pekko erlaubt nur einen messageAdapter pro Message-Klasse pro Actor. Eine zweite Registrierung für die gleiche Klasse ersetzt die erste.
Im EnvironmentCoordinator müssen wir Receptionist.Listing für zwei Service-Keys (TemperatureSensor & WeatherSensor) verarbeiten.
Lösung ist ein einziger Adapter, der intern anhand listing.isForKey() entscheidet, welche Command-Variante er erzeugt.


### 4. Blocking-IO-Dispatcher für PersistenceActor
JDBC-Calls blockieren den ausführenden Thread. Wenn der PersistenceActor auf dem Default-Dispatcher liefe, würden seine synchronen DB-Inserts Threads aus dem geteilten Pool belegen und andere
Aktoren ausbremsen. Wir spawnen ihn deshalb auf einem blocking-io-dispatcher
(siehe `src/main/resources/orderprocessor.conf`).

Vier Threads im Pool sind genug für unsere Umgebung.


### 5. JDBC statt EventSourcedBehavior
Wir haben nur ein JDBC verwendet statt ORM.
Event Sourcing würde zwar passen, da die Orders immutable sind. Sie werden jedoch nicht angepasst und wir fanden es wäre ein Overkill.

H2 läuft im AUTO_SERVER-Modus, damit man die DB auch von außen inspizieren kann.

### 6. Per-Session-OrderProcessor (Child) statt geteilter Worker
Pro Bestellung spawnt der Fridge einen neuen OrderProcessor, der genau eine ProcessOrder bearbeitet und sich danach selbst stoppt.
Somit hat jeder Order ihren eigenen Lifecycle und keine geteilten Zustände.
Dies ist das Per-Session-Child-Pattern. 


### 7. Auto-Reorder bei leerem Bestand
Sobald ein Produkt im Fridge auf 0 fällt, triggert der Fridge eine Auto-Order über denselben Mechanismus wie eine User-Order, nur ohne replyTo.
Wenn der Auto-Reorder die Kapazitätsgrenzen nicht passen würde, wir er mit Warn-Log abgelehnt. Das Produkt bleibt dann weg.

### 8. Reordering-UX im Frontend
Wenn die letzte Einheit konsumiert wird, würde die Produktzeile in der UI normalerweise verschwinden (quantity > 0 Filter), bis Sekunden später die Auto-Order durchkommt.
Stattdessen werden solche Produkte als pending reorder im Frontend angezeigt und dann mit einer "Reordering..."-Pill.
Sobald das Backend wieder Bestand > 0 meldet, fällt der Marker durch den Flag pendingReorders.has(id) && quantity === 0 automatisch weg.


### 9. Beendigung & Shutdown-Hook
Beide ActorSystems registrieren einen JVM-Shutdown-Hook, der system.terminate() aufruft und bis zu 5 s auf sauberes Herunterfahren wartet. So werden gRPC-Server, HTTP-Binding und MQTT-Verbindung
beim Strg+C ordentlich geschlossen statt abrupt abzureißen.


## Weiterführende Doku
[INTERACTION_PATTERNS.md](INTERACTION_PATTERNS.md)