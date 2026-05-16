package at.fhv.sysarch.lab2.homeautomation.devices;

import org.apache.pekko.actor.typed.ActorRef;
import org.apache.pekko.actor.typed.Behavior;
import org.apache.pekko.actor.typed.PostStop;
import org.apache.pekko.actor.typed.javadsl.AbstractBehavior;
import org.apache.pekko.actor.typed.javadsl.ActorContext;
import org.apache.pekko.actor.typed.javadsl.Behaviors;
import org.apache.pekko.actor.typed.javadsl.Receive;
import org.apache.pekko.actor.typed.javadsl.StashBuffer;
import org.apache.pekko.actor.typed.receptionist.Receptionist;
import org.apache.pekko.actor.typed.receptionist.ServiceKey;

public class MediaStation extends AbstractBehavior<MediaStation.MediaStationCommand> {
    public interface MediaStationCommand {}

    public record PlayMovie(String movieName) implements MediaStationCommand {}
    public record StopMovie() implements MediaStationCommand {}
    public record GetStatus(ActorRef<StatusResponse> replyTo) implements MediaStationCommand {}

    public record StatusResponse(boolean isPlaying, String currentMovie) {}
    private record BlindsListingUpdated(Receptionist.Listing listing) implements MediaStationCommand {}

    public static final ServiceKey<MediaStationCommand> SERVICE_KEY =
            ServiceKey.create(MediaStationCommand.class, "mediaStation");

    private static final int STASH_CAPACITY = 100;

    public static Behavior<MediaStationCommand> create(String identifier) {
        return Behaviors.withStash(STASH_CAPACITY, stash ->
                Behaviors.setup(context -> new MediaStation(context, stash, identifier))
        );
    }

    private final String identifier;

    // Puffert Commands, bis der Blinds Actor über Receptionist gefunden wurde.
    // Play/Stop Requests gehen nicht verloren und werden erst verarbeitet, wenn alle Abhängigkeiten verfügbar sind.
    private final StashBuffer<MediaStationCommand> stash;

    private ActorRef<Blinds.BlindsCommand> blindsActor;
    private boolean isPlaying = false;
    private String currentMovie;

    private MediaStation(ActorContext<MediaStationCommand> context, StashBuffer<MediaStationCommand> stash, String identifier) {
        super(context);
        this.stash = stash;
        this.identifier = identifier;

        ActorRef<Receptionist.Listing> listingAdapter = context.messageAdapter(Receptionist.Listing.class, BlindsListingUpdated::new);
        context.getSystem().receptionist().tell(Receptionist.subscribe(Blinds.SERVICE_KEY, listingAdapter));
        getContext().getLog().info("Media Station '{}' started — awaiting Blinds discovery", identifier);
    }

    @Override
    public Receive<MediaStationCommand> createReceive() {
        return newReceiveBuilder()
                .onMessage(PlayMovie.class, this::onPlayMovie)
                .onMessage(StopMovie.class, this::onStopMovie)
                .onMessage(GetStatus.class, this::onGetStatus)
                .onMessage(BlindsListingUpdated.class, this::onBlindsListingUpdated)
                .onSignal(PostStop.class, signal -> onPostStop())
                .build();
    }

    private Behavior<MediaStationCommand> onBlindsListingUpdated(BlindsListingUpdated msg) {
        msg.listing().getServiceInstances(Blinds.SERVICE_KEY).stream().findFirst()
                .ifPresent(blinds -> {
                    if (this.blindsActor == null) {
                        this.blindsActor = blinds;
                        getContext().getLog().info("MediaStation '{}': Blinds discovered, unstashing {} pending commands", identifier, stash.size());
                    } else {
                        this.blindsActor = blinds;
                    }
                });

        if (blindsActor != null && stash.nonEmpty()) {
            return stash.unstashAll(this);
        }
        return Behaviors.same();
    }

    private Behavior<MediaStationCommand> onPlayMovie(PlayMovie msg) {
        if (blindsActor == null) {
            getContext().getLog().info("Media Station '{}': Blinds not yet available — stashing PlayMovie('{}')", identifier, msg.movieName());
            stash.stash(msg);
            return Behaviors.same();
        }
        if (isPlaying) {
            getContext().getLog().warn("Media Station '{}': cannot play '{}' — '{}' already playing", identifier, msg.movieName(), currentMovie);
            return Behaviors.same();
        }

        isPlaying = true;
        currentMovie = msg.movieName();
        blindsActor.tell(new Blinds.MovieStatusChanged(true));
        getContext().getLog().info("Media Station '{}': NOW PLAYING '{}'", identifier, currentMovie);
        return Behaviors.same();
    }

    private Behavior<MediaStationCommand> onStopMovie(StopMovie msg) {
        if (blindsActor == null) {
            getContext().getLog().info("Media Station '{}': Blinds not yet available — stashing StopMovie", identifier);
            stash.stash(msg);
            return Behaviors.same();
        }
        if (!isPlaying) {
            getContext().getLog().warn("Media Station '{}': no movie is currently playing", identifier);
            return Behaviors.same();
        }

        String stoppedMovie = currentMovie;
        isPlaying = false;
        currentMovie = null;
        blindsActor.tell(new Blinds.MovieStatusChanged(false));
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
