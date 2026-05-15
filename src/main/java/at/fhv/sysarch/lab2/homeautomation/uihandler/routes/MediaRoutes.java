package at.fhv.sysarch.lab2.homeautomation.uihandler.routes;

import at.fhv.sysarch.lab2.homeautomation.devices.MediaStation;
import at.fhv.sysarch.lab2.homeautomation.uihandler.dtos.MediaStatusResponse;
import at.fhv.sysarch.lab2.homeautomation.uihandler.dtos.SuccessResponse;
import at.fhv.sysarch.lab2.homeautomation.uihandler.exception.ErrorResponse;
import org.apache.pekko.actor.typed.ActorRef;
import org.apache.pekko.actor.typed.ActorSystem;
import org.apache.pekko.actor.typed.javadsl.AskPattern;
import org.apache.pekko.http.javadsl.marshallers.jackson.Jackson;
import org.apache.pekko.http.javadsl.model.StatusCodes;
import org.apache.pekko.http.javadsl.server.AllDirectives;
import org.apache.pekko.http.javadsl.server.Route;

import java.time.Duration;

public class MediaRoutes extends AllDirectives {
    private static final Duration TIMEOUT = Duration.ofSeconds(5);

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
            if (movieName.isEmpty()) {
                return complete(StatusCodes.BAD_REQUEST, new ErrorResponse("Movie name is required"), Jackson.marshaller());
            }
            mediaStationActor.tell(new MediaStation.PlayMovie(movieName));
            return complete(StatusCodes.ACCEPTED, new SuccessResponse("Movie playback requested"), Jackson.marshaller());
        });
    }

    private Route stopMovie() {
        mediaStationActor.tell(new MediaStation.StopMovie());
        return complete(StatusCodes.ACCEPTED, new SuccessResponse("Movie stop requested"), Jackson.marshaller());
    }

    private Route getStatus() {
        return get(() ->
                onComplete(
                        AskPattern.ask(mediaStationActor, MediaStation.GetStatus::new, TIMEOUT, system.scheduler()),
                        response -> {
                            if (!response.isSuccess()) {
                                return complete(StatusCodes.INTERNAL_SERVER_ERROR, new ErrorResponse("media status failed"), Jackson.marshaller());
                            }
                            MediaStation.StatusResponse r = response.get();
                            return complete(StatusCodes.OK, new MediaStatusResponse(r.currentMovie(), r.isPlaying()),
                                    Jackson.marshaller());
                        }
                )
        );
    }
}