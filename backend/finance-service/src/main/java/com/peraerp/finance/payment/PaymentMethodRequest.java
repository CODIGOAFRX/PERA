package com.peraerp.finance.payment;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.List;
public record PaymentMethodRequest(@NotBlank @Size(max=40) String code,@NotBlank @Size(max=160) String name,
                                   @NotEmpty List<@Valid PaymentRuleRequest> rules){}
