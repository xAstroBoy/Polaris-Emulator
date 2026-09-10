package com.eu.habbo.habbohotel.calls;

import com.eu.habbo.Emulator;
import com.google.gson.JsonObject;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.Base64;

/**
 * LiveKit access tokens (JWT, HS256) signed with the API key/secret from the emulator settings
 * (calls.livekit.key / calls.livekit.secret). One token = one identity allowed into one room.
 */
public final class LiveKitTokens {
    private LiveKitTokens() {}

    public static String url() {
        return Emulator.getConfig().getValue("calls.livekit.url", "");
    }

    public static boolean isConfigured() {
        return !url().isBlank() && !Emulator.getConfig().getValue("calls.livekit.key", "").isBlank() && !Emulator.getConfig().getValue("calls.livekit.secret", "").isBlank();
    }

    public static String create(String room, String identity, String displayName) {
        String key = Emulator.getConfig().getValue("calls.livekit.key", "");
        String secret = Emulator.getConfig().getValue("calls.livekit.secret", "");
        int ttl = Math.max(600, Emulator.getConfig().getInt("calls.token.ttl.seconds", 7200));
        long now = System.currentTimeMillis() / 1000L;

        JsonObject header = new JsonObject();
        header.addProperty("alg", "HS256");
        header.addProperty("typ", "JWT");

        JsonObject video = new JsonObject();
        video.addProperty("room", room);
        video.addProperty("roomJoin", true);
        video.addProperty("canPublish", true);
        video.addProperty("canSubscribe", true);
        video.addProperty("canPublishData", true);

        JsonObject payload = new JsonObject();
        payload.addProperty("iss", key);
        payload.addProperty("sub", identity);
        payload.addProperty("name", displayName);
        payload.addProperty("nbf", now - 30);
        payload.addProperty("exp", now + ttl);
        payload.addProperty("jti", identity + "-" + now);
        payload.add("video", video);

        String signingInput = base64Url(header.toString()) + "." + base64Url(payload.toString());
        return signingInput + "." + sign(signingInput, secret);
    }

    private static String base64Url(String value) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }

    private static String sign(String input, String secret) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(mac.doFinal(input.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("HMAC-SHA256 unavailable", e);
        }
    }
}
