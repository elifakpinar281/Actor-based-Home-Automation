package at.fhv.sysarch.lab2.homeautomation.uihandler.routes;

import at.fhv.sysarch.lab2.homeautomation.devices.MediaStation;
import at.fhv.sysarch.lab2.homeautomation.shared.exceptions.InvalidRequestException;
import at.fhv.sysarch.lab2.homeautomation.uihandler.dtos.MediaStatusResponse;
import at.fhv.sysarch.lab2.homeautomation.uihandler.dtos.SuccessResponse;
import org.apache.pekko.actor.typed.ActorRef;
import org.apache.pekko.actor.typed.ActorSystem;
import org.apache.pekko.actor.typed.javadsl.AskPattern;
import org.apache.pekko.http.javadsl.marshallers.jackson.Jackson;
import org.apache.pekko.http.javadsl.model.StatusCodes;
import org.apache.pekko.http.javadsl.server.AllDirectives;
import org.apache.pekko.http.javadsl.server.Route;

import java.time.Duration;

public class MediaRoutes extends AllDirectives {
    private static final Duration ASK_TIMEOUT = Duration.ofSeconds(5);

    private final ActorRef<MediaStation.MediaStationCommand> mediaStationActor;
    private final ActorSystem<?> system;

    public MediaRoutes(ActorRef<MediaStation.MediaStationCommand> mediaStationActor, ActorSystem<?> system) {
        this.mediaStationActor = mediaStationActor;
        this.system = system;
    }

    public Route routes() {
        return pathPrefix("media-station", () -> concat(
                path("play", () -> post(this::playMovie)),
                path("stop", () -> post(this::stopMovie)),
                path("status", this::getStatus)
        ));
    }

    private Route playMovie() {
        return parameter("movieName", movieName -> {
            if (movieName == null || movieName.isBlank()) {
                throw new InvalidRequestException("Movie name is required");
            }
            return onSuccess(
                    AskPattern.<MediaStation.MediaStationCommand, MediaStation.PlayMovieResult>ask(
                            mediaStationActor,
                            replyTo -> new MediaStation.PlayMovie(movieName, replyTo),
                            ASK_TIMEOUT,
                            system.scheduler()
                    ),
                    result -> {
                        if (result.accepted()) {
                            return complete(StatusCodes.OK, new SuccessResponse(result.message()),
                                    Jackson.marshaller());
                        }
                        return complete(StatusCodes.CONFLICT, new SuccessResponse(result.message()),
                                Jackson.marshaller());
                    }
            );
        });
    }

    private Route stopMovie() {
        mediaStationActor.tell(new MediaStation.StopMovie());
        return complete(StatusCodes.ACCEPTED,
                new SuccessResponse("Movie stop requested"),
                Jackson.marshaller());
    }

    private Route getStatus() {
        return get(() -> onSuccess(
                AskPattern.ask(mediaStationActor, MediaStation.GetStatus::new, ASK_TIMEOUT, system.scheduler()),
                response -> complete(StatusCodes.OK,
                        new MediaStatusResponse(response.currentMovie(), response.isPlaying()),
                        Jackson.marshaller())
        ));
    }
}