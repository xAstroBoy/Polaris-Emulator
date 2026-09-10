package com.eu.habbo.habbohotel.commands;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class OnlineCommandContractTest {
    /**
     * ":online" is its own command rather than a branch of ":about", and it opens the searchable
     * online-users window. It used to build the list itself and print it as an alert; the window
     * replaced that, so the list now lives in OnlineUsersComposer and this only checks the wiring.
     */
    @Test
    void onlineHasItsOwnPublicCommandAndOpensTheOnlineUsersWindow() throws Exception {
        String about = Files.readString(Path.of(
                "src/main/java/com/eu/habbo/habbohotel/commands/AboutCommand.java"));
        String online = Files.readString(Path.of(
                "src/main/java/com/eu/habbo/habbohotel/commands/OnlineCommand.java"));
        String handler = Files.readString(Path.of(
                "src/main/java/com/eu/habbo/habbohotel/commands/CommandHandler.java"));

        assertFalse(about.contains("\"online\""));
        assertTrue(online.contains("super(\"cmd_online\", new String[] {\"online\""));
        assertTrue(online.contains("new OnlineUsersComposer(gameClient.getHabbo())"));
        assertTrue(handler.contains("addCommand(new OnlineCommand())"));
    }

    /** The window's own list: every online user, sorted by name, each with the room they are in. */
    @Test
    void theOnlineUsersComposerBuildsTheLiveList() throws Exception {
        String composer = Files.readString(Path.of(
                        "src/main/java/com/eu/habbo/messages/outgoing/users/OnlineUsersComposer.java"))
                .replaceAll("\\s+", " ");

        assertTrue(composer.contains("getOnlineHabbos()"));
        assertTrue(composer.contains("String.CASE_INSENSITIVE_ORDER"));
        assertTrue(composer.contains("getCurrentRoom()"));
    }
}
