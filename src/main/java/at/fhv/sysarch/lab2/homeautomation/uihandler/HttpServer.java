package at.fhv.sysarch.lab2.homeautomation.uihandler;

import at.fhv.sysarch.lab2.homeautomation.devices.*;
import at.fhv.sysarch.lab2.homeautomation.devices.model.Order;
import at.fhv.sysarch.lab2.homeautomation.devices.model.Product;
import at.fhv.sysarch.lab2.homeautomation.environment.EnvironmentCoordinator;
import at.fhv.sysarch.lab2.homeautomation.environment.EnvironmentSnapshot;
import at.fhv.sysarch.lab2.homeautomation.environment.SimulationMode;
import at.fhv.sysarch.lab2.homeautomation.shared.exceptions.InvalidModeException;
import at.fhv.sysarch.lab2.homeautomation.shared.exceptions.InvalidWeatherConditionException;
import at.fhv.sysarch.lab2.homeautomation.shared.model.WeatherCondition;
import at.fhv.sysarch.lab2.homeautomation.uihandler.dtos.*;

import org.apache.pekko.actor.typed.ActorRef;
import org.apache.pekko.actor.typed.ActorSystem;
import org.apache.pekko.http.javadsl.marshallers.jackson.Jackson;
import org.apache.pekko.http.javadsl.model.ContentTypes;
import org.apache.pekko.http.javadsl.model.HttpEntities;
import org.apache.pekko.http.javadsl.model.StatusCodes;
import org.apache.pekko.http.javadsl.model.headers.RawHeader;
import org.apache.pekko.http.javadsl.server.AllDirectives;
import org.apache.pekko.http.javadsl.server.Route;
import org.apache.pekko.actor.typed.javadsl.AskPattern;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class HttpServer extends AllDirectives {
    private static final Duration TIMEOUT = Duration.ofSeconds(5);

    private final ActorRef<EnvironmentCoordinator.Command> environmentCoordinator;
    private final ActorRef<Fridge.FridgeCommand> fridgeActor;
    private final ActorRef<MediaStation.MediaStationCommand> mediaStationActor;
    private final ActorRef<Blinds.BlindsCommand> blindsActor;
    private final ActorRef<AirCondition.AirConditionCommand> airConditionActor;
    private final ActorSystem<?> system;
    private final String homePage;

    public HttpServer(
            ActorRef<EnvironmentCoordinator.Command> environmentCoordinator,
            ActorRef<Fridge.FridgeCommand> fridgeActor,
            ActorRef<MediaStation.MediaStationCommand> mediaStationActor,
            ActorRef<Blinds.BlindsCommand> blindsActor,
            ActorRef<AirCondition.AirConditionCommand> airConditionActor,
            ActorSystem<?> system) {
        this.environmentCoordinator = environmentCoordinator;
        this.fridgeActor = fridgeActor;
        this.mediaStationActor = mediaStationActor;
        this.blindsActor = blindsActor;
        this.airConditionActor = airConditionActor;
        this.system = system;
        this.homePage = loadHomePage();
    }

    private String loadHomePage() {
        ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
        try (InputStream inputStream = classLoader.getResourceAsStream("index.html")) {
            if (inputStream == null) {
                return "<h1>Home Automation</h1><p>UI läuft im Next.js-Frontend (Port 3000).</p>";
            }
            return new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException cause) {
            // Falling back to the inline page keeps the API server alive even
            // when the bundled UI cannot be read for some reason.
            return "<h1>Home Automation</h1><p>UI could not be loaded.</p>";
        }
    }

    public Route createRoute() {
        return withCors(concat(
                path("", () -> get(() -> complete(StatusCodes.OK,
                        HttpEntities.create(ContentTypes.TEXT_HTML_UTF8, homePage)))),

                path("status", this::getStatus),

                pathPrefix("environment", () -> concat(
                        path("temperature", () -> post(this::setFixedTemperature)),
                        path("weather", () -> post(this::setFixedWeather)),
                        path("source", () -> post(this::setSimulationMode))
                )),

                // AC power
                pathPrefix("ac", () ->
                        path("power", () -> post(this::setAcPower))
                ),

                // Fridge
                pathPrefix("fridge", () -> concat(
                        path("products", this::getFridgeProducts),
                        path("capacity", this::getFridgeCapacity),
                        path("consume", this::consumeProduct),
                        path("order", this::orderProducts),
                        path("history", this::getOrderHistory)
                )),

                // Media station
                pathPrefix("media-station", () -> concat(
                        path("play", this::playMovie),
                        path("stop", this::stopMovie),
                        path("status", this::getMediaStatus)
                )),

                path("devices/status", this::getDeviceStatus),
                path("hello", () -> get(() -> complete("<h1>Say hello to Pekko-HTTP</h1>")))
        ));
    }

    // CORS wrapper (Next.js dev runs on :3000)
    private Route withCors(Route inner) {
        return respondWithHeaders(
                List.of(
                        RawHeader.create("Access-Control-Allow-Origin", "*"),
                        RawHeader.create("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS"),
                        RawHeader.create("Access-Control-Allow-Headers", "Content-Type, Authorization")
                ),
                () -> concat(
                        options(() -> complete(StatusCodes.OK)),
                        inner
                )
        );
    }

    // ---- Environment control ----

    private Route setFixedTemperature() {
        return parameter("value", valueStr -> {
            try {
                double temperature = Double.parseDouble(valueStr);
                environmentCoordinator.tell(new EnvironmentCoordinator.SetFixedTemperature(temperature));
                return complete(StatusCodes.OK,
                        new SuccessResponse("Temperature set to " + temperature + " °C"),
                        Jackson.marshaller());
            } catch (NumberFormatException ex) {
                return complete(StatusCodes.BAD_REQUEST,
                        new ErrorResponse("Invalid temperature value"), Jackson.marshaller());
            }
        });
    }

    private Route setFixedWeather() {
        return parameter("condition", conditionStr -> {
            try {
                WeatherCondition condition = WeatherCondition.fromString(conditionStr);
                environmentCoordinator.tell(new EnvironmentCoordinator.SetFixedWeather(condition));
                return complete(StatusCodes.OK,
                        new SuccessResponse("Weather set to " + condition), Jackson.marshaller());
            } catch (InvalidWeatherConditionException ex) {
                return complete(StatusCodes.BAD_REQUEST,
                        new ErrorResponse(ex.getMessage()), Jackson.marshaller());
            }
        });
    }

    private Route setSimulationMode() {
        return parameter("mode", modeStr -> {
            try {
                SimulationMode mode = SimulationMode.fromString(modeStr);
                environmentCoordinator.tell(new EnvironmentCoordinator.SetMode(mode));
                return complete(StatusCodes.OK,
                        new SuccessResponse("Environment source switched to " + mode), Jackson.marshaller());
            } catch (InvalidModeException ex) {
                return complete(StatusCodes.BAD_REQUEST,
                        new ErrorResponse(ex.getMessage()), Jackson.marshaller());
            }
        });
    }

    // ---- AC ----

    private Route setAcPower() {
        return parameter("on", onStr -> {
            boolean on = Boolean.parseBoolean(onStr);
            airConditionActor.tell(new AirCondition.PowerAirCondition(on));
            return complete(StatusCodes.OK,
                    new SuccessResponse("AC power set to " + on), Jackson.marshaller());
        });
    }

    // ---- /status (aggregated) ----

    private Route getStatus() {
        return get(() ->
                onComplete(
                        AskPattern.ask(
                                environmentCoordinator,
                                (ActorRef<EnvironmentSnapshot> replyTo) -> new EnvironmentCoordinator.GetCurrentState(replyTo),
                                TIMEOUT,
                                system.scheduler()
                        ),
                        envResult -> {
                            if (!envResult.isSuccess()) {
                                return complete(StatusCodes.INTERNAL_SERVER_ERROR,
                                        new ErrorResponse("env state failed"), Jackson.marshaller());
                            }
                            EnvironmentSnapshot env = envResult.get();
                            return onComplete(
                                    AskPattern.ask(
                                            airConditionActor,
                                            (ActorRef<AirCondition.StatusResponse> replyTo) -> new AirCondition.GetStatus(replyTo),
                                            TIMEOUT,
                                            system.scheduler()
                                    ),
                                    acResult -> {
                                        if (!acResult.isSuccess()) {
                                            return complete(StatusCodes.INTERNAL_SERVER_ERROR,
                                                    new ErrorResponse("ac status failed"), Jackson.marshaller());
                                        }
                                        AirCondition.StatusResponse ac = acResult.get();
                                        return onComplete(
                                                AskPattern.ask(
                                                        blindsActor,
                                                        (ActorRef<Blinds.StatusResponse> replyTo) -> new Blinds.GetStatus(replyTo),
                                                        TIMEOUT,
                                                        system.scheduler()
                                                ),
                                                blindsResult -> {
                                                    if (!blindsResult.isSuccess()) {
                                                        return complete(StatusCodes.INTERNAL_SERVER_ERROR,
                                                                new ErrorResponse("blinds status failed"), Jackson.marshaller());
                                                    }
                                                    Blinds.StatusResponse blinds = blindsResult.get();
                                                    return onComplete(
                                                            AskPattern.ask(
                                                                    mediaStationActor,
                                                                    (ActorRef<MediaStation.StatusResponse> replyTo) -> new MediaStation.GetStatus(replyTo),
                                                                    TIMEOUT,
                                                                    system.scheduler()
                                                            ),
                                                            mediaResult -> {
                                                                if (!mediaResult.isSuccess()) {
                                                                    return complete(StatusCodes.INTERNAL_SERVER_ERROR,
                                                                            new ErrorResponse("media status failed"), Jackson.marshaller());
                                                                }
                                                                MediaStation.StatusResponse media = mediaResult.get();
                                                                StatusDto dto = new StatusDto(
                                                                        Math.round(env.temperature().value() * 10.0) / 10.0,
                                                                        env.weather().name(),
                                                                        env.mode().name(),
                                                                        ac.isPoweredOn(),
                                                                        ac.isCooling(),
                                                                        blinds.areClosed(),
                                                                        media.isPlaying(),
                                                                        media.currentMovie()
                                                                );
                                                                return complete(StatusCodes.OK, dto, Jackson.marshaller());
                                                            }
                                                    );
                                                }
                                        );
                                    }
                            );
                        }
                )
        );
    }

    // ---- Fridge: products ----
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
                            if (!response.isSuccess()) {
                                return complete(StatusCodes.INTERNAL_SERVER_ERROR,
                                        new ErrorResponse("Failed to get products"), Jackson.marshaller());
                            }
                            List<ProductDto> dtos = new ArrayList<>();
                            for (Product p : response.get().products()) {
                                dtos.add(new ProductDto(p.getId(), p.getName(), p.getWeight(), p.getPrice(), p.getQuantity()));
                            }
                            return complete(StatusCodes.OK, new ProductsDto(dtos), Jackson.marshaller());
                        }
                )
        );
    }

    // ---- Fridge: capacity ----
    private Route getFridgeCapacity() {
        return get(() ->
                onComplete(
                        AskPattern.ask(
                                fridgeActor,
                                (ActorRef<Fridge.CapacityResponse> replyTo) -> new Fridge.GetCapacity(replyTo),
                                TIMEOUT,
                                system.scheduler()
                        ),
                        response -> {
                            if (!response.isSuccess()) {
                                return complete(StatusCodes.INTERNAL_SERVER_ERROR,
                                        new ErrorResponse("Failed to get capacity"), Jackson.marshaller());
                            }
                            Fridge.CapacityResponse c = response.get();
                            return complete(StatusCodes.OK,
                                    new CapacityDto(
                                            c.currentItems(),
                                            c.maxItems(),
                                            Math.round(c.currentWeight() * 100.0) / 100.0,
                                            c.maxWeight()
                                    ),
                                    Jackson.marshaller());
                        }
                )
        );
    }

    // ---- Fridge: history (with total + item names + unit prices) ----
    private Route getOrderHistory() {
        return get(() ->
                onComplete(
                        AskPattern.ask(
                                fridgeActor,
                                (ActorRef<Fridge.ProductsResponse> replyTo) -> new Fridge.GetProducts(replyTo),
                                TIMEOUT,
                                system.scheduler()
                        ),
                        productsResult -> {
                            if (!productsResult.isSuccess()) {
                                return complete(StatusCodes.INTERNAL_SERVER_ERROR,
                                        new ErrorResponse("Failed to get products"), Jackson.marshaller());
                            }
                            Map<String, Product> byId = new HashMap<>();
                            for (Product p : productsResult.get().products()) {
                                byId.put(p.getId(), p);
                            }
                            return onComplete(
                                    AskPattern.ask(
                                            fridgeActor,
                                            (ActorRef<Fridge.OrderHistoryResponse> replyTo) -> new Fridge.GetOrderHistory(replyTo),
                                            TIMEOUT,
                                            system.scheduler()
                                    ),
                                    historyResult -> {
                                        if (!historyResult.isSuccess()) {
                                            return complete(StatusCodes.INTERNAL_SERVER_ERROR,
                                                    new ErrorResponse("Failed to get history"), Jackson.marshaller());
                                        }
                                        List<OrderDto> dtos = new ArrayList<>();
                                        for (Order o : historyResult.get().orders()) {
                                            List<OrderItemDto> itemDtos = new ArrayList<>();
                                            for (Map.Entry<String, Integer> entry : o.getItems().entrySet()) {
                                                Product p = byId.get(entry.getKey());
                                                String name = p != null ? p.getName() : entry.getKey();
                                                double unitPrice = p != null ? p.getPrice() : 0.0;
                                                itemDtos.add(new OrderItemDto(entry.getKey(), name, entry.getValue(), unitPrice));
                                            }
                                            dtos.add(new OrderDto(
                                                    o.getOrderId(),
                                                    o.getTimestamp().toString(),
                                                    o.getStatus().name(),
                                                    o.getTotalPrice(),
                                                    itemDtos
                                            ));
                                        }
                                        return complete(StatusCodes.OK, new OrderHistoryDto(dtos), Jackson.marshaller());
                                    }
                            );
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
                                fridgeActor.tell(new Fridge.ConsumeProduct(productId, quantity));
                                return complete(StatusCodes.ACCEPTED,
                                        new SuccessResponse("Product consumption request sent"), Jackson.marshaller());
                            } catch (NumberFormatException ex) {
                                return complete(StatusCodes.BAD_REQUEST,
                                        new ErrorResponse("Invalid quantity"), Jackson.marshaller());
                            }
                        })
                )
        );
    }

    // Order: JSON body { items: { productId: qty, ... } }
    private Route orderProducts() {
        return post(() ->
                entity(Jackson.unmarshaller(OrderRequest.class), request -> {
                    if (request.items() == null || request.items().isEmpty()) {
                        return complete(StatusCodes.BAD_REQUEST,
                                new ErrorResponse("Items are required"), Jackson.marshaller());
                    }
                    for (Integer qty : request.items().values()) {
                        if (qty == null || qty <= 0) {
                            return complete(StatusCodes.BAD_REQUEST,
                                    new ErrorResponse("Quantities must be positive"), Jackson.marshaller());
                        }
                    }
                    fridgeActor.tell(new Fridge.OrderProducts(request.items(), null));
                    return complete(StatusCodes.ACCEPTED,
                            new SuccessResponse("Order request sent"), Jackson.marshaller());
                })
        );
    }

    private Route playMovie() {
        return post(() ->
                parameter("movieName", movieName -> {
                    if (movieName == null || movieName.isEmpty()) {
                        return complete(StatusCodes.BAD_REQUEST,
                                new ErrorResponse("Movie name is required"), Jackson.marshaller());
                    }
                    mediaStationActor.tell(new MediaStation.PlayMovie(movieName));
                    return complete(StatusCodes.ACCEPTED,
                            new SuccessResponse("Movie playback requested"), Jackson.marshaller());
                })
        );
    }

    private Route stopMovie() {
        return post(() -> {
            mediaStationActor.tell(new MediaStation.StopMovie());
            return complete(StatusCodes.ACCEPTED,
                    new SuccessResponse("Movie stop requested"), Jackson.marshaller());
        });
    }

    private Route getMediaStatus() {
        return get(() ->
                onComplete(
                        AskPattern.ask(
                                mediaStationActor,
                                (ActorRef<MediaStation.StatusResponse> replyTo) -> new MediaStation.GetStatus(replyTo),
                                TIMEOUT,
                                system.scheduler()
                        ),
                        response -> {
                            if (!response.isSuccess()) {
                                return complete(StatusCodes.INTERNAL_SERVER_ERROR,
                                        new ErrorResponse("media status failed"), Jackson.marshaller());
                            }
                            MediaStation.StatusResponse r = response.get();
                            return complete(StatusCodes.OK,
                                    new MediaStatusResponse(r.currentMovie(), r.isPlaying()), Jackson.marshaller());
                        }
                )
        );
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

}
