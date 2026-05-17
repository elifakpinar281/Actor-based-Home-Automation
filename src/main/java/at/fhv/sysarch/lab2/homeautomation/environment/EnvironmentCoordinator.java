package at.fhv.sysarch.lab2.homeautomation.environment;

import at.fhv.sysarch.lab2.homeautomation.devices.sensor.TemperatureSensor;
import at.fhv.sysarch.lab2.homeautomation.devices.sensor.WeatherSensor;
import at.fhv.sysarch.lab2.homeautomation.shared.model.environment.Temperature;
import at.fhv.sysarch.lab2.homeautomation.shared.model.environment.WeatherCondition;
import org.apache.pekko.actor.typed.ActorRef;
import org.apache.pekko.actor.typed.Behavior;
import org.apache.pekko.actor.typed.javadsl.AbstractBehavior;
import org.apache.pekko.actor.typed.javadsl.ActorContext;
import org.apache.pekko.actor.typed.javadsl.Behaviors;
import org.apache.pekko.actor.typed.javadsl.Receive;
import org.apache.pekko.actor.typed.receptionist.Receptionist;
import org.apache.pekko.actor.typed.receptionist.ServiceKey;

import java.util.HashSet;
import java.util.Set;

public class EnvironmentCoordinator extends AbstractBehavior<EnvironmentCoordinator.Command> {
    public interface Command {}

    public record SetMode(SimulationMode mode) implements Command {}
    public record SetFixedTemperature(double celsius) implements Command {}
    public record SetFixedWeather(WeatherCondition condition) implements Command {}

    public record InternalTemperatureUpdate(double celsius) implements Command {}
    public record InternalWeatherUpdate(WeatherCondition condition) implements Command {}
    public record MqttTemperatureUpdate(double celsius) implements Command {}
    public record MqttWeatherUpdate(WeatherCondition condition) implements Command {}

    public record GetCurrentState(ActorRef<EnvironmentSnapshot> replyTo) implements Command {}

    private record TemperatureSensorsUpdated(Set<ActorRef<TemperatureSensor.TemperatureSensorCommand>> sensors) implements Command {}
    private record WeatherSensorsUpdated(Set<ActorRef<WeatherSensor.WeatherSensorCommand>> sensors) implements Command {}

    public static final ServiceKey<Command> SERVICE_KEY =
            ServiceKey.create(Command.class, "environmentCoordinator");

    private final Set<ActorRef<TemperatureSensor.TemperatureSensorCommand>> temperatureSensors = new HashSet<>();
    private final Set<ActorRef<WeatherSensor.WeatherSensorCommand>> weatherSensors = new HashSet<>();

    private SimulationMode mode = SimulationMode.INTERNAL;
    private Temperature currentTemperature = Temperature.celsius(23.0);
    private WeatherCondition currentWeather = WeatherCondition.SUNNY;
    private double fixedTemperature = 20.0;
    private WeatherCondition fixedWeather = WeatherCondition.SUNNY;


    public static Behavior<Command> create() {
        return Behaviors.setup(EnvironmentCoordinator::new);
    }

    private EnvironmentCoordinator(ActorContext<Command> context) {
        super(context);

        ActorRef<Receptionist.Listing> temperatureAdapter = context.messageAdapter(
                Receptionist.Listing.class,
                listing -> new TemperatureSensorsUpdated(listing.getServiceInstances(TemperatureSensor.SERVICE_KEY))
        );
        ActorRef<Receptionist.Listing> weatherAdapter = context.messageAdapter(
                Receptionist.Listing.class,
                listing -> new WeatherSensorsUpdated(listing.getServiceInstances(WeatherSensor.SERVICE_KEY))
        );

        context.getSystem().receptionist().tell(Receptionist.subscribe(TemperatureSensor.SERVICE_KEY, temperatureAdapter));
        context.getSystem().receptionist().tell(Receptionist.subscribe(WeatherSensor.SERVICE_KEY, weatherAdapter));

        getContext().getLog().info("EnvironmentCoordinator started in mode {} — discovering sensors via Receptionist", mode);
    }

    @Override
    public Receive<Command> createReceive() {
        return newReceiveBuilder()
                .onMessage(SetMode.class, this::onSetMode)
                .onMessage(SetFixedTemperature.class, this::onSetFixedTemperature)
                .onMessage(SetFixedWeather.class, this::onSetFixedWeather)
                .onMessage(InternalTemperatureUpdate.class, this::onInternalTemperatureUpdate)
                .onMessage(InternalWeatherUpdate.class, this::onInternalWeatherUpdate)
                .onMessage(MqttTemperatureUpdate.class, this::onMqttTemperatureUpdate)
                .onMessage(MqttWeatherUpdate.class, this::onMqttWeatherUpdate)
                .onMessage(GetCurrentState.class, this::onGetCurrentState)
                .onMessage(TemperatureSensorsUpdated.class, this::onTemperatureSensorsUpdated)
                .onMessage(WeatherSensorsUpdated.class, this::onWeatherSensorsUpdated)
                .build();
    }

    private Behavior<Command> onTemperatureSensorsUpdated(TemperatureSensorsUpdated message) {
        temperatureSensors.clear();
        temperatureSensors.addAll(message.sensors());
        getContext().getLog().info("EnvironmentCoordinator: temperature sensors discovered → {} registered", temperatureSensors.size());
        return this;
    }

    private Behavior<Command> onWeatherSensorsUpdated(WeatherSensorsUpdated message) {
        weatherSensors.clear();
        weatherSensors.addAll(message.sensors());
        getContext().getLog().info("EnvironmentCoordinator: weather sensors discovered → {} registered", weatherSensors.size());
        return this;
    }

    private Behavior<Command> onSetMode(SetMode message) {
        SimulationMode previous = mode;
        mode = message.mode();
        getContext().getLog().info("Simulation mode changed: {} → {}", previous, mode);
        if (mode == SimulationMode.FIXED) {
            pushTemperature(fixedTemperature);
            pushWeather(fixedWeather);
        }
        return this;
    }

    private Behavior<Command> onSetFixedTemperature(SetFixedTemperature message) {
        fixedTemperature = message.celsius();
        if (mode != SimulationMode.FIXED) {
            mode = SimulationMode.FIXED;
            getContext().getLog().info("Mode auto-switched to FIXED (explicit temperature override)");
        }
        pushTemperature(fixedTemperature);
        return this;
    }

    private Behavior<Command> onSetFixedWeather(SetFixedWeather message) {
        fixedWeather = message.condition();
        if (mode != SimulationMode.FIXED) {
            mode = SimulationMode.FIXED;
            getContext().getLog().info("Mode auto-switched to FIXED (explicit weather override)");
        }
        pushWeather(fixedWeather);
        return this;
    }

    private Behavior<Command> onInternalTemperatureUpdate(InternalTemperatureUpdate message) {
        if (mode == SimulationMode.INTERNAL) {
            pushTemperature(message.celsius());
        }
        return this;
    }

    private Behavior<Command> onInternalWeatherUpdate(InternalWeatherUpdate message) {
        if (mode == SimulationMode.INTERNAL) {
            pushWeather(message.condition());
        }
        return this;
    }

    private Behavior<Command> onMqttTemperatureUpdate(MqttTemperatureUpdate message) {
        if (mode == SimulationMode.EXTERNAL_MQTT) {
            pushTemperature(message.celsius());
        }
        return this;
    }

    private Behavior<Command> onMqttWeatherUpdate(MqttWeatherUpdate message) {
        if (mode == SimulationMode.EXTERNAL_MQTT) {
            pushWeather(message.condition());
        }
        return this;
    }

    private Behavior<Command> onGetCurrentState(GetCurrentState message) {
        message.replyTo().tell(new EnvironmentSnapshot(currentTemperature, currentWeather, mode));
        return this;
    }

    private void pushTemperature(double celsius) {
        double clamped = Temperature.clampToRange(celsius);
        currentTemperature = Temperature.celsius(clamped);
        if (temperatureSensors.isEmpty()) {
            getContext().getLog().debug("Pushed temperature {}°C — no temperature sensors registered yet", clamped);
            return;
        }
        for (ActorRef<TemperatureSensor.TemperatureSensorCommand> sensor : temperatureSensors) {
            sensor.tell(new TemperatureSensor.TemperatureMeasured(clamped));
        }
        getContext().getLog().debug("Pushed temperature {}°C to {} sensor(s)", clamped, temperatureSensors.size());
    }

    private void pushWeather(WeatherCondition condition) {
        currentWeather = condition;
        if (weatherSensors.isEmpty()) {
            getContext().getLog().debug("Pushed weather {} — no weather sensors registered yet", condition);
            return;
        }
        for (ActorRef<WeatherSensor.WeatherSensorCommand> sensor : weatherSensors) {
            sensor.tell(new WeatherSensor.WeatherMeasured(condition));
        }
        getContext().getLog().debug("Pushed weather {} to {} sensor(s)", condition, weatherSensors.size());
    }
}