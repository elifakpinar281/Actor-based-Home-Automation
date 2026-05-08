package at.fhv.sysarch.lab2.homeautomation.uihandler;

import at.fhv.sysarch.lab2.homeautomation.devices.*;
import at.fhv.sysarch.lab2.homeautomation.environment.EnvironmentSwitch;
import at.fhv.sysarch.lab2.homeautomation.environment.SimulationMode;
import at.fhv.sysarch.lab2.homeautomation.shared.exceptions.InvalidModeException;
import at.fhv.sysarch.lab2.homeautomation.devices.sensor.WeatherCondition;
import org.apache.pekko.actor.typed.ActorRef;
import org.apache.pekko.actor.typed.ActorSystem;
import org.apache.pekko.http.javadsl.marshallers.jackson.Jackson;
import org.apache.pekko.http.javadsl.model.StatusCodes;
import org.apache.pekko.http.javadsl.server.AllDirectives;
import org.apache.pekko.http.javadsl.server.Route;
import org.apache.pekko.actor.typed.javadsl.AskPattern;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

public class HttpServer extends AllDirectives {
    private final ActorRef<EnvironmentSwitch.EnvironmentSwitchCommand> environmentSwitch;
    private final ActorRef<Fridge.FridgeCommand> fridgeActor;
    private final ActorRef<MediaStation.MediaStationCommand> mediaStationActor;
    private final ActorRef<Blinds.BlindsCommand> blindsActor;
    private final ActorSystem<?> system;
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
    }

    public Route createRoute() {
        return concat(
                path("", () -> get(() -> complete(buildHomePage()))),
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

    private String buildHomePage() {
        return """
                <!DOCTYPE html>
                <html>
                <head>
                <meta charset="UTF-8">
                <title>Home Automation System</title>
                <style>
                    * { margin: 0; padding: 0; box-sizing: border-box; }
                    body { 
                        font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif;
                        background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
                        min-height: 100vh;
                        padding: 20px;
                    }
                    .container { 
                        max-width: 900px; 
                        margin: 0 auto;
                    }
                    h1 { 
                        color: white; 
                        text-align: center; 
                        margin-bottom: 30px;
                        font-size: 2.5em;
                        text-shadow: 0 2px 10px rgba(0,0,0,0.2);
                    }
                    .section { 
                        background: white;
                        margin: 20px 0; 
                        padding: 25px; 
                        border-radius: 10px;
                        box-shadow: 0 8px 16px rgba(0,0,0,0.1);
                    }
                    h2 { 
                        color: #333; 
                        margin-bottom: 15px;
                        font-size: 1.5em;
                    }
                    .form-group {
                        margin: 15px 0;
                        display: flex;
                        gap: 10px;
                        align-items: center;
                        flex-wrap: wrap;
                    }
                    input, select { 
                        padding: 10px 15px; 
                        border: 2px solid #ddd;
                        border-radius: 5px;
                        font-size: 14px;
                        transition: border-color 0.3s;
                    }
                    input:focus, select:focus {
                        outline: none;
                        border-color: #667eea;
                    }
                    button { 
                        padding: 10px 20px; 
                        background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
                        color: white; 
                        border: none; 
                        border-radius: 5px; 
                        cursor: pointer;
                        font-weight: 600;
                        transition: transform 0.2s, box-shadow 0.2s;
                    }
                    button:hover { 
                        transform: translateY(-2px);
                        box-shadow: 0 4px 12px rgba(102, 126, 234, 0.4);
                    }
                    button:active {
                        transform: translateY(0);
                    }
                    .response { 
                        margin-top: 15px; 
                        padding: 15px; 
                        background: #f5f5f5; 
                        border-left: 4px solid #667eea;
                        border-radius: 4px;
                        font-family: 'Courier New', monospace;
                        display: none;
                        max-height: 300px;
                        overflow-y: auto;
                        white-space: pre-wrap;
                    }
                    .response.success {
                        border-left-color: #28a745;
                        background: #f0f7f0;
                    }
                    .response.error {
                        border-left-color: #dc3545;
                        background: #fdf0f0;
                    }
                    .response.show {
                        display: block;
                    }
                    label {
                        font-weight: 600;
                        color: #333;
                        min-width: 120px;
                    }
                </style>
                </head>
                <body>
                <div class="container">
                    <h1>🏠 Home Automation System</h1>
                    
                    <!-- Environment Control -->
                    <div class="section">
                        <h2>🌡️ Environment Control</h2>
                        <div class="form-group">
                            <label for="tempInput">Temperature (°C):</label>
                            <input type="number" id="tempInput" placeholder="20" step="0.1" value="20">
                            <button onclick="setTemperature()">Set Temperature</button>
                        </div>
                        <div class="form-group">
                            <label for="weatherSelect">Weather:</label>
                            <select id="weatherSelect">
                                <option value="SUNNY">☀️ Sunny</option>
                                <option value="CLOUDY">☁️ Cloudy</option>
                                <option value="RAINY">🌧️ Rainy</option>
                            </select>
                            <button onclick="setWeather()">Set Weather</button>
                        </div>
                        <div class="form-group">
                            <label for="sourceSelect">Environment Source:</label>
                            <select id="sourceSelect">
                                <option value="SIMULATION">Simulation</option>
                                <option value="MQTT">MQTT</option>
                                <option value="MANUAL">Manual</option>
                                <option value="DISABLED">Disabled</option>
                            </select>
                            <button onclick="setSource()">Switch Source</button>
                        </div>
                        <div id="envResponse" class="response"></div>
                    </div>
                    
                    <!-- Fridge Management -->
                    <div class="section">
                        <h2>❄️ Fridge Management</h2>
                        <div class="form-group">
                            <button onclick="getProducts()">📦 Get Products</button>
                            <button onclick="getOrderHistory()">📜 Get Order History</button>
                        </div>
                        <div class="form-group">
                            <label for="productIdInput">Product ID:</label>
                            <input type="text" id="productIdInput" placeholder="e.g., P001">
                            <label for="consumeQtyInput">Quantity:</label>
                            <input type="number" id="consumeQtyInput" placeholder="1" min="1" value="1">
                            <button onclick="consumeProduct()">Consume</button>
                        </div>
                        <div class="form-group">
                            <label for="orderIdInput">Product ID:</label>
                            <input type="text" id="orderIdInput" placeholder="e.g., P001">
                            <label for="orderQtyInput">Quantity:</label>
                            <input type="number" id="orderQtyInput" placeholder="1" min="1" value="1">
                            <button onclick="orderProduct()">Order</button>
                        </div>
                        <div id="fridgeResponse" class="response"></div>
                    </div>
                    
                    <!-- Media Station -->
                    <div class="section">
                        <h2>🎬 Media Station</h2>
                        <div class="form-group">
                            <label for="movieInput">Movie Name:</label>
                            <input type="text" id="movieInput" placeholder="e.g., Avatar">
                            <button onclick="playMovie()">▶️ Play</button>
                        </div>
                        <div class="form-group">
                            <button onclick="stopMovie()">⏹️ Stop Movie</button>
                            <button onclick="getMediaStatus()">📊 Get Status</button>
                        </div>
                        <div id="mediaResponse" class="response"></div>
                    </div>
                    
                    <!-- Device Status -->
                    <div class="section">
                        <h2>📊 System Status</h2>
                        <div class="form-group">
                            <button onclick="getDeviceStatus()">Get All Device Status</button>
                        </div>
                        <div id="statusResponse" class="response"></div>
                    </div>
                </div>

                <script>
                    const API_BASE = '';
                    
                    // Environment Functions
                    async function setTemperature() {
                        const value = document.getElementById('tempInput').value;
                        if (!value) { alert('Please enter temperature'); return; }
                        try {
                            const response = await fetch(
                                `${API_BASE}/environment/temperature?value=${value}`,
                                { method: 'POST' }
                            );
                            const data = await response.json();
                            showResponse('envResponse', JSON.stringify(data, null, 2), true);
                        } catch(e) {
                            showResponse('envResponse', 'Error: ' + e.message, false);
                        }
                    }

                    async function setWeather() {
                        const condition = document.getElementById('weatherSelect').value;
                        try {
                            const response = await fetch(
                                `${API_BASE}/environment/weather?condition=${condition}`,
                                { method: 'POST' }
                            );
                            const data = await response.json();
                            showResponse('envResponse', JSON.stringify(data, null, 2), true);
                        } catch(e) {
                            showResponse('envResponse', 'Error: ' + e.message, false);
                        }
                    }

                    async function setSource() {
                        const mode = document.getElementById('sourceSelect').value;
                        try {
                            const response = await fetch(
                                `${API_BASE}/environment/source?mode=${mode}`,
                                { method: 'POST' }
                            );
                            const data = await response.json();
                            showResponse('envResponse', JSON.stringify(data, null, 2), true);
                        } catch(e) {
                            showResponse('envResponse', 'Error: ' + e.message, false);
                        }
                    }

                    // Fridge Functions
                    async function getProducts() {
                        try {
                            const response = await fetch(`${API_BASE}/fridge/products`);
                            const data = await response.json();
                            showResponse('fridgeResponse', JSON.stringify(data, null, 2), true);
                        } catch(e) {
                            showResponse('fridgeResponse', 'Error: ' + e.message, false);
                        }
                    }

                    async function getOrderHistory() {
                        try {
                            const response = await fetch(`${API_BASE}/fridge/history`);
                            const data = await response.json();
                            showResponse('fridgeResponse', JSON.stringify(data, null, 2), true);
                        } catch(e) {
                            showResponse('fridgeResponse', 'Error: ' + e.message, false);
                        }
                    }

                    async function consumeProduct() {
                        const productId = document.getElementById('productIdInput').value;
                        const quantity = document.getElementById('consumeQtyInput').value;
                        if (!productId || !quantity) { alert('Please fill all fields'); return; }
                        try {
                            const response = await fetch(
                                `${API_BASE}/fridge/consume?productId=${productId}&quantity=${quantity}`,
                                { method: 'POST' }
                            );
                            const data = await response.json();
                            showResponse('fridgeResponse', JSON.stringify(data, null, 2), true);
                        } catch(e) {
                            showResponse('fridgeResponse', 'Error: ' + e.message, false);
                        }
                    }

                    async function orderProduct() {
                        const productId = document.getElementById('orderIdInput').value;
                        const quantity = document.getElementById('orderQtyInput').value;
                        if (!productId || !quantity) { alert('Please fill all fields'); return; }
                        try {
                            const response = await fetch(
                                `${API_BASE}/fridge/order?productId=${productId}&quantity=${quantity}`,
                                { method: 'POST' }
                            );
                            const data = await response.json();
                            showResponse('fridgeResponse', JSON.stringify(data, null, 2), true);
                        } catch(e) {
                            showResponse('fridgeResponse', 'Error: ' + e.message, false);
                        }
                    }

                    // Media Station Functions
                    async function playMovie() {
                        const name = document.getElementById('movieInput').value;
                        if (!name) { alert('Please enter movie name'); return; }
                        try {
                            const response = await fetch(
                                `${API_BASE}/media-station/play?movieName=${name}`,
                                { method: 'POST' }
                            );
                            const data = await response.json();
                            showResponse('mediaResponse', JSON.stringify(data, null, 2), true);
                        } catch(e) {
                            showResponse('mediaResponse', 'Error: ' + e.message, false);
                        }
                    }

                    async function stopMovie() {
                        try {
                            const response = await fetch(
                                `${API_BASE}/media-station/stop`,
                                { method: 'POST' }
                            );
                            const data = await response.json();
                            showResponse('mediaResponse', JSON.stringify(data, null, 2), true);
                        } catch(e) {
                            showResponse('mediaResponse', 'Error: ' + e.message, false);
                        }
                    }

                    async function getMediaStatus() {
                        try {
                            const response = await fetch(`${API_BASE}/media-station/status`);
                            const data = await response.json();
                            showResponse('mediaResponse', JSON.stringify(data, null, 2), true);
                        } catch(e) {
                            showResponse('mediaResponse', 'Error: ' + e.message, false);
                        }
                    }

                    // Device Status
                    async function getDeviceStatus() {
                        try {
                            const response = await fetch(`${API_BASE}/devices/status`);
                            const data = await response.json();
                            showResponse('statusResponse', JSON.stringify(data, null, 2), true);
                        } catch(e) {
                            showResponse('statusResponse', 'Error: ' + e.message, false);
                        }
                    }

                    function showResponse(elementId, content, isSuccess) {
                        const elem = document.getElementById(elementId);
                        elem.textContent = content;
                        elem.classList.add('show');
                        elem.classList.remove(isSuccess ? 'error' : 'success');
                        elem.classList.add(isSuccess ? 'success' : 'error');
                    }
                </script>
                </body>
                </html>
                """;
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