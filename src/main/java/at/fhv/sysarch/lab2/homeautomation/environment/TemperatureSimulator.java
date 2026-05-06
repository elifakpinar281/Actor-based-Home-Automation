package at.fhv.sysarch.lab2.homeautomation.environment;

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

public class TemperatureSimulator extends AbstractBehavior<TemperatureSimulator.TemperatureSimulatorCommand> {
    public interface TemperatureSimulatorCommand {}

    private enum Tick implements TemperatureSimulatorCommand {
        INSTANCE
    }

    public record ReadCurrentTemperature(ActorRef<TemperatureResponse> replyTo) implements TemperatureSimulatorCommand {}

    public record TemperatureResponse(double temperature) {}

    private static final double DEFAULT_INITIAL_TEMPERATURE = 23.0;
    private static final double MAX_DELTA = 1.0;
    private static final double MIN_TEMPERATURE = -20.0;
    private static final double MAX_TEMPERATURE = 50.0;
    private static final Duration TICK_INTERVAL = Duration.ofSeconds(3);
    private static final Object TICK_TIMER_KEY = "temperature-tick";

    private double currentTemperature;
    private final Random random;
    private final TimerScheduler<TemperatureSimulatorCommand> timers;

    public static Behavior<TemperatureSimulatorCommand> create() {
        return create(DEFAULT_INITIAL_TEMPERATURE);
    }

    public static Behavior<TemperatureSimulatorCommand> create(double initialTemperature) {
        return Behaviors.setup(context -> Behaviors.withTimers(timers ->
                        new TemperatureSimulator(context, timers, initialTemperature)));
    }

    private TemperatureSimulator(ActorContext<TemperatureSimulatorCommand> context, TimerScheduler<TemperatureSimulatorCommand> timers,
            double initialTemperature) {
        super(context);
        this.timers = timers;
        this.currentTemperature = initialTemperature;
        this.random = new Random();

        timers.startTimerAtFixedRate(TICK_TIMER_KEY, Tick.INSTANCE, TICK_INTERVAL);
        getContext().getLog().info("TemperatureSimulator started at {}°C (tick every {}s)", initialTemperature, TICK_INTERVAL.getSeconds());
    }

    @Override
    public Receive<TemperatureSimulatorCommand> createReceive() {
        return newReceiveBuilder()
                .onMessage(Tick.class, this::onTick)
                .onMessage(ReadCurrentTemperature.class, this::onReadCurrentTemperature)
                .onSignal(PostStop.class, signal -> onPostStop())
                .build();
    }

    private Behavior<TemperatureSimulatorCommand> onTick(Tick tick) {
        double delta = (random.nextDouble() * 2 * MAX_DELTA) - MAX_DELTA;
        double newTemperature = currentTemperature + delta;
        newTemperature = Math.max(MIN_TEMPERATURE, Math.min(MAX_TEMPERATURE, newTemperature));
        getContext().getLog().debug("Temperature changed: {}°C -> {}°C (delta: {})", currentTemperature, newTemperature, delta);
        currentTemperature = newTemperature;
        return this;
    }

    private Behavior<TemperatureSimulatorCommand> onReadCurrentTemperature(ReadCurrentTemperature request) {
        request.replyTo().tell(new TemperatureResponse(currentTemperature));
        return this;
    }

    private TemperatureSimulator onPostStop() {
        getContext().getLog().info("TemperatureSimulator stopped (last value: {}°C)", currentTemperature);
        return this;
    }
}
