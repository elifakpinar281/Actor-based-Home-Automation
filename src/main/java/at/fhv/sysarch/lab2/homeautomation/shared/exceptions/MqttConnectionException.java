package at.fhv.sysarch.lab2.homeautomation.shared.exceptions;

public class MqttConnectionException extends DomainException {
    public MqttConnectionException(String brokerUrl, String reason) {
        super("Failed to connect to MQTT broker at '" + brokerUrl + "' : " + reason, ErrorCode.MQTT_CONNECTION_FAILURE);
    }

    public MqttConnectionException(String brokerUrl, Throwable cause) {
        super("Failed to connect to MQTT broker at '" + brokerUrl + "' : " + cause.getMessage(), ErrorCode.MQTT_CONNECTION_FAILURE, cause);
    }

}
