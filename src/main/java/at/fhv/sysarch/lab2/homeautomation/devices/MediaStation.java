package at.fhv.sysarch.lab2.homeautomation.devices;

import org.apache.pekko.actor.typed.ActorRef;
import org.apache.pekko.actor.typed.Behavior;
import org.apache.pekko.actor.typed.PostStop;
import org.apache.pekko.actor.typed.javadsl.AbstractBehavior;
import org.apache.pekko.actor.typed.javadsl.ActorContext;
import org.apache.pekko.actor.typed.javadsl.Behaviors;
import org.apache.pekko.actor.typed.javadsl.Receive;

public class MediaStation extends AbstractBehavior<MediaStation.MediaStationCommand> {

    public interface MediaStationCommand { }

    public record PlayMovie(String movieName, ActorRef<Blinds.BlindsCommand> blindsActor) implements MediaStationCommand { }
    public record StopMovie(ActorRef<Blinds.BlindsCommand> blindsActor) implements MediaStationCommand { }
    public record GetStatus(ActorRef<StatusResponse> replyTo) implements MediaStationCommand { }

    public record StatusResponse(boolean isPlaying, String currentMovie) { }

    public static Behavior<MediaStationCommand> create(String identifier) {
        return Behaviors.setup(context -> new MediaStation(context, identifier));
    }

    private final String identifier;
    private boolean isPlaying = false;
    private String currentMovie = null;

    public MediaStation(ActorContext<MediaStationCommand> context, String identifier) {
        super(context);
        this.identifier = identifier;
        getContext().getLog().info("Media Station Actor '{}' started", identifier);
    }

    @Override
    public Receive<MediaStationCommand> createReceive() {
        return newReceiveBuilder()
                .onMessage(PlayMovie.class, this::onPlayMovie)
                .onMessage(StopMovie.class, this::onStopMovie)
                .onMessage(GetStatus.class, this::onGetStatus)
                .onSignal(PostStop.class, signal -> onPostStop())
                .build();
    }

    private Behavior<MediaStationCommand> onPlayMovie(PlayMovie msg) {
        if (isPlaying) {
            getContext().getLog().warn("Media Station '{}': Cannot play '{}' - another movie is playing",
                    identifier, msg.movieName);
            return Behaviors.same();
        }

        isPlaying = true;
        currentMovie = msg.movieName;

        msg.blindsActor.tell(new Blinds.MovieStatusChanged(true));

        getContext().getLog().info("Media Station '{}': NOW PLAYING '{}'", identifier, currentMovie);
        return Behaviors.same();
    }

    private Behavior<MediaStationCommand> onStopMovie(StopMovie msg) {
        if (!isPlaying) {
            getContext().getLog().warn("Media Station '{}': No movie is currently playing", identifier);
            return Behaviors.same();
        }

        String stoppedMovie = currentMovie;
        isPlaying = false;
        currentMovie = null;

        msg.blindsActor.tell(new Blinds.MovieStatusChanged(false));

        getContext().getLog().info("Media Station '{}': STOPPED '{}'", identifier, stoppedMovie);
        return Behaviors.same();
    }

    private Behavior<MediaStationCommand> onGetStatus(GetStatus msg) {
        msg.replyTo.tell(new StatusResponse(isPlaying, currentMovie));
        return Behaviors.same();
    }

    private MediaStation onPostStop() {
        getContext().getLog().info("Media Station Actor '{}' stopped", identifier);
        return this;
    }
}
