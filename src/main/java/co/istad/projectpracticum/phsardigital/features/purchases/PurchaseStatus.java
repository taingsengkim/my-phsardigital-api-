package co.istad.projectpracticum.phsardigital.features.purchases;

public enum PurchaseStatus {
    PENDING,     // buyer placed it; awaiting seller
    CONFIRMED,   // seller accepted; stock reserved, out for delivery
    COMPLETED,   // delivered & cash collected
    CANCELLED
}