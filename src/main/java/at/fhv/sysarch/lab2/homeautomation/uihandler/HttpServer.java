package at.fhv.sysarch.lab2.homeautomation.uihandler;

import at.fhv.sysarch.lab2.homeautomation.devices.*;
import at.fhv.sysarch.lab2.homeautomation.environment.EnvironmentSwitch;
import at.fhv.sysarch.lab2.homeautomation.environment.SimulationMode;
import at.fhv.sysarch.lab2.homeautomation.shared.exceptions.InvalidModeException;
import at.fhv.sysarch.lab2.homeautomation.shared.model.WeatherCondition;
import org.apache.pekko.actor.typed.ActorRef;
import org.apache.pekko.actor.typed.ActorSystem;
import org.apache.pekko.http.javadsl.marshallers.jackson.Jackson;
import org.apache.pekko.http.javadsl.model.ContentTypes;
import org.apache.pekko.http.javadsl.model.HttpEntities;
import org.apache.pekko.http.javadsl.model.StatusCodes;
import org.apache.pekko.http.javadsl.server.AllDirectives;
import org.apache.pekko.http.javadsl.server.Route;
import org.apache.pekko.actor.typed.javadsl.AskPattern;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

public class HttpServer extends AllDirectives {
    private final ActorRef<EnvironmentSwitch.EnvironmentSwitchCommand> environmentSwitch;
    private final ActorRef<Fridge.FridgeCommand> fridgeActor;
    private final ActorRef<MediaStation.MediaStationCommand> mediaStationActor;
    private final ActorRef<Blinds.BlindsCommand> blindsActor;
    private final ActorSystem<?> system;
    private final String homePage;
    private static final Duration TIMEOUT = Duration.ofSeconds(5);

    public HttpServer(
            ActorRef<EnvironmentSwitch.EnvironmentSwitchCommand> environmentSwitch,
            ActorRef<Fridge.FridgeCommand> fridgeActor,
            ActorRef<MediaStation.MediaStationCommand> mediaStationActor,
            ActorRef<Blinds.BlindsCommand> blindsActor,
            ActorSystem<?> system) {
        this.environmentSwitch = environmentSwitch;
        this.fridgeActor = fridgeActor;
        this.mediaStationActor = mediaStationActor;
        this.blindsActor = blindsActor;
        this.system = system;

        try {
            ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
            InputStream inputStream = classLoader.getResourceAsStream("index.html");
            if (inputStream == null) {
                throw new RuntimeException("index.html nicht gefunden in src/main/resources/");
            }
            this.homePage = new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
            inputStream.close();
        } catch (Exception e) {
            throw new RuntimeException("Failed to load index.html: " + e.getMessage(), e);
        }
    }

    public Route createRoute() {
        return concat(
                // Home Page - lade externe HTML Datei
                path("", () -> get(() -> complete(StatusCodes.OK, HttpEntities.create(ContentTypes.TEXT_HTML_UTF8, homePage)))),

                // Environment Control
                pathPrefix("environment", () -> concat(
                        path("temperature", () -> post(() ->
                                parameter("value", valueStr -> {
                                    try {
                                        double temperature = Double.parseDouble(valueStr);
                                        environmentSwitch.tell(new EnvironmentSwitch.SetFixedTemperature(temperature));
                                        return complete(StatusCodes.OK,
                                                new SuccessResponse("Temperature set to " + temperature + " °C"), Jackson.marshaller());
                                    } catch (NumberFormatException e) {
                                        return complete(StatusCodes.BAD_REQUEST,
                                                new ErrorResponse("Invalid temperature value"), Jackson.marshaller());
                                    }
                                })
                        )),
                        path("weather", () -> post(() ->
                                parameter("condition", conditionStr -> {
                                    try {
                                        WeatherCondition condition = WeatherCondition.fromString(conditionStr);
                                        environmentSwitch.tell(new EnvironmentSwitch.SetFixedWeather(condition));
                                        return complete(StatusCodes.OK,
                                                new SuccessResponse("Weather set to " + condition), Jackson.marshaller());
                                    } catch (Exception e) {
                                        return complete(StatusCodes.BAD_REQUEST,
                                                new ErrorResponse("Invalid weather condition"), Jackson.marshaller());
                                    }
                                })
                        )),
                        path("source", () -> post(() ->
                                parameter("mode", modeStr -> {
                                    try {
                                        SimulationMode mode = SimulationMode.fromString(modeStr);
                                        environmentSwitch.tell(new EnvironmentSwitch.SetMode(mode));
                                        return complete(StatusCodes.OK,
                                                new SuccessResponse("Environment source switched to " + mode), Jackson.marshaller());
                                    } catch (InvalidModeException exception) {
                                        return complete(StatusCodes.BAD_REQUEST,
                                                new ErrorResponse("Invalid mode: " + modeStr), Jackson.marshaller());
                                    }
                                })
                        ))
                )),

                // Fridge Management
                pathPrefix("fridge", () -> concat(
                        path("products", this::getFridgeProducts),
                        path("consume", this::consumeProduct),
                        path("order", this::orderProducts),
                        path("history", this::getOrderHistory)
                )),

                // Media Station
                pathPrefix("media-station", () -> concat(
                        path("play", this::playMovie),
                        path("stop", this::stopMovie),
                        path("status", this::getMediaStatus)
                )),

                // Device Status
                path("devices/status", this::getDeviceStatus),

                // Demo Route
                path("hello", () -> get(() -> complete("<h1>Say hello to Pekko-HTTP</h1>")))
        );
    }

    private Route getFridgeProducts() {
        return get(() ->
                onComplete(
                        AskPattern.ask(
                                fridgeActor,
                                (ActorRef<Fridge.ProductsResponse> replyTo) -> new Fridge.GetProducts(replyTo),
                                TIMEOUT,
                                system.scheduler()
                        ),
                        response -> {
                            if (response.isSuccess()) {
                                return complete(StatusCodes.OK, response.get(), Jackson.marshaller());
                            } else {
                                System.err.println("Error getting fridge products: " + response.failed().get().getMessage());
                                return complete(StatusCodes.INTERNAL_SERVER_ERROR,
                                        new ErrorResponse("Failed to get products: " + response.failed().get().getMessage()), Jackson.marshaller());
                            }
                        }
                )
        );
    }

    private Route getOrderHistory() {
        return get(() ->
                onComplete(
                        AskPattern.ask(
                                fridgeActor,
                                (ActorRef<Fridge.OrderHistoryResponse> replyTo) -> new Fridge.GetOrderHistory(replyTo),
                                TIMEOUT,
                                system.scheduler()
                        ),
                        response -> {
                            if (response.isSuccess()) {
                                return complete(StatusCodes.OK, response.get(), Jackson.marshaller());
                            } else {
                                System.err.println("Error getting order history: " + response.failed().get().getMessage());
                                return complete(StatusCodes.INTERNAL_SERVER_ERROR,
                                        new ErrorResponse("Failed to get order history: " + response.failed().get().getMessage()), Jackson.marshaller());
                            }
                        }
                )
        );
    }

    private Route consumeProduct() {
        return post(() ->
                parameter("productId", productId ->
                        parameter("quantity", quantityStr -> {
                            try {
                                int quantity = Integer.parseInt(quantityStr);
                                if (quantity <= 0) {
                                    return complete(StatusCodes.BAD_REQUEST,
                                            new ErrorResponse("Quantity must be positive"), Jackson.marshaller());
                                }
                                // Fire-and-forget - keine Response erwartet
                                fridgeActor.tell(new Fridge.ConsumeProduct(productId, quantity));
                                return complete(StatusCodes.ACCEPTED,
                                        new SuccessResponse("Product consumption request sent"), Jackson.marshaller());
                            } catch (NumberFormatException e) {
                                return complete(StatusCodes.BAD_REQUEST,
                                        new ErrorResponse("Invalid quantity"), Jackson.marshaller());
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
                                    return complete(StatusCodes.BAD_REQUEST,
                                            new ErrorResponse("Quantity must be positive"), Jackson.marshaller());
                                }

                                Map<String, Integer> items = new HashMap<>();
                                items.put(productId, quantity);

                                // Fire-and-forget - Order wird async verarbeitet
                                fridgeActor.tell(new Fridge.OrderProducts(items, null));
                                return complete(StatusCodes.ACCEPTED,
                                        new SuccessResponse("Order request sent"), Jackson.marshaller());
                            } catch (NumberFormatException e) {
                                return complete(StatusCodes.BAD_REQUEST,
                                        new ErrorResponse("Invalid quantity"), Jackson.marshaller());
                            }
                        })
                )
        );
    }

    private Route playMovie() {
        return post(() ->
                parameter("movieName", movieName -> {
                    if (movieName == null || movieName.isEmpty()) {
                        return complete(StatusCodes.BAD_REQUEST,
                                new ErrorResponse("Movie name is required"), Jackson.marshaller());
                    }
                    mediaStationActor.tell(new MediaStation.PlayMovie(movieName, blindsActor));
                    return complete(StatusCodes.ACCEPTED,
                            new SuccessResponse("Movie playback requested"), Jackson.marshaller());
                })
        );
    }

    private Route stopMovie() {
        return post(() -> {
            mediaStationActor.tell(new MediaStation.StopMovie(blindsActor));
            return complete(StatusCodes.ACCEPTED,
                    new SuccessResponse("Movie stop requested"), Jackson.marshaller());
        });
    }

    private Route getMediaStatus() {
        MediaStatusResponse status = new MediaStatusResponse("unknown", false);
        return complete(StatusCodes.OK, status, Jackson.marshaller());
    }

    private Route getDeviceStatus() {
        Map<String, String> status = new HashMap<>();
        status.put("fridge", "active");
        status.put("mediaStation", "active");
        status.put("blinds", "active");
        status.put("ac", "active");
        status.put("environment", "active");

        return complete(StatusCodes.OK, status, Jackson.marshaller());
    }

    public static class SuccessResponse {
        public final String message;

        public SuccessResponse(String message) {
            this.message = message;
        }
    }

    public static class ErrorResponse {
        public final String error;

        public ErrorResponse(String error) {
            this.error = error;
        }
    }

    public static class MediaStatusResponse {
        public final String currentMovie;
        public final boolean isPlaying;

        public MediaStatusResponse(String currentMovie, boolean isPlaying) {
            this.currentMovie = currentMovie;
            this.isPlaying = isPlaying;
        }
    }
}