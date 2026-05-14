package at.fhv.sysarch.lab2.homeautomation.devices;

import at.fhv.sysarch.lab2.homeautomation.devices.model.Order;
import at.fhv.sysarch.lab2.homeautomation.devices.model.Product;
import at.fhv.sysarch.lab2.homeautomation.devices.model.Receipt;
import at.fhv.sysarch.lab2.homeautomation.shared.exceptions.FridgeException;
import at.fhv.sysarch.lab2.homeautomation.shared.exceptions.InsufficientSpaceException;
import at.fhv.sysarch.lab2.homeautomation.shared.exceptions.InsufficientWeightCapacityException;
import at.fhv.sysarch.lab2.homeautomation.shared.exceptions.InvalidOrderException;
import org.apache.pekko.actor.typed.ActorRef;
import org.apache.pekko.actor.typed.Behavior;
import org.apache.pekko.actor.typed.PostStop;
import org.apache.pekko.actor.typed.javadsl.AbstractBehavior;
import org.apache.pekko.actor.typed.javadsl.ActorContext;
import org.apache.pekko.actor.typed.javadsl.Behaviors;
import org.apache.pekko.actor.typed.javadsl.Receive;
import org.apache.pekko.actor.typed.receptionist.ServiceKey;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Fridge extends AbstractBehavior<Fridge.FridgeCommand> {

    public interface FridgeCommand { }

    public record GetProducts(ActorRef<ProductsResponse> replyTo) implements FridgeCommand { }
    public record GetOrderHistory(ActorRef<OrderHistoryResponse> replyTo) implements FridgeCommand { }
    public record GetCapacity(ActorRef<CapacityResponse> replyTo) implements FridgeCommand { }
    public record ConsumeProduct(String productId, int quantity) implements FridgeCommand { }
    public record OrderProducts(Map<String, Integer> items, ActorRef<OrderResponse> replyTo) implements FridgeCommand { }

    public record ProductsResponse(List<Product> products) { }
    public record OrderHistoryResponse(List<Order> orders) { }
    public record CapacityResponse(int currentItems, int maxItems, double currentWeight, double maxWeight) { }
    public record OrderResponse(boolean success, String message, Receipt receipt) { }
    public record OrderCompleted(Order order, Receipt receipt) implements FridgeCommand {}

    public static Behavior<FridgeCommand> create(
            String identifier, int maxItems, double maxWeightKg) {
        return Behaviors.setup(context ->
                new Fridge(context, identifier, maxItems, maxWeightKg)
        );
    }

    private final String identifier;
    private final int maxItems;
    private final double maxWeightKg;

    private final Map<String, Product> inventory = new HashMap<>();
    private final List<Order> orderHistory = new ArrayList<>();
    private int currentItemCount = 0;
    private double currentWeightKg = 0.0;


    public static final ServiceKey<FridgeCommand> SERVICE_KEY =
            ServiceKey.create(FridgeCommand.class, "fridge");


    public Fridge(ActorContext<FridgeCommand> context,
                  String identifier,
                  int maxItems,
                  double maxWeightKg) {
        super(context);
        this.identifier = identifier;
        this.maxItems = maxItems;
        this.maxWeightKg = maxWeightKg;
        initializeSampleProducts();
        getContext().getLog().info("Fridge Actor '{}' started - Max: {} items, {} kg",
                identifier, maxItems, maxWeightKg);
    }

    private void initializeSampleProducts() {
        addProduct(new Product("P001", "Milk",          1.03, 1.49, 2));
        addProduct(new Product("P002", "Apples",        0.18, 0.40, 6));
        addProduct(new Product("P003", "Sourdough",     0.55, 3.20, 0));
        addProduct(new Product("P004", "Gruyère",       0.30, 5.80, 1));
        addProduct(new Product("P005", "Eggs (6er)",    0.42, 2.10, 1));
        addProduct(new Product("P006", "Chicken Breast",0.50, 7.40, 0));
        addProduct(new Product("P007", "Salmon Filet",  0.35, 8.90, 8));
        addProduct(new Product("P008", "Mineral Water", 1.50, 0.99, 3));
        addProduct(new Product("P009", "Carrots",       1.00, 1.80, 0));
    }

    private void addProduct(Product product) {
        inventory.put(product.getId(), product);
        currentItemCount += product.getQuantity();
        currentWeightKg += product.getTotalWeight();
    }

    @Override
    public Receive<FridgeCommand> createReceive() {
        return newReceiveBuilder()
                .onMessage(GetProducts.class, this::onGetProducts)
                .onMessage(GetOrderHistory.class, this::onGetOrderHistory)
                .onMessage(GetCapacity.class, this::onGetCapacity)
                .onMessage(ConsumeProduct.class, this::onConsumeProduct)
                .onMessage(OrderProducts.class, this::onOrderProducts)
                .onMessage(OrderCompleted.class, this::onOrderCompleted)
                .onSignal(PostStop.class, signal -> onPostStop())
                .build();
    }

    private Behavior<FridgeCommand> onGetProducts(GetProducts msg) {
        List<Product> products = new ArrayList<>(inventory.values());
        msg.replyTo.tell(new ProductsResponse(products));
        return Behaviors.same();
    }

    private Behavior<FridgeCommand> onGetOrderHistory(GetOrderHistory msg) {
        List<Order> history = new ArrayList<>(orderHistory);
        msg.replyTo.tell(new OrderHistoryResponse(history));
        return Behaviors.same();
    }

    private Behavior<FridgeCommand> onGetCapacity(GetCapacity msg) {
        msg.replyTo.tell(new CapacityResponse(currentItemCount, maxItems, currentWeightKg, maxWeightKg));
        return Behaviors.same();
    }

    private Behavior<FridgeCommand> onConsumeProduct(ConsumeProduct msg) {
        Product product = inventory.get(msg.productId);

        if (product == null) {
            getContext().getLog().warn("Fridge '{}': Product {} not found", identifier, msg.productId);
            return Behaviors.same();
        }

        if (product.getQuantity() < msg.quantity) {
            getContext().getLog().warn("Fridge '{}': Not enough quantity of {}",
                    identifier, product.getName());
            return Behaviors.same();
        }

        product.removeQuantity(msg.quantity);
        currentItemCount -= msg.quantity;
        currentWeightKg -= (product.getWeight() * msg.quantity);

        getContext().getLog().info("Fridge '{}': Consumed {} x {}",
                identifier, msg.quantity, product.getName());

        if (product.getQuantity() == 0) {
            autoOrderProduct(product.getId(), 5);
        }

        return Behaviors.same();
    }

    private Behavior<FridgeCommand> onOrderProducts(OrderProducts msg) {

        try {
            validateOrder(msg.items);

            double totalPrice = 0.0;
            for (Map.Entry<String, Integer> entry : msg.items.entrySet()) {
                Product p = inventory.get(entry.getKey());
                totalPrice += p.getPrice() * entry.getValue();
            }

            Order order = new Order(msg.items);
            order.setTotalPrice(totalPrice);
            order.setStatus(Order.OrderStatus.PROCESSING);
            orderHistory.add(order);

            ActorRef<OrderProcessor.OrderProcessorCommand> sessionProcessor =
                    getContext().spawnAnonymous(OrderProcessor.create());

            Map<String, Double> prices = new HashMap<>();
            for (Map.Entry<String, Integer> entry : msg.items.entrySet()) {
                prices.put(entry.getKey(), inventory.get(entry.getKey()).getPrice());
            }

            sessionProcessor.tell(
                    new OrderProcessor.ProcessOrder(
                            order, msg.items, prices, msg.replyTo, this.identifier, getContext().getSelf()
                    )
            );

            return Behaviors.same();

        } catch (FridgeException e) {
            getContext().getLog().error("Order validation failed: {}", e.getMessage());
            if (msg.replyTo != null) {
                msg.replyTo.tell(new OrderResponse(false, e.getMessage(), null));
            }
            return Behaviors.same();
        }
    }

    private Behavior<FridgeCommand> onOrderCompleted(OrderCompleted msg) {
        completeOrder(msg.order(), msg.receipt());
        return Behaviors.same();
    }

    private void validateOrder(Map<String, Integer> items) throws FridgeException {
        for (String productId : items.keySet()) {
            if (!inventory.containsKey(productId)) {
                throw new InvalidOrderException("Product " + productId + " not found");
            }
            if (items.get(productId) <= 0) {
                throw new InvalidOrderException("Quantity must be positive");
            }
        }

        int totalNewItems = items.values().stream().mapToInt(Integer::intValue).sum();
        if (currentItemCount + totalNewItems > maxItems) {
            throw new InsufficientSpaceException(currentItemCount, maxItems, totalNewItems);
        }

        double totalNewWeight = 0.0;
        for (Map.Entry<String, Integer> entry : items.entrySet()) {
            Product p = inventory.get(entry.getKey());
            totalNewWeight += (p.getWeight() * entry.getValue());
        }

        if (currentWeightKg + totalNewWeight > maxWeightKg) {
            throw new InsufficientWeightCapacityException(currentWeightKg, maxWeightKg, totalNewWeight);
        }
    }

    private void autoOrderProduct(String productId, int quantity) {
        Map<String, Integer> items = new HashMap<>();
        items.put(productId, quantity);

        getContext().getLog().info("Fridge '{}': Auto-ordering {} x product {}",
                identifier, quantity, productId);

        getContext().getSelf().tell(new OrderProducts(items, null));
    }

    public void completeOrder(Order order, Receipt receipt) {
        order.setStatus(Order.OrderStatus.COMPLETED);

        for (Map.Entry<String, Integer> entry : order.getItems().entrySet()) {
            Product p = inventory.get(entry.getKey());
            if (p != null) {
                p.addQuantity(entry.getValue());
                currentItemCount += entry.getValue();
                currentWeightKg += (p.getWeight() * entry.getValue());
            }
        }

        getContext().getLog().info("Fridge '{}': Order {} completed - {}",
                identifier, order.getOrderId(), receipt);
    }

    private Fridge onPostStop() {
        getContext().getLog().info("Fridge Actor '{}' stopped", identifier);
        return this;
    }
}
