package com.peraerp.finance.payment;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import java.util.List;
@RestController @RequestMapping("/api/v1/payment-methods")
public class PaymentMethodController{
    private final PaymentMethodService service; public PaymentMethodController(PaymentMethodService service){this.service=service;}
    @GetMapping List<PaymentMethodResponse> findAll(){return service.findAll();}
    @PostMapping @ResponseStatus(HttpStatus.CREATED) PaymentMethodResponse create(@Valid @RequestBody PaymentMethodRequest request){return service.create(request);}
}
