package at.fhv.sysarch.lab2.homeautomation.devices.sensor;

import at.fhv.sysarch.lab2.homeautomation.devices.AirCondition;
import at.fhv.sysarch.lab2.homeautomation.environment.TemperatureEnvironment;
import at.fhv.sysarch.lab2.homeautomation.shared.model.Temperature;
import org.apache.pekko.actor.typed.ActorRef;
import org.apache.pekko.actor.typed.Behavior;
import org.apache.pekko.actor.typed.javadsl.*;

import java.time.Duration;

public class TemperatureSensor extends AbstractBehavior<TemperatureSensor.TemperatureSensorCommand> {
    public interface TemperatureSensorCommand {};
    public record SetEnabled(boolean enabled) implements TemperatureSensorCommand {}
    public record MeasureTemperature() implements TemperatureSensorCommand {}
    public record TemperatureResult(TemperatureReading temperature) implements TemperatureSensorCommand {}

    private boolean enabled = true;
    private final ActorRef<TemperatureEnvironment.TemperatureEnvironmentCommand> environment;
    private final ActorRef<AirCondition.AirConditionCommand> aircondition;

    public static Behavior<TemperatureSensorCommand> create(
            ActorRef<TemperatureEnvironment.TemperatureEnvironmentCommand> environment,
            ActorRef<AirCondition.AirConditionCommand> aircondition) {
        return Behaviors.setup(context -> Behaviors.withTimers(timers -> new TemperatureSensor(context, timers, environment, aircondition)));
    }

    private TemperatureSensor(ActorContext<TemperatureSensorCommand> context, TimerScheduler<TemperatureSensorCommand> timers, ActorRef<TemperatureEnvironment.TemperatureEnvironmentCommand> environment, ActorRef<AirCondition.AirConditionCommand> aircondition) {
        super(context);
        this.environment = environment;
        this.aircondition = aircondition;
        timers.startTimerWithFixedDelay("measure", new MeasureTemperature(), Duration.ofSeconds(5));
    }

    @Override
    public Receive<TemperatureSensorCommand> createReceive() {
        return newReceiveBuilder()
                .onMessage(MeasureTemperature.class, this::onMeasure)
                .onMessage(TemperatureResult.class, this::onResult)
                .onMessage(SetEnabled.class, this::onSetEnabled)
                .build();
    }

    private Behavior<TemperatureSensorCommand> onMeasure(MeasureTemperature measure) {
        if (!enabled) {
            getContext().getLog().debug("Sensor disabled, skipping measurement");
            return this;
        }
        environment.tell(new TemperatureEnvironment.RequestTemperature(getContext().getSelf()));
        return this;
    }



    private Behavior<TemperatureSensorCommand> onResult(TemperatureResult result) {
        getContext().getLog().info("Temperature sensor received result " + result);
        aircondition.tell(new AirCondition.EnrichedTemperature(
                result.temperature().value(),
                result.temperature().unit()
        ));
        return this;

    }

    private Behavior<TemperatureSensorCommand> onSetEnabled(SetEnabled message) {
        this.enabled = message.enabled;
        getContext().getLog().info("Sensor {}", enabled ? "enabled" : "disabled");
        return this;
    }

}
