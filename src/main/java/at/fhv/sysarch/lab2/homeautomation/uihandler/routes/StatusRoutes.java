package at.fhv.sysarch.lab2.homeautomation.uihandler.routes;

import at.fhv.sysarch.lab2.homeautomation.devices.AirCondition;
import at.fhv.sysarch.lab2.homeautomation.devices.Blinds;
import at.fhv.sysarch.lab2.homeautomation.devices.MediaStation;
import at.fhv.sysarch.lab2.homeautomation.environment.EnvironmentCoordinator;
import at.fhv.sysarch.lab2.homeautomation.environment.EnvironmentSnapshot;
import at.fhv.sysarch.lab2.homeautomation.uihandler.dtos.StatusDto;
import org.apache.pekko.actor.typed.ActorRef;
import org.apache.pekko.actor.typed.ActorSystem;
import org.apache.pekko.actor.typed.javadsl.AskPattern;
import org.apache.pekko.http.javadsl.marshallers.jackson.Jackson;
import org.apache.pekko.http.javadsl.model.StatusCodes;
import org.apache.pekko.http.javadsl.server.AllDirectives;
import org.apache.pekko.http.javadsl.server.Route;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CompletionStage;

public class StatusRoutes extends AllDirectives {
    private static final Duration ASK_TIMEOUT = Duration.ofSeconds(5);

    private final ActorRef<EnvironmentCoordinator.Command> environmentCoordinator;
    private final ActorRef<AirCondition.AirConditionCommand> airConditionActor;
    private final ActorRef<Blinds.BlindsCommand> blindsActor;
    private final ActorRef<MediaStation.MediaStationCommand> mediaStationActor;
    private final ActorSystem<?> system;

    public StatusRoutes(ActorRef<EnvironmentCoordinator.Command> environmentCoordinator, ActorRef<AirCondition.AirConditionCommand> airConditionActor,
                        ActorRef<Blinds.BlindsCommand> blindsActor, ActorRef<MediaStation.MediaStationCommand> mediaStationActor,
                        ActorSystem<?> system) {
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
        CompletionStage<EnvironmentSnapshot> environmentFuture =
                AskPattern.<EnvironmentCoordinator.Command, EnvironmentSnapshot>ask(
                        environmentCoordinator,
                        EnvironmentCoordinator.GetCurrentState::new,
                        ASK_TIMEOUT, system.scheduler());
        CompletionStage<AirCondition.StatusResponse> acFuture =
                AskPattern.<AirCondition.AirConditionCommand, AirCondition.StatusResponse>ask(
                        airConditionActor,
                        AirCondition.GetStatus::new,
                        ASK_TIMEOUT, system.scheduler());
        CompletionStage<Blinds.StatusResponse> blindsFuture =
                AskPattern.<Blinds.BlindsCommand, Blinds.StatusResponse>ask(
                        blindsActor,
                        Blinds.GetStatus::new,
                        ASK_TIMEOUT, system.scheduler());
        CompletionStage<MediaStation.StatusResponse> mediaFuture =
                AskPattern.<MediaStation.MediaStationCommand, MediaStation.StatusResponse>ask(mediaStationActor, MediaStation.GetStatus::new, ASK_TIMEOUT, system.scheduler());

        CompletionStage<StatusDto> aggregated = environmentFuture.thenCompose(env ->
                acFuture.thenCompose(ac ->
                        blindsFuture.thenCompose(blinds ->
                                mediaFuture.thenApply(media -> new StatusDto(
                                        Math.round(env.temperature().value() * 10.0) / 10.0,
                                        env.weather().name(),
                                        env.mode().name(),
                                        ac.isPoweredOn(),
                                        ac.isCooling(),
                                        blinds.areClosed(),
                                        media.isPlaying(),
                                        media.currentMovie()
                                ))
                        )
                )
        );

        return onSuccess(aggregated, dto -> complete(StatusCodes.OK, dto, Jackson.marshaller()));
    }

    private Route getDeviceStatus() {
        Map<String, String> status = new LinkedHashMap<>();
        status.put("environment", "active");
        status.put("fridge", "active");
        status.put("ac", "active");
        status.put("blinds", "active");
        status.put("mediaStation", "active");
        return complete(StatusCodes.OK, status, Jackson.marshaller());
    }
}