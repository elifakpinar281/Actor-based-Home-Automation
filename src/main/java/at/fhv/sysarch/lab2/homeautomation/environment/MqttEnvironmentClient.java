package at.fhv.sysarch.lab2.homeautomation.environment;

import at.fhv.sysarch.lab2.homeautomation.shared.model.exceptions.InvalidWeatherConditionException;
import at.fhv.sysarch.lab2.homeautomation.shared.model.exceptions.MqttConnectionException;
import at.fhv.sysarch.lab2.homeautomation.shared.model.exceptions.MqttMessageParseException;
import at.fhv.sysarch.lab2.homeautomation.shared.model.environment.WeatherCondition;
import org.apache.pekko.actor.typed.ActorRef;
import org.eclipse.paho.client.mqttv3.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MqttEnvironmentClient {
    private static final Logger log = LoggerFactory.getLogger(MqttEnvironmentClient.class);

    private static final String BROKER_URL = "tcp://10.0.40.161:1883";
    private static final String SUBSCRIBE_ALL_TOPICS = "#";
    private static final String TEMPERATURE_TOPIC_MARKER = "temperature";
    private static final String WEATHER_TOPIC_MARKER = "weather";

    private final ActorRef<EnvironmentCoordinator.Command> coordinator;
    private IMqttClient mqttClient;

    public MqttEnvironmentClient(ActorRef<EnvironmentCoordinator.Command> coordinator) {
        this.coordinator = coordinator;
    }

    public void connect() {
        try {
            mqttClient = new MqttClient(BROKER_URL, MqttClient.generateClientId());

            MqttConnectOptions options = new MqttConnectOptions();
            options.setAutomaticReconnect(true);
            options.setCleanSession(true);

            mqttClient.connect(options);
            mqttClient.subscribe(SUBSCRIBE_ALL_TOPICS, this::onMessageReceived);
            log.info("MQTT client connected to {} and subscribed to '{}'", BROKER_URL, SUBSCRIBE_ALL_TOPICS);
        } catch (MqttException cause) {
            throw new MqttConnectionException(BROKER_URL, cause);
        }
    }

    public void disconnect() {
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
            log.warn("Dropping malformed MQTT message: {}", ex.getMessage());
        }
    }

    private void handleTemperatureMessage(String topic, String payload) {
        double celsius;
        try {
            celsius = Double.parseDouble(payload);
        } catch (NumberFormatException cause) {
            throw new MqttMessageParseException(topic, payload, "expected a number for temperature");
        }
        coordinator.tell(new EnvironmentCoordinator.MqttTemperatureUpdate(celsius));
    }

    private void handleWeatherMessage(String topic, String payload) {
        WeatherCondition condition = WeatherCondition.fromString(payload);
        coordinator.tell(new EnvironmentCoordinator.MqttWeatherUpdate(condition));
    }
}