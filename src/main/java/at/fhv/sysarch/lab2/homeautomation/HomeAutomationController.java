package at.fhv.sysarch.lab2.homeautomation;

import at.fhv.sysarch.lab2.homeautomation.devices.*;
import at.fhv.sysarch.lab2.homeautomation.devices.sensor.TemperatureSensor;
import at.fhv.sysarch.lab2.homeautomation.devices.sensor.WeatherSensor;
import at.fhv.sysarch.lab2.homeautomation.environment.EnvironmentCoordinator;
import at.fhv.sysarch.lab2.homeautomation.environment.TemperatureEnvironment;
import at.fhv.sysarch.lab2.homeautomation.environment.MqttEnvironmentClient;
import at.fhv.sysarch.lab2.homeautomation.environment.WeatherEnvironment;
import at.fhv.sysarch.lab2.homeautomation.shared.exceptions.MqttConnectionException;
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

import java.io.IOException;
import java.util.concurrent.CompletionStage;
import org.apache.pekko.actor.typed.receptionist.Receptionist;


public class HomeAutomationController extends AbstractBehavior<Void> {
    private static final String HTTP_HOST = "localhost";
    private static final int HTTP_PORT = 8084;

    public static Behavior<Void> create() {
        return Behaviors.setup(HomeAutomationController::new);
    }

    private HomeAutomationController(ActorContext<Void> context) {
        super(context);

        ActorRef<AirCondition.AirConditionCommand> airCondition =
                context.spawn(AirCondition.create("AC-01"), "airCondition");

        ActorRef<Blinds.BlindsCommand> blinds =
                context.spawn(Blinds.create("BLINDS-01"), "blinds");

        ActorRef<Fridge.FridgeCommand> fridge =
                context.spawn(Fridge.create("FRIDGE-01", 80, 25.0), "fridge");

        ActorRef<MediaStation.MediaStationCommand> mediaStation =
                context.spawn(MediaStation.create("MEDIA-01"), "mediaStation");

        ActorRef<TemperatureSensor.TemperatureSensorCommand> temperatureSensor =
                context.spawn(TemperatureSensor.create(airCondition), "temperatureSensor");

        ActorRef<WeatherSensor.WeatherSensorCommand> weatherSensor =
                context.spawn(WeatherSensor.create(blinds), "weatherSensor");

        ActorRef<EnvironmentCoordinator.Command> environmentCoordinator =
                context.spawn(EnvironmentCoordinator.create(temperatureSensor, weatherSensor), "environmentCoordinator");

        context.spawn(TemperatureEnvironment.create(environmentCoordinator), "temperatureEnvironment");
        context.spawn(WeatherEnvironment.create(environmentCoordinator), "weatherEnvironment");


        context.getSystem().receptionist().tell(Receptionist.register(AirCondition.SERVICE_KEY, airCondition));
        context.getSystem().receptionist().tell(Receptionist.register(Blinds.SERVICE_KEY, blinds));
        context.getSystem().receptionist().tell(Receptionist.register(MediaStation.SERVICE_KEY, mediaStation));
        context.getSystem().receptionist().tell(Receptionist.register(Fridge.SERVICE_KEY, fridge));
        context.getSystem().receptionist().tell(Receptionist.register(EnvironmentCoordinator.SERVICE_KEY, environmentCoordinator));

        connectMqttClient(environmentCoordinator);
        startHttpServer(context, environmentCoordinator, fridge, mediaStation, blinds, airCondition);

        getContext().getLog().info("HomeAutomation application started — press RETURN to exit");
        waitForShutdownSignal();
    }

    @Override
    public Receive<Void> createReceive() {
        return newReceiveBuilder().onSignal(PostStop.class, signal -> onPostStop()).build();
    }

    private void connectMqttClient(ActorRef<EnvironmentCoordinator.Command> environmentCoordinator) {
        try {
            MqttEnvironmentClient mqttClient = new MqttEnvironmentClient(environmentCoordinator);
            mqttClient.connect();
            getContext().getLog().info("MQTT connected successfully");
        } catch (MqttConnectionException ex) {
            getContext().getLog().warn("MQTT not available: {}", ex.getMessage());
        }
    }

    private void startHttpServer(ActorContext<Void> context, ActorRef<EnvironmentCoordinator.Command> environmentCoordinator,
                                 ActorRef<Fridge.FridgeCommand> fridge, ActorRef<MediaStation.MediaStationCommand> mediaStation,
                                 ActorRef<Blinds.BlindsCommand> blinds, ActorRef<AirCondition.AirConditionCommand> airCondition) {
        Http http = Http.get(context.getSystem());
        HttpServer app = new HttpServer(environmentCoordinator, fridge, mediaStation, blinds, airCondition, context.getSystem());
        CompletionStage<ServerBinding> binding = http.newServerAt(HTTP_HOST, HTTP_PORT).bind(app.createRoute());
        getContext().getLog().info("HTTP server bound to http://{}:{}", HTTP_HOST, HTTP_PORT);
    }

    private void waitForShutdownSignal() {
        try {
            System.in.read();
        } catch (IOException ex) {
            getContext().getLog().warn("Could not read shutdown signal: {}", ex.getMessage());
        }
    }

    private HomeAutomationController onPostStop() {
        getContext().getLog().info("HomeAutomation application stopped");
        return this;
    }
}
