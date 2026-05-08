package at.fhv.sysarch.lab2.homeautomation.shared.exceptions;

public class KeyNotFoundException extends DomainException {
    public KeyNotFoundException(String message, String json) {
        super("Key not found for: " + json, ErrorCode.KEY001);
    }

    public KeyNotFoundException(String message) {
        super("Key not found", ErrorCode.KEY001);
    }
}
