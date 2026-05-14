package at.fhv.sysarch.lab2.homeautomation;

import at.fhv.sysarch.lab2.homeautomation.devices.*;
import at.fhv.sysarch.lab2.homeautomation.devices.sensor.TemperatureSensor;
import at.fhv.sysarch.lab2.homeautomation.devices.sensor.WeatherSensor;
import at.fhv.sysarch.lab2.homeautomation.environment.EnvironmentSwitch;
import at.fhv.sysarch.lab2.homeautomation.environment.MqttEnvironmentClient;
import at.fhv.sysarch.lab2.homeautomation.environment.TemperatureEnvironment;
import at.fhv.sysarch.lab2.homeautomation.environment.WeatherEnvironment;
import at.fhv.sysarch.lab2.homeautomation.uihandler.HttpServer;
import org.apache.pekko.actor.typed.ActorRef;
import org.apache.pekko.actor.typed.Behavior;
import org.apache.pekko.actor.typed.PostStop;
import org.apache.pekko.actor.typed.javadsl.AbstractBehavior;
import org.apache.pekko.actor.typed.javadsl.ActorContext;
import org.apache.pekko.actor.typed.javadsl.Behaviors;
import org.apache.pekko.actor.typed.javadsl.Receive;
import org.apache.pekko.http.javadsl.Http;
import org.apache.pekko.http.javadsl.ServerBinding;
import java.util.concurrent.CompletionStage;
import org.apache.pekko.actor.typed.receptionist.Receptionist;
import org.apache.pekko.actor.typed.receptionist.ServiceKey;


public class HomeAutomationController extends AbstractBehavior<Void> {
    public static Behavior<Void> create() {
        return Behaviors.setup(HomeAutomationController::new);
    }

    private HomeAutomationController(ActorContext<Void> context) {
        super(context);

        ActorRef<AirCondition.AirConditionCommand> airCondition =
                getContext().spawn(AirCondition.create("AC-01"), "airCondition");

        ActorRef<Blinds.BlindsCommand> blinds =
                getContext().spawn(Blinds.create("BLINDS-01"), "blinds");

        ActorRef<OrderProcessor.OrderProcessorCommand> orderProcessor =
                getContext().spawn(OrderProcessor.create(), "orderProcessor");

        ActorRef<Fridge.FridgeCommand> fridge =
                getContext().spawn(
                        Fridge.create("FRIDGE-01", 80, 25.0, orderProcessor),
                        "fridge"
                );

        ActorRef<MediaStation.MediaStationCommand> mediaStation =
                getContext().spawn(MediaStation.create("MEDIA-01"), "mediaStation");


        ActorRef<TemperatureEnvironment.TemperatureEnvironmentCommand> temperatureEnvironment =
                context.spawn(TemperatureEnvironment.create(), "temperatureEnvironment");

        ActorRef<WeatherEnvironment.WeatherEnvironmentCommand> weatherEnvironment =
                context.spawn(WeatherEnvironment.create(), "weatherEnvironment");

        ActorRef<TemperatureSensor.TemperatureSensorCommand> temperatureSensor =
                context.spawn(TemperatureSensor.create(airCondition), "temperatureSensor");

        ActorRef<WeatherSensor.WeatherSensorCommand> weatherSensor =
                context.spawn(WeatherSensor.create(blinds), "weatherSensor");

        ActorRef<EnvironmentSwitch.EnvironmentSwitchCommand> environmentSwitch =
                context.spawn(EnvironmentSwitch.create(
                        temperatureSensor, weatherSensor
                ), "environmentSwitch");

        temperatureSensor.tell(new TemperatureSensor.SetEnvironmentSwitch(environmentSwitch));
        weatherSensor.tell(new WeatherSensor.SetEnvironmentSwitch(environmentSwitch));
        temperatureEnvironment.tell(new TemperatureEnvironment.SetEnvironmentSwitch(environmentSwitch));
        weatherEnvironment.tell(new WeatherEnvironment.SetEnvironmentSwitch(environmentSwitch));

        context.getSystem().receptionist().tell(
                Receptionist.register(AirCondition.SERVICE_KEY, airCondition));
        context.getSystem().receptionist().tell(
                Receptionist.register(Blinds.SERVICE_KEY, blinds));
        context.getSystem().receptionist().tell(
                Receptionist.register(MediaStation.SERVICE_KEY, mediaStation));
        context.getSystem().receptionist().tell(
                Receptionist.register(Fridge.SERVICE_KEY, fridge));
        context.getSystem().receptionist().tell(
                Receptionist.register(EnvironmentSwitch.SERVICE_KEY, environmentSwitch));

        try {
            MqttEnvironmentClient mqttClient = new MqttEnvironmentClient(environmentSwitch);
            mqttClient.connect();
            getContext().getLog().info("MQTT connected successfully");
        } catch (Exception e) {
            getContext().getLog().warn("MQTT not available: {}", e.getMessage());
        }

        final Http http = Http.get(context.getSystem());
        HttpServer app = new HttpServer(environmentSwitch, fridge, mediaStation, blinds, airCondition, context.getSystem());
        final CompletionStage<ServerBinding> binding =
                http.newServerAt("localhost", 8084).bind(app.createRoute());

        getContext().getLog().info("HomeAutomation Application started - PRESS RETURN TO EXIT");

        try {
            System.in.read();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public Receive<Void> createReceive() {
        return newReceiveBuilder().onSignal(PostStop.class, signal -> onPostStop()).build();
    }

    private HomeAutomationController onPostStop() {
        getContext().getLog().info("HomeAutomation Application stopped");
        return this;
    }
}
