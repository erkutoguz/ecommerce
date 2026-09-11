package dev.erkut.paymentservice.payment.api;

import dev.erkut.paymentservice.payment.api.error.PaymentExceptionHandler;
import dev.erkut.paymentservice.payment.api.response.PaymentResponse;
import dev.erkut.paymentservice.payment.application.PaymentService;
import dev.erkut.paymentservice.payment.application.exception.PaymentNotFoundException;
import dev.erkut.paymentservice.payment.domain.PaymentStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(PaymentController.class)
@Import(PaymentExceptionHandler.class)
class PaymentControllerTest {

    private static final UUID ORDER_ID = UUID.fromString("80000000-0000-0000-0000-000000000001");
    private static final String CHECKOUT_URL = "https://checkout.stripe.com/c/test-session";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private PaymentService paymentService;

    @Test
    void getPaymentByOrderId_existingPaymentReturnsPublicCheckpoint() throws Exception {
        when(paymentService.getPaymentByOrderId(ORDER_ID))
                .thenReturn(new PaymentResponse(ORDER_ID, PaymentStatus.AWAITING_CUSTOMER_ACTION, CHECKOUT_URL));

        mockMvc.perform(get("/payments/order/{orderId}", ORDER_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.orderId").value(ORDER_ID.toString()))
                .andExpect(jsonPath("$.status").value("AWAITING_CUSTOMER_ACTION"))
                .andExpect(jsonPath("$.checkoutUrl").value(CHECKOUT_URL))
                .andExpect(jsonPath("$.providerPaymentId").doesNotExist());

        verify(paymentService).getPaymentByOrderId(ORDER_ID);
    }

    @Test
    void getPaymentByOrderId_missingPaymentReturnsNotFound() throws Exception {
        when(paymentService.getPaymentByOrderId(ORDER_ID))
                .thenThrow(new PaymentNotFoundException("Payment not found with order id: " + ORDER_ID));

        mockMvc.perform(get("/payments/order/{orderId}", ORDER_ID))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("Payment not found with order id: " + ORDER_ID));
    }
}
