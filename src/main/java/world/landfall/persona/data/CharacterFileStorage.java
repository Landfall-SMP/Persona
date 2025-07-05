package world.landfall.persona.data;

import com.mojang.logging.LogUtils;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import org.slf4j.Logger;
import world.landfall.persona.Persona;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * File-based storage system for character data.
 * Each character is stored in a separate file named by its UUID to prevent playerdata size issues.
 */
public class CharacterFileStorage {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String CHARACTERS_DIR = "characters";
    private static final String CHARACTER_FILE_EXTENSION = ".dat";
    
    private static Path charactersDirectory;
    private static final ReentrantReadWriteLock storageLock = new ReentrantReadWriteLock();
    
    // Cache for loaded characters to improve performance
    private static final Map<UUID, CharacterProfile> characterCache = new ConcurrentHashMap<>();
    
    /**
     * Initializes the character file storage system.
     * @param worldPath The world directory path
     */
    public static void initialize(Path worldPath) {
        storageLock.writeLock().lock();
        try {
            // Create characters directory in world/persona/characters/
            Path personaDir = worldPath.resolve(Persona.MODID).normalize();
            charactersDirectory = personaDir.resolve(CHARACTERS_DIR).normalize();
            
            // Ensure directories exist
            Files.createDirectories(charactersDirectory);
            
            LOGGER.info("[CharacterFileStorage] Initialized character storage at: {}", charactersDirectory);
        } catch (IOException e) {
            LOGGER.error("[CharacterFileStorage] Failed to initialize character storage", e);
            throw new RuntimeException("Failed to initialize character file storage", e);
        } finally {
            storageLock.writeLock().unlock();
        }
    }
    
    /**
     * Saves a character to disk.
     * @param character The character to save
     * @return true if successful, false otherwise
     */
    public static boolean saveCharacter(CharacterProfile character) {
        if (character == null) {
            LOGGER.warn("[CharacterFileStorage] Cannot save null character");
            return false;
        }
        
        // Check if storage is initialized (server-side only)
        if (charactersDirectory == null) {
            LOGGER.debug("[CharacterFileStorage] Storage not initialized - this should only be called on server side");
            return false;
        }
        
        UUID characterId = character.getId();
        Path characterFile = getCharacterFilePath(characterId);
        
        storageLock.writeLock().lock();
        try {
            CompoundTag characterData = character.serialize();
            NbtIo.writeCompressed(characterData, characterFile);
            
            // Update cache
            characterCache.put(characterId, character);
            
            LOGGER.debug("[CharacterFileStorage] Saved character {} to file: {}", 
                character.getDisplayName(), characterFile.getFileName());
            return true;
            
        } catch (IOException e) {
            LOGGER.error("[CharacterFileStorage] Failed to save character {} ({})", 
                character.getDisplayName(), characterId, e);
            return false;
        } finally {
            storageLock.writeLock().unlock();
        }
    }
    
    /**
     * Loads a character from disk.
     * @param characterId The UUID of the character to load
     * @return The loaded character, or null if not found or error occurred
     */
    public static CharacterProfile loadCharacter(UUID characterId) {
        if (characterId == null) {
            LOGGER.warn("[CharacterFileStorage] Cannot load character with null ID");
            return null;
        }
        
        // Check if storage is initialized (server-side only)
        if (charactersDirectory == null) {
            LOGGER.debug("[CharacterFileStorage] Storage not initialized - this should only be called on server side");
            return null;
        }
        
        // Check cache first
        CharacterProfile cached = characterCache.get(characterId);
        if (cached != null) {
            return cached;
        }
        
        Path characterFile = getCharacterFilePath(characterId);
        
        storageLock.readLock().lock();
        try {
            if (!Files.exists(characterFile)) {
                LOGGER.debug("[CharacterFileStorage] Character file not found: {}", characterFile);
                return null;
            }
            
            CompoundTag characterData = NbtIo.readCompressed(characterFile, NbtAccounter.unlimitedHeap());
            if (characterData == null) {
                LOGGER.warn("[CharacterFileStorage] Failed to read character data from file: {}", characterFile);
                return null;
            }
            
            CharacterProfile character = CharacterProfile.deserialize(characterData);
            
            // Update cache
            characterCache.put(characterId, character);
            
            LOGGER.debug("[CharacterFileStorage] Loaded character {} from file: {}", 
                character.getDisplayName(), characterFile.getFileName());
            return character;
            
        } catch (IOException e) {
            LOGGER.error("[CharacterFileStorage] Failed to load character {}", characterId, e);
            return null;
        } finally {
            storageLock.readLock().unlock();
        }
    }
    
    /**
     * Deletes a character file from disk.
     * @param characterId The UUID of the character to delete
     * @return true if successful or file didn't exist, false if error occurred
     */
    public static boolean deleteCharacter(UUID characterId) {
        if (characterId == null) {
            LOGGER.warn("[CharacterFileStorage] Cannot delete character with null ID");
            return false;
        }
        
        Path characterFile = getCharacterFilePath(characterId);
        
        storageLock.writeLock().lock();
        try {
            // Remove from cache
            characterCache.remove(characterId);
            
            if (!Files.exists(characterFile)) {
                LOGGER.debug("[CharacterFileStorage] Character file already doesn't exist: {}", characterFile);
                return true;
            }
            
            Files.delete(characterFile);
            LOGGER.debug("[CharacterFileStorage] Deleted character file: {}", characterFile.getFileName());
            return true;
            
        } catch (IOException e) {
            LOGGER.error("[CharacterFileStorage] Failed to delete character {}", characterId, e);
            return false;
        } finally {
            storageLock.writeLock().unlock();
        }
    }
    
    /**
     * Checks if a character file exists.
     * @param characterId The UUID of the character to check
     * @return true if the character file exists, false otherwise
     */
    public static boolean characterExists(UUID characterId) {
        if (characterId == null) {
            return false;
        }
        
        // Check cache first
        if (characterCache.containsKey(characterId)) {
            return true;
        }
        
        Path characterFile = getCharacterFilePath(characterId);
        
        storageLock.readLock().lock();
        try {
            return Files.exists(characterFile);
        } finally {
            storageLock.readLock().unlock();
        }
    }
    
    /**
     * Loads all character IDs that belong to a specific player.
     * This method scans the character files and checks ownership.
     * @param playerId The player's UUID
     * @return Map of character IDs to their display names
     */
    public static Map<UUID, String> loadPlayerCharacterIds(UUID playerId) {
        if (playerId == null) {
            LOGGER.warn("[CharacterFileStorage] Cannot load characters for null player ID");
            return new HashMap<>();
        }
        
        Map<UUID, String> playerCharacters = new HashMap<>();
        
        storageLock.readLock().lock();
        try {
            if (!Files.exists(charactersDirectory)) {
                LOGGER.debug("[CharacterFileStorage] Characters directory doesn't exist yet");
                return playerCharacters;
            }
            
            // We need to check the global character registry to find which characters belong to this player
            // This is more efficient than loading every character file
            Map<UUID, UUID> characterToPlayerMap = world.landfall.persona.registry.GlobalCharacterRegistry.getCharacterToPlayerMap();
            
            for (Map.Entry<UUID, UUID> entry : characterToPlayerMap.entrySet()) {
                UUID characterId = entry.getKey();
                UUID characterPlayerId = entry.getValue();
                
                if (playerId.equals(characterPlayerId)) {
                    // Load character to get display name
                    CharacterProfile character = loadCharacter(characterId);
                    if (character != null) {
                        playerCharacters.put(characterId, character.getDisplayName());
                    }
                }
            }
            
            LOGGER.debug("[CharacterFileStorage] Found {} characters for player {}", playerCharacters.size(), playerId);
            return playerCharacters;
            
        } catch (Exception e) {
            LOGGER.error("[CharacterFileStorage] Failed to load character IDs for player {}", playerId, e);
            return new HashMap<>();
        } finally {
            storageLock.readLock().unlock();
        }
    }
    
    /**
     * Clears the character cache. Useful for testing or when memory is needed.
     */
    public static void clearCache() {
        storageLock.writeLock().lock();
        try {
            characterCache.clear();
            LOGGER.debug("[CharacterFileStorage] Cleared character cache");
        } finally {
            storageLock.writeLock().unlock();
        }
    }
    
    /**
     * Gets the file path for a character.
     * @param characterId The character's UUID
     * @return The path to the character file
     * @throws IllegalStateException if the storage system is not initialized
     */
    private static Path getCharacterFilePath(UUID characterId) {
        if (charactersDirectory == null) {
            throw new IllegalStateException("CharacterFileStorage is not initialized. This should only be called on the server side.");
        }
        String filename = characterId.toString() + CHARACTER_FILE_EXTENSION;
        return charactersDirectory.resolve(filename);
    }
    
    /**
     * Gets the characters directory path.
     * @return The path to the characters directory
     */
    public static Path getCharactersDirectory() {
        return charactersDirectory;
    }
} 