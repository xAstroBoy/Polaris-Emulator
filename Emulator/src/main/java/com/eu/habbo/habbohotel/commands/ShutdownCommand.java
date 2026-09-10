package com.eu.habbo.habbohotel.commands;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.gameclients.GameClient;
import com.eu.habbo.habbohotel.rooms.RoomTrade;
import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.outgoing.generic.alerts.GenericAlertComposer;
import com.eu.habbo.messages.outgoing.generic.alerts.HotelWillCloseInMinutesComposer;
import com.eu.habbo.threading.runnables.ShutdownEmulator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ShutdownCommand extends Command {
    private static final Logger LOGGER = LoggerFactory.getLogger(ShutdownCommand.class);

    public ShutdownCommand() {
        super("cmd_shutdown", Emulator.getTexts().getValue("commands.keys.cmd_shutdown").split(";"));
    }

    @Override
    public boolean handle(GameClient gameClient, String[] params) throws Exception {
        // params[1] is the delay in minutes whenever it parses as a number, and any reason follows it.
        // The delay used to be read only when nothing followed it, so ":shutdown 10 maintenance" fell
        // through with minutes still 0 and closed the hotel instantly instead of in ten minutes - the
        // countdown an admin typed was silently dropped, and "10" ended up quoted back inside the reason.
        int minutes = 0;
        int reasonStart = 1;

        if (params.length > 1) {
            try {
                minutes = Integer.parseInt(params[1]);
                reasonStart = 2;
            } catch (NumberFormatException ignored) {
                // No delay given, so the whole tail is the reason and the hotel closes immediately.
            }
        }

        StringBuilder reason = new StringBuilder("-");
        if (reasonStart < params.length) {
            reason = new StringBuilder();
            for (int i = reasonStart; i < params.length; i++) {
                if (i > reasonStart) {
                    reason.append(" ");
                }
                reason.append(params[i]);
            }
        }

        // ConsoleShutdownCommand calls this with a null client, and the alert below dereferences it.
        String requestedBy = "server console";
        String requestedByDetail = requestedBy;
        if (gameClient != null && gameClient.getHabbo() != null) {
            requestedBy = gameClient.getHabbo().getHabboInfo().getUsername();
            requestedByDetail = requestedBy + " (id=" + gameClient.getHabbo().getHabboInfo().getId() + ")";
        }

        // The emulator leaves through Runtime.exit(0), which systemd cannot tell apart from a clean stop,
        // so the service log is the only place a shutdown can be traced back to once chat history is gone.
        LOGGER.warn(
                "Hotel shutdown requested by {} - closing in {} minute(s). Reason: {}",
                requestedByDetail,
                minutes,
                reason);

        ServerMessage message;
        if (!reason.toString().equals("-")) {
            message = new GenericAlertComposer("<b>" + Emulator.getTexts().getValue("generic.warning") + "</b> \r\n" +
                    Emulator.getTexts().getValue("generic.shutdown").replace("%minutes%", minutes + "") + "\r\n" +
                    Emulator.getTexts().getValue("generic.reason.specified") + ": <b>" + reason + "</b>\r" +
                    "\r" +
                    "- " + requestedBy).compose();
        } else {
            message = new HotelWillCloseInMinutesComposer(minutes).compose();
        }
        RoomTrade.TRADING_ENABLED = false;
        ShutdownEmulator.timestamp = Emulator.getIntUnixTimestamp() + (60 * minutes);
        Emulator.getThreading().run(new ShutdownEmulator(message), (long) minutes * 60 * 1000);
        return true;
    }
}
