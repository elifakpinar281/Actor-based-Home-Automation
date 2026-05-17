package at.fhv.sysarch.lab2.homeautomation.devices.sensor;

import at.fhv.sysarch.lab2.homeautomation.devices.Blinds;
import at.fhv.sysarch.lab2.homeautomation.shared.model.environment.WeatherCondition;
import org.apache.pekko.actor.typed.ActorRef;
import org.apache.pekko.actor.typed.Behavior;
import org.apache.pekko.actor.typed.javadsl.*;
import org.apache.pekko.actor.typed.receptionist.Receptionist;
import org.apache.pekko.actor.typed.receptionist.ServiceKey;

import java.util.HashSet;
import java.util.Set;

public class WeatherSensor extends AbstractBehavior<WeatherSensor.WeatherSensorCommand> {
    public interface WeatherSensorCommand {}

    public record WeatherMeasured(WeatherCondition condition) implements WeatherSensorCommand {}
    public record SetEnabled(boolean enabled) implements WeatherSensorCommand {}

    private record BlindsUpdated(Set<ActorRef<Blinds.BlindsCommand>> blinds) implements WeatherSensorCommand {}
    private record IgnoredListing() implements WeatherSensorCommand {}

    public static final ServiceKey<WeatherSensorCommand> SERVICE_KEY =
            ServiceKey.create(WeatherSensorCommand.class, "weatherSensor");

    private final Set<ActorRef<Blinds.BlindsCommand>> blinds = new HashSet<>();
    private boolean enabled = true;

    public static Behavior<WeatherSensorCommand> create() {
        return Behaviors.setup(WeatherSensor::new);
    }

    private WeatherSensor(ActorContext<WeatherSensorCommand> context) {
        super(context);

        // Irrelevante Updates werden ignoriert. Nur Updates des Blind-Services werden verarbeitet.
        ActorRef<Receptionist.Listing> adapter = context.messageAdapter(
                Receptionist.Listing.class,
                listing -> listing.isForKey(Blinds.SERVICE_KEY)
                        ? new BlindsUpdated(listing.getServiceInstances(Blinds.SERVICE_KEY))
                        : new IgnoredListing()
        );
        context.getSystem().receptionist().tell(Receptionist.subscribe(Blinds.SERVICE_KEY, adapter));
        getContext().getLog().info("WeatherSensor started - discovering Blinds actuators via Receptionist");
    }

    @Override
    public Receive<WeatherSensorCommand> createReceive() {
        return newReceiveBuilder()
                .onMessage(WeatherMeasured.class, this::onWeatherMeasured)
                .onMessage(SetEnabled.class, this::onSetEnabled)
                .onMessage(BlindsUpdated.class, this::onBlindsUpdated)
                .onMessage(IgnoredListing.class, msg -> Behaviors.same())
                .build();
    }

    private Behavior<WeatherSensorCommand> onWeatherMeasured(WeatherMeasured measurement) {
        if (!enabled) {
            getContext().getLog().debug("WeatherSensor disabled, dropping {}", measurement.condition());
            return this;
        }
        WeatherCondition condition = measurement.condition();
        getContext().getLog().info("WeatherSensor: measured {} - broadcasting to {} subscriber(s)",
                condition, blinds.size());

        for (ActorRef<Blinds.BlindsCommand> b : blinds) {
            b.tell(new Blinds.WeatherUpdate(condition));
        }
        return this;
    }

    private Behavior<WeatherSensorCommand> onSetEnabled(SetEnabled message) {
        this.enabled = message.enabled();
        getContext().getLog().info("WeatherSensor {}", enabled ? "enabled" : "disabled");
        return this;
    }

    private Behavior<WeatherSensorCommand> onBlindsUpdated(BlindsUpdated message) {
        blinds.clear();
        blinds.addAll(message.blinds());
        getContext().getLog().info("WeatherSensor: Blinds actuators discovered -> {} registered", blinds.size());
        return this;
    }
}