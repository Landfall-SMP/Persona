package world.landfall.persona.features.landfalladdon;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;

import com.mojang.logging.LogUtils;
import org.slf4j.Logger;

import world.landfall.persona.data.CharacterProfile;
import world.landfall.persona.data.PlayerCharacterCapability;
import world.landfall.persona.data.PlayerCharacterData;

import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.UUID;

/**
 * Handles origin-related operations for the LandfallAddon system 
 * This class manages the storage and retrieval of origin data for character profiles
 */
public class OriginHandler {
    private static final Logger LOGGER = LogUtils.getLogger();
    
    // NBT key for storing origin data
    private static final String ORIGIN_KEY = "landfall_origin";
    
    // Constants for validation
    private static final int MAX_ORIGIN_LENGTH = 100;
    private static final String DEFAULT_ORIGIN = "unknown";
    
    // Thread safety for character data access
    private static final ReadWriteLock dataLock = new ReentrantReadWriteLock();

    /**
     * Sets the origin for a character profile
     */
    public static void setOrigin(CharacterProfile profile, String origin) {
        Objects.requireNonNull(profile, "CharacterProfile cannot be null");
        Objects.requireNonNull(origin, "Origin cannot be null");
        
        if (origin.isBlank()) {
            throw new IllegalArgumentException("Origin cannot be blank");
        }
        
        dataLock.writeLock().lock();
        try {
            if (!isProfileValid(profile)) {
                LOGGER.warn("Attempted to set origin for invalid profile: {}", getProfileName(profile));
                return;
            }
            
            String sanitizedOrigin = sanitizeOrigin(origin);
            if (sanitizedOrigin == null) {
                LOGGER.error("Failed to sanitize origin '{}' for profile {}", origin, getProfileName(profile));
                return;
            }
            
            CompoundTag modData = getOrCreateModData(profile);
            if (modData == null) {
                LOGGER.error("Failed to get/create mod data for profile {}", getProfileName(profile));
                return;
            }
            
            modData.putString(ORIGIN_KEY, sanitizedOrigin);
            
            LOGGER.debug("Set origin '{}' for profile {}", sanitizedOrigin, getProfileName(profile));
            
        } catch (Exception e) {
            LOGGER.error("Error setting origin '{}' for profile {}", origin, getProfileName(profile), e);
            throw new RuntimeException("Failed to set origin", e);
        } finally {
            dataLock.writeLock().unlock();
        }
    }

    /**
     * Gets the origin for a character profile
     */
    public static String getOrigin(CharacterProfile profile) {
        Objects.requireNonNull(profile, "CharacterProfile cannot be null");
        
        dataLock.readLock().lock();
        try {
            if (!isProfileValid(profile)) {
                LOGGER.debug("Profile invalid, returning default origin for: {}", getProfileName(profile));
                return DEFAULT_ORIGIN;
            }
            
            CompoundTag modData = getModData(profile);
            if (modData == null) {
                LOGGER.debug("No mod data found, returning default origin for profile: {}", getProfileName(profile));
                return DEFAULT_ORIGIN;
            }
            
            if (!modData.contains(ORIGIN_KEY)) {
                LOGGER.debug("No origin key found, returning default origin for profile: {}", getProfileName(profile));
                return DEFAULT_ORIGIN;
            }
            
            String origin = modData.getString(ORIGIN_KEY);
            if (origin == null || origin.isBlank()) {
                LOGGER.debug("Origin is null/blank, returning default origin for profile: {}", getProfileName(profile));
                return DEFAULT_ORIGIN;
            }
            
            String validatedOrigin = validateRetrievedOrigin(origin);
            LOGGER.debug("Retrieved origin '{}' for profile {}", validatedOrigin, getProfileName(profile));
            
            return validatedOrigin;
            
        } catch (Exception e) {
            LOGGER.error("Error getting origin for profile {}, returning default", getProfileName(profile), e);
            return DEFAULT_ORIGIN;
        } finally {
            dataLock.readLock().unlock();
        }
    }

    /**
     * Checks if a character profile has an origin set.
     */
    public static boolean hasOrigin(CharacterProfile profile) {
        Objects.requireNonNull(profile, "CharacterProfile cannot be null");
        
        dataLock.readLock().lock();
        try {
            if (!isProfileValid(profile)) {
                return false;
            }
            
            CompoundTag modData = getModData(profile);
            if (modData == null) {
                return false;
            }
            
            if (!modData.contains(ORIGIN_KEY)) {
                return false;
            }
            
            String origin = modData.getString(ORIGIN_KEY);
            return origin != null && !origin.isBlank() && !DEFAULT_ORIGIN.equals(origin);
            
        } catch (Exception e) {
            LOGGER.error("Error checking if profile {} has origin", getProfileName(profile), e);
            return false;
        } finally {
            dataLock.readLock().unlock();
        }
    }

    /**
     * Clears the origin for a character profile.
     */
    public static void clearOrigin(CharacterProfile profile) {
        Objects.requireNonNull(profile, "CharacterProfile cannot be null");
        
        dataLock.writeLock().lock();
        try {
            if (!isProfileValid(profile)) {
                LOGGER.warn("Attempted to clear origin for invalid profile: {}", getProfileName(profile));
                return;
            }
            
            CompoundTag modData = getModData(profile);
            if (modData == null) {
                LOGGER.debug("No mod data found, nothing to clear for profile: {}", getProfileName(profile));
                return;
            }
            
            if (modData.contains(ORIGIN_KEY)) {
                modData.remove(ORIGIN_KEY);
                LOGGER.debug("Cleared origin for profile {}", getProfileName(profile));
            } else {
                LOGGER.debug("No origin to clear for profile {}", getProfileName(profile));
            }
            
        } catch (Exception e) {
            LOGGER.error("Error clearing origin for profile {}", getProfileName(profile), e);
        } finally {
            dataLock.writeLock().unlock();
        }
    }

    /**
     * Gets the origin for a player's current character profile.
     */
    public static String getPlayerOrigin(ServerPlayer player) {
        Objects.requireNonNull(player, "ServerPlayer cannot be null");
        
        try {
            Optional<CharacterProfile> profileOpt = getPlayerCharacterProfile(player);
            if (profileOpt.isEmpty()) {
                LOGGER.debug("No character profile found for player {}, returning default origin", 
                    getPlayerName(player));
                return DEFAULT_ORIGIN;
            }
            
            return getOrigin(profileOpt.get());
            
        } catch (Exception e) {
            LOGGER.error("Error getting origin for player {}, returning default", 
                getPlayerName(player), e);
            return DEFAULT_ORIGIN;
        }
    }

    /**
     * Sets the origin for a player's current character profile.
     */
    public static void setPlayerOrigin(ServerPlayer player, String origin) {
        Objects.requireNonNull(player, "ServerPlayer cannot be null");
        Objects.requireNonNull(origin, "Origin cannot be null");
        
        if (origin.isBlank()) {
            throw new IllegalArgumentException("Origin cannot be blank");
        }
        
        try {
            Optional<CharacterProfile> profileOpt = getPlayerCharacterProfile(player);
            if (profileOpt.isEmpty()) {
                LOGGER.warn("No character profile found for player {}, cannot set origin", 
                    getPlayerName(player));
                return;
            }
            
            setOrigin(profileOpt.get(), origin);
            LOGGER.debug("Set origin '{}' for player {}", origin, getPlayerName(player));
            
        } catch (Exception e) {
            LOGGER.error("Error setting origin '{}' for player {}", origin, getPlayerName(player), e);
            throw new RuntimeException("Failed to set player origin", e);
        }
    }

    /**
     * Gets a player's character profile.
     */
    private static Optional<CharacterProfile> getPlayerCharacterProfile(ServerPlayer player) {
        try {
            if (!isPlayerValid(player)) {
                return Optional.empty();
            }
            
            PlayerCharacterData characterData = player.getData(PlayerCharacterCapability.CHARACTER_DATA);
            if (characterData == null) {
                LOGGER.debug("No character data found for player {}", getPlayerName(player));
                return Optional.empty();
            }
            
            UUID activeCharacterId = characterData.getActiveCharacterId();
            if (activeCharacterId == null) {
                LOGGER.debug("No active character ID for player {}", getPlayerName(player));
                return Optional.empty();
            }
            
            CharacterProfile profile = characterData.getCharacter(activeCharacterId);
            return Optional.ofNullable(profile);
            
        } catch (Exception e) {
            LOGGER.error("Error getting character profile for player {}", getPlayerName(player), e);
            return Optional.empty();
        }
    }

    /**
     * Gets mod data from a character profile.
     */
    private static CompoundTag getModData(CharacterProfile profile) {
        try {
            return profile.getModData(LandfallAddonData.DATA_KEY);
        } catch (Exception e) {
            LOGGER.error("Error getting mod data for profile {}", getProfileName(profile), e);
            return null;
        }
    }

    /**
     * Gets or creates mod data for a character profile.
     */
    private static CompoundTag getOrCreateModData(CharacterProfile profile) {
        try {
            CompoundTag modData = profile.getModData(LandfallAddonData.DATA_KEY);
            if (modData == null) {
                modData = new CompoundTag();
            }
            return modData;
        } catch (Exception e) {
            LOGGER.error("Error getting/creating mod data for profile {}", getProfileName(profile), e);
            return null;
        }
    }

    /**
     * Sanitizes an origin string for safe storage.
     */
    private static String sanitizeOrigin(String origin) {
        try {
            if (origin == null) {
                return null;
            }
            
            String sanitized = origin.trim();
            
            if (sanitized.length() > MAX_ORIGIN_LENGTH) {
                LOGGER.warn("Origin too long ({}), truncating: {}", sanitized.length(), sanitized);
                sanitized = sanitized.substring(0, MAX_ORIGIN_LENGTH);
            }
            
            if (sanitized.chars().anyMatch(Character::isISOControl)) {
                LOGGER.warn("Origin contains control characters, rejecting: {}", sanitized);
                return null;
            }
            
            return sanitized;
            
        } catch (Exception e) {
            LOGGER.error("Error sanitizing origin: {}", origin, e);
            return null;
        }
    }

    /**
     * Validates a retrieved origin string.
     */
    private static String validateRetrievedOrigin(String origin) {
        if (origin == null || origin.isBlank()) {
            return DEFAULT_ORIGIN;
        }
        
        if (origin.length() > MAX_ORIGIN_LENGTH) {
            LOGGER.warn("Retrieved origin too long, truncating: {}", origin);
            return origin.substring(0, MAX_ORIGIN_LENGTH);
        }
        
        return origin;
    }

    /**
     * Validates that a character profile is in a valid state.
     */
    private static boolean isProfileValid(CharacterProfile profile) {
        try {
            return profile != null && profile.getDisplayName() != null;
        } catch (Exception e) {
            LOGGER.debug("Error validating profile", e);
            return false;
        }
    }

    /**
     * Validates that a player is in a valid state.
     */
    private static boolean isPlayerValid(ServerPlayer player) {
        try {
            return player != null && 
                   player.connection != null && 
                   !player.hasDisconnected();
        } catch (Exception e) {
            LOGGER.debug("Error validating player", e);
            return false;
        }
    }

    /**
     * Gets a character profile's name for logging purposes.
     */
    private static String getProfileName(CharacterProfile profile) {
        try {
            if (profile == null) {
                return "null";
            }
            
            String displayName = profile.getDisplayName();
            return displayName != null ? displayName : "unnamed";
            
        } catch (Exception e) {
            return "error-getting-name";
        }
    }

    /**
     * Gets a player's name for logging purposes.
     */
    private static String getPlayerName(ServerPlayer player) {
        try {
            if (player == null) {
                return "null";
            }
            
            var name = player.getName();
            if (name == null) {
                return "unnamed";
            }
            
            String nameString = name.getString();
            return nameString != null ? nameString : "unnamed";
            
        } catch (Exception e) {
            return "error-getting-name";
        }
    }

    /**
     * Validates that the origin handler is properly configured.
     */
    public static boolean validateConfiguration() {
        try {
            Class.forName("net.minecraft.nbt.CompoundTag");
            Class.forName("world.landfall.persona.data.CharacterProfile");
            Class.forName("world.landfall.persona.data.PlayerCharacterCapability");
            Class.forName("world.landfall.persona.data.PlayerCharacterData");
            
            if (ORIGIN_KEY == null || ORIGIN_KEY.isBlank()) {
                LOGGER.error("OriginHandler ORIGIN_KEY is null or blank");
                return false;
            }
            
            dataLock.readLock().lock();
            dataLock.readLock().unlock();
            dataLock.writeLock().lock();
            dataLock.writeLock().unlock();
            
            LOGGER.debug("OriginHandler configuration validation passed");
            return true;
            
        } catch (ClassNotFoundException e) {
            LOGGER.error("OriginHandler configuration validation failed - missing required class", e);
            return false;
        } catch (Exception e) {
            LOGGER.error("OriginHandler configuration validation failed", e);
            return false;
        }
    }
} 