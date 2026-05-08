package at.fhv.sysarch.lab2.homeautomation.uihandler;

/*
public class DemoHttpServer extends AllDirectives {
    private final ActorRef<EnvironmentActor.EnvironmentCommand> environmentActor;

    public DemoHttpServer(ActorRef<EnvironmentActor.EnvironmentCommand> environmentActor) {
        this.environmentActor = environmentActor;
    }

    public Route createRoute() {
        return concat(
                path("", () -> get(() -> complete(buildHomePage()))),
                pathPrefix("environment", () -> concat(path("temperature", () -> post(() ->
                                parameter("value", valueStr -> {
                                    try {
                                        double temperature = Double.parseDouble(valueStr);
                                        environmentActor.tell(new EnvironmentActor.SetTemperature(temperature));
                                        return complete("Temperature set to " + temperature + " C");
                                    } catch (NumberFormatException e) {
                                        return complete("Invalid temperature value: " + valueStr);
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
                                        return complete("Invalid weather condition: " + conditionStr + ". Valid: SUNNY, CLOUDY, RAINY");
                                    }
                                })
                        )),

                        path("source", () -> post(() ->
                                parameter("mode", modeStr -> {
                                    try {
                                        EnvironmentActor.EnvironmentSource source = EnvironmentActor.EnvironmentSource.valueOf(modeStr.toUpperCase());
                                        environmentActor.tell(new EnvironmentActor.SwitchSource(source));
                                        return complete("Environment source switched to " + source);
                                    } catch (Exception e) {
                                        return complete("Invalid mode: " + modeStr + ". Valid: SIMULATION, MQTT, MANUAL, DISABLED");
                                    }
                                })
                        ))
                )),

                path("hello", () -> get(() -> complete("<h1>Say hello to pekko-http</h1>")))
        );
    }

    private String buildHomePage() {
        return """
                <html>
                    <head><title>Home Automation System</title>
                    </head>
                    <body>
                        <h1>Home Automation System</h1>
                    </body>
                </html>
                """;
    }
}


 */
