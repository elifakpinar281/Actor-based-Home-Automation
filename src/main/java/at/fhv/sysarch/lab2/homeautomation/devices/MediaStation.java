package at.fhv.sysarch.lab2.homeautomation.devices;

import org.apache.pekko.actor.typed.ActorRef;
import org.apache.pekko.actor.typed.Behavior;
import org.apache.pekko.actor.typed.PostStop;
import org.apache.pekko.actor.typed.javadsl.AbstractBehavior;
import org.apache.pekko.actor.typed.javadsl.ActorContext;
import org.apache.pekko.actor.typed.javadsl.Behaviors;
import org.apache.pekko.actor.typed.javadsl.Receive;
import org.apache.pekko.actor.typed.receptionist.Receptionist;
import org.apache.pekko.actor.typed.receptionist.ServiceKey;

import java.util.HashSet;
import java.util.Set;

public class MediaStation extends AbstractBehavior<MediaStation.MediaStationCommand> {
    public interface MediaStationCommand {}

    public record PlayMovie(String movieName, ActorRef<PlayMovieResult> replyTo) implements MediaStationCommand {}
    public record StopMovie() implements MediaStationCommand {}
    public record GetStatus(ActorRef<StatusResponse> replyTo) implements MediaStationCommand {}

    private record BlindsUpdated(Set<ActorRef<Blinds.BlindsCommand>> blinds) implements MediaStationCommand {}
    private record IgnoredListing() implements MediaStationCommand {}

    public record PlayMovieResult(boolean accepted, String message, String currentMovie) {}
    public record StatusResponse(boolean isPlaying, String currentMovie) {}

    public static final ServiceKey<MediaStationCommand> SERVICE_KEY =
            ServiceKey.create(MediaStationCommand.class, "mediaStation");

    public static Behavior<MediaStationCommand> create(String identifier) {
        return Behaviors.setup(context -> new MediaStation(context, identifier));
    }

    private final String identifier;
    private final Set<ActorRef<Blinds.BlindsCommand>> blinds = new HashSet<>();

    private boolean isPlaying = false;
    private String currentMovie;

    private MediaStation(ActorContext<MediaStationCommand> context, String identifier) {
        super(context);
        this.identifier = identifier;

        ActorRef<Receptionist.Listing> adapter = context.messageAdapter(
                Receptionist.Listing.class,
                listing -> listing.isForKey(Blinds.SERVICE_KEY)
                        ? new BlindsUpdated(listing.getServiceInstances(Blinds.SERVICE_KEY))
                        : new IgnoredListing()
        );
        context.getSystem().receptionist().tell(Receptionist.subscribe(Blinds.SERVICE_KEY, adapter));

        getContext().getLog().info("Media Station '{}' started - discovering Blinds actuators via Receptionist", identifier);
    }

    @Override
    public Receive<MediaStationCommand> createReceive() {
        return newReceiveBuilder()
                .onMessage(PlayMovie.class, this::onPlayMovie)
                .onMessage(StopMovie.class, this::onStopMovie)
                .onMessage(GetStatus.class, this::onGetStatus)
                .onMessage(BlindsUpdated.class, this::onBlindsUpdated)
                .onMessage(IgnoredListing.class, msg -> Behaviors.same())
                .onSignal(PostStop.class, signal -> onPostStop())
                .build();
    }

    private Behavior<MediaStationCommand> onPlayMovie(PlayMovie msg) {
        if (isPlaying) {
            getContext().getLog().warn("Media Station '{}': cannot play '{}' - '{}' already playing", identifier, msg.movieName(), currentMovie);
            msg.replyTo().tell(new PlayMovieResult(
                    false,
                    "Another movie is already playing: '" + currentMovie + "'",
                    currentMovie
            ));
            return Behaviors.same();
        }

        isPlaying = true;
        currentMovie = msg.movieName();
        notifyBlinds(true);
        getContext().getLog().info("Media Station '{}': NOW PLAYING '{}'", identifier, currentMovie);
        msg.replyTo().tell(new PlayMovieResult(true, "Movie started", currentMovie));
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
        notifyBlinds(false);
        getContext().getLog().info("Media Station '{}': STOPPED '{}'", identifier, stoppedMovie);
        return Behaviors.same();
    }

    private Behavior<MediaStationCommand> onGetStatus(GetStatus msg) {
        msg.replyTo().tell(new StatusResponse(isPlaying, currentMovie));
        return Behaviors.same();
    }

    private Behavior<MediaStationCommand> onBlindsUpdated(BlindsUpdated message) {
        blinds.clear();
        blinds.addAll(message.blinds());
        getContext().getLog().info("Media Station '{}': Blinds actuators discovered -> {} registered", identifier, blinds.size());
        return Behaviors.same();
    }

    private void notifyBlinds(boolean playing) {
        if (blinds.isEmpty()) {
            getContext().getLog().warn("Media Station '{}': no Blinds registered yet - movie status update dropped", identifier);
            return;
        }
        for (ActorRef<Blinds.BlindsCommand> b : blinds) {
            b.tell(new Blinds.MovieStatusChanged(playing));
        }
    }

    private Behavior<MediaStationCommand> onPostStop() {
        getContext().getLog().info("Media Station '{}' stopped", identifier);
        return this;
    }
}