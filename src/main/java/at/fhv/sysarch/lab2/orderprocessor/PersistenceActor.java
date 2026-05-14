package at.fhv.sysarch.lab2.orderprocessor;

import org.apache.pekko.actor.typed.*;
import org.apache.pekko.actor.typed.javadsl.*;

import java.sql.*;
import java.util.UUID;

public class PersistenceActor extends AbstractBehavior<PersistenceActor.Command> {

    public interface Command {}

    public record PersistOrder(
            java.util.List<ValidationActor.OrderItemData> items,
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
                    id VARCHAR(100) PRIMARY KEY,
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
        double totalPrice = msg.items.stream()
                .mapToDouble(i -> i.quantity() * i.unitPrice()).sum();

        try {
            for (ValidationActor.OrderItemData item : msg.items) {
                PreparedStatement ps = dbConnection.prepareStatement(
                        "INSERT INTO orders (id, product_id, quantity, unit_price, total_price, status) VALUES (?, ?, ?, ?, ?, ?)"
                );
                ps.setString(1, orderId + "-" + item.productId());
                ps.setString(2, item.productId());
                ps.setInt(3, item.quantity());
                ps.setDouble(4, item.unitPrice());
                ps.setDouble(5, item.quantity() * item.unitPrice());
                ps.setString(6, "COMPLETED");
                ps.executeUpdate();
            }
            msg.replyTo.tell(new ValidationActor.ValidationResult(true, orderId, msg.items));
        } catch (SQLException e) {
            msg.replyTo.tell(new ValidationActor.ValidationResult(false, "DB error: " + e.getMessage(), msg.items));
        }
        return Behaviors.same();
    }
}