package world.landfall.persona.features.landfalladdon;

import java.lang.reflect.Method;
import java.lang.reflect.InvocationTargetException;
import java.util.Set;
import java.util.HashSet;

import com.mojang.logging.LogUtils;
import org.slf4j.Logger;

/**
 * Integration class for LandfallEssentials (LFE) region system.
 * Uses reflection to safely access LFE's RegionManager methods without hard dependencies.
 */
public class PersonaLFEIntegration {
    private static final Logger LOGGER = LogUtils.getLogger();
    
    private static Class<?> regionManagerClass = null;
    private static Method isPlayerInTaggedRegionMethod = null;
    private static Method getPlayerRegionTagsMethod = null;
    private static boolean lfeAvailable = false;
    
    static {
        try {
            // Try to load the RegionManager class
            regionManagerClass = Class.forName("world.landfall.landfallessentials.regions.RegionManager");
            
            // Get the methods we need
            isPlayerInTaggedRegionMethod = regionManagerClass.getMethod(
                "isPlayerInTaggedRegion", 
                net.minecraft.world.entity.player.Player.class, 
                String.class
            );
            
            getPlayerRegionTagsMethod = regionManagerClass.getMethod(
                "getPlayerRegionTags",
                net.minecraft.world.entity.player.Player.class
            );
            
            lfeAvailable = true;
            LOGGER.info("[Persona] LFE integration enabled - RegionManager found");
            
        } catch (ClassNotFoundException | NoSuchMethodException e) {
            lfeAvailable = false;
            LOGGER.info("[Persona] LFE not found - region tag integration disabled");
        }
    }
    
    /**
     * Check if LFE is available
     */
    public static boolean isLFEAvailable() {
        return lfeAvailable;
    }
    
    /**
     * Check if player is in a region with specific tag using reflection
     */
    public static boolean isPlayerInTaggedRegion(net.minecraft.world.entity.player.Player player, String tag) {
        if (!lfeAvailable || player == null || tag == null) {
            return false;
        }
        
        try {
            Object result = isPlayerInTaggedRegionMethod.invoke(null, player, tag);
            return (Boolean) result;
        } catch (IllegalAccessException | InvocationTargetException e) {
            LOGGER.error("[Persona] Error calling LFE isPlayerInTaggedRegion: " + e.getMessage());
            return false;
        }
    }
    
    /**
     * Get all tags for player's current regions using reflection
     */
    @SuppressWarnings("unchecked")
    public static Set<String> getPlayerRegionTags(net.minecraft.world.entity.player.Player player) {
        if (!lfeAvailable || player == null) {
            return new HashSet<>();
        }
        
        try {
            Object result = getPlayerRegionTagsMethod.invoke(null, player);
            return (Set<String>) result;
        } catch (IllegalAccessException | InvocationTargetException e) {
            LOGGER.error("[Persona] Error calling LFE getPlayerRegionTags: " + e.getMessage());
            return new HashSet<>();
        }
    }
    
    /**
     * Specific method to check for "godfall" tag
     */
    public static boolean isPlayerInGodfallRegion(net.minecraft.world.entity.player.Player player) {
        return isPlayerInTaggedRegion(player, "godfall");
    }
} 