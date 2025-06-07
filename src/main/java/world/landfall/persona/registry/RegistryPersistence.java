package world.landfall.persona.registry;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.Tag;
import world.landfall.persona.Persona;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class RegistryPersistence {
    private static final String REGISTRY_FILE = "character_registry.dat";
    private static Path registryPath;

    public static void initialize(Path configDir) {
        try {
            // Ensure the directory exists
            Path personaDir = configDir.resolve(Persona.MODID).normalize();
            if (!personaDir.toFile().exists()) {
                personaDir.toFile().mkdirs();
            }
            
            registryPath = personaDir.resolve(REGISTRY_FILE).normalize();
            Persona.LOGGER.debug("[Persona] Registry file initialized at {}", registryPath);
        } catch (Exception e) {
            Persona.LOGGER.error("[Persona] Failed to initialize registry file", e);
            throw new RuntimeException("Failed to initialize registry file", e);
        }
    }

    public static Path getRegistryPath() {
        return registryPath;
    }

    public static void saveRegistry(Map<UUID, UUID> characterToPlayerMap, Map<String, UUID> characterNameMap) {
        if (registryPath == null) {
            Persona.LOGGER.error("[Persona] Cannot save registry: path not initialized");
            return;
        }

        try {
            CompoundTag root = new CompoundTag();
            
            // Save character to player mappings
            ListTag characterPlayerList = new ListTag();
            characterToPlayerMap.forEach((charId, playerId) -> {
                CompoundTag entry = new CompoundTag();
                entry.putString("characterId", charId.toString());
                entry.putString("playerId", playerId.toString());
                characterPlayerList.add(entry);
            });
            root.put("characterToPlayerMap", characterPlayerList);

            // Save character name mappings
            ListTag characterNameList = new ListTag();
            characterNameMap.forEach((name, charId) -> {
                CompoundTag entry = new CompoundTag();
                entry.putString("name", name);
                entry.putString("characterId", charId.toString());
                characterNameList.add(entry);
            });
            root.put("characterNameMap", characterNameList);

            // Save to file
            NbtIo.writeCompressed(root, registryPath);
            Persona.LOGGER.debug("[Persona] Registry saved successfully");
        } catch (IOException e) {
            Persona.LOGGER.error("[Persona] Failed to save registry", e);
        }
    }

    public static class RegistryData {
        public final ConcurrentHashMap<UUID, UUID> characterToPlayerMap;
        public final ConcurrentHashMap<String, UUID> characterNameMap;

        public RegistryData() {
            this.characterToPlayerMap = new ConcurrentHashMap<>();
            this.characterNameMap = new ConcurrentHashMap<>();
        }
    }

    public static RegistryData loadRegistry() {
        RegistryData data = new RegistryData();
        
        if (registryPath == null) {
            Persona.LOGGER.error("[Persona] Cannot load registry: path not initialized");
            return data;
        }

        if (!registryPath.toFile().exists()) {
            Persona.LOGGER.debug("[Persona] No existing registry file found, starting fresh");
            return data;
        }

        try {
            CompoundTag root = NbtIo.readCompressed(registryPath, NbtAccounter.unlimitedHeap());
            if (root != null) {
                // Load character to player mappings
                ListTag characterPlayerList = root.getList("characterToPlayerMap", Tag.TAG_COMPOUND);
                for (int i = 0; i < characterPlayerList.size(); i++) {
                    CompoundTag entry = characterPlayerList.getCompound(i);
                    UUID characterId = UUID.fromString(entry.getString("characterId"));
                    UUID playerId = UUID.fromString(entry.getString("playerId"));
                    data.characterToPlayerMap.put(characterId, playerId);
                }

                // Load character name mappings
                ListTag characterNameList = root.getList("characterNameMap", Tag.TAG_COMPOUND);
                for (int i = 0; i < characterNameList.size(); i++) {
                    CompoundTag entry = characterNameList.getCompound(i);
                    String name = entry.getString("name");
                    UUID characterId = UUID.fromString(entry.getString("characterId"));
                    data.characterNameMap.put(name.toLowerCase(), characterId);
                }

                Persona.LOGGER.debug("[Persona] Registry loaded successfully");
            }
        } catch (IOException e) {
            Persona.LOGGER.error("[Persona] Failed to load registry", e);
        }

        return data;
    }
} 