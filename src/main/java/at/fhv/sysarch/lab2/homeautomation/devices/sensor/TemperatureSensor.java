package at.fhv.sysarch.lab2.homeautomation.devices.sensor;

import at.fhv.sysarch.lab2.homeautomation.devices.AirCondition;
import at.fhv.sysarch.lab2.homeautomation.shared.model.Temperature;
import org.apache.pekko.actor.typed.ActorRef;
import org.apache.pekko.actor.typed.Behavior;
import org.apache.pekko.actor.typed.javadsl.*;

public class TemperatureSensor extends AbstractBehavior<TemperatureSensor.TemperatureSensorCommand> {
    public interface TemperatureSensorCommand {}

    public record TemperatureMeasured(double celsius) implements TemperatureSensorCommand {}
    public record SetEnabled(boolean enabled) implements TemperatureSensorCommand {}

    private final ActorRef<AirCondition.AirConditionCommand> airCondition;
    private boolean enabled = true;

    public static Behavior<TemperatureSensorCommand> create(ActorRef<AirCondition.AirConditionCommand> airCondition) {
        return Behaviors.setup(context -> new TemperatureSensor(context, airCondition));
    }

    private TemperatureSensor(ActorContext<TemperatureSensorCommand> context, ActorRef<AirCondition.AirConditionCommand> airCondition) {
        super(context);
        this.airCondition = airCondition;
        getContext().getLog().info("TemperatureSensor started");
    }

    @Override
    public Receive<TemperatureSensorCommand> createReceive() {
        return newReceiveBuilder()
                .onMessage(TemperatureMeasured.class, this::onTemperatureMeasured)
                .onMessage(SetEnabled.class, this::onSetEnabled)
                .build();
    }

    private Behavior<TemperatureSensorCommand> onTemperatureMeasured(TemperatureMeasured measurement) {
        if (!enabled) {
            getContext().getLog().debug("TemperatureSensor disabled, dropping {}°C", measurement.celsius());
            return this;
        }
        Temperature temperature = Temperature.celsius(measurement.celsius());
        getContext().getLog().info("TemperatureSensor: measured {}", temperature);
        airCondition.tell(new AirCondition.EnrichedTemperature(temperature));
        return this;
    }

    private Behavior<TemperatureSensorCommand> onSetEnabled(SetEnabled message) {
        this.enabled = message.enabled();
        getContext().getLog().info("TemperatureSensor {}", enabled ? "enabled" : "disabled");
        return this;
    }
}
