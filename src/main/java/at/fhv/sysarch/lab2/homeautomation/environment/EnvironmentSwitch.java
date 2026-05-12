package at.fhv.sysarch.lab2.homeautomation.environment;

import at.fhv.sysarch.lab2.homeautomation.devices.sensor.TemperatureReading;
import at.fhv.sysarch.lab2.homeautomation.devices.sensor.TemperatureSensor;
import at.fhv.sysarch.lab2.homeautomation.devices.sensor.WeatherSensor;
import at.fhv.sysarch.lab2.homeautomation.shared.model.WeatherCondition;
import org.apache.pekko.actor.typed.ActorRef;
import org.apache.pekko.actor.typed.Behavior;
import org.apache.pekko.actor.typed.javadsl.AbstractBehavior;
import org.apache.pekko.actor.typed.javadsl.ActorContext;
import org.apache.pekko.actor.typed.javadsl.Behaviors;
import org.apache.pekko.actor.typed.javadsl.Receive;

public class EnvironmentSwitch extends AbstractBehavior<EnvironmentSwitch.EnvironmentSwitchCommand> {
    public interface EnvironmentSwitchCommand {}
    public record SetMode(SimulationMode mode) implements EnvironmentSwitchCommand {}
    public record SetFixedTemperature(double celsius) implements EnvironmentSwitchCommand {}
    public record SetFixedWeather(WeatherCondition condition) implements EnvironmentSwitchCommand {}
    public record InternalTemperatureUpdate(double celsius) implements EnvironmentSwitchCommand {}
    public record InternalWeatherUpdate(WeatherCondition condition) implements EnvironmentSwitchCommand {}
    public record MqttTemperatureUpdate(double celsius) implements EnvironmentSwitchCommand {}
    public record MqttWeatherUpdate(WeatherCondition condition) implements EnvironmentSwitchCommand {}

    public record RequestTemperature(ActorRef<TemperatureSensor.TemperatureSensorCommand> replyTo) implements EnvironmentSwitchCommand {}
    public record RequestWeather(ActorRef<WeatherSensor.WeatherSensorCommand> replyTo) implements EnvironmentSwitchCommand {}

    private SimulationMode mode = SimulationMode.INTERNAL;
    private double fixedTemperature = 20.0;
    private WeatherCondition fixedWeather = WeatherCondition.SUNNY;
    private double latestTemperature = 23.0;
    private WeatherCondition latestWeather = WeatherCondition.SUNNY;
    private final ActorRef<TemperatureSensor.TemperatureSensorCommand> temperatureSensor;
    private final ActorRef<WeatherSensor.WeatherSensorCommand> weatherSensor;

    public static Behavior<EnvironmentSwitchCommand> create(ActorRef<TemperatureSensor.TemperatureSensorCommand> temperatureSensor, ActorRef<WeatherSensor.WeatherSensorCommand> weatherSensor,
            ActorRef<TemperatureEnvironment.TemperatureEnvironmentCommand> temperatureEnvironment, ActorRef<WeatherEnvironment.WeatherEnvironmentCommand> weatherEnvironment) {
        return Behaviors.setup(context -> new EnvironmentSwitch(context, temperatureSensor, weatherSensor, temperatureEnvironment, weatherEnvironment)
        );
    }

    private EnvironmentSwitch(ActorContext<EnvironmentSwitchCommand> context, ActorRef<TemperatureSensor.TemperatureSensorCommand> temperatureSensor, ActorRef<WeatherSensor.WeatherSensorCommand> weatherSensor) {
        super(context);
        this.temperatureSensor = temperatureSensor;
        this.weatherSensor = weatherSensor;
    }

    @Override
    public Receive<EnvironmentSwitchCommand> createReceive() {
        return newReceiveBuilder()
                .onMessage(SetMode.class, this::onSetMode)
                .onMessage(SetFixedTemperature.class, this::onSetFixedTemperature)
                .onMessage(SetFixedWeather.class, this::onSetFixedWeather)
                .onMessage(InternalTemperatureUpdate.class, this::onInternalTemperature)
                .onMessage(InternalWeatherUpdate.class, this::onInternalWeather)
                .onMessage(MqttTemperatureUpdate.class, this::onMqttTemperature)
                .onMessage(MqttWeatherUpdate.class, this::onMqttWeather)
                .onMessage(RequestTemperature.class, this::onRequestTemperature)
                .onMessage(RequestWeather.class, this::onRequestWeather)
                .build();
    }

    private Behavior<EnvironmentSwitchCommand> onSetMode(SetMode setMode) {
        this.mode = setMode.mode();
        getContext().getLog().info("Simulation mode switched to {}", mode);
        if (mode == SimulationMode.FIXED) {
            pushTemperature(fixedTemperature);
            pushWeather(fixedWeather);
        }
        return this;
    }

    private Behavior<EnvironmentSwitchCommand> onSetFixedTemperature(SetFixedTemperature setFixedTemperature) {
        fixedTemperature = setFixedTemperature.celsius();
        if (mode == SimulationMode.FIXED) {
            pushTemperature(fixedTemperature);
        }
        return this;

    }

    private Behavior<EnvironmentSwitchCommand> onSetFixedWeather(SetFixedWeather setFixedWeather) {
        fixedWeather = setFixedWeather.condition();
        if (mode == SimulationMode.FIXED) {
            pushWeather(fixedWeather);
        }
        return this;
    }

    private Behavior<EnvironmentSwitchCommand> onInternalTemperature(InternalTemperatureUpdate temperatureUpdate) {
        if (mode == SimulationMode.INTERNAL) {
            pushTemperature(temperatureUpdate.celsius());
        }
        return this;

    }

    private Behavior<EnvironmentSwitchCommand> onInternalWeather(InternalWeatherUpdate weatherUpdate) {
        if (mode == SimulationMode.INTERNAL) {
            pushWeather(weatherUpdate.condition());
        }
        return this;
    }

    private Behavior<EnvironmentSwitchCommand> onMqttTemperature(MqttTemperatureUpdate temperatureUpdate) {
        if (mode == SimulationMode.EXTERNAL_MQTT) {
            pushTemperature(temperatureUpdate.celsius());
        }
        return this;
    }

    private Behavior<EnvironmentSwitchCommand> onMqttWeather(MqttWeatherUpdate weatherUpdate) {
        if (mode == SimulationMode.EXTERNAL_MQTT) {
            pushWeather(weatherUpdate.condition());
        }
        return this;
    }

    private Behavior<EnvironmentSwitchCommand> onRequestTemperature(RequestTemperature msg) {
        if (mode != SimulationMode.DISABLED) {
            msg.replyTo().tell(new TemperatureSensor.TemperatureResult(TemperatureReading.celsius(latestTemperature)));
        }
        return this;
    }

    private Behavior<EnvironmentSwitchCommand> onRequestWeather(RequestWeather msg) {
        if (mode != SimulationMode.DISABLED) {
            msg.replyTo().tell(new WeatherSensor.WeatherResult(latestWeather));
        }
        return this;
    }

    private void pushTemperature(double celsius) {
        latestTemperature = celsius;
        temperatureSensor.tell(new TemperatureSensor.TemperatureResult(TemperatureReading.celsius(celsius)));
    }

    private void pushWeather(WeatherCondition weatherCondition) {
        latestWeather = weatherCondition;
        weatherSensor.tell(new WeatherSensor.WeatherResult(weatherCondition));
    }
}
