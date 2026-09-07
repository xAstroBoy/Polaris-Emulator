package com.eu.habbo.threading.runnables;

import com.eu.habbo.Emulator;
import com.eu.habbo.messages.ServerMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ShutdownEmulator implements Runnable {
    private static final Logger LOGGER = LoggerFactory.getLogger(ShutdownEmulator.class);

    public static boolean instantiated = false;
    public static int timestamp = 0;

    public ShutdownEmulator(ServerMessage message) {
        if (!instantiated) {
            instantiated = true;

            if (message != null) {
                Emulator.getGameServer().getGameClientManager().sendBroadcastResponse(message);
            }
        }
    }

    @Override
    public void run() {
        // This is the only Runtime.exit in the emulator. It exits 0, so systemd reads it as a successful
        // stop and Restart=on-failure will not bring the hotel back - say so plainly before going down,
        // otherwise the journal shows nothing but the shutdown hook and the cause looks like a crash.
        LOGGER.warn("Hotel shutdown running now - exiting with status 0. The service will not auto-restart.");
        Emulator.getRuntime().exit(0);
    }
}
