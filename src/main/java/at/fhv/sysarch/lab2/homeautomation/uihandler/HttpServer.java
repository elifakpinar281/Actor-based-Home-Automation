package at.fhv.sysarch.lab2.homeautomation.uihandler;

import at.fhv.sysarch.lab2.homeautomation.devices.*;
import at.fhv.sysarch.lab2.homeautomation.devices.model.Order;
import at.fhv.sysarch.lab2.homeautomation.devices.model.Product;
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
import org.apache.pekko.http.javadsl.model.headers.RawHeader;
import org.apache.pekko.http.javadsl.server.AllDirectives;
import org.apache.pekko.http.javadsl.server.Route;
import org.apache.pekko.actor.typed.javadsl.AskPattern;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class HttpServer extends AllDirectives {
    private final ActorRef<EnvironmentSwitch.EnvironmentSwitchCommand> environmentSwitch;
    private final ActorRef<Fridge.FridgeCommand> fridgeActor;
    private final ActorRef<MediaStation.MediaStationCommand> mediaStationActor;
    private final ActorRef<Blinds.BlindsCommand> blindsActor;
    private final ActorRef<AirCondition.AirConditionCommand> airConditionActor;
    private final ActorSystem<?> system;
    private final String homePage;
    private static final Duration TIMEOUT = Duration.ofSeconds(5);

    public HttpServer(
            ActorRef<EnvironmentSwitch.EnvironmentSwitchCommand> environmentSwitch,
            ActorRef<Fridge.FridgeCommand> fridgeActor,
            ActorRef<MediaStation.MediaStationCommand> mediaStationActor,
            ActorRef<Blinds.BlindsCommand> blindsActor,
            ActorRef<AirCondition.AirConditionCommand> airConditionActor,
            ActorSystem<?> system) {
        this.environmentSwitch = environmentSwitch;
        this.fridgeActor = fridgeActor;
        this.mediaStationActor = mediaStationActor;
        this.blindsActor = blindsActor;
        this.airConditionActor = airConditionActor;
        this.system = system;

        try {
            ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
            InputStream inputStream = classLoader.getResourceAsStream("index.html");
            if (inputStream == null) {
                this.homePage = "<h1>Home Automation</h1><p>UI läuft im Next.js-Frontend (Port 3000).</p>";
            } else {
                this.homePage = new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
                inputStream.close();
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to load index.html: " + e.getMessage(), e);
        }
    }

    public Route createRoute() {
        return withCors(concat(
                path("", () -> get(() -> complete(StatusCodes.OK, HttpEntities.create(ContentTypes.TEXT_HTML_UTF8, homePage)))),

                // Frontend Aggregat
                path("status", this::getStatus),

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

                // AC Power
                pathPrefix("ac", () ->
                        path("power", () -> post(() ->
                                parameter("on", onStr -> {
                                    boolean on = Boolean.parseBoolean(onStr);
                                    airConditionActor.tell(new AirCondition.PowerAirCondition(on));
                                    return complete(StatusCodes.OK,
                                            new SuccessResponse("AC power set to " + on), Jackson.marshaller());
                                })
                        ))
                ),

                // Fridge
                pathPrefix("fridge", () -> concat(
                        path("products", this::getFridgeProducts),
                        path("capacity", this::getFridgeCapacity),
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

                path("devices/status", this::getDeviceStatus),
                path("hello", () -> get(() -> complete("<h1>Say hello to Pekko-HTTP</h1>")))
        ));
    }

    // CORS-Wrapper (Next.js dev läuft auf :3000)
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

    // ---- /status (aggregiert) ----
    private Route getStatus() {
        return get(() ->
                onComplete(
                        AskPattern.ask(
                                environmentSwitch,
                                (ActorRef<EnvironmentSwitch.CurrentStateResponse> replyTo) -> new EnvironmentSwitch.GetCurrentState(replyTo),
                                TIMEOUT,
                                system.scheduler()
                        ),
                        envResult -> {
                            if (!envResult.isSuccess()) {
                                return complete(StatusCodes.INTERNAL_SERVER_ERROR,
                                        new ErrorResponse("env state failed"), Jackson.marshaller());
                            }
                            EnvironmentSwitch.CurrentStateResponse env = envResult.get();
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
                                                                        Math.round(env.temperature() * 10.0) / 10.0,
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

    // ---- Fridge: history (mit Total + Item-Names + Unit-Prices) ----
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
                            } catch (NumberFormatException e) {
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
                    if (request.items == null || request.items.isEmpty()) {
                        return complete(StatusCodes.BAD_REQUEST,
                                new ErrorResponse("Items are required"), Jackson.marshaller());
                    }
                    for (Integer qty : request.items.values()) {
                        if (qty == null || qty <= 0) {
                            return complete(StatusCodes.BAD_REQUEST,
                                    new ErrorResponse("Quantities must be positive"), Jackson.marshaller());
                        }
                    }
                    fridgeActor.tell(new Fridge.OrderProducts(request.items, null));
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

    // Watchlist removed - not part of assignment

    private Route getDeviceStatus() {
        Map<String, String> status = new HashMap<>();
        status.put("fridge", "active");
        status.put("mediaStation", "active");
        status.put("blinds", "active");
        status.put("ac", "active");
        status.put("environment", "active");
        return complete(StatusCodes.OK, status, Jackson.marshaller());
    }

    // ---- DTOs ----

    public static class SuccessResponse {
        public final String message;
        public SuccessResponse(String message) { this.message = message; }
    }

    public static class ErrorResponse {
        public final String error;
        public ErrorResponse(String error) { this.error = error; }
    }

    public static class MediaStatusResponse {
        public final String currentMovie;
        public final boolean isPlaying;
        public MediaStatusResponse(String currentMovie, boolean isPlaying) {
            this.currentMovie = currentMovie;
            this.isPlaying = isPlaying;
        }
    }

    public static class OrderRequest {
        public Map<String, Integer> items;
        public OrderRequest() { }
    }

    public static class StatusDto {
        public final double temperature;
        public final String weather;
        public final String simulationMode;
        public final boolean acPoweredOn;
        public final boolean acCooling;
        public final boolean blindsClosed;
        public final boolean moviePlaying;
        public final String currentMovie;

        public StatusDto(double temperature, String weather, String simulationMode,
                         boolean acPoweredOn, boolean acCooling, boolean blindsClosed,
                         boolean moviePlaying, String currentMovie) {
            this.temperature = temperature;
            this.weather = weather;
            this.simulationMode = simulationMode;
            this.acPoweredOn = acPoweredOn;
            this.acCooling = acCooling;
            this.blindsClosed = blindsClosed;
            this.moviePlaying = moviePlaying;
            this.currentMovie = currentMovie;
        }
    }

    public static class ProductDto {
        public final String id;
        public final String name;
        public final double weight;
        public final double price;
        public final int quantity;
        public ProductDto(String id, String name, double weight, double price, int quantity) {
            this.id = id;
            this.name = name;
            this.weight = weight;
            this.price = price;
            this.quantity = quantity;
        }
    }

    public static class ProductsDto {
        public final List<ProductDto> products;
        public ProductsDto(List<ProductDto> products) { this.products = products; }
    }

    public static class CapacityDto {
        public final int currentItems;
        public final int maxItems;
        public final double currentWeight;
        public final double maxWeight;
        public CapacityDto(int currentItems, int maxItems, double currentWeight, double maxWeight) {
            this.currentItems = currentItems;
            this.maxItems = maxItems;
            this.currentWeight = currentWeight;
            this.maxWeight = maxWeight;
        }
    }

    public static class OrderItemDto {
        public final String productId;
        public final String productName;
        public final int quantity;
        public final double unitPrice;
        public OrderItemDto(String productId, String productName, int quantity, double unitPrice) {
            this.productId = productId;
            this.productName = productName;
            this.quantity = quantity;
            this.unitPrice = unitPrice;
        }
    }

    public static class OrderDto {
        public final String orderId;
        public final String timestamp;
        public final String status;
        public final double totalPrice;
        public final List<OrderItemDto> items;
        public OrderDto(String orderId, String timestamp, String status, double totalPrice, List<OrderItemDto> items) {
            this.orderId = orderId;
            this.timestamp = timestamp;
            this.status = status;
            this.totalPrice = totalPrice;
            this.items = items;
        }
    }

    public static class OrderHistoryDto {
        public final List<OrderDto> orders;
        public OrderHistoryDto(List<OrderDto> orders) { this.orders = orders; }
    }
}
