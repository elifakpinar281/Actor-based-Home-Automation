package at.fhv.sysarch.lab2.homeautomation.environment;

import at.fhv.sysarch.lab2.homeautomation.shared.exceptions.InvalidWeatherConditionException;
import at.fhv.sysarch.lab2.homeautomation.shared.exceptions.MqttConnectionException;
import at.fhv.sysarch.lab2.homeautomation.shared.model.WeatherCondition;
import org.apache.pekko.actor.typed.ActorRef;
import org.eclipse.paho.client.mqttv3.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MqttEnvironmentClient {
    private static final Logger log = LoggerFactory.getLogger(MqttEnvironmentClient.class);
    private final ActorRef<EnvironmentSwitch.EnvironmentSwitchCommand> environmentSwitch;
    private IMqttClient mqttClient;

    public MqttEnvironmentClient(ActorRef<EnvironmentSwitch.EnvironmentSwitchCommand> environmentSwitch) {
        this.environmentSwitch = environmentSwitch;
    }

    public void connect() {
        try {
            mqttClient = new MqttClient("tcp://10.0.40.161.1883", MqttClient.generateClientId());
            MqttConnectOptions options = new MqttConnectOptions();
            options.setAutomaticReconnect(true);
            options.setCleanSession(true);
            mqttClient.connect(options);
            mqttClient.subscribe("#", this::onMessageReceived);
        } catch (MqttException exception) {
            throw new MqttConnectionException("Mqtt connection failed", exception);
        }
    }

    private void onMessageReceived(String topic, MqttMessage message) {
        String payload = new String(message.getPayload());
        if (topic.contains("temperature")) {
            try {
                double temperature = Double.parseDouble(payload.trim());
                environmentSwitch.tell(new EnvironmentSwitch.MqttTemperatureUpdate(temperature));
            } catch (NumberFormatException exception) {
                throw new MqttConnectionException("Malformed temperature payload: " + payload, exception);
            }
        } else if (topic.contains("weather")) {
            try {
                WeatherCondition condition = WeatherCondition.valueOf(payload.trim().toUpperCase());
                environmentSwitch.tell(new EnvironmentSwitch.MqttWeatherUpdate(condition));
            } catch (InvalidWeatherConditionException exception) {
                throw new InvalidWeatherConditionException("Invalid weather condition: " + payload);
            }
        }
    }

    public void disconnect() {
        try {
            if (mqttClient != null && mqttClient.isConnected()) {
                mqttClient.disconnect();
            }
        } catch (MqttException exception) {
            throw new MqttConnectionException("MQTT disconnect failed", exception);
        }
    }
}
