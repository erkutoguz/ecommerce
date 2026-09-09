package dev.erkut.orderworkflowservice.saga.domain;

import dev.erkut.orderworkflowservice.saga.domain.exception.IllegalOrderSagaStateException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class OrderSagaTest {

    private static final UUID ORDER_ID = UUID.fromString("80000000-0000-0000-0000-000000000030");
    private static final UUID CUSTOMER_ID = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaa30");
    private static final Instant CREATED_AT = Instant.parse("2026-01-01T10:00:00Z");

    @Test
    void markFailed_fromOrderRejectionPending_shouldMoveSagaToFailedAtGivenTime() {
        OrderSaga saga = newSaga();
        saga.markOrderRejectionPending(CREATED_AT.plusSeconds(1));
        Instant failedAt = CREATED_AT.plusSeconds(2);

        saga.markFailed(failedAt);

        assertEquals(OrderSagaState.FAILED, saga.getState());
        assertEquals(failedAt, saga.getUpdatedAt());
    }

    @ParameterizedTest
    @EnumSource(
            value = OrderSagaState.class,
            mode = EnumSource.Mode.EXCLUDE,
            names = "ORDER_REJECTION_PENDING"
    )
    void markFailed_fromAnyOtherState_shouldRejectWithoutChangingState(OrderSagaState initialState) {
        OrderSaga saga = newSagaInState(initialState);

        assertThrows(
                IllegalOrderSagaStateException.class,
                () -> saga.markFailed(CREATED_AT.plusSeconds(1))
        );

        assertEquals(initialState, saga.getState());
    }

    private static OrderSaga newSagaInState(OrderSagaState state) {
        OrderSaga saga = newSaga();
        ReflectionTestUtils.setField(saga, "state", state);
        return saga;
    }

    private static OrderSaga newSaga() {
        return OrderSaga.start(
                ORDER_ID,
                new BigDecimal("200.00"),
                Currency.TRY,
                CUSTOMER_ID,
                CREATED_AT
        );
    }
}
