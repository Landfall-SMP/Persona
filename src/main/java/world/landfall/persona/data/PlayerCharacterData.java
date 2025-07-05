package world.landfall.persona.data;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class PlayerCharacterData {
    private UUID activeCharacterId;
    // Character IDs mapped to their display names (for quick access without loading full character data)
    private final Map<UUID, String> characterIds;
    // Client-side cache for character data received from server
    private final Map<UUID, CharacterProfile> clientCharacterCache;
    
    public PlayerCharacterData() {
        this.characterIds = new HashMap<>();
        this.clientCharacterCache = new HashMap<>();
        // Persona.LOGGER.info("PlayerCharacterData created.");
    }
    
    public CompoundTag serialize() {
        CompoundTag tag = new CompoundTag();
        if (activeCharacterId != null) {
            tag.putUUID("activeCharacter", activeCharacterId);
        }
        
        ListTag charactersList = new ListTag();
        characterIds.forEach((uuid, displayName) -> {
            CompoundTag characterEntry = new CompoundTag();
            characterEntry.putUUID("id", uuid);
            characterEntry.putString("displayName", displayName);
            charactersList.add(characterEntry);
        });
        tag.put("characterIds", charactersList);
        
        return tag;
    }
    
    public static PlayerCharacterData deserialize(CompoundTag tag) {
        PlayerCharacterData data = new PlayerCharacterData();
        
        if (tag.contains("activeCharacter", Tag.TAG_INT_ARRAY)) { // UUIDs are stored as TAG_INT_ARRAY
            data.activeCharacterId = tag.getUUID("activeCharacter");
        }
        
        // Load character IDs and display names
        if (tag.contains("characterIds", Tag.TAG_LIST)) {
            ListTag charactersList = tag.getList("characterIds", Tag.TAG_COMPOUND);
            for (Tag t : charactersList) {
                if (t instanceof CompoundTag entryTag) {
                    UUID id = entryTag.getUUID("id");
                    String displayName = entryTag.getString("displayName");
                    data.characterIds.put(id, displayName);
                }
            }
        }
        
        return data;
    }
    
    public UUID getActiveCharacterId() {
        return activeCharacterId;
    }
    
    public void setActiveCharacterId(UUID id) {
        this.activeCharacterId = id;
    }
    
    /**
     * Gets all character IDs and their display names owned by this player.
     * @return Map of character IDs to display names
     */
    public Map<UUID, String> getCharacterIds() {
        return new HashMap<>(characterIds);
    }
    
    /**
     * Gets a map of character IDs to CharacterProfile objects.
     * This method loads characters from file storage as needed.
     * @return Map of character IDs to CharacterProfile objects
     */
    public Map<UUID, CharacterProfile> getCharacters() {
        Map<UUID, CharacterProfile> characters = new HashMap<>();
        for (UUID characterId : characterIds.keySet()) {
            CharacterProfile character = getCharacter(characterId);
            if (character != null) {
                characters.put(characterId, character);
            }
        }
        return characters;
    }
    
    /**
     * Gets a specific character by ID. On server side, loads from file storage. On client side, uses cached data.
     * @param id The character ID
     * @return The character profile, or null if not found
     */
    public CharacterProfile getCharacter(UUID id) {
        if (!characterIds.containsKey(id)) {
            return null;
        }
        
        // Check client-side cache first
        CharacterProfile cached = clientCharacterCache.get(id);
        if (cached != null) {
            return cached;
        }
        
        // Try to load from file storage (server-side only)
        CharacterProfile character = CharacterFileStorage.loadCharacter(id);
        if (character != null) {
            // Cache it for future use
            clientCharacterCache.put(id, character);
        }
        
        return character;
    }
    
    /**
     * Adds a character to this player's character list and saves it to file storage.
     * @param id The character ID
     * @param profile The character profile
     */
    public void addCharacter(UUID id, CharacterProfile profile) {
        characterIds.put(id, profile.getDisplayName());
        CharacterFileStorage.saveCharacter(profile);
    }
    
    /**
     * Removes a character from this player's character list and deletes it from file storage.
     * @param id The character ID to remove
     */
    public void removeCharacter(UUID id) {
        characterIds.remove(id);
        CharacterFileStorage.deleteCharacter(id);
        if (activeCharacterId != null && activeCharacterId.equals(id)) {
            activeCharacterId = null;
        }
    }
    
    /**
     * Updates the display name for a character.
     * @param id The character ID
     * @param newDisplayName The new display name
     */
    public void updateCharacterDisplayName(UUID id, String newDisplayName) {
        if (characterIds.containsKey(id)) {
            characterIds.put(id, newDisplayName);
            // Also update the character file
            CharacterProfile character = CharacterFileStorage.loadCharacter(id);
            if (character != null) {
                CharacterFileStorage.saveCharacter(character);
            }
        }
    }
    
    /**
     * Checks if this player has a character with the given ID.
     * @param id The character ID to check
     * @return true if the character exists, false otherwise
     */
    public boolean hasCharacter(UUID id) {
        return characterIds.containsKey(id);
    }
    
    /**
     * Gets the number of characters this player has.
     * @return The number of characters
     */
    public int getCharacterCount() {
        return characterIds.size();
    }
    
    public void copyFrom(PlayerCharacterData other) {
        this.characterIds.clear();
        this.characterIds.putAll(other.characterIds);
        this.activeCharacterId = other.activeCharacterId;
    }
    
    /**
     * Loads character IDs from the file storage system.
     * This is used during player login to populate the character list.
     * @param playerId The player's UUID
     */
    public void loadCharacterIdsFromStorage(UUID playerId) {
        Map<UUID, String> storedCharacters = CharacterFileStorage.loadPlayerCharacterIds(playerId);
        this.characterIds.putAll(storedCharacters);
    }
    
    /**
     * Caches a character profile on the client side.
     * This is used when receiving character data from the server.
     * @param character The character profile to cache
     */
    public void cacheCharacter(CharacterProfile character) {
        if (character != null) {
            clientCharacterCache.put(character.getId(), character);
            characterIds.put(character.getId(), character.getDisplayName());
        }
    }
    
    /**
     * Clears the client-side character cache.
     * This is useful when disconnecting from a server.
     */
    public void clearClientCache() {
        clientCharacterCache.clear();
    }
}
