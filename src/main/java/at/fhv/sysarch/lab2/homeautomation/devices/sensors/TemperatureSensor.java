package at.fhv.sysarch.lab2.homeautomation.devices.sensors;

import at.fhv.sysarch.lab2.homeautomation.environment.EnvironmentActor;
import at.fhv.sysarch.lab2.homeautomation.shared.model.Temperature;
import org.apache.pekko.actor.typed.ActorRef;
import org.apache.pekko.actor.typed.Behavior;
import org.apache.pekko.actor.typed.PostStop;
import org.apache.pekko.actor.typed.javadsl.AbstractBehavior;
import org.apache.pekko.actor.typed.javadsl.ActorContext;
import org.apache.pekko.actor.typed.javadsl.Behaviors;
import org.apache.pekko.actor.typed.javadsl.Receive;
import org.apache.pekko.actor.typed.receptionist.Receptionist;
import org.apache.pekko.actor.typed.receptionist.ServiceKey;

import java.util.Optional;

public class TemperatureSensor extends AbstractBehavior<EnvironmentActor.TemperatureSensorNotification> {
    public static final ServiceKey<EnvironmentActor.TemperatureSensorNotification> TEMPERATURE_SENSOR_KEY =
            ServiceKey.create(EnvironmentActor.TemperatureSensorNotification.class, "temperatureSensor");

    public record ReadTemperature(ActorRef<TemperatureReading> replyTo) implements EnvironmentActor.TemperatureSensorNotification {}

    public record TemperatureReading(Optional<Temperature> temperature) {}
    private Temperature lastReading;

    public static Behavior<EnvironmentActor.TemperatureSensorNotification> create(ActorRef<EnvironmentActor.EnvironmentCommand> environmentActor) {
        return Behaviors.setup(context -> new TemperatureSensor(context, environmentActor));
    }

    private TemperatureSensor(ActorContext<EnvironmentActor.TemperatureSensorNotification> context, ActorRef<EnvironmentActor.EnvironmentCommand> environmentActor) {
        super(context);
        this.lastReading = null;
        context.getSystem().receptionist().tell(Receptionist.register(TEMPERATURE_SENSOR_KEY, context.getSelf()));
        environmentActor.tell(new EnvironmentActor.RegisterTemperatureSensor(context.getSelf()));
        getContext().getLog().info("TemperatureSensor started and registered with environment");
    }

    @Override
    public Receive<EnvironmentActor.TemperatureSensorNotification> createReceive() {
        return newReceiveBuilder()
                .onMessage(EnvironmentActor.TemperatureSensorNotification.EnvironmentTemperatureChanged.class,
                        this::onEnvironmentTemperatureChanged)
                .onMessage(ReadTemperature.class, this::onReadTemperature)
                .onSignal(PostStop.class, signal -> onPostStop())
                .build();
    }

    private Behavior<EnvironmentActor.TemperatureSensorNotification> onEnvironmentTemperatureChanged(EnvironmentActor.TemperatureSensorNotification.EnvironmentTemperatureChanged update) {
        try {
            Temperature measured = Temperature.celsius(update.temperature());
            this.lastReading = measured;
            getContext().getLog().info("TemperatureSensor measured: {}", measured);
        } catch (Exception e) {
            getContext().getLog().warn("TemperatureSensor received invalid value: {} ({})", update.temperature(), e.getMessage());
        }
        return this;
    }

    private Behavior<EnvironmentActor.TemperatureSensorNotification> onReadTemperature(ReadTemperature request) {
        request.replyTo().tell(new TemperatureReading(Optional.ofNullable(lastReading)));
        return this;
    }

    private TemperatureSensor onPostStop() {
        getContext().getLog().info("TemperatureSensor stopped (last reading: {})", lastReading != null ? lastReading : "none");
        return this;
    }
}
