package com.eu.habbo.messages.outgoing.users;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.habbohotel.users.HabboInfo;
import com.eu.habbo.habbohotel.users.infostand.InfostandBackgroundManager;
import com.eu.habbo.habbohotel.users.infostand.InfostandBackgroundManager.Category;
import com.eu.habbo.habbohotel.users.infostand.InfostandBackgroundManager.Entry;
import com.eu.habbo.habbohotel.users.infostand.UserLookExtras;
import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.outgoing.MessageComposer;
import com.eu.habbo.messages.outgoing.Outgoing;

import java.util.List;

/**
 * CUSTOM packet 10103: the look library the user may pick from (banners, tiles, overlays, wallpapers,
 * infostand borders, ornaments, name effects, name icons, profile backgrounds) plus the user's current
 * selection. Sent at login with the catalogue, and again without the catalogue after every change so the
 * options windows always mirror what the server stored.
 *
 * <pre>
 * bool hasCatalog
 * [ int categoryCount, per category: int code, int count, per item: int id, string name, int minRank,
 *   int width, int height, int marginTop, string nameColor, bool usable ]
 * int background, int stand, int overlay, int card, int border, int ornament, int nameEffect, int nameIcon,
 * int profileBg, string nameColor, string nameBorder, string avatarString
 * </pre>
 */
public class UserLookCatalogComposer extends MessageComposer {
    private final Habbo habbo;
    private final boolean includeCatalog;

    public UserLookCatalogComposer(Habbo habbo, boolean includeCatalog) {
        this.habbo = habbo;
        this.includeCatalog = includeCatalog;
    }

    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.UserLookCatalogComposer);
        this.response.appendBoolean(this.includeCatalog);

        if (this.includeCatalog) {
            InfostandBackgroundManager manager = Emulator.getGameEnvironment().getInfostandBackgroundManager();
            Category[] categories = Category.values();
            this.response.appendInt(categories.length);

            for (Category category : categories) {
                List<Entry> entries = manager.allEntries(category);
                this.response.appendInt(category.code);
                this.response.appendInt(entries.size());

                for (Entry entry : entries) {
                    this.response.appendInt(entry.id);
                    this.response.appendString(entry.name);
                    this.response.appendInt(entry.minRank);
                    this.response.appendInt(entry.width);
                    this.response.appendInt(entry.height);
                    this.response.appendInt(entry.marginTop);
                    this.response.appendString(entry.nameColor);
                    this.response.appendBoolean(manager.allows(this.habbo, entry));
                }
            }
        }

        HabboInfo info = this.habbo.getHabboInfo();
        UserLookExtras extras = info.getLookExtras();
        String nameColor = this.habbo.getInventory() != null && this.habbo.getInventory().getUserVisualSettingsComponent() != null
                ? this.habbo.getInventory().getUserVisualSettingsComponent().getNameColor()
                : "";

        this.response.appendInt(info.getInfostandBg());
        this.response.appendInt(info.getInfostandStand());
        this.response.appendInt(info.getInfostandOverlay());
        this.response.appendInt(info.getInfostandCardBg());
        this.response.appendInt(info.getInfostandBorder());
        this.response.appendInt(extras.getOrnamentId());
        this.response.appendInt(extras.getNameEffectId());
        this.response.appendInt(extras.getNameIconId());
        this.response.appendInt(extras.getProfileBgId());
        this.response.appendString(nameColor == null ? "" : nameColor);
        this.response.appendString(extras.getNameBorder());
        this.response.appendString(extras.getAvatarString());
        // CUSTOM batch A: preferences (hide ornaments, mention privacy, call privacy)
        this.response.appendInt(extras.isHideOrnaments() ? 1 : 0);
        this.response.appendInt(extras.getMentionPrivacy());
        this.response.appendInt(extras.getCallPrivacy());

        return this.response;
    }
}
