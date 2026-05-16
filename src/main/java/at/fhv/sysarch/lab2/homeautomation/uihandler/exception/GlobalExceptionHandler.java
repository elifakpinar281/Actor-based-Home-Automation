package at.fhv.sysarch.lab2.homeautomation.uihandler.exception;

import at.fhv.sysarch.lab2.homeautomation.shared.exceptions.*;
import org.apache.pekko.http.javadsl.marshallers.jackson.Jackson;
import org.apache.pekko.http.javadsl.model.StatusCode;
import org.apache.pekko.http.javadsl.model.StatusCodes;
import org.apache.pekko.http.javadsl.server.AllDirectives;
import org.apache.pekko.http.javadsl.server.ExceptionHandler;
import org.apache.pekko.http.javadsl.server.Route;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class GlobalExceptionHandler extends AllDirectives {
    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    private GlobalExceptionHandler() {}

    public static ExceptionHandler create() {
        return new GlobalExceptionHandler().buildHandler();
    }

    private ExceptionHandler buildHandler() {
        return ExceptionHandler.newBuilder()
                .match(InvalidRequestException.class, ex -> respond(StatusCodes.BAD_REQUEST, ex))
                .match(InvalidOrderException.class, ex -> respond(StatusCodes.BAD_REQUEST, ex))
                .match(InvalidModeException.class, ex -> respond(StatusCodes.BAD_REQUEST, ex))
                .match(InvalidTemperatureException.class, ex -> respond(StatusCodes.BAD_REQUEST, ex))
                .match(InvalidWeatherConditionException.class, ex -> respond(StatusCodes.BAD_REQUEST, ex))
                .match(MqttMessageParseException.class, ex -> respond(StatusCodes.BAD_REQUEST, ex))

                .match(ProductNotAvailableException.class, ex -> respond(StatusCodes.NOT_FOUND, ex))

                .match(FridgeException.class, ex -> respond(StatusCodes.UNPROCESSABLE_ENTITY, ex))

                .match(MqttConnectionException.class, ex -> respond(StatusCodes.SERVICE_UNAVAILABLE, ex))
                .match(SimulationNotActiveException.class, ex -> respond(StatusCodes.SERVICE_UNAVAILABLE, ex))

                .match(NumberFormatException.class, ex -> {
                    log.warn("Invalid number in request parameter: {}", ex.getMessage());
                    return complete(StatusCodes.BAD_REQUEST, new ErrorResponse("REQUEST001", "Invalid number: " + ex.getMessage()),
                            Jackson.marshaller());
                })
                .match(IllegalArgumentException.class, ex -> {
                    log.warn("Bad request: {}", ex.getMessage());
                    return complete(StatusCodes.BAD_REQUEST, new ErrorResponse("REQUEST001", ex.getMessage()),
                            Jackson.marshaller());
                })

                .match(DomainException.class, ex -> respond(StatusCodes.INTERNAL_SERVER_ERROR, ex))

                .matchAny(throwable -> {
                    log.error("Unhandled exception in HTTP route", throwable);
                    return complete(StatusCodes.INTERNAL_SERVER_ERROR, new ErrorResponse("INTERNAL_ERROR", "An unexpected error occurred"),
                            Jackson.marshaller());
                })
                .build();
    }

    private Route respond(StatusCode status, DomainException ex) {
        log.warn("[{}] {} → HTTP {}", ex.getErrorCode(), ex.getMessage(), status.intValue());
        return complete(status, new ErrorResponse(ex.getErrorCode().name(), ex.getMessage()), Jackson.marshaller());
    }
}