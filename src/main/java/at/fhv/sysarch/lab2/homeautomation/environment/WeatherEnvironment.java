package at.fhv.sysarch.lab2.homeautomation.environment;

import at.fhv.sysarch.lab2.homeautomation.devices.sensor.WeatherSensor;
import at.fhv.sysarch.lab2.homeautomation.shared.model.WeatherCondition;
import org.apache.pekko.actor.typed.ActorRef;
import org.apache.pekko.actor.typed.javadsl.*;
import org.apache.pekko.actor.typed.Behavior;
import org.apache.pekko.actor.typed.javadsl.Receive;

import java.time.Duration;
import java.util.Random;

public class WeatherEnvironment extends AbstractBehavior<WeatherEnvironment.WeatherEnvironmentCommand> {
    private static final Duration TICK_INTERVAL = Duration.ofSeconds(10);
    private static final WeatherCondition INITIAL_CONDITION = WeatherCondition.SUNNY;

    public interface WeatherEnvironmentCommand {}

    public record SetWeather(WeatherCondition condition) implements WeatherEnvironmentCommand {}
    public record RequestCurrentWeather(ActorRef<WeatherReply> replyTo) implements WeatherEnvironmentCommand {}
    public record WeatherReply(WeatherCondition condition) {}

    private record Tick() implements WeatherEnvironmentCommand {}
    private static final WeatherCondition[] CONDITIONS = WeatherCondition.values();

    private final ActorRef<EnvironmentCoordinator.Command> coordinator;
    private final Random random = new Random();
    private WeatherCondition currentCondition = INITIAL_CONDITION;


    public static Behavior<WeatherEnvironmentCommand> create(ActorRef<EnvironmentCoordinator.Command> coordinator) {
        return Behaviors.setup(context ->
                Behaviors.withTimers(timers -> {
                    timers.startTimerWithFixedDelay("weather-tick", new Tick(), TICK_INTERVAL);
                    return new WeatherEnvironment(context, coordinator);
                }));
    }

    private WeatherEnvironment(ActorContext<WeatherEnvironmentCommand> context, ActorRef<EnvironmentCoordinator.Command> coordinator) {
        super(context);
        this.coordinator = coordinator;
        getContext().getLog().info("WeatherEnvironment started at {}, tick every {}s", INITIAL_CONDITION, TICK_INTERVAL.toSeconds());
    }

    @Override
    public Receive<WeatherEnvironmentCommand> createReceive() {
        return newReceiveBuilder()
                .onMessage(Tick.class, this::onTick)
                .onMessage(SetWeather.class, this::onSetWeather)
                .onMessage(RequestCurrentWeather.class, this::onRequestCurrentWeather)
                .build();
    }

    private Behavior<WeatherEnvironmentCommand> onTick(Tick tick) {
        currentCondition = pickDifferentCondition(currentCondition);
        coordinator.tell(new EnvironmentCoordinator.InternalWeatherUpdate(currentCondition));
        getContext().getLog().debug("WeatherEnvironment: tick → {}", currentCondition);
        return this;
    }

    private Behavior<WeatherEnvironmentCommand> onSetWeather(SetWeather message) {
        currentCondition = message.condition();
        getContext().getLog().info("WeatherEnvironment: value overridden to {}", currentCondition);
        return this;
    }

    private Behavior<WeatherEnvironmentCommand> onRequestCurrentWeather(RequestCurrentWeather request) {
        request.replyTo().tell(new WeatherReply(currentCondition));
        return this;
    }

    private WeatherCondition pickDifferentCondition(WeatherCondition current) {
        int currentIndex = current.ordinal();
        int drawnIndex = random.nextInt(CONDITIONS.length - 1);
        if (drawnIndex >= currentIndex) {
            drawnIndex++;
        }
        return CONDITIONS[drawnIndex];
    }
}
