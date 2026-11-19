package com.luatweaker.storage;

import com.luatweaker.api.log.LogStage;
import com.luatweaker.api.log.LuaTweakerLog;
import com.luatweaker.api.pal.Platform;
import com.luatweaker.api.storage.IRobloxStorageService;
import com.luatweaker.api.vm.ILuaEngine;
import com.luatweaker.api.vm.ILuaTable;
import com.luatweaker.api.vm.ILuaValue;
import org.bson.BsonBinaryReader;
import org.bson.BsonBinaryWriter;
import org.bson.Document;
import org.bson.codecs.DecoderContext;
import org.bson.codecs.DocumentCodec;
import org.bson.codecs.EncoderContext;
import org.bson.io.BasicOutputBuffer;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * BSON-backed world/player/session storage.
 *
 * <p>Writes are atomic: the document is fully encoded to memory first, written to a temporary
 * file and then moved over the target, so an encoding failure can never truncate the previous
 * save. The in-memory documents are concurrent and every value is snapshotted on write, so a
 * deferred task running on a coroutine thread cannot corrupt a concurrent save.</p>
 */
public class StorageServiceImpl implements IRobloxStorageService {
    private static final String WORLD_FILE = "world_storage.bson";
    private static final String PLAYER_FILE = "player_storage.bson";
    private static final String TMP_SUFFIX = ".tmp";

    private final ILuaEngine engine;
    private final Object initLock = new Object();

    private final Map<String, Object> sessionStore = new ConcurrentHashMap<>();
    private final Map<String, Object> worldStore = new ConcurrentHashMap<>();
    private final Map<String, Map<String, Object>> playerStore = new ConcurrentHashMap<>();

    private volatile File storageDir;
    private volatile boolean initialized;

    public StorageServiceImpl(ILuaEngine engine) {
        this.engine = engine;
    }

    private File storageDir() {
        ensureInitialized();
        return storageDir;
    }

    /**
     * Loads the documents on first use instead of in the constructor. The platform resolves a
     * different directory before and after the server starts; deferring the load makes the first
     * real storage access use the per-world directory.
     */
    private void ensureInitialized() {
        if (initialized) {
            return;
        }
        synchronized (initLock) {
            if (initialized) {
                return;
            }
            File dir = Platform.getStorage().getStorageDirectory();
            if (!dir.exists() && !dir.mkdirs()) {
                LuaTweakerLog.get().error(LogStage.SYSTEM,
                        "Cannot create Lua storage directory: " + dir.getAbsolutePath());
            }
            storageDir = dir;
            loadWorldStorage(dir);
            loadPlayerStorage(dir);
            initialized = true;
        }
    }

    private void loadWorldStorage(File dir) {
        Map<String, Object> loaded = loadDocument(dir, WORLD_FILE);
        if (loaded != null) {
            worldStore.putAll(loaded);
        }
    }

    private void loadPlayerStorage(File dir) {
        Map<String, Object> loaded = loadDocument(dir, PLAYER_FILE);
        if (loaded == null) {
            return;
        }
        for (Map.Entry<String, Object> entry : loaded.entrySet()) {
            if (entry.getValue() instanceof Map<?, ?> raw) {
                Map<String, Object> player = new ConcurrentHashMap<>();
                for (Map.Entry<?, ?> value : raw.entrySet()) {
                    player.put(String.valueOf(value.getKey()), value.getValue());
                }
                playerStore.put(entry.getKey(), player);
            } else {
                LuaTweakerLog.get().warn(LogStage.SYSTEM,
                        "Ignoring malformed player entry '" + entry.getKey() + "' in " + PLAYER_FILE);
            }
        }
    }

    private Map<String, Object> loadDocument(File dir, String fileName) {
        File file = new File(dir, fileName);
        if (!file.isFile()) {
            return null;
        }
        try (FileInputStream in = new FileInputStream(file)) {
            byte[] bytes = in.readAllBytes();
            if (bytes.length == 0) {
                return null;
            }
            Document doc = new DocumentCodec().decode(
                    new BsonBinaryReader(ByteBuffer.wrap(bytes)), DecoderContext.builder().build());
            return doc != null ? new LinkedHashMap<>(doc) : null;
        } catch (Exception e) {
            quarantine(file, e);
            return null;
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
            LuaTweakerLog.get().error(LogStage.SYSTEM,
                    "Could not move corrupt storage file aside: " + moveFailure.getMessage());
        }
    }

    private synchronized void saveWorldStorage() {
        saveDocument(WORLD_FILE, toDocument(worldStore));
    }

    private synchronized void savePlayerStorage() {
        Document players = new Document();
        for (Map.Entry<String, Map<String, Object>> entry : playerStore.entrySet()) {
            players.put(entry.getKey(), toDocument(entry.getValue()));
        }
        saveDocument(PLAYER_FILE, players);
    }

    /** Encodes fully to memory before touching the target file, then swaps atomically. */
    private void saveDocument(String fileName, Document document) {
        File target = new File(storageDir(), fileName);
        File tmp = new File(storageDir(), fileName + TMP_SUFFIX);
        try {
            BasicOutputBuffer buffer = new BasicOutputBuffer();
            BsonBinaryWriter writer = new BsonBinaryWriter(buffer);
            new DocumentCodec().encode(writer, document, EncoderContext.builder().build());
            writer.close();
            byte[] bytes = buffer.toByteArray();
            Files.write(tmp.toPath(), bytes);
            try {
                Files.move(tmp.toPath(), target.toPath(),
                        StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(tmp.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (Exception e) {
            LuaTweakerLog.get().error(LogStage.SYSTEM,
                    "Failed to save " + fileName + " (" + e.getClass().getSimpleName()
                            + ": " + e.getMessage() + "); previous file left untouched");
            try {
                Files.deleteIfExists(tmp.toPath());
            } catch (IOException cleanupFailure) {
                LuaTweakerLog.get().warn(LogStage.SYSTEM,
                        "Could not delete temporary storage file: " + cleanupFailure.getMessage());
            }
        }
    }

    @Override
    public IDataStore GetWorldStorage() {
        ensureInitialized();
        return new IDataStore() {
            @Override
            public Object GetAsync(String key) {
                return convertToJavaObject(worldStore.get(key));
            }

            @Override
            public void SetAsync(String key, Object value) {
                if (value == null) {
                    worldStore.remove(key);
                } else {
                    worldStore.put(key, toStorable(key, value));
                }
                saveWorldStorage();
            }

            @Override
            public boolean RemoveAsync(String key) {
                boolean existed = worldStore.remove(key) != null;
                if (existed) {
                    saveWorldStorage();
                }
                return existed;
            }

            @Override
            public java.util.List<String> GetKeys() {
                return sortedKeys(worldStore.keySet());
            }

            @Override
            public double IncrementAsync(String key, double delta) {
                double[] updated = new double[1];
                worldStore.compute(key, (k, existing) -> {
                    updated[0] = requireNumber(k, existing) + delta;
                    return updated[0];
                });
                saveWorldStorage();
                return updated[0];
            }
        };
    }

    @Override
    public IDataStore GetPlayerStorage(String playerUuid) {
        ensureInitialized();
        return new IDataStore() {
            @Override
            public Object GetAsync(String key) {
                Map<String, Object> player = playerStore.get(playerUuid);
                return player == null ? null : convertToJavaObject(player.get(key));
            }

            @Override
            public void SetAsync(String key, Object value) {
                Object stored = value == null ? null : toStorable(key, value);
                playerStore.compute(playerUuid, (uuid, existing) -> {
                    Map<String, Object> next = existing == null
                            ? new ConcurrentHashMap<>() : new ConcurrentHashMap<>(existing);
                    if (stored == null) {
                        next.remove(key);
                    } else {
                        next.put(key, stored);
                    }
                    return next;
                });
                savePlayerStorage();
            }

            @Override
            public boolean RemoveAsync(String key) {
                boolean[] existed = new boolean[1];
                playerStore.compute(playerUuid, (uuid, existing) -> {
                    if (existing == null || !existing.containsKey(key)) {
                        return existing;
                    }
                    existed[0] = true;
                    Map<String, Object> next = new ConcurrentHashMap<>(existing);
                    next.remove(key);
                    return next;
                });
                if (existed[0]) {
                    savePlayerStorage();
                }
                return existed[0];
            }

            @Override
            public java.util.List<String> GetKeys() {
                Map<String, Object> player = playerStore.get(playerUuid);
                return sortedKeys(player == null ? java.util.Set.of() : player.keySet());
            }

            @Override
            public double IncrementAsync(String key, double delta) {
                double[] updated = new double[1];
                playerStore.compute(playerUuid, (uuid, existing) -> {
                    Map<String, Object> next = existing == null
                            ? new ConcurrentHashMap<>() : new ConcurrentHashMap<>(existing);
                    updated[0] = requireNumber(key, next.get(key)) + delta;
                    next.put(key, updated[0]);
                    return next;
                });
                savePlayerStorage();
                return updated[0];
            }
        };
    }

    @Override
    public IDataStore GetSessionStorage() {
        ensureInitialized();
        return new IDataStore() {
            @Override
            public Object GetAsync(String key) {
                return convertToJavaObject(sessionStore.get(key));
            }

            @Override
            public void SetAsync(String key, Object value) {
                if (value == null) {
                    sessionStore.remove(key);
                } else {
                    sessionStore.put(key, toStorable(key, value));
                }
            }

            @Override
            public boolean RemoveAsync(String key) {
                return sessionStore.remove(key) != null;
            }

            @Override
            public java.util.List<String> GetKeys() {
                return sortedKeys(sessionStore.keySet());
            }

            @Override
            public double IncrementAsync(String key, double delta) {
                double[] updated = new double[1];
                sessionStore.compute(key, (k, existing) -> {
                    updated[0] = requireNumber(k, existing) + delta;
                    return updated[0];
                });
                return updated[0];
            }
        };
    }

    private static java.util.List<String> sortedKeys(java.util.Set<String> keys) {
        java.util.List<String> sorted = new java.util.ArrayList<>(keys);
        java.util.Collections.sort(sorted);
        return sorted;
    }

    private static double requireNumber(String key, Object existing) {
        if (existing == null) {
            return 0.0;
        }
        if (existing instanceof Number number) {
            return number.doubleValue();
        }
        throw new IllegalStateException("Storage key '" + key + "' holds a "
                + existing.getClass().getSimpleName()
                + ", not a number; IncrementAsync would corrupt the stored value");
    }

    /**
     * Converts a Lua or Java value into an immutable snapshot that BSON can encode. Unsupported
     * values (functions, userdata, arbitrary Java objects) are rejected before any state changes,
     * so a bad write cannot corrupt the store.
     */
    private Object toStorable(String key, Object val) {
        Object stored = toStorableValue(key, val);
        return stored;
    }

    private Object toStorableValue(String key, Object val) {
        if (val == null) {
            return null;
        }
        if (val instanceof ILuaValue lv) {
            if (lv.isNil()) {
                return null;
            }
            if (lv.isTable()) {
                return tableToStorable(key, lv.asTable());
            }
            return requireScalar(key, lv.toJavaObject());
        }
        if (val instanceof Map<?, ?> map) {
            Map<String, Object> out = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                Object child = toStorableValue(key, entry.getValue());
                if (child != null) {
                    out.put(String.valueOf(entry.getKey()), child);
                }
            }
            return Collections.unmodifiableMap(out);
        }
        if (val instanceof List<?> list) {
            List<Object> out = new ArrayList<>(list.size());
            for (Object item : list) {
                out.add(toStorableValue(key, item));
            }
            return Collections.unmodifiableList(out);
        }
        return requireScalar(key, val);
    }

    private Object tableToStorable(String key, ILuaTable table) {
        int length = table.length();
        Map<ILuaValue, ILuaValue> entries = table.asMap();
        boolean isArray = length > 0 && entries.size() == length;
        if (isArray) {
            for (ILuaValue entryKey : entries.keySet()) {
                if (!entryKey.isNumber()) {
                    isArray = false;
                    break;
                }
                int index = entryKey.asInt();
                if (index < 1 || index > length) {
                    isArray = false;
                    break;
                }
            }
        }
        if (isArray) {
            List<Object> out = new ArrayList<>(length);
            for (int i = 1; i <= length; i++) {
                out.add(toStorableValue(key, table.rawget(i)));
            }
            return Collections.unmodifiableList(out);
        }
        Map<String, Object> out = new LinkedHashMap<>();
        for (Map.Entry<ILuaValue, ILuaValue> entry : entries.entrySet()) {
            Object child = toStorableValue(key, entry.getValue());
            if (child != null) {
                out.put(entry.getKey().asString(), child);
            }
        }
        return Collections.unmodifiableMap(out);
    }

    private Object requireScalar(String key, Object value) {
        if (value instanceof String || value instanceof Number || value instanceof Boolean) {
            return value;
        }
        throw new IllegalArgumentException(
                "Storage key '" + key + "' only accepts booleans, numbers, strings and tables of "
                        + "those values; got " + (value == null ? "null" : value.getClass().getName()));
    }

    private Document toDocument(Map<String, Object> values) {
        Document document = new Document();
        for (Map.Entry<String, Object> entry : values.entrySet()) {
            document.put(entry.getKey(), toBson(entry.getValue()));
        }
        return document;
    }

    private Object toBson(Object value) {
        if (value instanceof Map<?, ?> map) {
            Document document = new Document();
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                document.put(String.valueOf(entry.getKey()), toBson(entry.getValue()));
            }
            return document;
        }
        if (value instanceof List<?> list) {
            List<Object> out = new ArrayList<>(list.size());
            for (Object item : list) {
                out.add(toBson(item));
            }
            return out;
        }
        return value;
    }

    private Object convertToJavaObject(Object raw) {
        if (raw == null) return null;
        if (raw instanceof Map<?, ?> map) {
            ILuaTable table = engine.createTable();
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                Object convertedValue = convertToJavaObject(entry.getValue());
                ILuaValue val = wrapObject(convertedValue);
                table.rawset(String.valueOf(entry.getKey()), val);
            }
            return table;
        }
        if (raw instanceof List<?> list) {
            ILuaTable table = engine.createTable();
            int index = 1;
            for (Object item : list) {
                table.rawset(index++, wrapObject(convertToJavaObject(item)));
            }
            return table;
        }
        return raw;
    }

    private ILuaValue wrapObject(Object obj) {
        if (obj == null) return engine.nilValue();
        if (obj instanceof ILuaValue lv) return lv;
        if (obj instanceof String s) return engine.wrapString(s);
        if (obj instanceof Number n) return engine.wrapNumber(n.doubleValue());
        if (obj instanceof Boolean b) return engine.wrapBoolean(b);
        return engine.wrapUserdata(obj);
    }
}
