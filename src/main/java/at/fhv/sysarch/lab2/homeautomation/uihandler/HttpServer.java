package at.fhv.sysarch.lab2.homeautomation.uihandler;

import at.fhv.sysarch.lab2.homeautomation.devices.AirCondition;
import at.fhv.sysarch.lab2.homeautomation.devices.Blinds;
import at.fhv.sysarch.lab2.homeautomation.devices.Fridge;
import at.fhv.sysarch.lab2.homeautomation.devices.MediaStation;
import at.fhv.sysarch.lab2.homeautomation.environment.EnvironmentCoordinator;
import at.fhv.sysarch.lab2.homeautomation.uihandler.exception.GlobalExceptionHandler;
import at.fhv.sysarch.lab2.homeautomation.uihandler.routes.EnvironmentRoutes;
import at.fhv.sysarch.lab2.homeautomation.uihandler.routes.FridgeRoutes;
import at.fhv.sysarch.lab2.homeautomation.uihandler.routes.MediaRoutes;
import at.fhv.sysarch.lab2.homeautomation.uihandler.routes.StatusRoutes;
import org.apache.pekko.actor.typed.ActorRef;
import org.apache.pekko.actor.typed.ActorSystem;
import org.apache.pekko.http.javadsl.model.ContentTypes;
import org.apache.pekko.http.javadsl.model.HttpEntities;
import org.apache.pekko.http.javadsl.model.StatusCodes;
import org.apache.pekko.http.javadsl.model.headers.RawHeader;
import org.apache.pekko.http.javadsl.server.AllDirectives;
import org.apache.pekko.http.javadsl.server.ExceptionHandler;
import org.apache.pekko.http.javadsl.server.Route;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

public class HttpServer extends AllDirectives {
    private final String homePage;
    private final EnvironmentRoutes environmentRoutes;
    private final FridgeRoutes fridgeRoutes;
    private final MediaRoutes mediaRoutes;
    private final StatusRoutes statusRoutes;

    public HttpServer(ActorRef<EnvironmentCoordinator.Command> environmentCoordinator, ActorRef<Fridge.FridgeCommand> fridgeActor,
                      ActorRef<MediaStation.MediaStationCommand> mediaStationActor, ActorRef<Blinds.BlindsCommand> blindsActor,
                      ActorRef<AirCondition.AirConditionCommand> airConditionActor, ActorSystem<?> system) {
        this.homePage = loadHomePage();
        this.environmentRoutes = new EnvironmentRoutes(environmentCoordinator, airConditionActor);
        this.fridgeRoutes = new FridgeRoutes(fridgeActor, system);
        this.mediaRoutes = new MediaRoutes(mediaStationActor, system);
        this.statusRoutes = new StatusRoutes(environmentCoordinator, airConditionActor, blindsActor, mediaStationActor, system);
    }

    public Route createRoute() {
        ExceptionHandler exceptionHandler = GlobalExceptionHandler.create();
        return handleExceptions(exceptionHandler, () -> withCors(concat(
                path("", () -> get(() -> complete(StatusCodes.OK, HttpEntities.create(ContentTypes.TEXT_HTML_UTF8, homePage)))),
                statusRoutes.routes(),
                environmentRoutes.routes(),
                fridgeRoutes.routes(),
                mediaRoutes.routes(),
                path("hello", () -> get(() -> complete("<h1>Say hello to Pekko-HTTP</h1>")))
        )));
    }

    private Route withCors(Route inner) {
        return respondWithHeaders(
                List.of(
                        RawHeader.create("Access-Control-Allow-Origin", "*"),
                        RawHeader.create("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS"),
                        RawHeader.create("Access-Control-Allow-Headers", "Content-Type, Authorization")
                ),
                () -> concat(options(() -> complete(StatusCodes.OK)), inner)
        );
    }

    private String loadHomePage() {
        ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
        try (InputStream inputStream = classLoader.getResourceAsStream("index.html")) {
            if (inputStream == null) {
                return "<h1>Home Automation</h1><p>UI läuft im Next.js-Frontend (Port 3000).</p>";
            }
            return new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException cause) {
            return "<h1>Home Automation</h1><p>UI could not be loaded.</p>";
        }
    }
}