package at.fhv.sysarch.lab2.homeautomation.shared.exceptions;

public class KeyNotFoundException extends DomainException {
    private final String json;

    public KeyNotFoundException(String message, String json) {
        super("Key not found for: " + json, ErrorCode.KEY001);
        this.json = json;
    }
}
