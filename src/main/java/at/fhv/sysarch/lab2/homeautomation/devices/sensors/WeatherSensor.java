package at.fhv.sysarch.lab2.homeautomation.devices.sensors;

import at.fhv.sysarch.lab2.homeautomation.environment.EnvironmentActor;
import at.fhv.sysarch.lab2.homeautomation.shared.model.WeatherCondition;
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
public class WeatherSensor extends AbstractBehavior<EnvironmentActor.WeatherSensorNotification> {
    public record ReadWeather(ActorRef<WeatherReading> replyTo) implements EnvironmentActor.WeatherSensorNotification {}
    public record WeatherReading(Optional<WeatherCondition> condition) {}

    private WeatherCondition lastReading;

    public static Behavior<EnvironmentActor.WeatherSensorNotification> create(ActorRef<EnvironmentActor.EnvironmentCommand> environmentActor) {
        return Behaviors.setup(context -> new WeatherSensor(context, environmentActor));
    }

    private WeatherSensor(ActorContext<EnvironmentActor.WeatherSensorNotification> context,
                          ActorRef<EnvironmentActor.EnvironmentCommand> environmentActor) {
        super(context);
        this.lastReading = null;
        context.getSystem().receptionist().tell(Receptionist.register(EnvironmentActor.WEATHER_SENSOR_SERVICE_KEY, context.getSelf()));

        environmentActor.tell(new EnvironmentActor.RegisterWeatherSensor(context.getSelf()));
        getContext().getLog().info("WeatherSensor started and registered via Receptionist");
    }

    @Override
    public Receive<EnvironmentActor.WeatherSensorNotification> createReceive() {
        return newReceiveBuilder()
                .onMessage(
                        EnvironmentActor.WeatherSensorNotification.EnvironmentWeatherChanged.class,
                        this::onEnvironmentWeatherChanged)
                .onMessage(ReadWeather.class, this::onReadWeather)
                .onSignal(PostStop.class, signal -> onPostStop())
                .build();
    }

    private Behavior<EnvironmentActor.WeatherSensorNotification> onEnvironmentWeatherChanged(EnvironmentActor.WeatherSensorNotification.EnvironmentWeatherChanged update) {
        this.lastReading = update.condition();
        getContext().getLog().info("WeatherSensor measured: {}", lastReading);
        return this;
    }

    private Behavior<EnvironmentActor.WeatherSensorNotification> onReadWeather(ReadWeather request) {
        request.replyTo().tell(new WeatherReading(Optional.ofNullable(lastReading)));
        return this;
    }

    private WeatherSensor onPostStop() {
        getContext().getLog().info("WeatherSensor stopped (last reading: {})", lastReading != null ? lastReading : "none");
        return this;
    }
}
