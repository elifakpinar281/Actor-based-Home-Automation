package at.fhv.sysarch.lab2.homeautomation.devices;

import at.fhv.sysarch.lab2.homeautomation.shared.model.WeatherCondition;
import org.apache.pekko.actor.typed.Behavior;
import org.apache.pekko.actor.typed.PostStop;
import org.apache.pekko.actor.typed.javadsl.AbstractBehavior;
import org.apache.pekko.actor.typed.javadsl.ActorContext;
import org.apache.pekko.actor.typed.javadsl.Behaviors;
import org.apache.pekko.actor.typed.javadsl.Receive;

public class Blinds extends AbstractBehavior<Blinds.BlindsCommand> {

    public interface BlindsCommand { }

    public record WeatherUpdate(WeatherCondition condition) implements BlindsCommand { }
    public record MovieStatusChanged(boolean isPlaying) implements BlindsCommand { }

    public static Behavior<BlindsCommand> create(String identifier) {
        return Behaviors.setup(context -> new Blinds(context, identifier));
    }

    private final String identifier;
    private boolean areClosed = false;
    private boolean isMoviePlaying = false;
    private WeatherCondition currentWeather = WeatherCondition.CLOUDY;

    public Blinds(ActorContext<BlindsCommand> context, String identifier) {
        super(context);
        this.identifier = identifier;
        getContext().getLog().info("Blinds Actor '{}' started", identifier);
    }

    @Override
    public Receive<BlindsCommand> createReceive() {
        return newReceiveBuilder()
                .onMessage(WeatherUpdate.class, this::onWeatherUpdate)
                .onMessage(MovieStatusChanged.class, this::onMovieStatusChanged)
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

    private void updateBlindsState() {
        boolean shouldBeClosed;

        if (isMoviePlaying) {
            // Film läuft = immer geschlossen
            shouldBeClosed = true;
        } else if (currentWeather == WeatherCondition.SUNNY) {
            // Sonnnig = geschlossen
            shouldBeClosed = true;
        } else {
            // Nicht sunny = offen
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