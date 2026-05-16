package at.fhv.sysarch.lab2.homeautomation.uihandler.routes;

import at.fhv.sysarch.lab2.homeautomation.devices.AirCondition;
import at.fhv.sysarch.lab2.homeautomation.environment.EnvironmentCoordinator;
import at.fhv.sysarch.lab2.homeautomation.environment.SimulationMode;
import at.fhv.sysarch.lab2.homeautomation.shared.exceptions.InvalidTemperatureException;
import at.fhv.sysarch.lab2.homeautomation.shared.model.environment.Temperature;
import at.fhv.sysarch.lab2.homeautomation.shared.model.environment.WeatherCondition;
import at.fhv.sysarch.lab2.homeautomation.uihandler.dtos.SuccessResponse;
import org.apache.pekko.actor.typed.ActorRef;
import org.apache.pekko.http.javadsl.marshallers.jackson.Jackson;
import org.apache.pekko.http.javadsl.model.StatusCodes;
import org.apache.pekko.http.javadsl.server.AllDirectives;
import org.apache.pekko.http.javadsl.server.Route;

public class EnvironmentRoutes extends AllDirectives {
    private final ActorRef<EnvironmentCoordinator.Command> environmentCoordinator;
    private final ActorRef<AirCondition.AirConditionCommand> airConditionActor;

    public EnvironmentRoutes(ActorRef<EnvironmentCoordinator.Command> environmentCoordinator,
            ActorRef<AirCondition.AirConditionCommand> airConditionActor) {
        this.environmentCoordinator = environmentCoordinator;
        this.airConditionActor = airConditionActor;
    }

    public Route routes() {
        return concat(
                pathPrefix("environment", () -> concat(
                        path("temperature", () -> post(this::setFixedTemperature)),
                        path("weather", () -> post(this::setFixedWeather)),
                        path("source", () -> post(this::setSimulationMode))
                )),
                pathPrefix("ac", () -> path("power", () -> post(this::setAcPower)))
        );
    }

    private Route setFixedTemperature() {
        return parameter("value", valueStr -> {
            double temperature = Double.parseDouble(valueStr);
            if (!Temperature.isInRange(temperature)) {
                throw new InvalidTemperatureException(temperature);
            }
            environmentCoordinator.tell(new EnvironmentCoordinator.SetFixedTemperature(temperature));
            return complete(StatusCodes.OK, new SuccessResponse("Temperature set to " + temperature + " °C"), Jackson.marshaller());
        });
    }

    private Route setFixedWeather() {
        return parameter("condition", conditionStr -> {
            WeatherCondition condition = WeatherCondition.fromString(conditionStr);
            environmentCoordinator.tell(new EnvironmentCoordinator.SetFixedWeather(condition));
            return complete(StatusCodes.OK, new SuccessResponse("Weather set to " + condition), Jackson.marshaller());
        });
    }

    private Route setSimulationMode() {
        return parameter("mode", modeStr -> {
            SimulationMode mode = SimulationMode.fromString(modeStr);
            environmentCoordinator.tell(new EnvironmentCoordinator.SetMode(mode));
            return complete(StatusCodes.OK, new SuccessResponse("Environment source switched to " + mode), Jackson.marshaller());
        });
    }

    private Route setAcPower() {
        return parameter("on", onStr -> {
            boolean on = Boolean.parseBoolean(onStr);
            airConditionActor.tell(new AirCondition.PowerAirCondition(on));
            return complete(StatusCodes.OK, new SuccessResponse("AC power set to " + on), Jackson.marshaller());
        });
    }
}