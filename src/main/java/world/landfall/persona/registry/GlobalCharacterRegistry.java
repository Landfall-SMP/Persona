package world.landfall.persona.registry;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import world.landfall.persona.Persona;
import world.landfall.persona.data.PlayerCharacterData;
import world.landfall.persona.data.PlayerCharacterCapability;

import java.util.Map;
import java.util.UUID;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * Thread-safe global registry for character profiles.
 * Manages the mapping between characters, their owners, and names.
 */
@EventBusSubscriber(modid = Persona.MODID)
public class GlobalCharacterRegistry {
    private static final Map<UUID, UUID> characterToPlayerMap = new ConcurrentHashMap<>();
    private static final Map<String, UUID> characterNameMap = new ConcurrentHashMap<>();
    private static final ReentrantReadWriteLock registryLock = new ReentrantReadWriteLock();
    
    public static void initialize() {
        registryLock.writeLock().lock();
        try {
            characterToPlayerMap.clear();
            characterNameMap.clear();
            Persona.LOGGER.info("[Persona] Global Character Registry initialized.");
        } finally {
            registryLock.writeLock().unlock();
        }
    }
    
    /**
     * Attempts to register a character with the given name.
     * @param characterId The UUID of the character
     * @param playerId The UUID of the player who owns the character
     * @param characterName The name to register for the character
     * @return true if registration was successful, false if the name was already taken
     */
    public static boolean registerCharacter(UUID characterId, UUID playerId, String characterName) {
        if (characterId == null || playerId == null || characterName == null) {
            throw new IllegalArgumentException("Character registration parameters cannot be null");
        }

        String normalizedName = characterName.toLowerCase();
        registryLock.writeLock().lock();
        try {
            // Check if name is already taken
            if (characterNameMap.containsKey(normalizedName)) {
                UUID existingCharId = characterNameMap.get(normalizedName);
                // Allow if it's the same character being re-registered (e.g., during player login)
                if (!characterId.equals(existingCharId)) {
                    return false;
                }
            }
            
            characterToPlayerMap.put(characterId, playerId);
            characterNameMap.put(normalizedName, characterId);
            return true;
        } finally {
            registryLock.writeLock().unlock();
        }
    }
    
    /**
     * Atomically updates a character's name.
     * @param characterId The UUID of the character
     * @param oldName The current name of the character
     * @param newName The new name to assign
     * @return true if the update was successful, false if the new name was taken
     */
    public static boolean updateCharacterName(UUID characterId, String oldName, String newName) {
        if (characterId == null || oldName == null || newName == null) {
            throw new IllegalArgumentException("Character update parameters cannot be null");
        }

        String normalizedOldName = oldName.toLowerCase();
        String normalizedNewName = newName.toLowerCase();
        
        registryLock.writeLock().lock();
        try {
            // Verify the old name belongs to this character
            UUID existingCharId = characterNameMap.get(normalizedOldName);
            if (!characterId.equals(existingCharId)) {
                return false;
            }
            
            // Check if new name is already taken by a different character
            UUID existingForNewName = characterNameMap.get(normalizedNewName);
            if (existingForNewName != null && !characterId.equals(existingForNewName)) {
                return false;
            }
            
            // Update the name mapping
            characterNameMap.remove(normalizedOldName);
            characterNameMap.put(normalizedNewName, characterId);
            return true;
        } finally {
            registryLock.writeLock().unlock();
        }
    }
    
    public static void unregisterCharacter(UUID characterId, String characterName) {
        if (characterId == null || characterName == null) {
            throw new IllegalArgumentException("Character unregistration parameters cannot be null");
        }

        String normalizedName = characterName.toLowerCase();
        registryLock.writeLock().lock();
        try {
            // Only remove the name if it belongs to this character
            UUID existingCharId = characterNameMap.get(normalizedName);
            if (characterId.equals(existingCharId)) {
                characterNameMap.remove(normalizedName);
            }
            characterToPlayerMap.remove(characterId);
        } finally {
            registryLock.writeLock().unlock();
        }
    }
    
    public static Optional<UUID> getPlayerForCharacter(UUID characterId) {
        if (characterId == null) {
            return Optional.empty();
        }
        registryLock.readLock().lock();
        try {
            return Optional.ofNullable(characterToPlayerMap.get(characterId));
        } finally {
            registryLock.readLock().unlock();
        }
    }

    public static boolean isNameTaken(String name) {
        if (name == null) {
            return false;
        }
        String normalizedName = name.toLowerCase();
        registryLock.readLock().lock();
        try {
            return characterNameMap.containsKey(normalizedName);
        } finally {
            registryLock.readLock().unlock();
        }
    }

    public static Optional<UUID> getCharacterIdByName(String name) {
        if (name == null) {
            return Optional.empty();
        }
        String normalizedName = name.toLowerCase();
        registryLock.readLock().lock();
        try {
            return Optional.ofNullable(characterNameMap.get(normalizedName));
        } finally {
            registryLock.readLock().unlock();
        }
    }
    
    @SubscribeEvent
    public static void onPlayerLogin(final PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            PlayerCharacterData data = player.getData(PlayerCharacterCapability.CHARACTER_DATA);
            if (data != null) {
                registryLock.writeLock().lock();
                try {
                    // Register all characters atomically
                    data.getCharacters().forEach((id, profile) -> {
                        characterToPlayerMap.put(id, player.getUUID());
                        characterNameMap.put(profile.getDisplayName().toLowerCase(), id);
                    });
                    PersonaNetworking.sendToPlayer(data, player);
                } finally {
                    registryLock.writeLock().unlock();
                }
            }
        }
    }
    
    @SubscribeEvent
    public static void onPlayerLogout(final PlayerEvent.PlayerLoggedOutEvent event) {
        removePlayerCharacters(event.getEntity());
    }
    
    public static void removePlayerCharacters(Player player) {
        PlayerCharacterData data = player.getData(PlayerCharacterCapability.CHARACTER_DATA);
        if (data != null) {
            registryLock.writeLock().lock();
            try {
                data.getCharacters().forEach((id, profile) -> {
                    characterToPlayerMap.remove(id);
                    characterNameMap.remove(profile.getDisplayName().toLowerCase());
                });
            } finally {
                registryLock.writeLock().unlock();
            }
        } else {
            Persona.LOGGER.warn("[Persona] Player {} missing character data for character removal from registry.",
                              player.getName().getString());
        }
    }

    /**
     * Synchronizes the registry data with a player.
     * This method is thread-safe and can be called from any thread.
     * @param player The player to synchronize with
     */
    public static void syncRegistry(ServerPlayer player) {
        if (player == null) {
            return;
        }
        PlayerCharacterData data = player.getData(PlayerCharacterCapability.CHARACTER_DATA);
        if (data != null) {
            registryLock.readLock().lock();
            try {
                PersonaNetworking.sendToPlayer(data, player);
            } finally {
                registryLock.readLock().unlock();
            }
        }
    }
} 