package at.fhv.sysarch.lab2.homeautomation.shared.model.order;

import java.util.List;

public final class FridgeInventory {
    private FridgeInventory() {}

    public static List<Product> defaultProducts() {
        return List.of(
                new Product("P001", "Milk",1.03, 1.49, 2),
                new Product("P002", "Apples", 0.18, 0.40, 6),
                new Product("P003", "Sourdough",0.55, 3.20, 3),
                new Product("P004", "Gruyère",0.30, 5.80, 1),
                new Product("P005", "Eggs (6er)", 0.42, 2.10, 1),
                new Product("P007", "Salmon Filet",0.35, 8.90, 8),
                new Product("P009", "Carrots",1.00, 1.80, 2)
        );
    }
}