package at.fhv.sysarch.lab2.homeautomation.shared.exceptions;

public class MqttMessageParseException extends DomainException {
    public MqttMessageParseException(String topic, String payload, String reason) {
        super("Cannot parse MQTT message on topic '" + topic + "' with payload '" + payload + "': " + reason, ErrorCode.MQTT002);
    }
}
