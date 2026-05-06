package at.fhv.sysarch.lab2.homeautomation.environment;

import at.fhv.sysarch.lab2.homeautomation.shared.model.WeatherCondition;
import org.apache.pekko.actor.typed.ActorRef;
import org.apache.pekko.actor.typed.Behavior;
import org.apache.pekko.actor.typed.PostStop;
import org.apache.pekko.actor.typed.javadsl.AbstractBehavior;
import org.apache.pekko.actor.typed.javadsl.ActorContext;
import org.apache.pekko.actor.typed.javadsl.Behaviors;
import org.apache.pekko.actor.typed.javadsl.Receive;
import org.apache.pekko.actor.typed.javadsl.TimerScheduler;

import java.time.Duration;
import java.util.Random;

public class WeatherSimulator extends AbstractBehavior<WeatherSimulator.WeatherSimulatorCommand> {
    public interface WeatherSimulatorCommand {}

    private enum Tick implements WeatherSimulatorCommand {
        INSTANCE
    }

    public record ReadCurrentWeather(ActorRef<WeatherResponse> replyTo) implements WeatherSimulatorCommand {}

    public record WeatherResponse(WeatherCondition condition) {}

    private static final WeatherCondition DEFAULT_INITIAL_WEATHER = WeatherCondition.SUNNY;
    private static final Duration TICK_INTERVAL = Duration.ofSeconds(5);
    private static final Object TICK_TIMER_KEY = "weather-tick";

    private static final double CHANGE_PROBABILITY = 0.3;

    private WeatherCondition currentCondition;
    private final Random random;
    private final TimerScheduler<WeatherSimulatorCommand> timers;


    public static Behavior<WeatherSimulatorCommand> create() {
        return create(DEFAULT_INITIAL_WEATHER);
    }

    public static Behavior<WeatherSimulatorCommand> create(WeatherCondition initialCondition) {
        return Behaviors.setup(context -> Behaviors.withTimers(timers ->
                new WeatherSimulator(context, timers, initialCondition)));
    }

    private WeatherSimulator(ActorContext<WeatherSimulatorCommand> context, TimerScheduler<WeatherSimulatorCommand> timers,
            WeatherCondition initialCondition) {
        super(context);
        this.timers = timers;
        this.currentCondition = initialCondition;
        this.random = new Random();

        timers.startTimerAtFixedRate(TICK_TIMER_KEY, Tick.INSTANCE, TICK_INTERVAL);
        getContext().getLog().info("WeatherSimulator started with {} (tick every {}s)", initialCondition, TICK_INTERVAL.getSeconds());
    }

    @Override
    public Receive<WeatherSimulatorCommand> createReceive() {
        return newReceiveBuilder()
                .onMessage(Tick.class, this::onTick)
                .onMessage(ReadCurrentWeather.class, this::onReadCurrentWeather)
                .onSignal(PostStop.class, signal -> onPostStop())
                .build();
    }

    private Behavior<WeatherSimulatorCommand> onTick(Tick tick) {
        boolean shouldChange = random.nextDouble() < CHANGE_PROBABILITY;
        if (!shouldChange) {
            return this;
        }

        WeatherCondition[] allConditions = WeatherCondition.values();
        WeatherCondition newCondition;
        do {
            newCondition = allConditions[random.nextInt(allConditions.length)];
        } while (newCondition == currentCondition && allConditions.length > 1);

        getContext().getLog().debug("Weather changed: {} -> {}", currentCondition, newCondition);
        currentCondition = newCondition;
        return this;
    }

    private Behavior<WeatherSimulatorCommand> onReadCurrentWeather(ReadCurrentWeather request) {
        request.replyTo().tell(new WeatherResponse(currentCondition));
        return this;
    }

    private WeatherSimulator onPostStop() {
        getContext().getLog().info("WeatherSimulator stopped (last condition: {})", currentCondition);
        return this;
    }
}
