package world.landfall.persona.features.landfalladdon;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

import com.mojang.logging.LogUtils;
import org.slf4j.Logger;

import world.landfall.persona.Persona;
import world.landfall.persona.data.CharacterProfile;
import world.landfall.persona.features.landfalladdon.shells.Shell;
import world.landfall.persona.features.landfalladdon.shells.ShellManager;

import java.util.Objects;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * Handles all LandfallAddon-specific data operations
 * This centralizes the data management that was previously scattered in CharacterProfile
 */
public class LandfallAddonData {
    private static final Logger LOGGER = LogUtils.getLogger();
    
    // Data keys
    public static final ResourceLocation DATA_KEY = ResourceLocation.fromNamespaceAndPath(Persona.MODID, "landfall_addon_data");
    private static final ResourceLocation ORIGIN_KEY = ResourceLocation.fromNamespaceAndPath(Persona.MODID, "origin_input");

    // NBT keys for spawn point data
    public static final String SPAWN_X_KEY = "spawn_x";
    public static final String SPAWN_Y_KEY = "spawn_y";
    public static final String SPAWN_Z_KEY = "spawn_z";
    public static final String SPAWN_DIMENSION_KEY = "spawn_dimension";
    public static final String SPAWN_FORCED_KEY = "spawn_forced";
    
    // NBT keys for other data
    private static final String CURRENT_SHELL_KEY = "currentShell";
    private static final String DEATH_COUNT_KEY = "deathCount";
    private static final String SELECTED_ORIGIN_KEY = "selectedOrigin";
    
    // Constants
    private static final String UNKNOWN_ORIGIN = "UNKNOWN_ORIGIN";
    private static final int MAX_DEATH_COUNT = 10000; // Reasonable upper bound
    private static final double MAX_COORDINATE = 30000000.0; // Minecraft world border limit
    private static final double MIN_COORDINATE = -30000000.0;
    
    // Thread safety for data access
    private static final ReadWriteLock DATA_LOCK = new ReentrantReadWriteLock();

    /**
     * Initializes LandfallAddon data for a new character profile.
     * @param profile The character profile to initialize, must not be null
     * @throws IllegalArgumentException if profile is null
     * @throws IllegalStateException if data initialization fails
     */
    public static void initializeData(CharacterProfile profile) {
        validateProfile(profile);
        
        DATA_LOCK.writeLock().lock();
        try {
            CompoundTag existingData = profile.getModData(DATA_KEY);
            if (existingData != null && !existingData.isEmpty()) {
                LOGGER.debug("LandfallAddon data already exists for character {}, skipping initialization", 
                    profile.getId());
                return;
            }

            CompoundTag landfallData = new CompoundTag();
            landfallData.putString(CURRENT_SHELL_KEY, Shell.NEUTRAL.name());
            landfallData.putInt(DEATH_COUNT_KEY, 0);
            
            profile.setModData(DATA_KEY, landfallData);
            LOGGER.info("Initialized LandfallAddon data for character {}", profile.getId());
            
        } catch (Exception e) {
            LOGGER.error("Failed to initialize LandfallAddon data for character {}", profile.getId(), e);
            throw new IllegalStateException("Failed to initialize LandfallAddon data", e);
        } finally {
            DATA_LOCK.writeLock().unlock();
        }
    }

    /**
     * Gets the current shell for a character with comprehensive validation.
     * @param profile The character profile, must not be null
     * @return The current shell, never null (defaults to NEUTRAL on any error)
     * @throws IllegalArgumentException if profile is null
     */
    public static Shell getCurrentShell(CharacterProfile profile) {
        validateProfile(profile);
        
        DATA_LOCK.readLock().lock();
        try {
            CompoundTag landfallData = getOrCreateData(profile);
            
            if (!landfallData.contains(CURRENT_SHELL_KEY, CompoundTag.TAG_STRING)) {
                LOGGER.debug("No shell data found for character {}, defaulting to NEUTRAL", profile.getId());
                return Shell.NEUTRAL;
            }
            
            String shellName = landfallData.getString(CURRENT_SHELL_KEY);
            if (shellName == null || shellName.isBlank()) {
                LOGGER.warn("Empty shell name for character {}, defaulting to NEUTRAL", profile.getId());
                return Shell.NEUTRAL;
            }
            
            try {
                Shell shell = Shell.valueOf(shellName.trim().toUpperCase());
                if (!ShellManager.isValidShell(shell)) {
                    LOGGER.warn("Invalid shell {} for character {}, defaulting to NEUTRAL", 
                        shellName, profile.getId());
                    return Shell.NEUTRAL;
                }
                return shell;
            } catch (IllegalArgumentException e) {
                LOGGER.warn("Failed to parse shell '{}' for character {}, defaulting to NEUTRAL", 
                    shellName, profile.getId(), e);
                return Shell.NEUTRAL;
            }
            
        } catch (Exception e) {
            LOGGER.error("Error getting current shell for character {}, defaulting to NEUTRAL", 
                profile.getId(), e);
            return Shell.NEUTRAL;
        } finally {
            DATA_LOCK.readLock().unlock();
        }
    }

    /**
     * Sets the current shell for a character with validation.
     * @param profile The character profile, must not be null
     * @param shell The shell to set, must not be null
     * @throws IllegalArgumentException if profile or shell is null, or shell is invalid
     */
    public static void setCurrentShell(CharacterProfile profile, Shell shell) {
        validateProfile(profile);
        Objects.requireNonNull(shell, "Shell cannot be null");
        
        if (!ShellManager.isValidShell(shell)) {
            throw new IllegalArgumentException("Invalid shell: " + shell);
        }
        
        DATA_LOCK.writeLock().lock();
        try {
            CompoundTag landfallData = getOrCreateData(profile);
            String previousShell = landfallData.getString(CURRENT_SHELL_KEY);
            
            landfallData.putString(CURRENT_SHELL_KEY, shell.name());
            profile.setModData(DATA_KEY, landfallData);
            
            LOGGER.info("Updated shell for character {} from {} to {}", 
                profile.getId(), previousShell, shell.name());
                
        } catch (Exception e) {
            LOGGER.error("Failed to set shell {} for character {}", shell, profile.getId(), e);
            throw new IllegalStateException("Failed to set shell", e);
        } finally {
            DATA_LOCK.writeLock().unlock();
        }
    }

    /**
     * Gets the death count for a character with bounds checking.
     * @param profile The character profile, must not be null
     * @return The death count, guaranteed to be >= 0
     * @throws IllegalArgumentException if profile is null
     */
    public static int getDeathCount(CharacterProfile profile) {
        validateProfile(profile);
        
        DATA_LOCK.readLock().lock();
        try {
            CompoundTag landfallData = getOrCreateData(profile);
            
            if (!landfallData.contains(DEATH_COUNT_KEY, CompoundTag.TAG_INT)) {
                return 0;
            }
            
            int deathCount = landfallData.getInt(DEATH_COUNT_KEY);
            if (deathCount < 0) {
                LOGGER.warn("Negative death count {} for character {}, returning 0", 
                    deathCount, profile.getId());
                return 0;
            }
            
            if (deathCount > MAX_DEATH_COUNT) {
                LOGGER.warn("Death count {} exceeds maximum {} for character {}, clamping", 
                    deathCount, MAX_DEATH_COUNT, profile.getId());
                return MAX_DEATH_COUNT;
            }
            
            return deathCount;
            
        } catch (Exception e) {
            LOGGER.error("Error getting death count for character {}, returning 0", profile.getId(), e);
            return 0;
        } finally {
            DATA_LOCK.readLock().unlock();
        }
    }

    /**
     * Increments the death count for a character with overflow protection.
     * @param profile The character profile, must not be null
     * @throws IllegalArgumentException if profile is null
     * @throws IllegalStateException if the operation fails
     */
    public static void incrementDeathCount(CharacterProfile profile) {
        validateProfile(profile);
        
        DATA_LOCK.writeLock().lock();
        try {
            int currentCount = getDeathCount(profile);
            
            if (currentCount >= MAX_DEATH_COUNT) {
                LOGGER.warn("Death count for character {} is already at maximum {}, not incrementing", 
                    profile.getId(), MAX_DEATH_COUNT);
                return;
            }
            
            int newCount = currentCount + 1;
            CompoundTag landfallData = getOrCreateData(profile);
            landfallData.putInt(DEATH_COUNT_KEY, newCount);
            profile.setModData(DATA_KEY, landfallData);
            
            LOGGER.info("Incremented death count for character {} from {} to {}", 
                profile.getId(), currentCount, newCount);
                
        } catch (Exception e) {
            LOGGER.error("Failed to increment death count for character {}", profile.getId(), e);
            throw new IllegalStateException("Failed to increment death count", e);
        } finally {
            DATA_LOCK.writeLock().unlock();
        }
    }

    /**
     * Gets the character's origin string from modData with validation.
     * @param profile The character profile, must not be null
     * @return The origin string, never null (returns UNKNOWN_ORIGIN if not set or invalid)
     * @throws IllegalArgumentException if profile is null
     */
    public static String getOrigin(CharacterProfile profile) {
        validateProfile(profile);
        
        DATA_LOCK.readLock().lock();
        try {
            CompoundTag originData = profile.getModData(ORIGIN_KEY);
            if (originData == null || !originData.contains(SELECTED_ORIGIN_KEY, CompoundTag.TAG_STRING)) {
                return UNKNOWN_ORIGIN;
            }
            
            String origin = originData.getString(SELECTED_ORIGIN_KEY);
            if (origin == null || origin.isBlank()) {
                return UNKNOWN_ORIGIN;
            }
            
            return origin.trim().toUpperCase();
            
        } catch (Exception e) {
            LOGGER.error("Error getting origin for character {}, returning UNKNOWN_ORIGIN", 
                profile.getId(), e);
            return UNKNOWN_ORIGIN;
        } finally {
            DATA_LOCK.readLock().unlock();
        }
    }

    /**
     * Sets the spawn point for a character with comprehensive validation.
     * @param profile The character profile, must not be null
     * @param x The X coordinate, must be within world bounds
     * @param y The Y coordinate, must be within world bounds  
     * @param z The Z coordinate, must be within world bounds
     * @param dimension The dimension resource location, must not be null
     * @param forced Whether the spawn is forced
     * @throws IllegalArgumentException if any parameter is invalid
     * @throws IllegalStateException if the operation fails
     */
    public static void setSpawnPoint(CharacterProfile profile, double x, double y, double z, 
                                   ResourceLocation dimension, boolean forced) {
        validateProfile(profile);
        Objects.requireNonNull(dimension, "Dimension cannot be null");
        validateCoordinate(x, "X");
        validateCoordinate(y, "Y");
        validateCoordinate(z, "Z");
        
        DATA_LOCK.writeLock().lock();
        try {
            CompoundTag data = getOrCreateData(profile);
            data.putDouble(SPAWN_X_KEY, x);
            data.putDouble(SPAWN_Y_KEY, y);
            data.putDouble(SPAWN_Z_KEY, z);
            data.putString(SPAWN_DIMENSION_KEY, dimension.toString());
            data.putBoolean(SPAWN_FORCED_KEY, forced);
            
            profile.setModData(DATA_KEY, data);
            
            LOGGER.info("Set spawn point for character {} to ({}, {}, {}) in {} (forced: {})", 
                profile.getId(), x, y, z, dimension, forced);
                
        } catch (Exception e) {
            LOGGER.error("Failed to set spawn point for character {}", profile.getId(), e);
            throw new IllegalStateException("Failed to set spawn point", e);
        } finally {
            DATA_LOCK.writeLock().unlock();
        }
    }

    /**
     * Checks if a character has a valid spawn point set.
     * @param profile The character profile, must not be null
     * @return true if a valid spawn point exists, false otherwise
     * @throws IllegalArgumentException if profile is null
     */
    public static boolean hasSpawnPoint(CharacterProfile profile) {
        validateProfile(profile);
        
        DATA_LOCK.readLock().lock();
        try {
            CompoundTag data = getOrCreateData(profile);
            return data.contains(SPAWN_X_KEY, CompoundTag.TAG_DOUBLE) && 
                   data.contains(SPAWN_Y_KEY, CompoundTag.TAG_DOUBLE) && 
                   data.contains(SPAWN_Z_KEY, CompoundTag.TAG_DOUBLE) && 
                   data.contains(SPAWN_DIMENSION_KEY, CompoundTag.TAG_STRING) &&
                   !data.getString(SPAWN_DIMENSION_KEY).isBlank();
                   
        } catch (Exception e) {
            LOGGER.error("Error checking spawn point for character {}", profile.getId(), e);
            return false;
        } finally {
            DATA_LOCK.readLock().unlock();
        }
    }

    /**
     * Gets the spawn point for a character with validation.
     * @param profile The character profile, must not be null
     * @return The spawn point as GlobalPos, or null if not set or invalid
     * @throws IllegalArgumentException if profile is null
     */
    public static GlobalPos getSpawnPoint(CharacterProfile profile) {
        validateProfile(profile);
        
        DATA_LOCK.readLock().lock();
        try {
            if (!hasSpawnPoint(profile)) {
                return null;
            }
            
            CompoundTag data = getOrCreateData(profile);
            
            double x = data.getDouble(SPAWN_X_KEY);
            double y = data.getDouble(SPAWN_Y_KEY);
            double z = data.getDouble(SPAWN_Z_KEY);
            String dimensionStr = data.getString(SPAWN_DIMENSION_KEY);
            
            // Validate coordinates
            if (!isValidCoordinate(x) || !isValidCoordinate(y) || !isValidCoordinate(z)) {
                LOGGER.warn("Invalid spawn coordinates for character {}: ({}, {}, {})", 
                    profile.getId(), x, y, z);
                return null;
            }
            
            ResourceLocation dimRL = ResourceLocation.tryParse(dimensionStr);
            if (dimRL == null) {
                LOGGER.warn("Invalid dimension '{}' for character {}", dimensionStr, profile.getId());
                return null;
            }

            try {
                ResourceKey<Level> dim = ResourceKey.create(
                    ResourceKey.createRegistryKey(ResourceLocation.fromNamespaceAndPath("minecraft", "dimension")), 
                    dimRL
                );
                BlockPos pos = BlockPos.containing(x, y, z);
                return GlobalPos.of(dim, pos);
                
            } catch (Exception e) {
                LOGGER.warn("Failed to create GlobalPos for character {}", profile.getId(), e);
                return null;
            }
            
        } catch (Exception e) {
            LOGGER.error("Error getting spawn point for character {}", profile.getId(), e);
            return null;
        } finally {
            DATA_LOCK.readLock().unlock();
        }
    }

    /**
     * Checks if the spawn point is forced for a character.
     * @param profile The character profile, must not be null
     * @return true if spawn is forced, false otherwise
     * @throws IllegalArgumentException if profile is null
     */
    public static boolean isSpawnForced(CharacterProfile profile) {
        validateProfile(profile);
        
        DATA_LOCK.readLock().lock();
        try {
            CompoundTag data = getOrCreateData(profile);
            return data.getBoolean(SPAWN_FORCED_KEY);
        } catch (Exception e) {
            LOGGER.error("Error checking forced spawn for character {}", profile.getId(), e);
            return false;
        } finally {
            DATA_LOCK.readLock().unlock();
        }
    }

    /**
     * Clears all LandfallAddon data for a character.
     * @param profile The character profile, must not be null
     * @throws IllegalArgumentException if profile is null
     */
    public static void clearData(CharacterProfile profile) {
        validateProfile(profile);
        
        DATA_LOCK.writeLock().lock();
        try {
            profile.setModData(DATA_KEY, new CompoundTag());
            LOGGER.info("Cleared LandfallAddon data for character {}", profile.getId());
        } catch (Exception e) {
            LOGGER.error("Failed to clear data for character {}", profile.getId(), e);
            throw new IllegalStateException("Failed to clear data", e);
        } finally {
            DATA_LOCK.writeLock().unlock();
        }
    }

    // Private helper methods

    /**
     * Gets or creates the LandfallAddon data tag for a profile.
     * This method assumes the caller has appropriate locks.
     */
    private static CompoundTag getOrCreateData(CharacterProfile profile) {
        CompoundTag data = profile.getModData(DATA_KEY);
        if (data == null) {
            data = new CompoundTag();
            profile.setModData(DATA_KEY, data);
        }
        return data;
    }

    /**
     * Validates that a profile is not null and has a valid ID.
     */
    private static void validateProfile(CharacterProfile profile) {
        Objects.requireNonNull(profile, "CharacterProfile cannot be null");
        if (profile.getId() == null) {
            throw new IllegalArgumentException("CharacterProfile must have a valid ID");
        }
    }

    /**
     * Validates that a coordinate is within acceptable bounds.
     */
    private static void validateCoordinate(double coordinate, String name) {
        if (!isValidCoordinate(coordinate)) {
            throw new IllegalArgumentException(String.format(
                "%s coordinate %f is out of bounds [%f, %f]", 
                name, coordinate, MIN_COORDINATE, MAX_COORDINATE));
        }
    }

    /**
     * Checks if a coordinate is within valid bounds.
     */
    private static boolean isValidCoordinate(double coordinate) {
        return !Double.isNaN(coordinate) && 
               !Double.isInfinite(coordinate) && 
               coordinate >= MIN_COORDINATE && 
               coordinate <= MAX_COORDINATE;
    }
} 