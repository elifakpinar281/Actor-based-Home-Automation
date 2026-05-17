package at.fhv.sysarch.lab2.homeautomation.devices;

import org.apache.pekko.actor.typed.ActorRef;
import org.apache.pekko.actor.typed.Behavior;
import org.apache.pekko.actor.typed.PostStop;
import org.apache.pekko.actor.typed.javadsl.AbstractBehavior;
import org.apache.pekko.actor.typed.javadsl.ActorContext;
import org.apache.pekko.actor.typed.javadsl.Behaviors;
import org.apache.pekko.actor.typed.javadsl.Receive;
import org.apache.pekko.actor.typed.receptionist.ServiceKey;

public class MediaStation extends AbstractBehavior<MediaStation.MediaStationCommand> {
    public interface MediaStationCommand {}

    public record PlayMovie(String movieName) implements MediaStationCommand {}
    public record StopMovie() implements MediaStationCommand {}
    public record GetStatus(ActorRef<StatusResponse> replyTo) implements MediaStationCommand {}

    public record StatusResponse(boolean isPlaying, String currentMovie) {}

    public static final ServiceKey<MediaStationCommand> SERVICE_KEY =
            ServiceKey.create(MediaStationCommand.class, "mediaStation");

    public static Behavior<MediaStationCommand> create(String identifier, ActorRef<Blinds.BlindsCommand> blinds) {
        return Behaviors.setup(context -> new MediaStation(context, identifier, blinds));
    }

    private final String identifier;
    private final ActorRef<Blinds.BlindsCommand> blinds;

    private boolean isPlaying = false;
    private String currentMovie;

    private MediaStation(ActorContext<MediaStationCommand> context, String identifier, ActorRef<Blinds.BlindsCommand> blinds) {
        super(context);
        this.identifier = identifier;
        this.blinds = blinds;
        getContext().getLog().info("Media Station '{}' started", identifier);
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
            getContext().getLog().warn("Media Station '{}': cannot play '{}' — '{}' already playing", identifier, msg.movieName(), currentMovie);
            return Behaviors.same();
        }

        isPlaying = true;
        currentMovie = msg.movieName();
        blinds.tell(new Blinds.MovieStatusChanged(true));
        getContext().getLog().info("Media Station '{}': NOW PLAYING '{}'", identifier, currentMovie);
        return Behaviors.same();
    }

    private Behavior<MediaStationCommand> onStopMovie(StopMovie msg) {
        if (!isPlaying) {
            getContext().getLog().warn("Media Station '{}': no movie is currently playing", identifier);
            return Behaviors.same();
        }

        String stoppedMovie = currentMovie;
        isPlaying = false;
        currentMovie = null;
        blinds.tell(new Blinds.MovieStatusChanged(false));
        getContext().getLog().info("Media Station '{}': STOPPED '{}'", identifier, stoppedMovie);
        return Behaviors.same();
    }

    private Behavior<MediaStationCommand> onGetStatus(GetStatus msg) {
        msg.replyTo().tell(new StatusResponse(isPlaying, currentMovie));
        return Behaviors.same();
    }

    private Behavior<MediaStationCommand> onPostStop() {
        getContext().getLog().info("Media Station '{}' stopped", identifier);
        return this;
    }
}