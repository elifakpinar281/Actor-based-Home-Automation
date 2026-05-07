package at.fhv.sysarch.lab2.homeautomation.shared.exceptions;

public class MqttConnectionException extends DomainException {
    public MqttConnectionException(String brokerUrl, String reason) {
        super("Failed to connect to MQTT broker at '" + brokerUrl + "' : " + reason, ErrorCode.MQTT001);
    }

    public MqttConnectionException(String brokerUrl, Throwable cause) {
        super("Failed to connect to MQTT broker at '" + brokerUrl + "' : " + cause.getMessage(), ErrorCode.MQTT001, cause);
    }

}
