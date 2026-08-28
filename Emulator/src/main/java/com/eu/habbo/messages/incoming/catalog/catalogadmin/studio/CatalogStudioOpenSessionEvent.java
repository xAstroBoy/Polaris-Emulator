package com.eu.habbo.messages.incoming.catalog.catalogadmin.studio;

import com.eu.habbo.habbohotel.catalog.versioning.CatalogStudioSessionState;
import com.eu.habbo.messages.outgoing.catalog.catalogadmin.studio.CatalogStudioPublishedVersion;
import com.eu.habbo.messages.outgoing.catalog.catalogadmin.studio.CatalogStudioSessionComposer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class CatalogStudioOpenSessionEvent extends CatalogStudioEvent {
    private static final Logger LOGGER = LoggerFactory.getLogger(CatalogStudioOpenSessionEvent.class);

    @Override
    public void handle() {
        if (!authorize()) return;

        // Loading ~90k offers and composing a multi-megabyte response must never
        // occupy a GamePacketHandler thread: that stalls room joins/login packets
        // for every connection assigned to the same executor.
        com.eu.habbo.Emulator.getThreading().run(this::openSession);
    }

    private void openSession() {
        try {
            CatalogStudioSessionState state = studio().queries().loadSession();
            var live = studio().liveMutations().loadLive();

            var composer = new CatalogStudioSessionComposer(
                    live.version().id(),
                    live.version().id(),
                    live.version().revision(),
                    state.activeUpdatedAt(),
                    live.version().createdAt(),
                    0,
                    java.util.List.of(),
                    true,
                    0,
                    state.publishedVersions().stream()
                            .map(version ->
                                    new CatalogStudioPublishedVersion(
                                            version.id(),
                                            version.label(),
                                            version.publishedAt()))
                            .toList(),
                    live.pages(),
                    live.offers());

            var response = composer.compose();
            if (this.client.getChannel().isActive()) this.client.sendResponse(response);

        } catch (RuntimeException exception) {
            LOGGER.error("[CatalogStudio] session open FAILED", exception);
        }
    }
}
