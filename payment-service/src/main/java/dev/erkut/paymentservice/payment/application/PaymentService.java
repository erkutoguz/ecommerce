package dev.erkut.paymentservice.payment.application;

import dev.erkut.paymentservice.inbox.application.InboxService;
import dev.erkut.paymentservice.message.MessageEnvelope;
import dev.erkut.paymentservice.message.command.InitiatePaymentCommand;
import dev.erkut.paymentservice.payment.domain.Currency;
import dev.erkut.paymentservice.payment.domain.Payment;
import dev.erkut.paymentservice.payment.persistence.PaymentRepository;
import dev.erkut.paymentservice.provider.payment.PaymentProvider;
import dev.erkut.paymentservice.provider.payment.PaymentSession;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Service
public class PaymentService {
    private final PaymentRepository paymentRepository;
    private final InboxService inboxService;
    private final PaymentProvider paymentProvider;

    public PaymentService(
            PaymentRepository paymentRepository,
            InboxService inboxService,
            PaymentProvider paymentProvider) {
        this.paymentRepository = paymentRepository;
        this.inboxService = inboxService;
        this.paymentProvider = paymentProvider;
    }

    @Transactional
    public void handleInitiatePaymentCommand(MessageEnvelope envelope, InitiatePaymentCommand command) {
        validateInitiatePaymentCommand(envelope, command);

        Instant now = Instant.now();

        if(isDuplicate(envelope, command.orderId(), now)) {
            return;
        }

        Currency currency = CurrencyMapper.from(command.currency());
        Payment payment = Payment.create(command.orderId(), command.totalAmount(), currency, now);
        PaymentSession session =
                paymentProvider.initializePaymentSession(command.orderId(), command.totalAmount(), currency);

        payment.markAwaitingCustomerAction(session.providerPaymentId(), session.checkoutUrl(), now);

        paymentRepository.save(payment);
    }

    private void validateInitiatePaymentCommand(MessageEnvelope envelope, Object event) {
        if (envelope == null) {
            throw new IllegalArgumentException("Message envelope cannot be null");
        }
        if (event == null) {
            throw new IllegalArgumentException("Initiate payment command cannot be null");
        }

    }

    private boolean isDuplicate(
            MessageEnvelope envelope,
            UUID orderId,
            Instant now
    ) {
        return !inboxService.tryRegister(
                envelope.messageId(),
                envelope.messageType(),
                orderId,
                now
        );
    }
}
