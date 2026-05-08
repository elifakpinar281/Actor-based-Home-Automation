package at.fhv.sysarch.lab2.homeautomation.environment;

import at.fhv.sysarch.lab2.homeautomation.devices.sensor.TemperatureReading;
import at.fhv.sysarch.lab2.homeautomation.devices.sensor.TemperatureSensor;
import org.apache.pekko.actor.typed.ActorRef;
import org.apache.pekko.actor.typed.Behavior;
import org.apache.pekko.actor.typed.javadsl.AbstractBehavior;
import org.apache.pekko.actor.typed.javadsl.ActorContext;
import org.apache.pekko.actor.typed.javadsl.Behaviors;
import org.apache.pekko.actor.typed.javadsl.Receive;

import java.time.Duration;
import java.util.Random;

public class TemperatureEnvironment extends AbstractBehavior<TemperatureEnvironment.TemperatureEnvironmentCommand> {
    public interface TemperatureEnvironmentCommand {}
    public record SetTemperature(double value) implements TemperatureEnvironmentCommand {}
    public record RequestTemperature(ActorRef<TemperatureSensor.TemperatureSensorCommand> replyTo) implements TemperatureEnvironmentCommand {}
    private record Tick() implements TemperatureEnvironmentCommand {}
    public record SetEnvironmentSwitch(ActorRef<EnvironmentSwitch.EnvironmentSwitchCommand> environmentSwitch) implements TemperatureEnvironmentCommand {}

    // state
    private double temperature = 23.0;
    private final Random random = new Random();
    private ActorRef<EnvironmentSwitch.EnvironmentSwitchCommand> environmentSwitch;

    public static Behavior<TemperatureEnvironmentCommand> create() {
        return Behaviors.setup(context ->
                Behaviors.withTimers(timers -> {
                    timers.startTimerWithFixedDelay("tick", new Tick(), Duration.ofSeconds(5));
                    return new TemperatureEnvironment(context);
                }));
    }

    private TemperatureEnvironment(ActorContext<TemperatureEnvironmentCommand> context) {
        super(context);
    }

    @Override
    public Receive<TemperatureEnvironmentCommand> createReceive() {
        return newReceiveBuilder()
                .onMessage(Tick.class, this::onTick)
                .onMessage(RequestTemperature.class, this::onRequestTemperature)
                .onMessage(SetTemperature.class, this::onSetTemperature)
                .onMessage(SetEnvironmentSwitch.class, this::onSetEnvironmentSwitch)
                .build();
    }

    private Behavior<TemperatureEnvironmentCommand> onTick(Tick message) {
        double delta = random.nextDouble(-1.0, 1.0);
        temperature += delta;
        if (environmentSwitch != null) {
            environmentSwitch.tell(new EnvironmentSwitch.InternalTemperatureUpdate(temperature));
        }
        getContext().getLog().debug("Temperature is now {}°C", String.format("%.2f", temperature));
        return this;
    }

    private Behavior<TemperatureEnvironmentCommand> onRequestTemperature(RequestTemperature message) {
        message.replyTo().tell(new TemperatureSensor.TemperatureResult(TemperatureReading.celsius(temperature)));
        return this;
    }

    private Behavior<TemperatureEnvironmentCommand> onSetTemperature(SetTemperature message) {
        temperature = message.value();
        getContext().getLog().info("Temperature set to {}°C", temperature);
        return this;
    }

    private Behavior<TemperatureEnvironmentCommand> onSetEnvironmentSwitch(SetEnvironmentSwitch message) {
        this.environmentSwitch = message.environmentSwitch();
        return this;
    }
}
