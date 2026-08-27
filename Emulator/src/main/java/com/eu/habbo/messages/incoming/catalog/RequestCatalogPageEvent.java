package com.eu.habbo.messages.incoming.catalog;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.catalog.CatalogPage;
import com.eu.habbo.habbohotel.catalog.CatalogPageType;
import com.eu.habbo.habbohotel.modtool.ScripterManager;
import com.eu.habbo.messages.incoming.MessageHandler;
import com.eu.habbo.messages.outgoing.catalog.CatalogPageComposer;

import java.util.Comparator;

public class RequestCatalogPageEvent extends MessageHandler {

    @Override
    public void handle() throws Exception {
        int catalogPageId = this.packet.readInt();
        int offerId = this.packet.readInt();
        String mode = this.packet.readString();
        CatalogPageType requestedType = CatalogPageType.fromString(mode);

        CatalogPage page = Emulator.getGameEnvironment().getCatalogManager().getCatalogPage(catalogPageId, requestedType);

        if (catalogPageId > 0 && page != null) {
            if (page.getRank() <= this.client.getHabbo().getHabboInfo().getRank().getId() && page.isEnabled()) {
                CatalogPage contentPage = firstViewerLeaf(page);
                this.client.sendResponse(new CatalogPageComposer(
                        contentPage, this.client.getHabbo(), offerId, mode, catalogPageId));
            } else {
                if (!page.isVisible()) {
                    ScripterManager.scripterDetected(this.client, Emulator.getTexts().getValue("scripter.warning.catalog.page").replace("%username%", this.client.getHabbo().getHabboInfo().getUsername()).replace("%pagename%", page.getCaption()));
                }
            }
        }
    }

    /**
     * Xabbo's Furni Viewer exposes category branches, but answers a click on a branch with its
     * first real leaf. Mirror that behaviour for database-backed BSS viewer pages so a category
     * never opens as an empty catalogue page.
     */
    private CatalogPage firstViewerLeaf(CatalogPage page) {
        if (!page.getPageName().startsWith("bss_viewer") || !page.getCatalogItems().isEmpty()) {
            return page;
        }

        return page.getChildPages().values().stream()
                .filter(CatalogPage::isVisible)
                .filter(CatalogPage::isEnabled)
                .filter(child -> child.getRank() <= this.client.getHabbo().getHabboInfo().getRank().getId())
                .sorted(Comparator.naturalOrder())
                .map(this::firstViewerLeaf)
                .filter(child -> !child.getCatalogItems().isEmpty())
                .findFirst()
                .orElse(page);
    }
}
