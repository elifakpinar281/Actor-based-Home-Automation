package at.fhv.sysarch.lab2.orderprocessor.exception;

public abstract class OrderProcessorException extends RuntimeException {
    private final OrderProcessorErrorCode errorCode;

    protected OrderProcessorException(String message, OrderProcessorErrorCode errorCode) {
        super(message);
        this.errorCode = errorCode;
    }

    protected OrderProcessorException(String message, OrderProcessorErrorCode errorCode, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
    }

    public OrderProcessorErrorCode getErrorCode() {
        return errorCode;
    }

    @Override
    public String toString() {
        return "[" + errorCode + "] " + getMessage();
    }
}