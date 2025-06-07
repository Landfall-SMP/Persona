package world.landfall.persona.util;

import com.mojang.logging.LogUtils;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import org.slf4j.Logger;
import world.landfall.persona.Persona;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages cooldowns for character switching to prevent rapid switching that could cause issues.
 */
@EventBusSubscriber(modid = Persona.MODID)
public final class CharacterSwitchCooldownManager {
    private static final Logger LOGGER = LogUtils.getLogger();
    
    /** Cooldown duration in milliseconds (3 seconds) */
    private static final long COOLDOWN_DURATION_MS = 3000L;
    
    /** Map to store the last switch time for each player UUID */
    private static final ConcurrentHashMap<UUID, Long> lastSwitchTimes = new ConcurrentHashMap<>();
    
    // Private constructor to prevent instantiation
    private CharacterSwitchCooldownManager() {
        throw new UnsupportedOperationException("Utility class cannot be instantiated");
    }
    
    /**
     * Checks if a player can switch characters (not on cooldown).
     * 
     * @param player The player attempting to switch characters, must not be null
     * @return true if the player can switch, false if they're on cooldown or player is null
     * @throws IllegalArgumentException if player is null
     */
    public static boolean canSwitchCharacter(@Nullable ServerPlayer player) {
        if (player == null) {
            LOGGER.warn("[CharacterSwitchCooldown] Attempted to check cooldown for null player");
            return false;
        }
        
        try {
            UUID playerId = player.getUUID();
            long currentTime = System.currentTimeMillis();
            Long lastSwitchTime = lastSwitchTimes.get(playerId);
            
            if (lastSwitchTime == null) {
                // Player has never switched, allow the switch
                return true;
            }
            
            long timeSinceLastSwitch = currentTime - lastSwitchTime;
            boolean canSwitch = timeSinceLastSwitch >= COOLDOWN_DURATION_MS;
            
            if (!canSwitch && LOGGER.isDebugEnabled()) {
                LOGGER.debug("[CharacterSwitchCooldown] Player {} is on cooldown. Time remaining: {}ms", 
                    player.getName().getString(), COOLDOWN_DURATION_MS - timeSinceLastSwitch);
            }
            
            return canSwitch;
        } catch (Exception e) {
            LOGGER.error("[CharacterSwitchCooldown] Error checking cooldown for player {}: {}", 
                player.getName().getString(), e.getMessage(), e);
            // Fail safe: allow switch if there's an error
            return true;
        }
    }
    
    /**
     * Gets the remaining cooldown time for a player in milliseconds.
     * 
     * @param player The player to check, must not be null
     * @return remaining cooldown time in milliseconds, or 0 if no cooldown or player is null
     */
    public static long getRemainingCooldown(@Nullable ServerPlayer player) {
        if (player == null) {
            LOGGER.warn("[CharacterSwitchCooldown] Attempted to get remaining cooldown for null player");
            return 0L;
        }
        
        try {
            UUID playerId = player.getUUID();
            long currentTime = System.currentTimeMillis();
            Long lastSwitchTime = lastSwitchTimes.get(playerId);
            
            if (lastSwitchTime == null) {
                return 0L;
            }
            
            long timeSinceLastSwitch = currentTime - lastSwitchTime;
            long remainingCooldown = COOLDOWN_DURATION_MS - timeSinceLastSwitch;
            
            return Math.max(0L, remainingCooldown);
        } catch (Exception e) {
            LOGGER.error("[CharacterSwitchCooldown] Error getting remaining cooldown for player {}: {}", 
                player.getName().getString(), e.getMessage(), e);
            return 0L;
        }
    }
    
    /**
     * Gets the remaining cooldown time for a player in seconds (rounded up).
     * 
     * @param player The player to check, must not be null
     * @return remaining cooldown time in seconds, or 0 if no cooldown or player is null
     */
    public static int getRemainingCooldownSeconds(@Nullable ServerPlayer player) {
        long remainingMs = getRemainingCooldown(player);
        return (int) Math.ceil(remainingMs / 1000.0);
    }
    
    /**
     * Records that a player has switched characters, starting their cooldown.
     * 
     * @param player The player who switched characters, must not be null
     * @throws IllegalArgumentException if player is null
     */
    public static void recordCharacterSwitch(@Nonnull ServerPlayer player) {
        Objects.requireNonNull(player, "Player cannot be null");
        
        try {
            UUID playerId = player.getUUID();
            long currentTime = System.currentTimeMillis();
            lastSwitchTimes.put(playerId, currentTime);
            
            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug("[CharacterSwitchCooldown] Recorded character switch for player {}. Cooldown active for {}ms", 
                    player.getName().getString(), COOLDOWN_DURATION_MS);
            }
        } catch (Exception e) {
            LOGGER.error("[CharacterSwitchCooldown] Error recording character switch for player {}: {}", 
                player.getName().getString(), e.getMessage(), e);
        }
    }
    
    /**
     * Clears the cooldown for a player (useful for admin commands or special cases).
     * 
     * @param player The player whose cooldown should be cleared, must not be null
     * @throws IllegalArgumentException if player is null
     */
    public static void clearCooldown(@Nonnull ServerPlayer player) {
        Objects.requireNonNull(player, "Player cannot be null");
        
        try {
            UUID playerId = player.getUUID();
            Long previousTime = lastSwitchTimes.remove(playerId);
            
            if (LOGGER.isDebugEnabled()) {
                if (previousTime != null) {
                    LOGGER.debug("[CharacterSwitchCooldown] Cleared cooldown for player {}", player.getName().getString());
                } else {
                    LOGGER.debug("[CharacterSwitchCooldown] No cooldown to clear for player {}", player.getName().getString());
                }
            }
        } catch (Exception e) {
            LOGGER.error("[CharacterSwitchCooldown] Error clearing cooldown for player {}: {}", 
                player.getName().getString(), e.getMessage(), e);
        }
    }
    
    /**
     * Cleans up cooldown data for a player when they disconnect to prevent memory leaks.
     * 
     * @param playerId The UUID of the player who disconnected, must not be null
     * @throws IllegalArgumentException if playerId is null
     */
    public static void cleanupPlayerData(@Nonnull UUID playerId) {
        Objects.requireNonNull(playerId, "Player ID cannot be null");
        
        try {
            Long previousTime = lastSwitchTimes.remove(playerId);
            
            if (LOGGER.isDebugEnabled()) {
                if (previousTime != null) {
                    LOGGER.debug("[CharacterSwitchCooldown] Cleaned up cooldown data for player {}", playerId);
                } else {
                    LOGGER.debug("[CharacterSwitchCooldown] No cooldown data to clean up for player {}", playerId);
                }
            }
        } catch (Exception e) {
            LOGGER.error("[CharacterSwitchCooldown] Error cleaning up cooldown data for player {}: {}", 
                playerId, e.getMessage(), e);
        }
    }
    
    /**
     * Event handler to clean up player data when they log out.
     * This prevents memory leaks by removing cooldown data for disconnected players.
     * 
     * @param event The player logout event
     */
    @SubscribeEvent
    public static void onPlayerLogout(@Nonnull net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerLoggedOutEvent event) {
        try {
            if (event.getEntity() instanceof ServerPlayer serverPlayer) {
                UUID playerId = serverPlayer.getUUID();
                cleanupPlayerData(playerId);
                // Also cleanup pending auto-switches to prevent memory leaks
                CharacterUtils.clearPendingAutoSwitch(playerId);
            }
        } catch (Exception e) {
            LOGGER.error("[CharacterSwitchCooldown] Error handling player logout event: {}", e.getMessage(), e);
        }
    }
    
    /**
     * Gets the cooldown duration in milliseconds.
     * 
     * @return cooldown duration in milliseconds
     */
    public static long getCooldownDurationMs() {
        return COOLDOWN_DURATION_MS;
    }
    
    /**
     * Gets the cooldown duration in seconds.
     * 
     * @return cooldown duration in seconds
     */
    public static int getCooldownDurationSeconds() {
        return (int) (COOLDOWN_DURATION_MS / 1000L);
    }
    
    /**
     * Gets the current number of players with active cooldowns.
     * This method is primarily for debugging and monitoring purposes.
     * 
     * @return number of players currently on cooldown
     */
    public static int getActiveCooldownCount() {
        try {
            long currentTime = System.currentTimeMillis();
            return (int) lastSwitchTimes.values().stream()
                .filter(Objects::nonNull)
                .mapToLong(Long::longValue)
                .filter(lastSwitchTime -> (currentTime - lastSwitchTime) < COOLDOWN_DURATION_MS)
                .count();
        } catch (Exception e) {
            LOGGER.error("[CharacterSwitchCooldown] Error counting active cooldowns: {}", e.getMessage(), e);
            return 0;
        }
    }
    
    /**
     * Clears all cooldown data. This method should only be used for testing or emergency cleanup.
     * 
     * @apiNote This method is intended for administrative use only
     */
    public static void clearAllCooldowns() {
        try {
            int clearedCount = lastSwitchTimes.size();
            lastSwitchTimes.clear();
            LOGGER.info("[CharacterSwitchCooldown] Cleared all cooldown data ({} entries)", clearedCount);
        } catch (Exception e) {
            LOGGER.error("[CharacterSwitchCooldown] Error clearing all cooldowns: {}", e.getMessage(), e);
        }
    }
} 