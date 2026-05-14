package at.fhv.sysarch.lab2.orderprocessor;

import org.apache.pekko.actor.typed.*;
import org.apache.pekko.actor.typed.javadsl.*;

import java.sql.*;
import java.util.UUID;

public class PersistenceActor extends AbstractBehavior<PersistenceActor.Command> {

    public interface Command {}

    public record PersistOrder(
            String productId, int quantity, double unitPrice,
            ActorRef<ValidationActor.ValidationResult> replyTo
    ) implements Command {}

    private Connection dbConnection;

    public static Behavior<Command> create() {
        return Behaviors.setup(PersistenceActor::new);
    }

    private PersistenceActor(ActorContext<Command> context) {
        super(context);
        initDatabase();
    }

    private void initDatabase() {
        try {
            Class.forName("org.h2.Driver");
            dbConnection = DriverManager.getConnection(
                    "jdbc:h2:./data/orders;AUTO_SERVER=TRUE", "sa", ""
            );
            dbConnection.createStatement().execute("""
                CREATE TABLE IF NOT EXISTS orders (
                    id VARCHAR(36) PRIMARY KEY,
                    product_id VARCHAR(50) NOT NULL,
                    quantity INT NOT NULL,
                    unit_price DOUBLE NOT NULL,
                    total_price DOUBLE NOT NULL,
                    status VARCHAR(20) NOT NULL,
                    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                )
            """);
            getContext().getLog().info("H2 Database initialized");
        } catch (Exception e) {
            getContext().getLog().error("Database init failed: {}", e.getMessage());
        }
    }

    @Override
    public Receive<Command> createReceive() {
        return newReceiveBuilder()
                .onMessage(PersistOrder.class, this::onPersist)
                .onSignal(PostStop.class, signal -> {
                    if (dbConnection != null) {
                        try { dbConnection.close(); } catch (SQLException ignored) {}
                    }
                    return this;
                })
                .build();
    }

    private Behavior<Command> onPersist(PersistOrder msg) {
        String orderId = UUID.randomUUID().toString();
        double totalPrice = msg.quantity * msg.unitPrice;

        try {
            PreparedStatement ps = dbConnection.prepareStatement(
                    "INSERT INTO orders (id, product_id, quantity, unit_price, total_price, status) " +
                            "VALUES (?, ?, ?, ?, ?, ?)"
            );
            ps.setString(1, orderId);
            ps.setString(2, msg.productId);
            ps.setInt(3, msg.quantity);
            ps.setDouble(4, msg.unitPrice);
            ps.setDouble(5, totalPrice);
            ps.setString(6, "COMPLETED");
            ps.executeUpdate();

            getContext().getLog().info("Order {} persisted to H2", orderId);
            msg.replyTo.tell(new ValidationActor.ValidationResult(
                    true, orderId, msg.productId, msg.quantity, msg.unitPrice
            ));
        } catch (SQLException e) {
            getContext().getLog().error("Persist failed: {}", e.getMessage());
            msg.replyTo.tell(new ValidationActor.ValidationResult(
                    false, "DB error: " + e.getMessage(), msg.productId, msg.quantity, msg.unitPrice
            ));
        }
        return Behaviors.same();
    }
}