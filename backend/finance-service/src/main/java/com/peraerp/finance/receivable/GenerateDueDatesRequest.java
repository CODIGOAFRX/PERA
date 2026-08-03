package com.peraerp.finance.receivable;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;
public record GenerateDueDatesRequest(@NotNull UUID documentId,@NotNull UUID paymentMethodId,@NotNull LocalDate issueDate,
                                      @NotNull @DecimalMin(value="0",inclusive=false) BigDecimal totalAmount){}
