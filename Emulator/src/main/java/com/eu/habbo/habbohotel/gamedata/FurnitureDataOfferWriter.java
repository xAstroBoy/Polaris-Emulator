package com.eu.habbo.habbohotel.gamedata;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.catalog.CatalogItem;
import com.eu.habbo.habbohotel.catalog.CatalogManager;
import com.eu.habbo.habbohotel.catalog.CatalogPage;
import com.eu.habbo.habbohotel.items.FurnitureTextProvider;
import com.eu.habbo.habbohotel.items.Item;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.ReentrantLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Keeps the {@code offerid} of the client's FurnitureData.json pointing at the offer that actually sells
 * each furni.
 *
 * The catalogue search in Nitro runs entirely client side: it starts from FurnitureData, reads a furni's
 * {@code offerid} and looks it up among the offers the catalogue tree sent. The id the emulator serialises is
 * {@code catalog_items.id} (see {@link com.eu.habbo.habbohotel.catalog.CatalogItem#getSearchOfferId}), so as soon
 * as an offer is created, moved or deleted the file goes stale and those furni stop being findable even though the
 * catalogue itself is perfectly fine.
 *
 * Every catalogue edit queues its furni here and the file is rewritten shortly after, so a bulk edit of hundreds of
 * offers still costs a single pass. The rewrite is a linear scan over the raw text - the document is never parsed
 * into a tree, because 86k entries would cost hundreds of megabytes on a live hotel - and lands atomically, since
 * clients download the file while we are replacing it.
 */
public final class FurnitureDataOfferWriter {
    private static final Logger LOGGER = LoggerFactory.getLogger(FurnitureDataOfferWriter.class);
    private static final String FILE_NAME = "FurnitureData.json";
    private static final ReentrantLock LOCK = new ReentrantLock();

    /** Sprite ids whose selling offer has to be worked out again. */
    private static final Set<Integer> PENDING = ConcurrentHashMap.newKeySet();

    private static final AtomicBoolean FLUSH_SCHEDULED = new AtomicBoolean(false);

    /**
     * Il catalogo con cui risolvere le offerte.
     *
     * Durante l avvio il CatalogManager sta ancora costruendo se stesso e non e ancora appeso al
     * GameEnvironment: chiederlo da li tornerebbe null proprio nel momento in cui serve.
     */
    private static volatile CatalogManager manager;

    private FurnitureDataOfferWriter() {}

    private static boolean enabled() {
        return Emulator.getConfig() == null || Emulator.getConfig().getBoolean("furnidata.offerid.sync", true);
    }

    private static long flushDelay() {
        if (Emulator.getConfig() == null) return 1500L;
        return Math.max(200L, Emulator.getConfig().getInt("furnidata.offerid.sync.delay", 1500));
    }

    /**
     * The furnidata the client is served: {@code furnidata.offerid.path} when configured, otherwise the source the
     * furniture text provider already resolved, otherwise the first FurnitureData.json above it.
     */
    public static Path resolvePath() {
        String configured = Emulator.getConfig() == null
                ? ""
                : Emulator.getConfig().getValue("furnidata.offerid.path", "");
        if (configured != null && !configured.isBlank()) return Path.of(configured.trim());

        FurnitureTextProvider provider = Emulator.getGameEnvironment() == null
                ? null
                : Emulator.getGameEnvironment().getFurnitureTextProvider();
        if (provider == null || provider.getSource() == null) return null;

        Path source = provider.getSource().toAbsolutePath();
        if (!provider.isSourceDirectory() && source.getFileName().toString().equalsIgnoreCase(FILE_NAME)) {
            return source;
        }

        Path directory = provider.isSourceDirectory() ? source : source.getParent();
        for (Path current = directory; current != null; current = current.getParent()) {
            Path candidate = current.resolve(FILE_NAME);
            if (Files.exists(candidate)) return candidate;
        }

        return null;
    }

    /**
     * Marks every furni of {@code item} as needing its {@code offerid} worked out again.
     *
     * The offer itself is not recorded: the same furni can be sold on several pages, so deleting one offer does not
     * mean the furni became unbuyable. What sells it is decided at write time, when the catalogue is settled again.
     */
    public static void queue(CatalogItem item) {
        if (!enabled() || item == null) return;

        for (Item base : item.getBaseItems()) {
            if (base != null && base.getSpriteId() > 0) PENDING.add(base.getSpriteId());
        }

        if (!PENDING.isEmpty()) scheduleFlush();
    }

    /**
     * Marks every furni of the catalogue for a full check.
     *
     * Needed after a catalogue reload (startup, RCON updatecatalog): that path rebuilds the offers straight from
     * the database without going through the editor, so nothing else would notice that the ids moved - which is
     * exactly how the file went stale after the offers were renumbered.
     */
    public static void queueAll(CatalogManager catalogManager) {
        if (!enabled() || catalogManager == null) return;

        manager = catalogManager;

        for (CatalogPage page : catalogManager.catalogPages.values()) {
            if (page == null) continue;

            for (CatalogItem item : page.getCatalogItems().values()) {
                for (Item base : item.getBaseItems()) {
                    if (base != null && base.getSpriteId() > 0) PENDING.add(base.getSpriteId());
                }
            }
        }

        if (!PENDING.isEmpty()) scheduleFlush();
    }

    private static void scheduleFlush() {
        if (!FLUSH_SCHEDULED.compareAndSet(false, true)) return;

        try {
            Emulator.getThreading().run(FurnitureDataOfferWriter::flushNow, flushDelay());
        } catch (Exception exception) {
            // No pool yet (startup / shutdown): write inline rather than losing the change.
            FLUSH_SCHEDULED.set(false);
            flushNow();
        }
    }

    /** Applies everything queued so far. Safe to call from anywhere; does nothing when there is nothing to do. */
    public static void flushNow() {
        FLUSH_SCHEDULED.set(false);

        if (PENDING.isEmpty()) return;

        LOCK.lock();
        try {
            Set<Integer> sprites = new HashSet<>(PENDING);
            PENDING.removeAll(sprites);
            Map<Integer, Integer> batch = resolveOffers(sprites);

            Path target = resolvePath();
            if (target == null) {
                LOGGER.warn("[furnidata] {} not found: set furnidata.offerid.path to keep catalogue search working",
                        FILE_NAME);
                return;
            }

            String content = Files.readString(target, StandardCharsets.UTF_8);
            Rewrite rewrite = rewrite(content, batch);

            if (rewrite.changed == 0) return;

            atomicWrite(target, rewrite.text);
            LOGGER.info("[furnidata] offerid aligned for {} furni", rewrite.changed);
        } catch (IOException exception) {
            LOGGER.error("[furnidata] could not update {}", FILE_NAME, exception);
        } finally {
            LOCK.unlock();
        }
    }

    private record Rewrite(String text, int changed) {}

    /**
     * The offer that sells each of these furni right now, or -1 when none does.
     *
     * Among several offers the most reachable one wins - lowest rank first, then a page that is actually visible,
     * then the smallest id - so search sends a player to the page they can really open. One pass over the
     * catalogue covers the whole batch, however many furni were touched.
     */
    private static Map<Integer, Integer> resolveOffers(Set<Integer> sprites) {
        Map<Integer, Integer> best = new HashMap<>();
        Map<Integer, long[]> score = new HashMap<>();

        for (Integer sprite : sprites) best.put(sprite, -1);

        CatalogManager catalogManager = manager;
        if (catalogManager == null && Emulator.getGameEnvironment() != null) {
            catalogManager = Emulator.getGameEnvironment().getCatalogManager();
        }
        if (catalogManager == null) return best;

        for (CatalogPage page : catalogManager.catalogPages.values()) {
            if (page == null) continue;

            long rank = page.getRank();
            long hidden = (page.isVisible() && page.isEnabled()) ? 0 : 1;

            for (CatalogItem item : page.getCatalogItems().values()) {
                int offerId = item.getSearchOfferId();
                if (offerId < 0) continue;

                for (Item base : item.getBaseItems()) {
                    if (base == null) continue;

                    int sprite = base.getSpriteId();
                    if (sprite <= 0 || !sprites.contains(sprite)) continue;

                    long[] candidate = {rank, hidden, offerId};
                    long[] current = score.get(sprite);

                    if (current == null || less(candidate, current)) {
                        score.put(sprite, candidate);
                        best.put(sprite, offerId);
                    }
                }
            }
        }

        return best;
    }

    private static boolean less(long[] a, long[] b) {
        for (int i = 0; i < a.length; i++) {
            if (a[i] != b[i]) return a[i] < b[i];
        }
        return false;
    }

    /**
     * One linear pass over the document, rewriting the {@code offerid} of every furni in {@code wanted}.
     *
     * Only the elements of the {@code furnitype} arrays are touched: brace matching from the first {@code &#123;}
     * would grab the whole document as a single object, and then only the first furni in the file would ever be
     * corrected. Everything outside those arrays is copied through untouched, so formatting stays as it was.
     */
    static Rewrite rewrite(String content, Map<Integer, Integer> wanted) {
        StringBuilder out = new StringBuilder(content.length() + 64);
        String marker = "\"furnitype\"";
        int changed = 0;
        int i = 0;

        while (i < content.length()) {
            int at = content.indexOf(marker, i);
            if (at < 0) break;

            int open = content.indexOf('[', at + marker.length());
            if (open < 0) break;

            out.append(content, i, open + 1);
            i = open + 1;

            // gli elementi dell'array: ognuno e' un furni, e la loro fine chiude l'array
            while (i < content.length()) {
                char c = content.charAt(i);

                if (c == ']') break;

                if (c != '{') {
                    out.append(c);
                    i++;
                    continue;
                }

                int end = objectEnd(content, i);
                if (end < 0) break;

                String object = content.substring(i, end);
                int id = readInt(object, "\"id\":");
                Integer offer = id > 0 ? wanted.get(id) : null;
                String edited = offer == null ? null : replaceInt(object, "\"offerid\":", offer);

                if (edited == null) {
                    out.append(object);
                } else {
                    out.append(edited);
                    changed++;
                }

                i = end;
            }
        }

        out.append(content, i, content.length());
        return new Rewrite(out.toString(), changed);
    }

    /** Index just past the object that starts at {@code start}, ignoring braces inside strings. */
    private static int objectEnd(String content, int start) {
        int depth = 0;
        boolean inString = false;
        boolean escaped = false;

        for (int i = start; i < content.length(); i++) {
            char c = content.charAt(i);

            if (inString) {
                if (escaped) escaped = false;
                else if (c == '\\') escaped = true;
                else if (c == '"') inString = false;
                continue;
            }

            if (c == '"') inString = true;
            else if (c == '{') depth++;
            else if (c == '}' && --depth == 0) return i + 1;
        }

        return -1;
    }

    /** The integer right after {@code key} in this object, or -1. Nested objects are not searched. */
    private static int readInt(String object, String key) {
        int at = object.indexOf(key);
        if (at < 0) return -1;

        int from = at + key.length();
        int to = from;
        while (to < object.length() && (Character.isDigit(object.charAt(to)) || (to == from && object.charAt(to) == '-'))) {
            to++;
        }

        if (to == from) return -1;

        try {
            return Integer.parseInt(object.substring(from, to));
        } catch (NumberFormatException exception) {
            return -1;
        }
    }

    /** The object with {@code key}'s number replaced, or null when the key is not there. */
    private static String replaceInt(String object, String key, int value) {
        int at = object.indexOf(key);
        if (at < 0) return null;

        int from = at + key.length();
        int to = from;
        if (to < object.length() && object.charAt(to) == '-') to++;
        while (to < object.length() && Character.isDigit(object.charAt(to))) to++;

        if (to == from) return null;
        if (object.substring(from, to).equals(Integer.toString(value))) return null;

        return object.substring(0, from) + value + object.substring(to);
    }

    private static void atomicWrite(Path target, String content) throws IOException {
        Path temporary = target.resolveSibling(target.getFileName() + ".tmp");
        Files.writeString(temporary, content, StandardCharsets.UTF_8);

        try {
            Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException exception) {
            Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }
}
