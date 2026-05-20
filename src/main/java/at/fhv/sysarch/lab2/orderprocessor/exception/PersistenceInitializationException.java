package at.fhv.sysarch.lab2.orderprocessor.exception;

public class PersistenceInitializationException extends OrderProcessorException {
    public PersistenceInitializationException(String reason) {
        super("Persistence initialization failed: " + reason, OrderProcessorErrorCode.PERSISTENCE_INITIALIZATION_FAILURE);
    }

    public PersistenceInitializationException(String reason, Throwable cause) {
        super("Persistence initialization failed: " + reason, OrderProcessorErrorCode.PERSISTENCE_INITIALIZATION_FAILURE, cause);
    }
}
