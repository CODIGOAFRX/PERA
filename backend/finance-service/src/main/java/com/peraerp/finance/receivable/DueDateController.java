package com.peraerp.finance.receivable;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import java.util.*;
@RestController @RequestMapping("/api/v1/due-dates")
public class DueDateController{
    private final DueDateService service; public DueDateController(DueDateService service){this.service=service;}
    @PostMapping("/generate") @ResponseStatus(HttpStatus.CREATED) List<DueDateResponse> generate(@Valid @RequestBody GenerateDueDatesRequest request){return service.generate(request);}
    @GetMapping List<DueDateResponse> byDocument(@RequestParam UUID documentId){return service.findByDocument(documentId);}
}
