package at.fhv.sysarch.lab2.homeautomation.environment;

import at.fhv.sysarch.lab2.homeautomation.shared.model.exceptions.InvalidWeatherConditionException;
import at.fhv.sysarch.lab2.homeautomation.shared.model.exceptions.MqttConnectionException;
import at.fhv.sysarch.lab2.homeautomation.shared.model.exceptions.MqttMessageParseException;
import at.fhv.sysarch.lab2.homeautomation.shared.model.environment.WeatherCondition;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.pekko.actor.typed.ActorRef;
import org.eclipse.paho.client.mqttv3.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

// Lebt außerhalb des Actor-Systems. Eingehende Nachrichten werden geparst und als Message an EnvironmentCoordinator weitergegeben
// Wenn der Broker beim Start nicht erreichbar ist, versucht der Client alle 60 Sekunden neu zu verbinden
public class MqttEnvironmentClient {
    private static final Logger log = LoggerFactory.getLogger(MqttEnvironmentClient.class);

    private static final String BROKER_URL = "tcp://10.0.40.161:1883";
    private static final String SUBSCRIBE_ALL_TOPICS = "#";
    private static final String TEMPERATURE_TOPIC_MARKER = "temperature";
    private static final String WEATHER_TOPIC_MARKER = "weather";
    private static final String JSON_FIELD_TEMPERATURE = "temperature";
    private static final String JSON_FIELD_CONDITION = "condition";

    private static final long RECONNECT_INTERVAL_SECONDS = 60;

    private final ActorRef<EnvironmentCoordinator.Command> coordinator;
    private final ObjectMapper jsonMapper = new ObjectMapper();
    private final ScheduledExecutorService reconnectScheduler =
            Executors.newSingleThreadScheduledExecutor(runnable -> {
                Thread thread = new Thread(runnable, "mqtt-reconnect-scheduler");
                thread.setDaemon(true);
                return thread;
            });

    private IMqttClient mqttClient;
    private ScheduledFuture<?> reconnectTask;
    private volatile boolean shutdown = false;

    public MqttEnvironmentClient(ActorRef<EnvironmentCoordinator.Command> coordinator) {
        this.coordinator = coordinator;
    }

    public void connect() {
        startReconnectScheduler();
        attemptConnect();
    }

    private synchronized void attemptConnect() {
        if (shutdown || isConnected()) {
            return;
        }
        try {
            mqttClient = new MqttClient(BROKER_URL, MqttClient.generateClientId());

            MqttConnectOptions options = new MqttConnectOptions();
            options.setAutomaticReconnect(true);
            options.setCleanSession(true);
            options.setConnectionTimeout(10);

            mqttClient.connect(options);
            mqttClient.subscribe(SUBSCRIBE_ALL_TOPICS, this::onMessageReceived);
            log.info("MQTT client connected to {} and subscribed to '{}'", BROKER_URL, SUBSCRIBE_ALL_TOPICS);
        } catch (MqttException cause) {
            mqttClient = null;
            throw new MqttConnectionException(BROKER_URL, cause);
        }
    }

    private void startReconnectScheduler() {
        if (reconnectTask != null && !reconnectTask.isCancelled()) {
            return;
        }
        reconnectTask = reconnectScheduler.scheduleAtFixedRate(() -> {
            if (shutdown || isConnected()) {
                return;
            }
            try {
                log.info("MQTT client not connected - attempting reconnect to {}", BROKER_URL);
                attemptConnect();
                log.info("MQTT client successfully reconnected to {}", BROKER_URL);
            } catch (MqttConnectionException ex) {
                log.warn("MQTT reconnect attempt failed - will retry in {}s: {}",
                        RECONNECT_INTERVAL_SECONDS, ex.getMessage());
            }
        }, RECONNECT_INTERVAL_SECONDS, RECONNECT_INTERVAL_SECONDS, TimeUnit.SECONDS);
        log.info("MQTT auto-reconnect scheduler started (interval: {}s)", RECONNECT_INTERVAL_SECONDS);
    }

    private boolean isConnected() {
        return mqttClient != null && mqttClient.isConnected();
    }

    public void disconnect() {
        shutdown = true;
        if (reconnectTask != null) {
            reconnectTask.cancel(false);
        }
        reconnectScheduler.shutdown();

        try {
            if (mqttClient != null && mqttClient.isConnected()) {
                mqttClient.disconnect();
                log.info("MQTT client disconnected from {}", BROKER_URL);
            }
        } catch (MqttException cause) {
            throw new MqttConnectionException(BROKER_URL, cause);
        }
    }

    private void onMessageReceived(String topic, MqttMessage message) {
        String payload = new String(message.getPayload()).trim();
        try {
            if (topic.contains(TEMPERATURE_TOPIC_MARKER)) {
                handleTemperatureMessage(topic, payload);
            } else if (topic.contains(WEATHER_TOPIC_MARKER)) {
                handleWeatherMessage(topic, payload);
            }
        } catch (MqttMessageParseException | InvalidWeatherConditionException ex) {
            log.warn("Dropping malformed MQTT message on topic '{}': {}", topic, ex.getMessage());
        }
    }

    private void handleTemperatureMessage(String topic, String payload) {
        double celsius = extractTemperatureFromJson(topic, payload);
        coordinator.tell(new EnvironmentCoordinator.MqttTemperatureUpdate(celsius));
    }

    private void handleWeatherMessage(String topic, String payload) {
        String conditionString = extractConditionFromJson(topic, payload);
        WeatherCondition condition = WeatherCondition.fromString(conditionString);
        coordinator.tell(new EnvironmentCoordinator.MqttWeatherUpdate(condition));
    }

    private double extractTemperatureFromJson(String topic, String payload) {
        JsonNode root = parseJson(topic, payload);
        JsonNode temperatureNode = root.get(JSON_FIELD_TEMPERATURE);
        if (temperatureNode == null || temperatureNode.isNull()) {
            throw new MqttMessageParseException(topic, payload, "missing field '" + JSON_FIELD_TEMPERATURE + "'");
        }
        try {
            return Double.parseDouble(temperatureNode.asText());
        } catch (NumberFormatException cause) {
            throw new MqttMessageParseException(topic, payload, "field '" + JSON_FIELD_TEMPERATURE + "' is not a valid number");
        }
    }

    private String extractConditionFromJson(String topic, String payload) {
        JsonNode root = parseJson(topic, payload);
        JsonNode conditionNode = root.get(JSON_FIELD_CONDITION);
        if (conditionNode == null || conditionNode.isNull()) {
            throw new MqttMessageParseException(topic, payload, "missing field '" + JSON_FIELD_CONDITION + "'");
        }
        return conditionNode.asText();
    }

    private JsonNode parseJson(String topic, String payload) {
        try {
            return jsonMapper.readTree(payload);
        } catch (Exception cause) {
            throw new MqttMessageParseException(topic, payload, "payload is not valid JSON");
        }
    }
}