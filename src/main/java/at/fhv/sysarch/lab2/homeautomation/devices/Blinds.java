package at.fhv.sysarch.lab2.homeautomation.devices;

import at.fhv.sysarch.lab2.homeautomation.shared.model.environment.WeatherCondition;
import org.apache.pekko.actor.typed.ActorRef;
import org.apache.pekko.actor.typed.Behavior;
import org.apache.pekko.actor.typed.PostStop;
import org.apache.pekko.actor.typed.javadsl.AbstractBehavior;
import org.apache.pekko.actor.typed.javadsl.ActorContext;
import org.apache.pekko.actor.typed.javadsl.Behaviors;
import org.apache.pekko.actor.typed.javadsl.Receive;
import org.apache.pekko.actor.typed.receptionist.ServiceKey;

import java.util.Optional;

public class Blinds extends AbstractBehavior<Blinds.BlindsCommand> {
    public interface BlindsCommand {}

    public record WeatherUpdate(WeatherCondition condition) implements BlindsCommand {}
    public record MovieStatusChanged(boolean isPlaying) implements BlindsCommand {}
    public record GetStatus(ActorRef<StatusResponse> replyTo) implements BlindsCommand {}

    public record StatusResponse(boolean areClosed) {}
    public static final ServiceKey<BlindsCommand> SERVICE_KEY = ServiceKey.create(BlindsCommand.class, "blinds");

    public static Behavior<BlindsCommand> create(String identifier) {
        return Behaviors.setup(context -> new Blinds(context, identifier));
    }

    private final String identifier;
    private Optional<WeatherCondition> currentWeather = Optional.empty();
    private boolean isMoviePlaying = false;
    private boolean areClosed = false;

    private Blinds(ActorContext<BlindsCommand> context, String identifier) {
        super(context);
        this.identifier = identifier;
        getContext().getLog().info("Blinds '{}' started — initial state: OPEN (awaiting first weather update)", identifier);
    }

    @Override
    public Receive<BlindsCommand> createReceive() {
        return newReceiveBuilder()
                .onMessage(WeatherUpdate.class, this::onWeatherUpdate)
                .onMessage(MovieStatusChanged.class, this::onMovieStatusChanged)
                .onMessage(GetStatus.class, this::onGetStatus)
                .onSignal(PostStop.class, signal -> onPostStop())
                .build();
    }

    private Behavior<BlindsCommand> onWeatherUpdate(WeatherUpdate msg) {
        this.currentWeather = Optional.of(msg.condition());
        updateBlindsState();
        return Behaviors.same();
    }

    private Behavior<BlindsCommand> onMovieStatusChanged(MovieStatusChanged msg) {
        this.isMoviePlaying = msg.isPlaying();
        updateBlindsState();
        return Behaviors.same();
    }

    private Behavior<BlindsCommand> onGetStatus(GetStatus msg) {
        msg.replyTo().tell(new StatusResponse(areClosed));
        return Behaviors.same();
    }

    private void updateBlindsState() {
        boolean shouldBeClosed;
        if (isMoviePlaying) {
            shouldBeClosed = true;
        } else {
            shouldBeClosed = currentWeather.map(condition -> condition == WeatherCondition.SUNNY)
                    .orElse(false);
        }

        if (shouldBeClosed != areClosed) {
            areClosed = shouldBeClosed;
            String reason = isMoviePlaying
                    ? "movie playing"
                    : "weather: " + currentWeather.map(Enum::name).orElse("unknown");
            getContext().getLog().info("Blinds '{}': {} (reason: {})",
                    identifier, areClosed ? "CLOSED" : "OPENED", reason);
        }
    }

    private Behavior<BlindsCommand> onPostStop() {
        getContext().getLog().info("Blinds '{}' stopped", identifier);
        return this;
    }
}
