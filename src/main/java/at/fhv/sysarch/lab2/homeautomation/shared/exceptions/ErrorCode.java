package at.fhv.sysarch.lab2.homeautomation.shared.exceptions;

public enum ErrorCode {
    ENV001,
    ENV002,
    ENV003,

    MQTT001,
    MQTT002,

    KEY001,

    FRIDGE001,  // Insufficient space
    FRIDGE002,  // Insufficient weight capacity
    FRIDGE003,  // Product not available
    FRIDGE004,  // Invalid order
    FRIDGE005,  // Order processing failed

    MEDIA001,   // Media station error
    AC001,      // AC error
    BLINDS001   // Blinds error
}
