package at.fhv.sysarch.lab2.homeautomation.devices.sensor;

import at.fhv.sysarch.lab2.homeautomation.devices.Blinds;
import at.fhv.sysarch.lab2.homeautomation.shared.model.WeatherCondition;
import org.apache.pekko.actor.typed.ActorRef;
import org.apache.pekko.actor.typed.Behavior;
import org.apache.pekko.actor.typed.javadsl.*;

public class WeatherSensor extends AbstractBehavior<WeatherSensor.WeatherSensorCommand> {
    public interface WeatherSensorCommand {}

    public record WeatherMeasured(WeatherCondition condition) implements WeatherSensorCommand {}
    public record SetEnabled(boolean enabled) implements WeatherSensorCommand {}
    private final ActorRef<Blinds.BlindsCommand> blinds;
    private boolean enabled = true;

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
                .onMessage(WeatherMeasured.class, this::onWeatherMeasured)
                .onMessage(SetEnabled.class, this::onSetEnabled)
                .build();
    }

    private Behavior<WeatherSensorCommand> onWeatherMeasured(WeatherMeasured measurement) {
        if (!enabled) {
            getContext().getLog().debug("WeatherSensor disabled, dropping {}", measurement.condition());
            return this;
        }
        WeatherCondition condition = measurement.condition();
        getContext().getLog().info("WeatherSensor: measured {}", condition);
        blinds.tell(new Blinds.WeatherUpdate(condition));
        return this;
    }

    private Behavior<WeatherSensorCommand> onSetEnabled(SetEnabled message) {
        this.enabled = message.enabled();
        getContext().getLog().info("WeatherSensor {}", enabled ? "enabled" : "disabled");
        return this;
    }
}