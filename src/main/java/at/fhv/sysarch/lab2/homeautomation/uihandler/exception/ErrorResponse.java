package at.fhv.sysarch.lab2.homeautomation.uihandler.exception;

public record ErrorResponse(
        String errorCode,
        String message
) {}