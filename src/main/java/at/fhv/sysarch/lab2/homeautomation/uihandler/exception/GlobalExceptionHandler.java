package at.fhv.sysarch.lab2.homeautomation.uihandler.exception;

import at.fhv.sysarch.lab2.homeautomation.shared.exceptions.DomainException;
import at.fhv.sysarch.lab2.homeautomation.shared.exceptions.FridgeException;
import at.fhv.sysarch.lab2.homeautomation.shared.exceptions.InvalidModeException;
import at.fhv.sysarch.lab2.homeautomation.shared.exceptions.InvalidOrderException;
import at.fhv.sysarch.lab2.homeautomation.shared.exceptions.InvalidTemperatureException;
import at.fhv.sysarch.lab2.homeautomation.shared.exceptions.InvalidWeatherConditionException;
import at.fhv.sysarch.lab2.homeautomation.shared.exceptions.KeyNotFoundException;
import at.fhv.sysarch.lab2.homeautomation.shared.exceptions.MqttConnectionException;
import at.fhv.sysarch.lab2.homeautomation.shared.exceptions.MqttMessageParseException;
import at.fhv.sysarch.lab2.homeautomation.shared.exceptions.OrderProcessingException;
import at.fhv.sysarch.lab2.homeautomation.shared.exceptions.ProductNotAvailableException;
import at.fhv.sysarch.lab2.homeautomation.shared.exceptions.SimulationNotActiveException;
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
                .match(InvalidTemperatureException.class, ex -> respond(StatusCodes.BAD_REQUEST, ex))
                .match(InvalidWeatherConditionException.class, ex -> respond(StatusCodes.BAD_REQUEST, ex))
                .match(InvalidModeException.class, ex -> respond(StatusCodes.BAD_REQUEST, ex))
                .match(InvalidOrderException.class, ex -> respond(StatusCodes.BAD_REQUEST, ex))
                .match(MqttMessageParseException.class, ex -> respond(StatusCodes.BAD_REQUEST, ex))

                .match(KeyNotFoundException.class, ex -> respond(StatusCodes.NOT_FOUND, ex))
                .match(ProductNotAvailableException.class, ex -> respond(StatusCodes.NOT_FOUND, ex))

                .match(FridgeException.class, ex -> respond(StatusCodes.UNPROCESSABLE_ENTITY, ex))
                .match(OrderProcessingException.class, ex -> respond(StatusCodes.UNPROCESSABLE_ENTITY, ex))

                .match(MqttConnectionException.class, ex -> respond(StatusCodes.SERVICE_UNAVAILABLE, ex))
                .match(SimulationNotActiveException.class, ex -> respond(StatusCodes.SERVICE_UNAVAILABLE, ex))

                .match(IllegalArgumentException.class, ex -> {
                    log.warn("Bad request: {}", ex.getMessage());
                    return complete(StatusCodes.BAD_REQUEST, new ErrorResponse("GENERIC_BAD_REQUEST", ex.getMessage()), Jackson.marshaller());
                })

                .match(DomainException.class, ex -> respond(StatusCodes.INTERNAL_SERVER_ERROR, ex))

                .matchAny(t -> {
                    log.error("Unhandled exception in HTTP route", t);
                    return complete(StatusCodes.INTERNAL_SERVER_ERROR, new ErrorResponse("INTERNAL_ERROR", "An unexpected error occurred"), Jackson.marshaller());
                })
                .build();
    }

    private Route respond(StatusCode status, DomainException ex) {
        log.warn("[{}] {} → HTTP {}", ex.getErrorCode(), ex.getMessage(), status.intValue());
        return complete(status, new ErrorResponse(ex.getErrorCode().name(), ex.getMessage()), Jackson.marshaller());
    }
}