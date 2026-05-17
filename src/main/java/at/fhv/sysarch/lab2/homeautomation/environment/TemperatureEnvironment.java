package at.fhv.sysarch.lab2.homeautomation.environment;

import at.fhv.sysarch.lab2.homeautomation.shared.model.environment.Temperature;
import org.apache.pekko.actor.typed.ActorRef;
import org.apache.pekko.actor.typed.Behavior;
import org.apache.pekko.actor.typed.javadsl.AbstractBehavior;
import org.apache.pekko.actor.typed.javadsl.ActorContext;
import org.apache.pekko.actor.typed.javadsl.Behaviors;
import org.apache.pekko.actor.typed.javadsl.Receive;

import java.time.Duration;
import java.util.Random;

public class TemperatureEnvironment extends AbstractBehavior<TemperatureEnvironment.TemperatureEnvironmentCommand> {
    private static final Duration TICK_INTERVAL = Duration.ofSeconds(5);
    private static final double INITIAL_TEMPERATURE = 23.0;
    private static final double MAX_DRIFT_PER_TICK = 1.0;

    public interface TemperatureEnvironmentCommand {}

    public record SetTemperature(double celsius) implements TemperatureEnvironmentCommand {}
    public record RequestCurrentTemperature(ActorRef<TemperatureReply> replyTo) implements TemperatureEnvironmentCommand {}
    public record TemperatureReply(double celsius) {}

    private record Tick() implements TemperatureEnvironmentCommand {}

    private final ActorRef<EnvironmentCoordinator.Command> coordinator;
    private final Random random = new Random();
    private double currentCelsius = INITIAL_TEMPERATURE;

    public static Behavior<TemperatureEnvironmentCommand> create(ActorRef<EnvironmentCoordinator.Command> coordinator) {
        return Behaviors.setup(context ->
                Behaviors.withTimers(timers -> {
                    timers.startTimerWithFixedDelay("temperature-tick", new Tick(), TICK_INTERVAL);
                    return new TemperatureEnvironment(context, coordinator);
                }));
    }

    private TemperatureEnvironment(ActorContext<TemperatureEnvironmentCommand> context,
                                   ActorRef<EnvironmentCoordinator.Command> coordinator) {
        super(context);
        this.coordinator = coordinator;
        getContext().getLog().info("TemperatureEnvironment started at {}°C, tick every {}s",
                INITIAL_TEMPERATURE, TICK_INTERVAL.toSeconds());
    }

    @Override
    public Receive<TemperatureEnvironmentCommand> createReceive() {
        return newReceiveBuilder()
                .onMessage(Tick.class, this::onTick)
                .onMessage(RequestCurrentTemperature.class, this::onRequestCurrentTemperature)
                .onMessage(SetTemperature.class, this::onSetTemperature)
                .build();
    }

    private Behavior<TemperatureEnvironmentCommand> onTick(Tick tick) {
        double delta = random.nextDouble(-MAX_DRIFT_PER_TICK, MAX_DRIFT_PER_TICK);
        currentCelsius = Temperature.clampToRange(currentCelsius + delta);

        coordinator.tell(new EnvironmentCoordinator.InternalTemperatureUpdate(currentCelsius));
        getContext().getLog().debug("TemperatureEnvironment: tick -> {}°C (Δ {})",
                String.format("%.2f", currentCelsius), String.format("%+.2f", delta));
        return this;
    }

    private Behavior<TemperatureEnvironmentCommand> onRequestCurrentTemperature(RequestCurrentTemperature request) {
        request.replyTo().tell(new TemperatureReply(currentCelsius));
        return this;
    }

    private Behavior<TemperatureEnvironmentCommand> onSetTemperature(SetTemperature message) {
        currentCelsius = Temperature.clampToRange(message.celsius());
        getContext().getLog().info("TemperatureEnvironment: value overridden to {}°C", currentCelsius);
        return this;
    }
}
