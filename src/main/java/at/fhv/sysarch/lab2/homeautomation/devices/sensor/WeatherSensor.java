package at.fhv.sysarch.lab2.homeautomation.devices.sensor;

import at.fhv.sysarch.lab2.homeautomation.devices.Blinds;
import at.fhv.sysarch.lab2.homeautomation.environment.EnvironmentSwitch;
import at.fhv.sysarch.lab2.homeautomation.environment.WeatherEnvironment;
import at.fhv.sysarch.lab2.homeautomation.shared.model.WeatherCondition;
import org.apache.pekko.actor.typed.ActorRef;
import org.apache.pekko.actor.typed.Behavior;
import org.apache.pekko.actor.typed.javadsl.*;

import java.time.Duration;

public class WeatherSensor extends AbstractBehavior<WeatherSensor.WeatherSensorCommand> {
    public interface WeatherSensorCommand {}
    public record SetEnabled(boolean enabled) implements WeatherSensorCommand {}
    public record MeasureWeather() implements WeatherSensorCommand {}
    public record WeatherResult(WeatherCondition condition) implements WeatherSensorCommand {}
    public record SetEnvironmentSwitch(ActorRef<EnvironmentSwitch.EnvironmentSwitchCommand> environmentSwitch) implements WeatherSensorCommand {}

    private boolean enabled = true;
    private ActorRef<EnvironmentSwitch.EnvironmentSwitchCommand> environmentSwitch;
    private final ActorRef<Blinds.BlindsCommand> blinds;

    public static Behavior<WeatherSensorCommand> create(ActorRef<Blinds.BlindsCommand> blinds) {
        return Behaviors.setup(context ->
                Behaviors.withTimers(timers ->
                        new WeatherSensor(context, timers, blinds)
                )
        );
    }

    private WeatherSensor(ActorContext<WeatherSensorCommand> context, TimerScheduler<WeatherSensorCommand> timers, ActorRef<Blinds.BlindsCommand> blinds) {
        super(context);
        this.blinds = blinds;
        timers.startTimerWithFixedDelay("measure", new MeasureWeather(), Duration.ofSeconds(5));
    }

    @Override
    public Receive<WeatherSensorCommand> createReceive() {
        return newReceiveBuilder()
                .onMessage(MeasureWeather.class, this::onMeasure)
                .onMessage(WeatherResult.class, this::onResult)
                .onMessage(SetEnabled.class, this::onSetEnabled)
                .onMessage(SetEnvironmentSwitch.class, this::onSetEnvironmentSwitch)
                .build();
    }

    private Behavior<WeatherSensorCommand> onSetEnvironmentSwitch(SetEnvironmentSwitch msg) {
        this.environmentSwitch = msg.environmentSwitch();
        return this;
    }

    private Behavior<WeatherSensorCommand> onMeasure(MeasureWeather message) {
        if (!enabled || environmentSwitch == null) {
            getContext().getLog().debug("Sensor disabled, skipping measurement");
            return this;
        }

        environmentSwitch.tell(new EnvironmentSwitch.RequestWeather(getContext().getSelf()));
        return this;
    }

    private Behavior<WeatherSensorCommand> onSetEnabled(SetEnabled message) {
        this.enabled = message.enabled();
        getContext().getLog().info("Sensor {}", enabled ? "enabled" : "disabled");
        return this;
    }

    private Behavior<WeatherSensorCommand> onResult(WeatherResult message) {
        getContext().getLog().info("WeatherSensor measured {}", message.condition());
        blinds.tell(new Blinds.WeatherUpdate(message.condition()));
        return this;
    }

}
