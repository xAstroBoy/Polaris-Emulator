package com.eu.habbo.habbohotel.catalog;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/**
 * What a page lets a buyer do, rank by rank.
 *
 * <p>A purchase names a page id and an offer id on the wire. Both are just numbers: a modified
 * client can send any pair it likes, including a staff page it was never shown and an offer id it
 * found by counting upwards. The page's own rank is therefore the only thing standing between a
 * member and a staff-only furni, and it has to hold for every rank, for the gift path as much as
 * the ordinary one.
 */
class CatalogPurchaseAuthorizationTest {

    private static final int RANK_MEMBER = 1;
    private static final int RANK_VIP = 2;
    private static final int RANK_SUPPORT = 4;
    private static final int RANK_ADMIN = 7;

    @Test
    void noRankBelowThePageRankMayBuyFromIt() {
        CatalogPage staffPage = page(RANK_SUPPORT, true, false);

        assertFalse(CatalogPageAccessPolicy.canAccess(staffPage, RANK_MEMBER, false));
        assertFalse(CatalogPageAccessPolicy.canAccess(staffPage, RANK_VIP, false));
        assertFalse(CatalogPageAccessPolicy.canAccess(staffPage, RANK_VIP, true), "club is not a rank");
        assertTrue(CatalogPageAccessPolicy.canAccess(staffPage, RANK_SUPPORT, false));
        assertTrue(CatalogPageAccessPolicy.canAccess(staffPage, RANK_ADMIN, false));
    }

    /**
     * A page taken out of the catalog is not merely hidden from the window: its offers must stop
     * being purchasable, or anyone who noted the ids down keeps buying from it.
     */
    @Test
    void aDisabledPageSellsToNobody() {
        CatalogPage retired = page(RANK_MEMBER, false, false);

        for (int rank : new int[] {RANK_MEMBER, RANK_VIP, RANK_SUPPORT, RANK_ADMIN}) {
            assertFalse(CatalogPageAccessPolicy.canAccess(retired, rank, false), "rank " + rank);
            assertFalse(CatalogPageAccessPolicy.canAccess(retired, rank, true), "rank " + rank + " with club");
        }
    }

    @Test
    void aClubPageSellsOnlyToAClubMember() {
        CatalogPage clubPage = page(RANK_MEMBER, true, true);

        assertFalse(CatalogPageAccessPolicy.canAccess(clubPage, RANK_MEMBER, false));
        assertTrue(CatalogPageAccessPolicy.canAccess(clubPage, RANK_MEMBER, true));
        assertFalse(CatalogPageAccessPolicy.canAccess(clubPage, RANK_ADMIN, false), "rank does not stand in for club");
    }

    @Test
    void anUnknownPageIsNeverPurchasable() {
        assertFalse(CatalogPageAccessPolicy.canAccess(null, RANK_ADMIN, true));
    }

    /**
     * Both purchase paths must consult the policy. The gift path is the one worth naming: it takes a
     * recipient as well, and an offer that cannot be bought for yourself must not become buyable by
     * addressing it to somebody else.
     */
    @Test
    void bothThePlainAndTheGiftPurchaseConsultThePolicy() throws Exception {
        String plain = Files.readString(Path.of(
                "src/main/java/com/eu/habbo/messages/incoming/catalog/CatalogPurchaseApplicationService.java"));
        String gift = Files.readString(
                Path.of("src/main/java/com/eu/habbo/messages/incoming/catalog/CatalogBuyItemAsGiftEvent.java"));

        assertTrue(plain.contains("CatalogPageAccessPolicy.canAccess"), "the ordinary purchase must check the page");
        assertTrue(gift.contains("CatalogPageAccessPolicy.canAccess"), "the gift purchase must check the page too");
    }

    private static CatalogPage page(int rank, boolean enabled, boolean clubOnly) {
        CatalogPage page = mock(CatalogPage.class);

        when(page.getRank()).thenReturn(rank);
        when(page.isEnabled()).thenReturn(enabled);
        when(page.isClubOnly()).thenReturn(clubOnly);

        return page;
    }
}
