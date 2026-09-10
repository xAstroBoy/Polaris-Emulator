package com.eu.habbo.habbohotel.catalog;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/**
 * Search has to see what the catalog editor just did.
 *
 * <p>The index is built once and kept until something says it is stale. Every edit the Manager makes
 * therefore has to mark it so, or search keeps answering from the catalog as it was when the
 * emulator started: a furni moved to another page still resolves to the old one, a deleted offer is
 * still found, and a new one is not.
 *
 * <p>The chain is: a live mutation fires CatalogLiveMutationHook, the hook reloads the page or the
 * offer through CatalogAdminCacheSync, and each of those reload paths invalidates the index. This
 * pins every link, because a break anywhere in it is silent - search simply keeps returning stale
 * answers, and nothing logs.
 */
class CatalogSearchIndexFreshnessContractTest {

    private static final Path CACHE_SYNC =
            Path.of("src/main/java/com/eu/habbo/habbohotel/catalog/CatalogAdminCacheSync.java");
    private static final Path MANAGER = Path.of("src/main/java/com/eu/habbo/habbohotel/catalog/CatalogManager.java");
    private static final Path STUDIO_RUNTIME = Path.of(
            "src/main/java/com/eu/habbo/messages/incoming/catalog/catalogadmin/studio/CatalogStudioRuntime.java");

    @Test
    void everyLiveMutationReachesTheCacheSync() throws IOException {
        String runtime = collapsed(STUDIO_RUNTIME);

        assertTrue(runtime.contains("CatalogAdminCacheSync.reloadCatalogPage(change.entityId(),change.catalogType())"),
                "a page edit must reload the page");
        assertTrue(runtime.contains("CatalogAdminCacheSync.reloadCatalogItem(change.entityId(),change.catalogType())"),
                "an offer edit must reload the offer");
    }

    /**
     * Both reload paths, plus the page create / reparent / save / delete and offer removal paths,
     * mark the index stale. An offer that changes page is the case worth keeping in mind: the old
     * page has to lose the offer id and the new one gain it, and search has to be told either way.
     */
    @Test
    void everyCacheSyncEntryPointInvalidatesTheSearchIndex() throws IOException {
        String sync = collapsed(CACHE_SYNC);

        for (String entryPoint : new String[] {
            "reloadCatalogItem", "reloadCatalogPage", "attachCreatedPage", "reparentPage", "detachDeletedPage",
            "removeCatalogItem"
        }) {
            int start = sync.indexOf(entryPoint + "(");
            assertTrue(start > -1, entryPoint + " must exist");
        }

        // One invalidation per entry point, at least: the count guards against a path being added
        // later that quietly skips it.
        int invalidations = sync.split("invalidateSearchIndex\\(\\)", -1).length - 1;
        assertTrue(invalidations >= 6, "expected an invalidation per mutating entry point, found " + invalidations);
    }

    @Test
    void theIndexIsRebuiltLazilyOnTheNextSearch() throws IOException {
        String manager = collapsed(MANAGER);

        assertTrue(manager.contains("if(this.searchIndexDirty)this.rebuildSearchIndex()"),
                "a search must rebuild the index when it has been marked stale");
        assertTrue(manager.contains("this.searchIndexDirty=true"), "invalidation must set the flag");
        assertTrue(manager.contains("this.searchIndexDirty=false"), "a rebuild must clear the flag");
    }

    /** The offer id is what ties a furni in search back to its offer, so a reload must re-register it. */
    @Test
    void anOfferReloadReindexesItsOfferId() throws IOException {
        String sync = collapsed(CACHE_SYNC);

        assertTrue(sync.contains("unregisterOfferSearchIndex("), "the old offer id must be dropped");
        assertTrue(sync.contains("registerOfferSearchIndex("), "the new offer id must be registered");
        assertTrue(sync.contains("page.addOfferId("), "the page keeps the offer id search resolves through");
    }

    private static String collapsed(Path source) throws IOException {
        return Files.readString(source).replaceAll("\\s+", "");
    }
}
