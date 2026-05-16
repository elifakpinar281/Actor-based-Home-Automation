package at.fhv.sysarch.lab2.orderprocessor;

import org.apache.pekko.actor.typed.*;
import org.apache.pekko.actor.typed.javadsl.*;

import java.sql.*;
import java.util.List;
import java.util.UUID;

public class PersistenceActor extends AbstractBehavior<PersistenceActor.Command> {
    public interface Command {}

    public record PersistOrder(List<ValidationActor.OrderItemData> items, ActorRef<ValidationActor.ValidationResult> replyTo) implements Command {}

    private static final String JDBC_URL = "jdbc:h2:./data/orders;AUTO_SERVER=TRUE";
    private static final String INSERT_SQL = "INSERT INTO orders (id, product_id, quantity, unit_price, total_price, status) VALUES (?, ?, ?, ?, ?, ?)";

    private final Connection dbConnection;

    public static Behavior<Command> create() {
        return Behaviors.setup(PersistenceActor::new);
    }

    private PersistenceActor(ActorContext<Command> context) {
        super(context);
        this.dbConnection = initDatabase();
        getContext().getLog().info("PersistenceActor: H2 database initialized at {}", JDBC_URL);
    }

    private Connection initDatabase() {
        try {
            Class.forName("org.h2.Driver");
            Connection connection = DriverManager.getConnection(JDBC_URL, "sa", "");
            try (Statement statement = connection.createStatement()) {
                statement.execute("""
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
            }
            return connection;
        } catch (ClassNotFoundException ex) {
            throw new IllegalStateException("H2 Driver not found on classpath", ex);
        } catch (SQLException ex) {
            throw new IllegalStateException("Failed to initialize H2 database: " + ex.getMessage(), ex);
        }
    }

    @Override
    public Receive<Command> createReceive() {
        return newReceiveBuilder()
                .onMessage(PersistOrder.class, this::onPersist)
                .onSignal(PostStop.class, signal -> onPostStop())
                .build();
    }

    private Behavior<Command> onPersist(PersistOrder msg) {
        String orderId = UUID.randomUUID().toString();
        double totalPrice = msg.items.stream()
                .mapToDouble(item -> item.quantity() * item.unitPrice())
                .sum();

        try {
            dbConnection.setAutoCommit(false);
            try (PreparedStatement preparedStatement = dbConnection.prepareStatement(INSERT_SQL)) {
                for (ValidationActor.OrderItemData item : msg.items) {
                    preparedStatement.setString(1, orderId + "-" + item.productId());
                    preparedStatement.setString(2, item.productId());
                    preparedStatement.setInt(3, item.quantity());
                    preparedStatement.setDouble(4, item.unitPrice());
                    preparedStatement.setDouble(5, item.quantity() * item.unitPrice());
                    preparedStatement.setString(6, "COMPLETED");
                    preparedStatement.addBatch();
                }
                preparedStatement.executeBatch();
            }
            dbConnection.commit();
            getContext().getLog().info("PersistenceActor: persisted order {} ({} items, total {})", orderId, msg.items.size(), totalPrice);
            msg.replyTo.tell(ValidationActor.ValidationResult.success(orderId, msg.items));
        } catch (SQLException ex) {
            rollbackSilently();
            getContext().getLog().error("PersistenceActor: failed to persist order: {}", ex.getMessage());
            msg.replyTo.tell(ValidationActor.ValidationResult.failure("Database error: " + ex.getMessage(), msg.items));
        }
        return Behaviors.same();
    }

    private void rollbackSilently() {
        try {
            dbConnection.rollback();
        } catch (SQLException ignored) {
            // Fehler beim Rollback ist in der SQLException gelogged -> deswegen ignoriert
        }
    }

    private Behavior<Command> onPostStop() {
        try {
            dbConnection.close();
            getContext().getLog().info("PersistenceActor: database connection closed");
        } catch (SQLException exception) {
            getContext().getLog().warn("PersistenceActor: error closing connection: {}", exception.getMessage());
        }
        return this;
    }
}
