package com.eu.habbo.habbohotel.calls;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.messages.outgoing.friends.CallIncomingComposer;
import com.eu.habbo.messages.outgoing.friends.CallStateComposer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * HSmile-style friend calls (voice / video / screen share). The emulator only does the signalling: who is calling
 * whom, ringing, accept / decline / end, and the LiveKit room + tokens both sides use once the call is accepted.
 * Media never touches the emulator.
 *
 * Rules: both must be friends, the callee must be online and allow calls (user_look_extras.call_privacy 0), and
 * neither side may already be in a call. An unanswered call is "missed" after calls.ring.seconds (45).
 */
public final class CallManager {
    private static final Logger LOGGER = LoggerFactory.getLogger(CallManager.class);

    public static final String RINGING = "ringing";
    public static final String CONNECTED = "connected";
    public static final String DECLINED = "declined";
    public static final String ENDED = "ended";
    public static final String MISSED = "missed";
    public static final String BUSY = "busy";
    public static final String UNAVAILABLE = "unavailable";
    public static final String FAILED = "failed";

    public static final class Call {
        public final int id;
        public final int callerId;
        public final int calleeId;
        public final boolean video;
        public final long createdAt = System.currentTimeMillis();
        public volatile String state = RINGING;

        Call(int id, int callerId, int calleeId, boolean video) {
            this.id = id;
            this.callerId = callerId;
            this.calleeId = calleeId;
            this.video = video;
        }

        public int peerOf(int userId) {
            return userId == this.callerId ? this.calleeId : this.callerId;
        }

        public boolean involves(int userId) {
            return userId == this.callerId || userId == this.calleeId;
        }

        public String roomName() {
            return "call-" + this.id;
        }
    }

    private static final AtomicInteger IDS = new AtomicInteger(1);
    private static final Map<Integer, Call> CALLS = new ConcurrentHashMap<>();
    private static final Map<Integer, Integer> BY_USER = new ConcurrentHashMap<>();

    private CallManager() {}

    public static boolean isEnabled() {
        return Emulator.getConfig().getBoolean("calls.enabled", true) && LiveKitTokens.isConfigured();
    }

    public static Call getActiveCall(int userId) {
        Integer callId = BY_USER.get(userId);
        return callId == null ? null : CALLS.get(callId);
    }

    /** Starts ringing the target. The caller is told the outcome through CallStateComposer in every case. */
    public static synchronized void start(Habbo caller, int targetId, boolean video) {
        if (caller == null) return;
        int callerId = caller.getHabboInfo().getId();

        if (!isEnabled()) {
            sendState(caller, 0, UNAVAILABLE, targetId, "", "", video, "disabled");
            return;
        }
        if (targetId <= 0 || targetId == callerId) return;

        Habbo target = Emulator.getGameEnvironment().getHabboManager().getHabbo(targetId);
        boolean friends = caller.getMessenger() != null && caller.getMessenger().getFriends().containsKey(targetId);

        if (!friends) {
            sendState(caller, 0, UNAVAILABLE, targetId, "", "", video, "not_friends");
            return;
        }
        if (target == null || !target.isOnline() || target.getClient() == null) {
            sendState(caller, 0, UNAVAILABLE, targetId, "", "", video, "offline");
            return;
        }
        if (target.getHabboInfo().getLookExtras().getCallPrivacy() != 0) {
            sendState(caller, 0, UNAVAILABLE, targetId, target.getHabboInfo().getUsername(), target.getHabboInfo().getLook(), video, "privacy");
            return;
        }
        if (getActiveCall(callerId) != null) {
            sendState(caller, 0, BUSY, targetId, target.getHabboInfo().getUsername(), target.getHabboInfo().getLook(), video, "self_busy");
            return;
        }
        if (getActiveCall(targetId) != null) {
            sendState(caller, 0, BUSY, targetId, target.getHabboInfo().getUsername(), target.getHabboInfo().getLook(), video, "peer_busy");
            return;
        }

        Call call = new Call(IDS.getAndIncrement(), callerId, targetId, video);
        CALLS.put(call.id, call);
        BY_USER.put(callerId, call.id);
        BY_USER.put(targetId, call.id);

        target.getClient().sendResponse(new CallIncomingComposer(call.id, callerId, caller.getHabboInfo().getUsername(), caller.getHabboInfo().getLook(), video));
        sendState(caller, call.id, RINGING, targetId, target.getHabboInfo().getUsername(), target.getHabboInfo().getLook(), video, "");

        int ringSeconds = Math.max(10, Math.min(120, Emulator.getConfig().getInt("calls.ring.seconds", 45)));
        Emulator.getThreading().run(() -> timeout(call.id), ringSeconds * 1000L);
    }

    private static synchronized void timeout(int callId) {
        Call call = CALLS.get(callId);
        if (call == null || !RINGING.equals(call.state)) return;

        finish(call, MISSED, "timeout");
    }

    /** The callee accepts or declines. On accept both sides receive the LiveKit room and their token. */
    public static synchronized void answer(Habbo habbo, int callId, boolean accept) {
        if (habbo == null) return;

        Call call = CALLS.get(callId);
        if (call == null || call.calleeId != habbo.getHabboInfo().getId() || !RINGING.equals(call.state)) return;

        if (!accept) {
            finish(call, DECLINED, "declined");
            return;
        }

        Habbo caller = Emulator.getGameEnvironment().getHabboManager().getHabbo(call.callerId);
        if (caller == null || caller.getClient() == null) {
            finish(call, FAILED, "caller_left");
            return;
        }

        call.state = CONNECTED;

        String url = LiveKitTokens.url();
        String room = call.roomName();

        try {
            String callerToken = LiveKitTokens.create(room, String.valueOf(call.callerId), caller.getHabboInfo().getUsername());
            String calleeToken = LiveKitTokens.create(room, String.valueOf(call.calleeId), habbo.getHabboInfo().getUsername());

            caller.getClient().sendResponse(new CallStateComposer(call.id, CONNECTED, call.calleeId, habbo.getHabboInfo().getUsername(), habbo.getHabboInfo().getLook(), call.video, url, callerToken, room, ""));
            habbo.getClient().sendResponse(new CallStateComposer(call.id, CONNECTED, call.callerId, caller.getHabboInfo().getUsername(), caller.getHabboInfo().getLook(), call.video, url, calleeToken, room, ""));
        } catch (RuntimeException e) {
            LOGGER.error("Failed to issue call tokens", e);
            finish(call, FAILED, "token");
        }
    }

    /** Either side hangs up (ringing or connected). */
    public static synchronized void end(Habbo habbo, int callId) {
        if (habbo == null) return;

        Call call = CALLS.get(callId);
        if (call == null || !call.involves(habbo.getHabboInfo().getId())) return;

        finish(call, RINGING.equals(call.state) && habbo.getHabboInfo().getId() == call.calleeId ? DECLINED : ENDED, "hangup");
    }

    /** A player disconnected: any call they were in ends. */
    public static synchronized void onDisconnect(int userId) {
        Call call = getActiveCall(userId);
        if (call != null) finish(call, ENDED, "disconnect");
    }

    private static void finish(Call call, String state, String reason) {
        call.state = state;
        CALLS.remove(call.id);
        BY_USER.remove(call.callerId, call.id);
        BY_USER.remove(call.calleeId, call.id);

        Habbo caller = Emulator.getGameEnvironment().getHabboManager().getHabbo(call.callerId);
        Habbo callee = Emulator.getGameEnvironment().getHabboManager().getHabbo(call.calleeId);

        if (caller != null && caller.getClient() != null) {
            sendState(caller, call.id, state, call.calleeId, callee == null ? "" : callee.getHabboInfo().getUsername(), callee == null ? "" : callee.getHabboInfo().getLook(), call.video, reason);
        }
        if (callee != null && callee.getClient() != null) {
            sendState(callee, call.id, state, call.callerId, caller == null ? "" : caller.getHabboInfo().getUsername(), caller == null ? "" : caller.getHabboInfo().getLook(), call.video, reason);
        }
    }

    private static void sendState(Habbo habbo, int callId, String state, int peerId, String peerName, String peerLook, boolean video, String reason) {
        if (habbo == null || habbo.getClient() == null) return;
        habbo.getClient().sendResponse(new CallStateComposer(callId, state, peerId, peerName, peerLook, video, "", "", "", reason));
    }
}
