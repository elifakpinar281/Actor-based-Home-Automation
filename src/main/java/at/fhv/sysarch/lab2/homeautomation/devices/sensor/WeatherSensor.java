package at.fhv.sysarch.lab2.homeautomation.devices.sensor;

import at.fhv.sysarch.lab2.homeautomation.devices.Blinds;
import at.fhv.sysarch.lab2.homeautomation.environment.EnvironmentSwitch;
import at.fhv.sysarch.lab2.homeautomation.shared.model.WeatherCondition;
import org.apache.pekko.actor.typed.ActorRef;
import org.apache.pekko.actor.typed.Behavior;
import org.apache.pekko.actor.typed.javadsl.*;

public class WeatherSensor extends AbstractBehavior<WeatherSensor.WeatherSensorCommand> {
    public interface WeatherSensorCommand {}
    public record SetEnabled(boolean enabled) implements WeatherSensorCommand {}
    public record WeatherResult(WeatherCondition condition) implements WeatherSensorCommand {}
    public record SetEnvironmentSwitch(ActorRef<EnvironmentSwitch.EnvironmentSwitchCommand> environmentSwitch) implements WeatherSensorCommand {}

    private boolean enabled = true;
    private final ActorRef<Blinds.BlindsCommand> blinds;

    public static Behavior<WeatherSensorCommand> create(ActorRef<Blinds.BlindsCommand> blinds) {
        return Behaviors.setup(context -> new WeatherSensor(context, blinds));
    }

    private WeatherSensor(ActorContext<WeatherSensorCommand> context, ActorRef<Blinds.BlindsCommand> blinds) {
        super(context);
        this.blinds = blinds;
        getContext().getLog().info("WeatherSensor started");
    }

    @Override
    public Receive<WeatherSensorCommand> createReceive() {
        return newReceiveBuilder()
                .onMessage(WeatherResult.class, this::onResult)
                .onMessage(SetEnabled.class, this::onSetEnabled)
                .onMessage(SetEnvironmentSwitch.class, this::onSetEnvironmentSwitch)
                .build();
    }

    private Behavior<WeatherSensorCommand> onSetEnvironmentSwitch(SetEnvironmentSwitch msg) {
        return this;
    }

    private Behavior<WeatherSensorCommand> onResult(WeatherResult message) {
        if (!enabled) {
            getContext().getLog().debug("Sensor disabled, ignoring result");
            return this;
        }
        getContext().getLog().info("WeatherSensor: measured {}", message.condition());
        blinds.tell(new Blinds.WeatherUpdate(message.condition()));
        return this;
    }

    private Behavior<WeatherSensorCommand> onSetEnabled(SetEnabled message) {
        this.enabled = message.enabled();
        getContext().getLog().info("WeatherSensor {}", enabled ? "enabled" : "disabled");
        return this;
    }
}