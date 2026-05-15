package at.fhv.sysarch.lab2.homeautomation.uihandler.routes;

import at.fhv.sysarch.lab2.homeautomation.devices.AirCondition;
import at.fhv.sysarch.lab2.homeautomation.devices.Blinds;
import at.fhv.sysarch.lab2.homeautomation.devices.MediaStation;
import at.fhv.sysarch.lab2.homeautomation.environment.EnvironmentCoordinator;
import at.fhv.sysarch.lab2.homeautomation.environment.EnvironmentSnapshot;
import at.fhv.sysarch.lab2.homeautomation.uihandler.dtos.StatusDto;
import at.fhv.sysarch.lab2.homeautomation.uihandler.exception.ErrorResponse;
import org.apache.pekko.actor.typed.ActorRef;
import org.apache.pekko.actor.typed.ActorSystem;
import org.apache.pekko.actor.typed.javadsl.AskPattern;
import org.apache.pekko.http.javadsl.marshallers.jackson.Jackson;
import org.apache.pekko.http.javadsl.model.StatusCodes;
import org.apache.pekko.http.javadsl.server.AllDirectives;
import org.apache.pekko.http.javadsl.server.Route;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

public class StatusRoutes extends AllDirectives {
    private static final Duration TIMEOUT = Duration.ofSeconds(5);

    private final ActorRef<EnvironmentCoordinator.Command> environmentCoordinator;
    private final ActorRef<AirCondition.AirConditionCommand> airConditionActor;
    private final ActorRef<Blinds.BlindsCommand> blindsActor;
    private final ActorRef<MediaStation.MediaStationCommand> mediaStationActor;
    private final ActorSystem<?> system;

    public StatusRoutes(ActorRef<EnvironmentCoordinator.Command> environmentCoordinator, ActorRef<AirCondition.AirConditionCommand> airConditionActor,
            ActorRef<Blinds.BlindsCommand> blindsActor, ActorRef<MediaStation.MediaStationCommand> mediaStationActor, ActorSystem<?> system) {
        this.environmentCoordinator = environmentCoordinator;
        this.airConditionActor = airConditionActor;
        this.blindsActor = blindsActor;
        this.mediaStationActor = mediaStationActor;
        this.system = system;
    }

    public Route routes() {
        return concat(
                path("status", () -> get(this::getAggregatedStatus)),
                path("devices/status", () -> get(this::getDeviceStatus))
        );
    }

    private Route getAggregatedStatus() {
        return onComplete(
                AskPattern.ask(environmentCoordinator, EnvironmentCoordinator.GetCurrentState::new, TIMEOUT, system.scheduler()),
                envResult -> {
                    if (!envResult.isSuccess()) {
                        return complete(StatusCodes.INTERNAL_SERVER_ERROR, new ErrorResponse("env state failed"), Jackson.marshaller());
                    }
                    EnvironmentSnapshot env = envResult.get();
                    return onComplete(
                            AskPattern.ask(airConditionActor, AirCondition.GetStatus::new, TIMEOUT, system.scheduler()),
                            acResult -> {
                                if (!acResult.isSuccess()) {
                                    return complete(StatusCodes.INTERNAL_SERVER_ERROR, new ErrorResponse("ac status failed"), Jackson.marshaller());
                                }
                                AirCondition.StatusResponse ac = acResult.get();
                                return onComplete(
                                        AskPattern.ask(blindsActor, Blinds.GetStatus::new, TIMEOUT, system.scheduler()),
                                        blindsResult -> {
                                            if (!blindsResult.isSuccess()) {
                                                return complete(StatusCodes.INTERNAL_SERVER_ERROR, new ErrorResponse("blinds status failed"), Jackson.marshaller());
                                            }
                                            Blinds.StatusResponse blinds = blindsResult.get();
                                            return onComplete(
                                                    AskPattern.ask(mediaStationActor, MediaStation.GetStatus::new, TIMEOUT, system.scheduler()),
                                                    mediaResult -> {
                                                        if (!mediaResult.isSuccess()) {
                                                            return complete(StatusCodes.INTERNAL_SERVER_ERROR, new ErrorResponse("media status failed"), Jackson.marshaller());
                                                        }
                                                        MediaStation.StatusResponse media = mediaResult.get();
                                                        return complete(StatusCodes.OK,
                                                                new StatusDto(
                                                                        Math.round(env.temperature().value() * 10.0) / 10.0,
                                                                        env.weather().name(),
                                                                        env.mode().name(),
                                                                        ac.isPoweredOn(),
                                                                        ac.isCooling(),
                                                                        blinds.areClosed(),
                                                                        media.isPlaying(),
                                                                        media.currentMovie()
                                                                ),
                                                                Jackson.marshaller());
                                                    }
                                            );
                                        }
                                );
                            }
                    );
                }
        );
    }

    private Route getDeviceStatus() {
        Map<String, String> status = new HashMap<>();
        status.put("fridge", "active");
        status.put("mediaStation", "active");
        status.put("blinds", "active");
        status.put("ac", "active");
        status.put("environment", "active");
        return complete(StatusCodes.OK, status, Jackson.marshaller());
    }
}