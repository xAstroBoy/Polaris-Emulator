package com.eu.habbo.habbohotel.commands;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.gameclients.GameClient;

import java.util.List;
import java.util.Comparator;
import java.util.ArrayList;

public class CommandsCommand extends Command {
    // :help is an overview, so show every command granted to the caller in
    // one complete alert instead of forcing them through partial pages.
    private static final int COMMANDS_PER_PAGE = Integer.MAX_VALUE;

    public CommandsCommand() {
        super("cmd_commands", Emulator.getTexts().getValue("commands.keys.cmd_commands").split(";"));
    }

    /**
     * The alert is HTML, and the client sanitises it before rendering, so a
     * placeholder like {@code <utente>} in a command description was swallowed
     * as an unknown tag - ":pulisci <utente> si" arrived as ":pulisci  si".
     */
    static String escapeHtml(String text) {
        return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    /**
     * One command's block in the {@code :comandi} alert: a usage line, and under it a line of prose
     * when there is any. A description that starts with a colon is itself the usage line - that is how
     * the hotel writes the ones that take arguments - and in that case the help text is the prose. A
     * description written as prose is the prose, and takes precedence over the help text, which is
     * usually a whole page about a family of commands rather than a line about this one.
     *
     * <p>Lines end with a bare CR: the alert already treats it as a break, and a CRLF here doubled the
     * spacing of a list that is long enough as it is.</p>
     */
    static String formatEntry(String command, String description, String helpText) {
        String usage = description.startsWith(":") ? description : command;
        String prose = description.startsWith(":") ? helpText : (description.isBlank() ? helpText : description);

        StringBuilder entry = new StringBuilder();
        entry.append(oneLine(usage)).append('\r');

        if (!prose.isBlank()) entry.append(oneLine(prose)).append('\r');

        return entry.toString();
    }

    private static String oneLine(String text) {
        return text.replace("\r\n", " ").replace('\r', ' ').replace('\n', ' ').trim();
    }

    @Override
    public boolean handle(GameClient gameClient, String[] params) throws Exception {
        List<Command> commands = new ArrayList<>(Emulator.getGameEnvironment().getCommandHandler()
                .getCommandsForRank(gameClient.getHabbo().getHabboInfo().getRank().getId()));
        commands.sort(Comparator.comparing(command -> command.keys[0], String.CASE_INSENSITIVE_ORDER));

        if (commands.isEmpty()) {
            gameClient.getHabbo().alert(Emulator.getTexts().getValue("commands.generic.cmd_commands.empty"));
            return true;
        }

        int pageCount = Math.max(1, (commands.size() + COMMANDS_PER_PAGE - 1) / COMMANDS_PER_PAGE);
        int page = 1;
        if (params.length >= 2) {
            try {
                page = Integer.parseInt(params[1]);
            } catch (NumberFormatException ignored) {
                page = 0;
            }
        }

        if (page < 1 || page > pageCount) {
            gameClient.getHabbo().alert(Emulator.getTexts()
                    .getValue("commands.error.cmd_commands.invalid_page")
                    .replace("%pages%", Integer.toString(pageCount)));
            return true;
        }

        StringBuilder message = new StringBuilder();
        message.append("<b>")
                .append(Emulator.getTexts().getValue("commands.generic.cmd_commands.text"))
                .append("</b>\r\n")
                .append(Emulator.getTexts().getValue("commands.generic.cmd_commands.page")
                        .replace("%page%", Integer.toString(page))
                        .replace("%pages%", Integer.toString(pageCount))
                        .replace("%count%", Integer.toString(commands.size())))
                .append("\r\n\r\n");

        int from = (page - 1) * COMMANDS_PER_PAGE;
        int to = Math.min(from + COMMANDS_PER_PAGE, commands.size());
        for (Command c : commands.subList(from, to)) {
            String textKey = "commands.description." + c.permission;
            String description = Emulator.getTexts().getValueQuietly(textKey, "");
            String helpKey = "commands.help." + c.permission;
            String helpText = Emulator.getTexts().getValueQuietly(helpKey, "");

            if (description.equals(textKey)) description = "";
            if (helpText.equals(helpKey)) helpText = "";

            message.append(escapeHtml(formatEntry(":" + c.keys[0], description, helpText)));
        }

        if (pageCount > 1) {
            message.append("\r\n")
                    .append(Emulator.getTexts().getValue("commands.generic.cmd_commands.hint"));
        }

        gameClient.getHabbo().alert(message.toString());

        return true;
    }
}
