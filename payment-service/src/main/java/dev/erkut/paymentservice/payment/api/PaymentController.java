package dev.erkut.paymentservice.payment.api;

import dev.erkut.paymentservice.payment.api.response.PaymentResponse;
import dev.erkut.paymentservice.payment.application.PaymentService;
import dev.erkut.paymentservice.security.CurrentUser;
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
    private final CurrentUser currentUser;

    public PaymentController(
            PaymentService paymentService,
            CurrentUser currentUser
    ) {
        this.paymentService = paymentService;
        this.currentUser = currentUser;
    }

    @GetMapping("/order/{orderId}")
    public ResponseEntity<PaymentResponse> getPaymentByOrderId(
            @PathVariable("orderId") UUID orderId
    ) {
        PaymentResponse response = paymentService.getPaymentByOrderId(
                orderId,
                currentUser.accessToken()
        );
        return ResponseEntity.status(HttpStatus.OK).body(response);
    }
}
