package at.fhv.sysarch.lab2;


import at.fhv.sysarch.lab2.homeautomation.HomeAutomationController;
import org.apache.pekko.actor.typed.ActorSystem;

public class HomeAutomationSystem {

    public static void main(String[] args) {
        ActorSystem<Void> system = ActorSystem.create(HomeAutomationController.create(), "HomeAutomation");

        // Hält die Anwendung im Hauptthread am Laufen und beendet das System, sobald der Benutzer im Terminal auf Enter drückt.
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            system.log().info("Shutdown signal received - terminating system");
            system.terminate();
        }));

        system.log().info("System started");
    }
}