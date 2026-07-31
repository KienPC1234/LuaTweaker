package com.luatweaker.storage;

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
import java.io.FileOutputStream;
import java.nio.ByteBuffer;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class StorageServiceImpl implements IRobloxStorageService {
    private final ILuaEngine engine;
    private final File storageDir;

    private final Map<String, Object> sessionStore = new ConcurrentHashMap<>();
    private final Document worldDocument = new Document();
    private final Document playerDocument = new Document();

    public StorageServiceImpl(ILuaEngine engine) {
        this.engine = engine;
        this.storageDir = Platform.get().getStorageDirectory();
        if (!storageDir.exists()) {
            storageDir.mkdirs();
        }
        loadWorldStorage();
        loadPlayerStorage();
    }

    private void loadWorldStorage() {
        File file = new File(storageDir, "world_storage.bson");
        if (file.exists()) {
            try (FileInputStream in = new FileInputStream(file)) {
                byte[] bytes = in.readAllBytes();
                if (bytes.length > 0) {
                    BsonBinaryReader reader = new BsonBinaryReader(ByteBuffer.wrap(bytes));
                    Document doc = new DocumentCodec().decode(reader, DecoderContext.builder().build());
                    if (doc != null) {
                        worldDocument.putAll(doc);
                    }
                }
            } catch (Exception ignored) {}
        }
    }

    private synchronized void saveWorldStorage() {
        File file = new File(storageDir, "world_storage.bson");
        try (FileOutputStream out = new FileOutputStream(file)) {
            BasicOutputBuffer buffer = new BasicOutputBuffer();
            BsonBinaryWriter writer = new BsonBinaryWriter(buffer);
            new DocumentCodec().encode(writer, worldDocument, EncoderContext.builder().build());
            out.write(buffer.toByteArray());
        } catch (Exception ignored) {}
    }

    private void loadPlayerStorage() {
        File file = new File(storageDir, "player_storage.bson");
        if (file.exists()) {
            try (FileInputStream in = new FileInputStream(file)) {
                byte[] bytes = in.readAllBytes();
                if (bytes.length > 0) {
                    BsonBinaryReader reader = new BsonBinaryReader(ByteBuffer.wrap(bytes));
                    Document doc = new DocumentCodec().decode(reader, DecoderContext.builder().build());
                    if (doc != null) {
                        playerDocument.putAll(doc);
                    }
                }
            } catch (Exception ignored) {}
        }
    }

    private synchronized void savePlayerStorage() {
        File file = new File(storageDir, "player_storage.bson");
        try (FileOutputStream out = new FileOutputStream(file)) {
            BasicOutputBuffer buffer = new BasicOutputBuffer();
            BsonBinaryWriter writer = new BsonBinaryWriter(buffer);
            new DocumentCodec().encode(writer, playerDocument, EncoderContext.builder().build());
            out.write(buffer.toByteArray());
        } catch (Exception ignored) {}
    }

    @Override
    public IDataStore GetWorldStorage() {
        return new IDataStore() {
            @Override
            public Object GetAsync(String key) {
                return convertToJavaObject(worldDocument.get(key));
            }

            @Override
            public void SetAsync(String key, Object value) {
                if (value == null) {
                    worldDocument.remove(key);
                } else {
                    worldDocument.put(key, convertToPrimitive(value));
                }
                saveWorldStorage();
            }
        };
    }

    @Override
    public IDataStore GetPlayerStorage(String playerUuid) {
        return new IDataStore() {
            @Override
            public Object GetAsync(String key) {
                Document pDoc = (Document) playerDocument.get(playerUuid);
                if (pDoc == null) return null;
                return convertToJavaObject(pDoc.get(key));
            }

            @Override
            public void SetAsync(String key, Object value) {
                Document pDoc = (Document) playerDocument.get(playerUuid);
                if (pDoc == null) {
                    pDoc = new Document();
                    playerDocument.put(playerUuid, pDoc);
                }
                if (value == null) {
                    pDoc.remove(key);
                } else {
                    pDoc.put(key, convertToPrimitive(value));
                }
                savePlayerStorage();
            }
        };
    }

    @Override
    public IDataStore GetSessionStorage() {
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
                    sessionStore.put(key, convertToPrimitive(value));
                }
            }
        };
    }

    private Object convertToPrimitive(Object val) {
        if (val instanceof ILuaValue lv) {
            if (lv.isNil()) return null;
            if (lv.isTable()) {
                ILuaTable table = lv.asTable();
                Document doc = new Document();
                for (Map.Entry<ILuaValue, ILuaValue> entry : table.asMap().entrySet()) {
                    doc.put(entry.getKey().asString(), convertToPrimitive(entry.getValue()));
                }
                return doc;
            }
            return lv.toJavaObject();
        }
        return val;
    }

    private Object convertToJavaObject(Object raw) {
        if (raw == null) return null;
        if (raw instanceof Document doc) {
            ILuaTable table = engine.createTable();
            for (Map.Entry<String, Object> entry : doc.entrySet()) {
                Object convertedValue = convertToJavaObject(entry.getValue());
                ILuaValue key = engine.wrapString(entry.getKey());
                ILuaValue val = wrapObject(convertedValue);
                table.rawset(key.asString(), val);
            }
            return table;
        }
        if (raw instanceof Map<?, ?> map) {
            ILuaTable table = engine.createTable();
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                Object convertedValue = convertToJavaObject(entry.getValue());
                ILuaValue key = engine.wrapString(entry.getKey().toString());
                ILuaValue val = wrapObject(convertedValue);
                table.rawset(key.asString(), val);
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
