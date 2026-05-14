package at.fhv.sysarch.lab2.homeautomation.devices;

import at.fhv.sysarch.lab2.homeautomation.shared.model.WeatherCondition;
import org.apache.pekko.actor.typed.ActorRef;
import org.apache.pekko.actor.typed.Behavior;
import org.apache.pekko.actor.typed.PostStop;
import org.apache.pekko.actor.typed.javadsl.AbstractBehavior;
import org.apache.pekko.actor.typed.javadsl.ActorContext;
import org.apache.pekko.actor.typed.javadsl.Behaviors;
import org.apache.pekko.actor.typed.javadsl.Receive;
import org.apache.pekko.actor.typed.receptionist.ServiceKey;

public class Blinds extends AbstractBehavior<Blinds.BlindsCommand> {

    public interface BlindsCommand { }

    public record WeatherUpdate(WeatherCondition condition) implements BlindsCommand { }
    public record MovieStatusChanged(boolean isPlaying) implements BlindsCommand { }
    public record GetStatus(ActorRef<StatusResponse> replyTo) implements BlindsCommand { }

    public record StatusResponse(boolean areClosed) { }

    public static Behavior<BlindsCommand> create(String identifier) {
        return Behaviors.setup(context -> new Blinds(context, identifier));
    }

    private final String identifier;
    private boolean areClosed = false;
    private boolean isMoviePlaying = false;
    // Initial-State matched dem EnvironmentSwitch-Default (SUNNY), damit
    // Blinds-State und tatsächliche Environment-State von Anfang an konsistent sind.
    private WeatherCondition currentWeather = WeatherCondition.SUNNY;

    public static final ServiceKey<BlindsCommand> SERVICE_KEY =
            ServiceKey.create(BlindsCommand.class, "blinds");


    public Blinds(ActorContext<BlindsCommand> context, String identifier) {
        super(context);
        this.identifier = identifier;
        // Initial-State direkt anhand der Defaults berechnen (sunny → closed)
        this.areClosed = (currentWeather == WeatherCondition.SUNNY);
        getContext().getLog().info("Blinds Actor '{}' started - initial state: {}",
                identifier, areClosed ? "CLOSED" : "OPEN");
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
        this.currentWeather = msg.condition;
        updateBlindsState();
        return Behaviors.same();
    }

    private Behavior<BlindsCommand> onMovieStatusChanged(MovieStatusChanged msg) {
        this.isMoviePlaying = msg.isPlaying;
        updateBlindsState();
        return Behaviors.same();
    }

    private Behavior<BlindsCommand> onGetStatus(GetStatus msg) {
        msg.replyTo.tell(new StatusResponse(areClosed));
        return Behaviors.same();
    }

    private void updateBlindsState() {
        boolean shouldBeClosed;

        if (isMoviePlaying) {
            shouldBeClosed = true;
        } else if (currentWeather == WeatherCondition.SUNNY) {
            shouldBeClosed = true;
        } else {
            shouldBeClosed = false;
        }

        if (shouldBeClosed != areClosed) {
            areClosed = shouldBeClosed;
            String action = areClosed ? "CLOSED" : "OPENED";
            String reason = isMoviePlaying ? "movie playing" : "weather: " + currentWeather;
            getContext().getLog().info("Blinds '{}': {} - Reason: {}", identifier, action, reason);
        }
    }

    private Blinds onPostStop() {
        getContext().getLog().info("Blinds Actor '{}' stopped", identifier);
        return this;
    }
}
