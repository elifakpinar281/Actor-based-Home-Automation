package at.fhv.sysarch.lab2.homeautomation.environment.mqtt;


import at.fhv.sysarch.lab2.homeautomation.environment.EnvironmentActor;
import at.fhv.sysarch.lab2.homeautomation.shared.exceptions.KeyNotFoundException;
import at.fhv.sysarch.lab2.homeautomation.shared.exceptions.MqttConnectionException;
import at.fhv.sysarch.lab2.homeautomation.shared.exceptions.MqttMessageParseException;
import at.fhv.sysarch.lab2.homeautomation.shared.model.WeatherCondition;
import org.apache.pekko.actor.typed.ActorRef;
import org.apache.pekko.actor.typed.Behavior;
import org.apache.pekko.actor.typed.PostStop;
import org.apache.pekko.actor.typed.javadsl.AbstractBehavior;
import org.apache.pekko.actor.typed.javadsl.ActorContext;
import org.apache.pekko.actor.typed.javadsl.Behaviors;
import org.apache.pekko.actor.typed.javadsl.Receive;
import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken;
import org.eclipse.paho.client.mqttv3.MqttCallback;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;



public class MqttEnvironmentSource extends AbstractBehavior<MqttEnvironmentSource.MqttCommand> {
    public interface MqttCommand {}

    public record Connect() implements MqttCommand {}
    public record Disconnect() implements MqttCommand {}
    public record MqttMessageReceived(String topic, String payload) implements MqttCommand {}
    public record ConnectionLost(String reason) implements MqttCommand {}

    private static final String DEFAULT_BROKER_URL = "tcp://10.0.40.161:1883";
    private static final String SUBSCRIBE_TOPIC = "#";
    private static final String TEMPERATURE_TOPIC_KEYWORD = "temperature";
    private static final String WEATHER_TOPIC_KEYWORD = "weather";

    private final ActorRef<EnvironmentActor.EnvironmentCommand> environmentActor;
    private final String brokerUrl;
    private MqttClient mqttClient;

    public static Behavior<MqttCommand> create(ActorRef<EnvironmentActor.EnvironmentCommand> environmentActor) {
        return create(environmentActor, DEFAULT_BROKER_URL);
    }

    public static Behavior<MqttCommand> create(ActorRef<EnvironmentActor.EnvironmentCommand> environmentActor, String brokerUrl) {
        return Behaviors.setup(context -> new MqttEnvironmentSource(context, environmentActor, brokerUrl));
    }

    private MqttEnvironmentSource(ActorContext<MqttCommand> context, ActorRef<EnvironmentActor.EnvironmentCommand> environmentActor,
            String brokerUrl) {
        super(context);
        this.environmentActor = environmentActor;
        this.brokerUrl = brokerUrl;
        this.mqttClient = null;
        context.getSelf().tell(new Connect());
        getContext().getLog().info("MqttEnvironmentSource created (broker: {})", brokerUrl);
    }

    @Override
    public Receive<MqttCommand> createReceive() {
        return newReceiveBuilder()
                .onMessage(Connect.class, this::onConnect)
                .onMessage(Disconnect.class, this::onDisconnect)
                .onMessage(MqttMessageReceived.class, this::onMqttMessageReceived)
                .onMessage(ConnectionLost.class, this::onConnectionLost)
                .onSignal(PostStop.class, signal -> onPostStop())
                .build();
    }

    private Behavior<MqttCommand> onConnect(Connect command) {
        if (mqttClient != null && mqttClient.isConnected()) {
            getContext().getLog().info("MQTT client is already connected");
            return this;
        }

        try {
            String clientId = "home-automation-" + getContext().getSelf().path().name() + "-" + System.currentTimeMillis();
            mqttClient = new MqttClient(brokerUrl, clientId);

            MqttConnectOptions options = new MqttConnectOptions();
            options.setCleanSession(true);
            options.setConnectionTimeout(10);
            options.setAutomaticReconnect(true);
            ActorRef<MqttCommand> selfRef = getContext().getSelf();
            mqttClient.setCallback(new MqttCallback() {

                @Override
                public void connectionLost(Throwable cause) {
                    selfRef.tell(new ConnectionLost(cause.getMessage()));
                }

                @Override
                public void messageArrived(String topic, MqttMessage message) {
                    String payload = new String(message.getPayload());
                    selfRef.tell(new MqttMessageReceived(topic, payload));
                }

                @Override
                public void deliveryComplete(IMqttDeliveryToken token) {}
            });

            mqttClient.connect(options);
            mqttClient.subscribe(SUBSCRIBE_TOPIC);
            getContext().getLog().info("MQTT connected to {} and subscribed to '{}'", brokerUrl, SUBSCRIBE_TOPIC);

        } catch (MqttException e) {
            getContext().getLog().error("MQTT connection failed: {}", e.getMessage());
            throw new MqttConnectionException(brokerUrl, e);
        }
        return this;
    }

    private Behavior<MqttCommand> onDisconnect(Disconnect command) {
        disconnectMqttClient();
        return this;
    }

    private Behavior<MqttCommand> onMqttMessageReceived(MqttMessageReceived message) {
        String topic = message.topic().toLowerCase();
        String payload = message.payload().trim();
        getContext().getLog().debug("MQTT message received — topic: '{}', payload: '{}'", topic, payload);

        if (topic.contains(TEMPERATURE_TOPIC_KEYWORD)) {
            handleTemperatureMessage(message.topic(), payload);
        } else if (topic.contains(WEATHER_TOPIC_KEYWORD)) {
            handleWeatherMessage(message.topic(), payload);
        } else {
            getContext().getLog().debug("Ignoring MQTT message on unknown topic: '{}'", message.topic());
        }
        return this;
    }

    private Behavior<MqttCommand> onConnectionLost(ConnectionLost event) {
        getContext().getLog().warn("MQTT connection lost: {}. Paho will attempt automatic reconnect.", event.reason());
        return this;
    }

    private void handleTemperatureMessage(String topic, String payload) {
        try {
            String value = extractJsonValue(payload, "temperature");
            double temperature = Double.parseDouble(value);
            environmentActor.tell(new EnvironmentActor.SetTemperature(temperature));
            getContext().getLog().info("MQTT temperature received: {}°C (topic: {})", temperature, topic);
        } catch (Exception e) {
            getContext().getLog().warn("Cannot parse temperature from MQTT payload: '{}' on topic '{}'", payload, topic);
        }
    }

    private void handleWeatherMessage(String topic, String payload) {
        try {
            String value = extractJsonValue(payload, "condition");
            WeatherCondition condition = WeatherCondition.fromString(value);
            environmentActor.tell(new EnvironmentActor.SetWeather(condition));
            getContext().getLog().info("MQTT weather received: {} (topic: {})", condition, topic);
        } catch (Exception e) {
            getContext().getLog().warn("Cannot parse weather from MQTT payload: '{}' on topic '{}' — {}", payload, topic, e.getMessage());
        }
    }

    private void disconnectMqttClient() {
        if (mqttClient == null) {
            return;
        }

        try {
            if (mqttClient.isConnected()) {
                mqttClient.disconnect();
                getContext().getLog().info("MQTT client disconnected from {}", brokerUrl);
            }
            mqttClient.close();
        } catch (MqttException e) {
            getContext().getLog().warn("Error while disconnecting MQTT client: {}", e.getMessage());
        } finally {
            mqttClient = null;
        }
    }

    private MqttEnvironmentSource onPostStop() {
        disconnectMqttClient();
        getContext().getLog().info("MqttEnvironmentSource stopped");
        return this;
    }

    private String extractJsonValue(String json, String key) {
        String search = "\"" + key + "\":";
        int idx = json.indexOf(search);
        if (idx == -1) {
            throw new KeyNotFoundException("Key not found for key: " + key, json);
        }

        int start = idx + search.length();
        if (json.charAt(start) == '"') {
            start++;
            int end = json.indexOf('"', start);
            return json.substring(start, end);
        } else {
            int end = json.indexOf(',', start);
            if (end == -1) end = json.indexOf('}', start);
            return json.substring(start, end).trim();
        }
    }
}

