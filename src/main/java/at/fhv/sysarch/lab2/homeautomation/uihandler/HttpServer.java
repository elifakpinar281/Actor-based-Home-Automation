package at.fhv.sysarch.lab2.homeautomation.uihandler;

import at.fhv.sysarch.lab2.homeautomation.devices.*;
import at.fhv.sysarch.lab2.homeautomation.environment.EnvironmentActor;
import at.fhv.sysarch.lab2.homeautomation.shared.model.WeatherCondition;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.pekko.actor.typed.ActorRef;
import org.apache.pekko.actor.typed.ActorSystem;
import org.apache.pekko.http.javadsl.model.StatusCodes;
import org.apache.pekko.http.javadsl.server.AllDirectives;
import org.apache.pekko.http.javadsl.server.Route;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletionStage;

public class HttpServer extends AllDirectives {
    private final ActorRef<EnvironmentActor.EnvironmentCommand> environmentActor;
    private final ActorRef<Fridge.FridgeCommand> fridgeActor;
    private final ActorRef<MediaStation.MediaStationCommand> mediaStationActor;
    private final ActorRef<Blinds.BlindsCommand> blindsActor;
    private final ActorSystem<?> system;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private static final Duration TIMEOUT = Duration.ofSeconds(5);

    public HttpServer(
            ActorRef<EnvironmentActor.EnvironmentCommand> environmentActor,
            ActorRef<Fridge.FridgeCommand> fridgeActor,
            ActorRef<MediaStation.MediaStationCommand> mediaStationActor,
            ActorRef<Blinds.BlindsCommand> blindsActor,
            ActorSystem<?> system) {
        this.environmentActor = environmentActor;
        this.fridgeActor = fridgeActor;
        this.mediaStationActor = mediaStationActor;
        this.blindsActor = blindsActor;
        this.system = system;
    }

    public Route createRoute() {
        return concat(
                path("", () -> get(() -> complete(buildHomePage()))),
                pathPrefix("environment", () -> concat(
                        path("temperature", () -> post(() ->
                                parameter("value", valueStr -> {
                                    try {
                                        double temperature = Double.parseDouble(valueStr);
                                        environmentActor.tell(new EnvironmentActor.SetTemperature(temperature));
                                        return complete("Temperature set to " + temperature + " °C");
                                    } catch (NumberFormatException e) {
                                        return complete(StatusCodes.BAD_REQUEST, "Invalid temperature value");
                                    }
                                })
                        )),
                        path("weather", () -> post(() ->
                                parameter("condition", conditionStr -> {
                                    try {
                                        WeatherCondition condition = WeatherCondition.fromString(conditionStr);
                                        environmentActor.tell(new EnvironmentActor.SetWeather(condition));
                                        return complete("Weather set to " + condition);
                                    } catch (Exception e) {
                                        return complete(StatusCodes.BAD_REQUEST, "Invalid weather condition");
                                    }
                                })
                        )),
                        path("source", () -> post(() ->
                                parameter("mode", modeStr -> {
                                    try {
                                        EnvironmentActor.EnvironmentSource source =
                                                EnvironmentActor.EnvironmentSource.valueOf(modeStr.toUpperCase());
                                        environmentActor.tell(new EnvironmentActor.SwitchSource(source));
                                        return complete("Environment source switched to " + source);
                                    } catch (Exception e) {
                                        return complete(StatusCodes.BAD_REQUEST, "Invalid mode");
                                    }
                                })
                        ))
                )),
                pathPrefix("fridge", () -> concat(
                        path("products", this::getFridgeProducts),
                        path("consume", this::consumeProduct),
                        path("order", this::orderProducts),
                        path("history", this::getOrderHistory)
                )),
                pathPrefix("media-station", () -> concat(
                        path("play", this::playMovie),
                        path("stop", this::stopMovie),
                        path("status", this::getMediaStatus)
                )),
                path("devices/status", this::getDeviceStatus),
                path("hello", () -> get(() -> complete("<h1>Say hello to Pekko-HTTP</h1>")))
        );
    }

    private Route getFridgeProducts() {
        return complete(StatusCodes.OK, "Products endpoint");
    }

    private Route consumeProduct() {
        return post(() ->
                parameter("productId", productId ->
                        parameter("quantity", quantityStr -> {
                            try {
                                int quantity = Integer.parseInt(quantityStr);
                                if (quantity <= 0) {
                                    return complete(StatusCodes.BAD_REQUEST, "Quantity must be positive");
                                }
                                fridgeActor.tell(new Fridge.ConsumeProduct(productId, quantity));
                                return complete("Product consumption request sent");
                            } catch (NumberFormatException e) {
                                return complete(StatusCodes.BAD_REQUEST, "Invalid quantity");
                            }
                        })
                )
        );
    }

    private Route orderProducts() {
        return post(() ->
                parameter("productId", productId ->
                        parameter("quantity", quantityStr -> {
                            try {
                                int quantity = Integer.parseInt(quantityStr);
                                if (quantity <= 0) {
                                    return complete(StatusCodes.BAD_REQUEST, "Quantity must be positive");
                                }

                                Map<String, Integer> items = new HashMap<>();
                                items.put(productId, quantity);
                                fridgeActor.tell(new Fridge.OrderProducts(items, null));
                                return complete("Order request sent");
                            } catch (NumberFormatException e) {
                                return complete(StatusCodes.BAD_REQUEST, "Invalid quantity");
                            }
                        })
                )
        );
    }

    private Route getOrderHistory() {
        return complete(StatusCodes.OK, "Order history endpoint");
    }

    private Route playMovie() {
        return post(() ->
                parameter("movieName", movieName -> {
                    if (movieName == null || movieName.isEmpty()) {
                        return complete(StatusCodes.BAD_REQUEST, "Movie name is required");
                    }
                    mediaStationActor.tell(new MediaStation.PlayMovie(movieName, blindsActor));
                    return complete("Movie playback requested");
                })
        );
    }

    private Route stopMovie() {
        return post(() -> {
            mediaStationActor.tell(new MediaStation.StopMovie(blindsActor));
            return complete("Movie stop requested");
        });
    }

    private Route getMediaStatus() {
        return complete(StatusCodes.OK, "Media status endpoint");
    }

    private Route getDeviceStatus() {
        Map<String, String> status = new HashMap<>();
        status.put("fridge", "active");
        status.put("mediaStation", "active");
        status.put("blinds", "active");
        status.put("ac", "active");
        status.put("environment", "active");

        try {
            String json = objectMapper.writeValueAsString(status);
            return respondWithHeader(org.apache.pekko.http.javadsl.model.headers.RawHeader.create("Content-Type", "application/json"),
                    () -> complete(json));
        } catch (Exception e) {
            return complete(StatusCodes.INTERNAL_SERVER_ERROR, "Error");
        }
    }

    private String buildHomePage() {
        return """
                <!DOCTYPE html>
                <html>
                <head>
                <meta charset="UTF-8">
                <title>Home Automation System</title>
                <style>
                body { font-family: Arial, sans-serif; margin: 20px; background: #f5f5f5; }
                h1 { color: #333; text-align: center; }
                .section { margin: 20px 0; padding: 20px; background: white; border-radius: 5px; }
                button { padding: 10px 20px; margin: 5px; background-color: #007bff; color: white; border: none; border-radius: 4px; cursor: pointer; }
                button:hover { background-color: #0056b3; }
                </style>
                </head>
                <body>
                <h1>Home Automation System</h1>
                <div class="section">
                <h2>Environment Control</h2>
                <p>Set temperature and weather conditions</p>
                </div>
                <div class="section">
                <h2>Fridge Management</h2>
                <p>Manage products and orders</p>
                </div>
                <div class="section">
                <h2>Media Station</h2>
                <p>Control movies and entertainment</p>
                </div>
                </body>
                </html>
                """;
    }
}