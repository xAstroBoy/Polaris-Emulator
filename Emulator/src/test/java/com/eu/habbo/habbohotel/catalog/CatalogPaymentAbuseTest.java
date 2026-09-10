package com.eu.habbo.habbohotel.catalog;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

/**
 * The arithmetic a purchase packet can reach.
 *
 * <p>Price and quantity arrive from the client. A negative price would pay the buyer, and a
 * quantity large enough to overflow an int would wrap a huge total into a small - or negative - one
 * and hand over the furni for nothing. Neither can be left to the wallet to notice afterwards: by
 * then the ledger has already been asked to move the money.
 */
class CatalogPaymentAbuseTest {

    @Test
    void aNegativePriceIsRefusedRatherThanPaidOut() {
        assertThrows(IllegalArgumentException.class, () -> CatalogPurchaseMath.requireNonNegative(-1, "price"));
        assertThrows(IllegalArgumentException.class, () -> CatalogPurchaseMath.checkedAdd(-1, 0));
        assertThrows(IllegalArgumentException.class, () -> CatalogPurchaseMath.checkedAdd(0, -1));
    }

    @Test
    void aQuantityThatWouldOverflowTheTotalIsRefused() {
        assertThrows(
                IllegalArgumentException.class, () -> CatalogPurchaseMath.checkedPrice(Integer.MAX_VALUE, 2));
        assertThrows(
                IllegalArgumentException.class, () -> CatalogPurchaseMath.checkedPrice(1_000_000, 1_000_000));
        assertThrows(
                IllegalArgumentException.class,
                () -> CatalogPurchaseMath.checkedAdd(Integer.MAX_VALUE, 1));
    }

    @Test
    void anHonestPurchaseStillAddsUp() {
        assertEquals(500, CatalogPurchaseMath.checkedPrice(50, 10));
        assertEquals(0, CatalogPurchaseMath.checkedPrice(0, 99));
        assertEquals(75, CatalogPurchaseMath.checkedAdd(50, 25));
    }

    @Test
    void aSubscriptionLongEnoughToOverflowIsRefused() {
        assertThrows(
                IllegalArgumentException.class,
                () -> CatalogPurchaseMath.checkedSubscriptionSeconds(Integer.MAX_VALUE));
    }

    /**
     * The reservation refuses a negative amount before it reaches the ledger: taking -1000 credits
     * is granting 1000, and it would be committed and published like any other movement.
     */
    @Test
    void theReservationRefusesNegativeAmountsBeforeTouchingTheLedger() {
        assertFalse(CatalogPaymentService.tryTake(null, 10, 0, 0), "no buyer, no payment");
        assertFalse(CatalogPaymentService.tryTake(null, -1000, 0, 0));
    }
}
