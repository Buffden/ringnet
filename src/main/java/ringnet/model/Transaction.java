package ringnet.model;

public record Transaction(String id, String fromAccountId, String toAccountId, String amount, String timestamp, String status) {}
