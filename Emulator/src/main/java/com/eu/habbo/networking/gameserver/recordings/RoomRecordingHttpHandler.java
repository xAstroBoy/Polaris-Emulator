package com.eu.habbo.networking.gameserver.recordings;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.permissions.Permission;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.networking.gameserver.GameServerAttributes;
import com.eu.habbo.networking.gameserver.auth.AccessTokenService;
import com.eu.habbo.networking.gameserver.auth.AuthRateLimiter;
import com.eu.habbo.networking.gameserver.auth.CorsOriginGate;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.handler.codec.http.*;
import io.netty.util.ReferenceCountUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.security.SecureRandom;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

/**
 * HSmile "registra la stanza": the client records its own room canvas (MediaRecorder) and uploads the video here in
 * chunks that stay under the aggregator's 500 KB body cap. Served under /api/chat/recordings because the gateway
 * only forwards a fixed set of /api prefixes to the emulator.
 *
 * POST   {base}/start            (bearer) → {uploadId}
 * POST   {base}/chunk            (bearer) headers X-Upload-Id, X-Upload-Index; body = the chunk bytes
 * POST   {base}/finish           (bearer) headers X-Upload-Id, X-Seconds, X-Room-Id → the recording row
 * GET    {base}                  (bearer) → {recordings:[…]} the caller's recordings
 * GET    {base}/{id}             public video stream with Range support
 * DELETE {base}/{id}             (bearer) owner or staff
 *
 * Files live under `recordings.path` (default data/recordings). Limits: recordings.max.seconds (300),
 * recordings.max.mb (80), recordings.max.per.user (12), recordings.cooldown.seconds (300), recordings.max.storage.gb (20).
 */
public class RoomRecordingHttpHandler extends ChannelInboundHandlerAdapter {
    private static final Logger LOGGER = LoggerFactory.getLogger(RoomRecordingHttpHandler.class);

    public static final String BASE_PATH = "/api/chat/recordings";

    private static final int MAX_CHUNK_BYTES = 480 * 1024;
    private static final Pattern ID_PATTERN = Pattern.compile("^[a-z0-9]{16,40}$");
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final String ALPHABET = "abcdefghijklmnopqrstuvwxyz0123456789";
    private static final long UPLOAD_STALE_MS = 30 * 60_000L;

    private record Upload(int userId, long startedAt, long bytes, int nextIndex) {}

    private static final Map<String, Upload> UPLOADS = new ConcurrentHashMap<>();

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        if (!(msg instanceof FullHttpRequest req)) {
            super.channelRead(ctx, msg);
            return;
        }

        String path = new QueryStringDecoder(req.uri()).path();
        if (!(path.equals(BASE_PATH) || path.startsWith(BASE_PATH + "/"))) {
            super.channelRead(ctx, msg);
            return;
        }

        try {
            handle(ctx, req, path);
        } finally {
            ReferenceCountUtil.release(req);
        }
    }

    private void handle(ChannelHandlerContext ctx, FullHttpRequest req, String path) {
        if (req.method() == HttpMethod.OPTIONS) {
            sendCors(ctx, req);
            return;
        }

        if (!isEnabled()) {
            sendJson(ctx, req, HttpResponseStatus.NOT_FOUND, error("Recordings are disabled.", "disabled"));
            return;
        }

        String trailing = path.length() > BASE_PATH.length() ? path.substring(BASE_PATH.length() + 1) : "";

        try {
            if (trailing.isEmpty()) {
                if (req.method() == HttpMethod.GET) {
                    handleList(ctx, req);
                    return;
                }
                sendJson(ctx, req, HttpResponseStatus.METHOD_NOT_ALLOWED, error("Use GET."));
                return;
            }

            switch (trailing) {
                case "start" -> {
                    if (req.method() != HttpMethod.POST) {
                        sendJson(ctx, req, HttpResponseStatus.METHOD_NOT_ALLOWED, error("Use POST."));
                        return;
                    }
                    handleStart(ctx, req);
                    return;
                }
                case "chunk" -> {
                    if (req.method() != HttpMethod.POST) {
                        sendJson(ctx, req, HttpResponseStatus.METHOD_NOT_ALLOWED, error("Use POST."));
                        return;
                    }
                    handleChunk(ctx, req);
                    return;
                }
                case "finish" -> {
                    if (req.method() != HttpMethod.POST) {
                        sendJson(ctx, req, HttpResponseStatus.METHOD_NOT_ALLOWED, error("Use POST."));
                        return;
                    }
                    handleFinish(ctx, req);
                    return;
                }
                default -> {
                    if (req.method() == HttpMethod.GET || req.method() == HttpMethod.HEAD) {
                        handleStream(ctx, req, trailing);
                        return;
                    }
                    if (req.method() == HttpMethod.DELETE) {
                        handleDelete(ctx, req, trailing);
                        return;
                    }
                    sendJson(ctx, req, HttpResponseStatus.METHOD_NOT_ALLOWED, error("Unsupported method."));
                }
            }
        } catch (Exception e) {
            LOGGER.error("[recordings] unexpected error path=" + path, e);
            sendJson(ctx, req, HttpResponseStatus.INTERNAL_SERVER_ERROR, error("Server error."));
        }
    }

    // ---------------------------------------------------------------- upload

    private void handleStart(ChannelHandlerContext ctx, FullHttpRequest req) throws IOException {
        int userId = authenticate(req);
        if (userId == 0) {
            sendJson(ctx, req, HttpResponseStatus.UNAUTHORIZED, error("Authentication required.", "unauthorized"));
            return;
        }

        Habbo habbo = Emulator.getGameEnvironment().getHabboManager().getHabbo(userId);
        if (habbo == null) {
            sendJson(ctx, req, HttpResponseStatus.CONFLICT, error("You must be online.", "offline"));
            return;
        }

        pruneStaleUploads();

        int cooldown = cooldownSeconds();
        int lastCreated = lastCreatedAt(userId);
        int now = Emulator.getIntUnixTimestamp();
        if (lastCreated > 0 && now - lastCreated < cooldown && !habbo.hasPermission(Permission.ACC_SUPPORTTOOL)) {
            JsonObject body = error("Wait before recording again.", "cooldown");
            body.addProperty("retryIn", cooldown - (now - lastCreated));
            sendJson(ctx, req, HttpResponseStatus.TOO_MANY_REQUESTS, body);
            return;
        }

        if (countForUser(userId) >= maxPerUser()) {
            sendJson(ctx, req, HttpResponseStatus.CONFLICT, error("Delete an old recording first.", "quota"));
            return;
        }

        for (Map.Entry<String, Upload> entry : UPLOADS.entrySet()) {
            if (entry.getValue().userId() == userId) {
                UPLOADS.remove(entry.getKey());
                Files.deleteIfExists(storageDir().resolve(entry.getKey() + ".part"));
            }
        }

        Path dir = storageDir();
        Files.createDirectories(dir);

        String uploadId = newId();
        Files.write(dir.resolve(uploadId + ".part"), new byte[0], StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        UPLOADS.put(uploadId, new Upload(userId, System.currentTimeMillis(), 0L, 0));

        JsonObject ok = new JsonObject();
        ok.addProperty("uploadId", uploadId);
        ok.addProperty("chunkBytes", MAX_CHUNK_BYTES);
        ok.addProperty("maxBytes", maxBytes());
        ok.addProperty("maxSeconds", maxSeconds());
        sendJson(ctx, req, HttpResponseStatus.CREATED, ok);
    }

    private void handleChunk(ChannelHandlerContext ctx, FullHttpRequest req) throws IOException {
        int userId = authenticate(req);
        if (userId == 0) {
            sendJson(ctx, req, HttpResponseStatus.UNAUTHORIZED, error("Authentication required.", "unauthorized"));
            return;
        }

        String uploadId = String.valueOf(req.headers().get("X-Upload-Id", "")).trim();
        Upload upload = ID_PATTERN.matcher(uploadId).matches() ? UPLOADS.get(uploadId) : null;
        if (upload == null || upload.userId() != userId) {
            sendJson(ctx, req, HttpResponseStatus.NOT_FOUND, error("Unknown upload.", "unknown_upload"));
            return;
        }

        int index;
        try {
            index = Integer.parseInt(String.valueOf(req.headers().get("X-Upload-Index", "-1")).trim());
        } catch (NumberFormatException e) {
            index = -1;
        }
        if (index != upload.nextIndex()) {
            sendJson(ctx, req, HttpResponseStatus.CONFLICT, error("Chunk out of order.", "out_of_order"));
            return;
        }

        int size = req.content().readableBytes();
        if (size <= 0 || size > MAX_CHUNK_BYTES) {
            sendJson(ctx, req, HttpResponseStatus.REQUEST_ENTITY_TOO_LARGE, error("Chunk size out of range.", "chunk_size"));
            return;
        }
        if (upload.bytes() + size > maxBytes()) {
            UPLOADS.remove(uploadId);
            Files.deleteIfExists(storageDir().resolve(uploadId + ".part"));
            sendJson(ctx, req, HttpResponseStatus.REQUEST_ENTITY_TOO_LARGE, error("Recording too large.", "too_large"));
            return;
        }

        byte[] bytes = new byte[size];
        req.content().getBytes(req.content().readerIndex(), bytes);
        Files.write(storageDir().resolve(uploadId + ".part"), bytes, StandardOpenOption.APPEND);

        UPLOADS.put(uploadId, new Upload(userId, upload.startedAt(), upload.bytes() + size, index + 1));

        JsonObject ok = new JsonObject();
        ok.addProperty("received", upload.bytes() + size);
        ok.addProperty("next", index + 1);
        sendJson(ctx, req, HttpResponseStatus.OK, ok);
    }

    private void handleFinish(ChannelHandlerContext ctx, FullHttpRequest req) throws IOException {
        int userId = authenticate(req);
        if (userId == 0) {
            sendJson(ctx, req, HttpResponseStatus.UNAUTHORIZED, error("Authentication required.", "unauthorized"));
            return;
        }

        String uploadId = String.valueOf(req.headers().get("X-Upload-Id", "")).trim();
        Upload upload = ID_PATTERN.matcher(uploadId).matches() ? UPLOADS.remove(uploadId) : null;
        if (upload == null || upload.userId() != userId) {
            sendJson(ctx, req, HttpResponseStatus.NOT_FOUND, error("Unknown upload.", "unknown_upload"));
            return;
        }

        Path part = storageDir().resolve(uploadId + ".part");
        if (!Files.isRegularFile(part) || upload.bytes() <= 0) {
            Files.deleteIfExists(part);
            sendJson(ctx, req, HttpResponseStatus.BAD_REQUEST, error("Empty recording.", "empty"));
            return;
        }

        byte[] head = new byte[16];
        try (RandomAccessFile file = new RandomAccessFile(part.toFile(), "r")) {
            int read = file.read(head);
            if (read < 12) {
                Files.deleteIfExists(part);
                sendJson(ctx, req, HttpResponseStatus.BAD_REQUEST, error("Empty recording.", "empty"));
                return;
            }
        }

        String extension = sniffVideoContainer(head);
        if (extension == null) {
            Files.deleteIfExists(part);
            sendJson(ctx, req, HttpResponseStatus.UNSUPPORTED_MEDIA_TYPE, error("Unsupported video format.", "invalid_video"));
            return;
        }

        int seconds;
        try {
            seconds = Integer.parseInt(String.valueOf(req.headers().get("X-Seconds", "0")).trim());
        } catch (NumberFormatException e) {
            seconds = 0;
        }
        seconds = Math.max(1, Math.min(maxSeconds(), seconds));

        int roomId;
        try {
            roomId = Integer.parseInt(String.valueOf(req.headers().get("X-Room-Id", "0")).trim());
        } catch (NumberFormatException e) {
            roomId = 0;
        }
        String roomName = "";
        if (roomId > 0) {
            Room room = Emulator.getGameEnvironment().getRoomManager().getRoom(roomId);
            if (room != null) roomName = room.getName();
        }
        if (roomName.length() > 96) roomName = roomName.substring(0, 96);

        if (!hasStorageRoom(upload.bytes())) {
            Files.deleteIfExists(part);
            sendJson(ctx, req, HttpResponseStatus.INSUFFICIENT_STORAGE, error("Recording storage is full.", "storage_full"));
            return;
        }

        String id = newId();
        Path target = storageDir().resolve(id + "." + extension);
        Files.move(part, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);

        int createdAt = Emulator.getIntUnixTimestamp();
        try (Connection connection = Emulator.getDatabase().getDataSource().getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "INSERT INTO room_recordings (id, user_id, room_id, room_name, seconds, bytes, extension, created_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?)")) {
            statement.setString(1, id);
            statement.setInt(2, userId);
            statement.setInt(3, roomId);
            statement.setString(4, roomName);
            statement.setInt(5, seconds);
            statement.setLong(6, upload.bytes());
            statement.setString(7, extension);
            statement.setInt(8, createdAt);
            statement.executeUpdate();
        } catch (SQLException e) {
            LOGGER.error("[recordings] failed to store recording row", e);
            Files.deleteIfExists(target);
            sendJson(ctx, req, HttpResponseStatus.INTERNAL_SERVER_ERROR, error("Server error."));
            return;
        }

        JsonObject ok = recordingJson(id, roomId, roomName, seconds, upload.bytes(), extension, createdAt);
        sendJson(ctx, req, HttpResponseStatus.CREATED, ok);
        LOGGER.debug("[recordings] user {} stored {} ({} bytes, {}s)", userId, id, upload.bytes(), seconds);
    }

    // ---------------------------------------------------------------- list / stream / delete

    private void handleList(ChannelHandlerContext ctx, FullHttpRequest req) {
        int userId = authenticate(req);
        if (userId == 0) {
            sendJson(ctx, req, HttpResponseStatus.UNAUTHORIZED, error("Authentication required.", "unauthorized"));
            return;
        }

        JsonArray list = new JsonArray();
        try (Connection connection = Emulator.getDatabase().getDataSource().getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT id, room_id, room_name, seconds, bytes, extension, created_at FROM room_recordings WHERE user_id = ? ORDER BY created_at DESC LIMIT 100")) {
            statement.setInt(1, userId);
            try (ResultSet set = statement.executeQuery()) {
                while (set.next()) {
                    list.add(recordingJson(set.getString("id"), set.getInt("room_id"), set.getString("room_name"), set.getInt("seconds"), set.getLong("bytes"), set.getString("extension"), set.getInt("created_at")));
                }
            }
        } catch (SQLException e) {
            LOGGER.error("[recordings] failed to list recordings", e);
            sendJson(ctx, req, HttpResponseStatus.INTERNAL_SERVER_ERROR, error("Server error."));
            return;
        }

        int cooldown = cooldownSeconds();
        int lastCreated = lastCreatedAt(userId);
        int now = Emulator.getIntUnixTimestamp();

        JsonObject ok = new JsonObject();
        ok.add("recordings", list);
        ok.addProperty("maxSeconds", maxSeconds());
        ok.addProperty("maxPerUser", maxPerUser());
        ok.addProperty("cooldownSeconds", cooldown);
        ok.addProperty("retryIn", lastCreated > 0 ? Math.max(0, cooldown - (now - lastCreated)) : 0);
        sendJson(ctx, req, HttpResponseStatus.OK, ok);
    }

    private void handleStream(ChannelHandlerContext ctx, FullHttpRequest req, String id) throws IOException {
        int dot = id.indexOf('.');
        if (dot >= 0) id = id.substring(0, dot);

        if (!ID_PATTERN.matcher(id).matches()) {
            sendJson(ctx, req, HttpResponseStatus.BAD_REQUEST, error("Invalid id."));
            return;
        }

        String ip = resolveClientIp(ctx, req);
        if (!AuthRateLimiter.tryProbe(ip)) {
            sendJson(ctx, req, HttpResponseStatus.TOO_MANY_REQUESTS, error("Too many requests."));
            return;
        }

        Path file = null;
        String type = null;
        for (String extension : new String[]{"webm", "mp4"}) {
            Path candidate = storageDir().resolve(id + "." + extension);
            if (Files.isRegularFile(candidate)) {
                file = candidate;
                type = "mp4".equals(extension) ? "video/mp4" : "video/webm";
                break;
            }
        }

        if (file == null) {
            sendJson(ctx, req, HttpResponseStatus.NOT_FOUND, error("Recording not found.", "not_found"));
            return;
        }

        long length = Files.size(file);
        long start = 0;
        long end = length - 1;
        boolean partial = false;

        String range = req.headers().get(HttpHeaderNames.RANGE);
        if (range != null && range.startsWith("bytes=")) {
            String spec = range.substring(6).trim();
            int dash = spec.indexOf('-');
            try {
                if (dash == 0) {
                    long suffix = Long.parseLong(spec.substring(1));
                    start = Math.max(0, length - suffix);
                } else if (dash > 0) {
                    start = Long.parseLong(spec.substring(0, dash));
                    if (dash < spec.length() - 1) end = Math.min(length - 1, Long.parseLong(spec.substring(dash + 1)));
                }
                partial = true;
            } catch (NumberFormatException e) {
                start = 0;
                end = length - 1;
                partial = false;
            }
            if (start > end || start >= length) {
                FullHttpResponse response = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.REQUESTED_RANGE_NOT_SATISFIABLE);
                response.headers().set(HttpHeaderNames.CONTENT_RANGE, "bytes */" + length);
                applyCors(req, response);
                ctx.writeAndFlush(response).addListener(ChannelFutureListener.CLOSE);
                return;
            }
        }

        // Keep every response under the frame cap the browser can chew comfortably; the client asks for the rest.
        long window = 4L * 1024 * 1024;
        if (end - start + 1 > window) {
            end = start + window - 1;
            partial = true;
        }

        int count = (int) (end - start + 1);
        byte[] bytes = new byte[req.method() == HttpMethod.HEAD ? 0 : count];
        if (req.method() != HttpMethod.HEAD) {
            try (RandomAccessFile raf = new RandomAccessFile(file.toFile(), "r")) {
                raf.seek(start);
                raf.readFully(bytes);
            }
        }

        FullHttpResponse response = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, partial ? HttpResponseStatus.PARTIAL_CONTENT : HttpResponseStatus.OK, Unpooled.wrappedBuffer(bytes));
        response.headers().set(HttpHeaderNames.CONTENT_TYPE, type);
        response.headers().setInt(HttpHeaderNames.CONTENT_LENGTH, count);
        response.headers().set(HttpHeaderNames.ACCEPT_RANGES, "bytes");
        if (partial) response.headers().set(HttpHeaderNames.CONTENT_RANGE, "bytes " + start + "-" + end + "/" + length);
        response.headers().set(HttpHeaderNames.CACHE_CONTROL, "public, max-age=86400");
        response.headers().set("X-Content-Type-Options", "nosniff");
        response.headers().set(HttpHeaderNames.CONTENT_DISPOSITION, "inline");
        applyCors(req, response);
        boolean keepAlive = isKeepAlive(req);
        if (keepAlive) response.headers().set(HttpHeaderNames.CONNECTION, HttpHeaderValues.KEEP_ALIVE);
        var future = ctx.writeAndFlush(response);
        if (!keepAlive) future.addListener(ChannelFutureListener.CLOSE);
    }

    private void handleDelete(ChannelHandlerContext ctx, FullHttpRequest req, String id) throws IOException {
        int userId = authenticate(req);
        if (userId == 0) {
            sendJson(ctx, req, HttpResponseStatus.UNAUTHORIZED, error("Authentication required.", "unauthorized"));
            return;
        }
        if (!ID_PATTERN.matcher(id).matches()) {
            sendJson(ctx, req, HttpResponseStatus.BAD_REQUEST, error("Invalid id."));
            return;
        }

        Habbo habbo = Emulator.getGameEnvironment().getHabboManager().getHabbo(userId);
        boolean staff = habbo != null && habbo.hasPermission(Permission.ACC_SUPPORTTOOL);

        int ownerId = 0;
        String extension = "webm";
        try (Connection connection = Emulator.getDatabase().getDataSource().getConnection();
             PreparedStatement statement = connection.prepareStatement("SELECT user_id, extension FROM room_recordings WHERE id = ? LIMIT 1")) {
            statement.setString(1, id);
            try (ResultSet set = statement.executeQuery()) {
                if (set.next()) {
                    ownerId = set.getInt("user_id");
                    extension = set.getString("extension");
                }
            }
            if (ownerId == 0) {
                sendJson(ctx, req, HttpResponseStatus.NOT_FOUND, error("Recording not found.", "not_found"));
                return;
            }
            if (ownerId != userId && !staff) {
                sendJson(ctx, req, HttpResponseStatus.FORBIDDEN, error("Not your recording.", "forbidden"));
                return;
            }
            try (PreparedStatement delete = connection.prepareStatement("DELETE FROM room_recordings WHERE id = ?")) {
                delete.setString(1, id);
                delete.executeUpdate();
            }
        } catch (SQLException e) {
            LOGGER.error("[recordings] failed to delete recording", e);
            sendJson(ctx, req, HttpResponseStatus.INTERNAL_SERVER_ERROR, error("Server error."));
            return;
        }

        Files.deleteIfExists(storageDir().resolve(id + "." + extension));

        JsonObject ok = new JsonObject();
        ok.addProperty("deleted", id);
        sendJson(ctx, req, HttpResponseStatus.OK, ok);
    }

    // ---------------------------------------------------------------- policy

    private static boolean isEnabled() {
        return Emulator.getConfig() == null || Emulator.getConfig().getBoolean("recordings.enabled", true);
    }

    private static int maxSeconds() {
        int value = Emulator.getConfig() == null ? 300 : Emulator.getConfig().getInt("recordings.max.seconds", 300);
        return Math.max(10, Math.min(1800, value));
    }

    private static long maxBytes() {
        int mb = Emulator.getConfig() == null ? 80 : Emulator.getConfig().getInt("recordings.max.mb", 80);
        return Math.max(1L, mb) * 1024L * 1024L;
    }

    private static int maxPerUser() {
        return Math.max(1, Emulator.getConfig() == null ? 12 : Emulator.getConfig().getInt("recordings.max.per.user", 12));
    }

    private static int cooldownSeconds() {
        return Math.max(0, Emulator.getConfig() == null ? 300 : Emulator.getConfig().getInt("recordings.cooldown.seconds", 300));
    }

    private static Path storageDir() {
        String configured = Emulator.getConfig() == null ? "" : Emulator.getConfig().getValue("recordings.path", "");
        return Paths.get(configured == null || configured.isBlank() ? "data/recordings" : configured).toAbsolutePath();
    }

    private static boolean hasStorageRoom(long incoming) {
        int gb = Emulator.getConfig() == null ? 20 : Emulator.getConfig().getInt("recordings.max.storage.gb", 20);
        long limit = Math.max(1L, gb) * 1024L * 1024L * 1024L;

        try (Connection connection = Emulator.getDatabase().getDataSource().getConnection();
             PreparedStatement statement = connection.prepareStatement("SELECT COALESCE(SUM(bytes), 0) AS total FROM room_recordings");
             ResultSet set = statement.executeQuery()) {
            if (set.next()) return set.getLong("total") + incoming <= limit;
        } catch (SQLException e) {
            LOGGER.error("[recordings] storage check failed", e);
        }
        return false;
    }

    private static int countForUser(int userId) {
        try (Connection connection = Emulator.getDatabase().getDataSource().getConnection();
             PreparedStatement statement = connection.prepareStatement("SELECT COUNT(*) AS c FROM room_recordings WHERE user_id = ?")) {
            statement.setInt(1, userId);
            try (ResultSet set = statement.executeQuery()) {
                if (set.next()) return set.getInt("c");
            }
        } catch (SQLException e) {
            LOGGER.error("[recordings] count failed", e);
        }
        return Integer.MAX_VALUE;
    }

    private static int lastCreatedAt(int userId) {
        try (Connection connection = Emulator.getDatabase().getDataSource().getConnection();
             PreparedStatement statement = connection.prepareStatement("SELECT MAX(created_at) AS last FROM room_recordings WHERE user_id = ?")) {
            statement.setInt(1, userId);
            try (ResultSet set = statement.executeQuery()) {
                if (set.next()) return set.getInt("last");
            }
        } catch (SQLException e) {
            LOGGER.error("[recordings] cooldown check failed", e);
        }
        return 0;
    }

    private static void pruneStaleUploads() {
        long cutoff = System.currentTimeMillis() - UPLOAD_STALE_MS;
        for (Map.Entry<String, Upload> entry : new ArrayList<>(UPLOADS.entrySet())) {
            if (entry.getValue().startedAt() < cutoff) {
                UPLOADS.remove(entry.getKey());
                try {
                    Files.deleteIfExists(storageDir().resolve(entry.getKey() + ".part"));
                } catch (IOException ignored) {
                    // best effort
                }
            }
        }
    }

    /** WebM = EBML header, MP4 = "ftyp" box at offset 4; anything else is refused. */
    static String sniffVideoContainer(byte[] bytes) {
        if (bytes == null || bytes.length < 12) return null;
        if ((bytes[0] & 0xFF) == 0x1A && (bytes[1] & 0xFF) == 0x45 && (bytes[2] & 0xFF) == 0xDF && (bytes[3] & 0xFF) == 0xA3) return "webm";
        if (bytes[4] == 'f' && bytes[5] == 't' && bytes[6] == 'y' && bytes[7] == 'p') return "mp4";
        return null;
    }

    private static JsonObject recordingJson(String id, int roomId, String roomName, int seconds, long bytes, String extension, int createdAt) {
        JsonObject obj = new JsonObject();
        obj.addProperty("id", id);
        obj.addProperty("url", BASE_PATH + "/" + id + "." + extension);
        obj.addProperty("roomId", roomId);
        obj.addProperty("roomName", roomName == null ? "" : roomName);
        obj.addProperty("seconds", seconds);
        obj.addProperty("bytes", bytes);
        obj.addProperty("extension", extension);
        obj.addProperty("createdAt", createdAt);
        return obj;
    }

    private static String newId() {
        StringBuilder builder = new StringBuilder(24);
        for (int i = 0; i < 24; i++) builder.append(ALPHABET.charAt(RANDOM.nextInt(ALPHABET.length())));
        return builder.toString();
    }

    // ---------------------------------------------------------------- http plumbing (same shape as ChatVoiceHttpHandler)

    private static int authenticate(FullHttpRequest req) {
        String header = req.headers().get(HttpHeaderNames.AUTHORIZATION);
        if (header == null || header.isEmpty()) return 0;
        String token = header.startsWith("Bearer ") ? header.substring(7).trim() : header.trim();
        return AccessTokenService.verify(token);
    }

    private static JsonObject error(String message) {
        return error(message, null);
    }

    private static JsonObject error(String message, String code) {
        JsonObject obj = new JsonObject();
        obj.addProperty("error", message);
        if (code != null) obj.addProperty("code", code);
        return obj;
    }

    private static void sendJson(ChannelHandlerContext ctx, FullHttpRequest req, HttpResponseStatus status, JsonObject body) {
        byte[] bytes = body.toString().getBytes(StandardCharsets.UTF_8);
        FullHttpResponse response = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, status, Unpooled.wrappedBuffer(bytes));
        response.headers().set(HttpHeaderNames.CONTENT_TYPE, "application/json; charset=utf-8");
        response.headers().setInt(HttpHeaderNames.CONTENT_LENGTH, bytes.length);
        applyCors(req, response);
        boolean keepAlive = isKeepAlive(req);
        if (keepAlive) response.headers().set(HttpHeaderNames.CONNECTION, HttpHeaderValues.KEEP_ALIVE);
        var future = ctx.writeAndFlush(response);
        if (!keepAlive) future.addListener(ChannelFutureListener.CLOSE);
    }

    private static void sendCors(ChannelHandlerContext ctx, FullHttpRequest req) {
        FullHttpResponse response = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.NO_CONTENT);
        applyCors(req, response);
        ctx.writeAndFlush(response).addListener(ChannelFutureListener.CLOSE);
    }

    private static void applyCors(FullHttpRequest req, FullHttpResponse response) {
        response.headers().set("Vary", "Origin");
        String origin = req.headers().get(HttpHeaderNames.ORIGIN);
        if (origin != null && !origin.isEmpty() && CorsOriginGate.isAllowed(req)) {
            response.headers().set("Access-Control-Allow-Origin", origin);
            response.headers().set("Access-Control-Allow-Credentials", "true");
            response.headers().set("Access-Control-Allow-Methods", "GET, POST, DELETE, OPTIONS");
            response.headers().set("Access-Control-Allow-Headers", "Authorization, Content-Type, Range, X-Upload-Id, X-Upload-Index, X-Seconds, X-Room-Id, X-Requested-With");
            response.headers().set("Access-Control-Expose-Headers", "Content-Range, Accept-Ranges, Content-Length");
        }
    }

    private static boolean isKeepAlive(FullHttpRequest req) {
        String connection = req.headers().get(HttpHeaderNames.CONNECTION);
        if (connection != null && connection.equalsIgnoreCase("close")) return false;
        if (connection != null && connection.equalsIgnoreCase("keep-alive")) return true;
        return req.protocolVersion().isKeepAliveDefault();
    }

    private static String resolveClientIp(ChannelHandlerContext ctx, FullHttpRequest req) {
        if (ctx.channel().attr(GameServerAttributes.WS_IP).get() != null) {
            return ctx.channel().attr(GameServerAttributes.WS_IP).get();
        }
        String forwarded = req.headers().get("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) return forwarded.split(",")[0].trim();
        if (ctx.channel().remoteAddress() instanceof InetSocketAddress address) return address.getAddress().getHostAddress();
        return "unknown";
    }
}
