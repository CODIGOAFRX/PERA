package com.peraerp.sales.document;
import jakarta.validation.constraints.NotNull;
public record UpdatePaymentStatusRequest(@NotNull PaymentStatus status) {}
