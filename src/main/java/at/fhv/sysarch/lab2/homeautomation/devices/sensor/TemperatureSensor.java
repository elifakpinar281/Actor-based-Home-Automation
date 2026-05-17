package at.fhv.sysarch.lab2.homeautomation.devices.sensor;

import at.fhv.sysarch.lab2.homeautomation.devices.AirCondition;
import at.fhv.sysarch.lab2.homeautomation.shared.model.environment.Temperature;
import org.apache.pekko.actor.typed.ActorRef;
import org.apache.pekko.actor.typed.Behavior;
import org.apache.pekko.actor.typed.javadsl.*;
import org.apache.pekko.actor.typed.receptionist.Receptionist;
import org.apache.pekko.actor.typed.receptionist.ServiceKey;

import java.util.HashSet;
import java.util.Set;

public class TemperatureSensor extends AbstractBehavior<TemperatureSensor.TemperatureSensorCommand> {
    public interface TemperatureSensorCommand {}

    public record TemperatureMeasured(double celsius) implements TemperatureSensorCommand {}
    public record SetEnabled(boolean enabled) implements TemperatureSensorCommand {}

    private record AirConditionsUpdated(Set<ActorRef<AirCondition.AirConditionCommand>> airConditions) implements TemperatureSensorCommand {}
    private record IgnoredListing() implements TemperatureSensorCommand {}

    public static final ServiceKey<TemperatureSensorCommand> SERVICE_KEY =
            ServiceKey.create(TemperatureSensorCommand.class, "temperatureSensor");

    private final Set<ActorRef<AirCondition.AirConditionCommand>> airConditions = new HashSet<>();
    private boolean enabled = true;

    public static Behavior<TemperatureSensorCommand> create() {
        return Behaviors.setup(TemperatureSensor::new);
    }

    private TemperatureSensor(ActorContext<TemperatureSensorCommand> context) {
        super(context);

        ActorRef<Receptionist.Listing> adapter = context.messageAdapter(
                Receptionist.Listing.class,
                listing -> listing.isForKey(AirCondition.SERVICE_KEY)
                        ? new AirConditionsUpdated(listing.getServiceInstances(AirCondition.SERVICE_KEY))
                        : new IgnoredListing()
        );
        context.getSystem().receptionist().tell(Receptionist.subscribe(AirCondition.SERVICE_KEY, adapter));

        getContext().getLog().info("TemperatureSensor started - discovering AC actuators via Receptionist");
    }

    @Override
    public Receive<TemperatureSensorCommand> createReceive() {
        return newReceiveBuilder()
                .onMessage(TemperatureMeasured.class, this::onTemperatureMeasured)
                .onMessage(SetEnabled.class, this::onSetEnabled)
                .onMessage(AirConditionsUpdated.class, this::onAirConditionsUpdated)
                .onMessage(IgnoredListing.class, msg -> Behaviors.same())
                .build();
    }

    private Behavior<TemperatureSensorCommand> onTemperatureMeasured(TemperatureMeasured measurement) {
        if (!enabled) {
            getContext().getLog().debug("TemperatureSensor disabled, dropping {}°C", measurement.celsius());
            return this;
        }
        Temperature temperature = Temperature.celsius(measurement.celsius());
        getContext().getLog().info("TemperatureSensor: measured {} - broadcasting to {} subscriber(s)",
                temperature, airConditions.size());

        for (ActorRef<AirCondition.AirConditionCommand> ac : airConditions) {
            ac.tell(new AirCondition.EnrichedTemperature(temperature));
        }
        return this;
    }

    private Behavior<TemperatureSensorCommand> onSetEnabled(SetEnabled message) {
        this.enabled = message.enabled();
        getContext().getLog().info("TemperatureSensor {}", enabled ? "enabled" : "disabled");
        return this;
    }

    private Behavior<TemperatureSensorCommand> onAirConditionsUpdated(AirConditionsUpdated message) {
        airConditions.clear();
        airConditions.addAll(message.airConditions());
        getContext().getLog().info("TemperatureSensor: AC actuators discovered -> {} registered", airConditions.size());
        return this;
    }
}