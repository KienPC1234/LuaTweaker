package com.luatweaker.storage;

import com.luatweaker.api.log.LogStage;
import com.luatweaker.api.log.LuaTweakerLog;
import org.bson.BSONException;
import org.bson.BsonBinaryReader;
import org.bson.BsonBinaryWriter;
import org.bson.Document;
import org.bson.codecs.DecoderContext;
import org.bson.codecs.DocumentCodec;
import org.bson.codecs.EncoderContext;
import org.bson.io.BasicOutputBuffer;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.file.AccessDeniedException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Reads and writes the BSON store files. All writes encode fully to memory and
 * swap atomically (with retry + stream-copy fallback for Win32 file locking),
 * so a crash can never leave a half-written store behind.
 */
final class StorageDiskIO {

    private static final String WORLD_FILE = "world_storage.bson";
    private static final String PLAYER_FILE = "player_storage.bson";
    private static final String TMP_SUFFIX = ".tmp";
    private static final int MAX_SAVE_RETRIES = 3;

    private static final DocumentCodec DOCUMENT_CODEC = new DocumentCodec();
    private static final EncoderContext ENCODER_CONTEXT = EncoderContext.builder().build();
    private static final DecoderContext DECODER_CONTEXT = DecoderContext.builder().build();

    private final Map<String, Object> worldStore;
    private final Map<String, Map<String, Object>> playerStore;
    private final Map<String, Map<String, Object>> playerDocCache;
    private final AtomicBoolean worldDirty;
    private final AtomicBoolean playerDirty;

    StorageDiskIO(Map<String, Object> worldStore,
                  Map<String, Map<String, Object>> playerStore,
                  Map<String, Map<String, Object>> playerDocCache,
                  AtomicBoolean worldDirty,
                  AtomicBoolean playerDirty) {
        this.worldStore = worldStore;
        this.playerStore = playerStore;
        this.playerDocCache = playerDocCache;
        this.worldDirty = worldDirty;
        this.playerDirty = playerDirty;
    }

    void loadWorldStorage(File dir) {
        loadDocument(dir, WORLD_FILE).ifPresent(worldStore::putAll);
    }

    void loadPlayerStorage(File dir) {
        Optional<Map<String, Object>> loaded = loadDocument(dir, PLAYER_FILE);
        if (loaded.isEmpty()) {
            return;
        }
        for (Map.Entry<String, Object> entry : loaded.get().entrySet()) {
            if (entry.getValue() instanceof Map<?, ?> raw) {
                playerStore.put(entry.getKey(), copyPlayerMap(raw));
            } else {
                LuaTweakerLog.get().warn(LogStage.SYSTEM,
                        "Ignoring malformed player entry '" + entry.getKey() + "' in " + PLAYER_FILE);
            }
        }
    }

    private static Map<String, Object> copyPlayerMap(Map<?, ?> raw) {
        Map<String, Object> player = new ConcurrentHashMap<>();
        for (Map.Entry<?, ?> value : raw.entrySet()) {
            player.put(String.valueOf(value.getKey()), value.getValue());
        }
        return player;
    }

    private Optional<Map<String, Object>> loadDocument(File dir, String fileName) {
        File file = new File(dir, fileName);
        if (!file.isFile()) {
            return Optional.empty();
        }
        try (InputStream in = Files.newInputStream(file.toPath())) {
            byte[] bytes = in.readAllBytes();
            if (bytes.length == 0) {
                return Optional.empty();
            }
            Map<String, Object> doc = DOCUMENT_CODEC.decode(
                    new BsonBinaryReader(ByteBuffer.wrap(bytes)), DECODER_CONTEXT);
            return Optional.ofNullable(doc == null ? null : new LinkedHashMap<>(doc));
        } catch (IOException | BSONException e) {
            quarantine(file, e);
            return Optional.empty();
        }
    }

    private void quarantine(File file, Exception cause) {
        LuaTweakerLog.get().error(LogStage.SYSTEM,
                "Failed to read " + file.getName() + " (" + cause.getClass().getSimpleName()
                        + ": " + cause.getMessage() + "); moving it aside so it can be recovered");
        File quarantined = new File(file.getParentFile(),
                file.getName() + ".corrupt-" + System.currentTimeMillis());
        try {
            Files.move(file.toPath(), quarantined.toPath(), StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException moveFailure) {
            LuaTweakerLog.get().warn(LogStage.SYSTEM,
                    "Could not move corrupt storage file aside: " + moveFailure.getMessage());
        }
    }

    /** Caller holds the flush lock: that is the single serializer for both store writes. */
    void saveWorldStorage(File dir) {
        if (!saveDocument(dir, WORLD_FILE, StorageBsonCodec.toDocument(worldStore))) {
            worldDirty.set(true);
        }
    }

    /** Caller holds the flush lock: that is the single serializer for both store writes. */
    void savePlayerStorage(File dir) {
        Map<String, Object> players = StorageBsonCodec.newBsonDocument();
        for (Map.Entry<String, Map<String, Object>> entry : playerStore.entrySet()) {
            String uuid = entry.getKey();
            Map<String, Object> pDoc = playerDocCache.computeIfAbsent(uuid,
                    key -> StorageBsonCodec.toDocument(entry.getValue()));
            players.put(uuid, pDoc);
        }
        if (!saveDocument(dir, PLAYER_FILE, players)) {
            playerDirty.set(true);
        }
    }

    /**
     * Encodes fully to memory before touching the target file, then swaps atomically with retry.
     *
     * @return {@code true} when the file was replaced, {@code false} when the previous file was
     *         left untouched and the caller should retry later
     */
    boolean saveDocument(File dir, String fileName, Map<String, Object> document) {
        File target = new File(dir, fileName);
        File tmp = new File(dir, fileName + TMP_SUFFIX);
        try {
            BasicOutputBuffer buffer = new BasicOutputBuffer();
            try (BsonBinaryWriter writer = new BsonBinaryWriter(buffer)) {
                DOCUMENT_CODEC.encode(writer, new Document(document), ENCODER_CONTEXT);
            }
            byte[] bytes = buffer.toByteArray();
            Files.write(tmp.toPath(), bytes);

            boolean moved = moveAtomicallyWithFallback(tmp, target);
            if (!moved && Files.exists(tmp.toPath())) {
                Files.deleteIfExists(tmp.toPath());
            }
            return moved;
        } catch (IOException | BSONException e) {
            LuaTweakerLog.get().error(LogStage.SYSTEM,
                    "Failed to save " + fileName + " (" + e.getClass().getSimpleName()
                            + ": " + e.getMessage() + "); previous file left untouched");
            try {
                Files.deleteIfExists(tmp.toPath());
            } catch (IOException cleanupFailure) {
                LuaTweakerLog.get().warn(LogStage.SYSTEM,
                        "Could not delete temporary storage file: " + cleanupFailure.getMessage());
            }
            return false;
        }
    }

    private boolean moveAtomicallyWithFallback(File tmp, File target) throws IOException {
        for (int attempt = 1; attempt <= MAX_SAVE_RETRIES; attempt++) {
            try {
                Files.move(tmp.toPath(), target.toPath(),
                        StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
                return true;
            } catch (AtomicMoveNotSupportedException | AccessDeniedException atomicFailure) {
                if (tryPlainMove(tmp, target)) {
                    return true;
                }
                if (attempt >= MAX_SAVE_RETRIES) {
                    break;
                }
                try {
                    TimeUnit.MILLISECONDS.sleep(10L * attempt);
                } catch (InterruptedException ie) {
                    // No caller observes the interrupt status (the flush runs on a daemon
                    // scheduler thread and cancel(false) never interrupts); report failure so
                    // the dirty flag is re-marked and the write is retried on the next flush.
                    Files.deleteIfExists(tmp.toPath());
                    return false;
                }
            }
        }
        Files.copy(tmp.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING);
        Files.deleteIfExists(tmp.toPath());
        return true;
    }

    /**
     * Plain (non-atomic) move for filesystems without atomic-move support.
     * Only a lock conflict fails soft; any other I/O error propagates.
     */
    private boolean tryPlainMove(File tmp, File target) throws IOException {
        try {
            Files.move(tmp.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING);
            return true;
        } catch (AccessDeniedException locked) {
            return false;
        }
    }
}
