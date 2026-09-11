package dev.erkut.paymentservice.payment.api;

import dev.erkut.paymentservice.payment.api.response.PaymentResponse;
import dev.erkut.paymentservice.payment.application.PaymentService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/payments")
public class PaymentController {

    private final PaymentService paymentService;

    public PaymentController(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    @GetMapping("/order/{orderId}")
    public ResponseEntity<PaymentResponse> getPaymentByOrderId(
            @PathVariable("orderId") UUID orderId
    ) {
        PaymentResponse response = paymentService.getPaymentByOrderId(orderId);
        return ResponseEntity.status(HttpStatus.OK).body(response);
    }
}
