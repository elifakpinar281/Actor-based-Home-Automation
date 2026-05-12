package at.fhv.sysarch.lab2.homeautomation.devices.sensor;

import at.fhv.sysarch.lab2.homeautomation.devices.AirCondition;
import at.fhv.sysarch.lab2.homeautomation.environment.EnvironmentSwitch;
import org.apache.pekko.actor.typed.ActorRef;
import org.apache.pekko.actor.typed.Behavior;
import org.apache.pekko.actor.typed.javadsl.*;

public class TemperatureSensor extends AbstractBehavior<TemperatureSensor.TemperatureSensorCommand> {
    public interface TemperatureSensorCommand {}
    public record SetEnabled(boolean enabled) implements TemperatureSensorCommand {}
    public record TemperatureResult(TemperatureReading temperature) implements TemperatureSensorCommand {}
    public record SetEnvironmentSwitch(ActorRef<EnvironmentSwitch.EnvironmentSwitchCommand> environmentSwitch) implements TemperatureSensorCommand {}

    private boolean enabled = true;
    private final ActorRef<AirCondition.AirConditionCommand> aircondition;

    public static Behavior<TemperatureSensorCommand> create(ActorRef<AirCondition.AirConditionCommand> aircondition) {
        return Behaviors.setup(context -> new TemperatureSensor(context, aircondition));
    }

    private TemperatureSensor(ActorContext<TemperatureSensorCommand> context, ActorRef<AirCondition.AirConditionCommand> aircondition) {
        super(context);
        this.aircondition = aircondition;
        getContext().getLog().info("TemperatureSensor started");
    }

    @Override
    public Receive<TemperatureSensorCommand> createReceive() {
        return newReceiveBuilder()
                .onMessage(TemperatureResult.class, this::onResult)
                .onMessage(SetEnabled.class, this::onSetEnabled)
                .onMessage(SetEnvironmentSwitch.class, this::onSetEnvironmentSwitch)
                .build();
    }

    private Behavior<TemperatureSensorCommand> onSetEnvironmentSwitch(SetEnvironmentSwitch msg) {
        return this;
    }

    private Behavior<TemperatureSensorCommand> onResult(TemperatureResult result) {
        if (!enabled) {
            getContext().getLog().debug("Sensor disabled, ignoring result");
            return this;
        }
        getContext().getLog().info("TemperatureSensor: measured {} {}", String.format("%.1f", result.temperature().value()), result.temperature().unit());
        aircondition.tell(new AirCondition.EnrichedTemperature(
                result.temperature().value(),
                result.temperature().unit()
        ));
        return this;
    }

    private Behavior<TemperatureSensorCommand> onSetEnabled(SetEnabled message) {
        this.enabled = message.enabled();
        getContext().getLog().info("TemperatureSensor {}", enabled ? "enabled" : "disabled");
        return this;
    }
}