package com.eu.habbo.architecture;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

/**
 * An interaction alias must never bury a handler that exists.
 *
 * <p>{@code LEGACY_INTERACTION_ALIASES} exists so furni imported with a misspelt or unsupported
 * {@code interaction_type} still load: the alias rewrites the type, and the ones with nothing behind
 * them are parked on {@code default} so the furni is inert instead of fatal. The catch is that
 * {@code resolveItemInteraction} consults the alias map <em>before</em> the configured type, so an
 * alias always wins.
 *
 * <p>That is fine while the alias is the only answer, and wrong the moment a handler arrives. It
 * happened: an audit parked four wired conditions on {@code default}, upstream later shipped
 * {@code WiredConditionFurniOpacityIs}, {@code WiredConditionNotFurniOpacityIs},
 * {@code WiredConditionHabboHasHighscorePoints} and
 * {@code WiredConditionNotHabboHasHighscorePoints}, and nothing connected the two facts. The furni
 * kept loading as inert defaults - bought, placed, and quietly never evaluated. Nothing failed, which
 * is exactly why it went unnoticed.
 *
 * <p>So the rule: for every alias key, if the registry has a real handler under that same key, the
 * alias is stale and must go. Aliases that point at another working type are the normal case and are
 * left alone - only the key itself is checked.
 */
class InteractionAliasContractTest {

    private static final Path ITEM_MANAGER = Path.of("src/main/java/com/eu/habbo/habbohotel/items/ItemManager.java");

    private static final Pattern ALIAS = Pattern.compile("Map\\.entry\\(\"([^\"]+)\",\\s*\"([^\"]+)\"\\)");
    private static final Pattern REGISTRATION =
            Pattern.compile("new ItemInteraction\\(\"([^\"]+)\",\\s*([A-Za-z0-9_]+)\\.class");

    @Test
    void noAliasShadowsARegisteredHandler() throws Exception {
        String source = Files.readString(ITEM_MANAGER);

        Map<String, String> handlers = new LinkedHashMap<>();
        Matcher registration = REGISTRATION.matcher(source);

        while (registration.find()) {
            handlers.put(registration.group(1).toLowerCase(java.util.Locale.ROOT), registration.group(2));
        }

        List<String> shadowed = new ArrayList<>();
        Matcher alias = ALIAS.matcher(aliasBlock(source));

        while (alias.find()) {
            String key = alias.group(1).toLowerCase(java.util.Locale.ROOT);
            String target = alias.group(2).toLowerCase(java.util.Locale.ROOT);
            String handler = handlers.get(key);

            // A handler of InteractionDefault is the registry agreeing with the alias, not a conflict.
            if (handler == null || "InteractionDefault".equals(handler)) continue;

            // The alias may still be pointing at the same class under another spelling, which is the
            // whole point of an alias: wf_cnd_not_battlebz and wf_cnd_not_battlebanzai both register
            // WiredConditionNoBattleBanzaiRunning. Nothing is lost there, so only a different (or
            // absent) destination is a real burial.
            if (handler.equals(handlers.get(target))) continue;

            shadowed.add(key + " -> alias '" + alias.group(2) + "' but " + handler + " is registered for it");
        }

        assertTrue(
                shadowed.isEmpty(),
                "LEGACY_INTERACTION_ALIASES entries hide a working handler, so those furni load inert. "
                        + "Delete the alias so resolveItemInteraction reaches the handler:\n  "
                        + String.join("\n  ", shadowed));
    }

    /** Just the alias map, so unrelated {@code Map.entry} calls elsewhere in the file cannot match. */
    private static String aliasBlock(String source) {
        int start = source.indexOf("LEGACY_INTERACTION_ALIASES = Map.ofEntries");

        assertTrue(start >= 0, "LEGACY_INTERACTION_ALIASES is gone from ItemManager; this test needs rewriting");

        int end = source.indexOf(");", start);

        assertTrue(end > start, "could not find the end of LEGACY_INTERACTION_ALIASES");

        return source.substring(start, end);
    }
}
