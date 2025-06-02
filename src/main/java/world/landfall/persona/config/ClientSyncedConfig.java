package world.landfall.persona.config;

import world.landfall.persona.Persona;

/**
 * Stores client-side synchronized configuration values from the server.
 */
public class ClientSyncedConfig {
    private static boolean agingSystemEnabled = true; // Default, should match server default
    private static boolean inventorySystemEnabled = true;
    private static boolean locationSystemEnabled = true;
    private static boolean landfallAddonsEnabled = true;

    /**
     * Called by network handler when server sends its config.
     * @param isEnabled Whether the aging system is enabled on the server.
     */
    public static void updateAgingSystemStatus(boolean isEnabled) {
        Persona.LOGGER.debug("[Persona] Client received server config: Aging System Enabled = {}", isEnabled);
        agingSystemEnabled = isEnabled;
    }

    /**
     * Updates client-side config for inventory system from server.
     */
    public static void updateInventorySystemStatus(boolean isEnabled) {
        Persona.LOGGER.debug("[Persona] Client received server config: Inventory System Enabled = {}", isEnabled);
        inventorySystemEnabled = isEnabled;
    }

    /**
     * Updates client-side config for location system from server.
     */
    public static void updateLocationSystemStatus(boolean isEnabled) {
        Persona.LOGGER.debug("[Persona] Client received server config: Location System Enabled = {}", isEnabled);
        locationSystemEnabled = isEnabled;
    }

    /**
     * Updates client-side config for Landfall addons from server.
     */
    public static void updateLandfallAddonsStatus(boolean isEnabled) {
        Persona.LOGGER.debug("[Persona] Client received server config: Landfall Addons Enabled = {}", isEnabled);
        landfallAddonsEnabled = isEnabled;
    }

    /**
     * Checks if the aging system is reported as enabled by the server.
     * @return True if the aging system is enabled on the server, false otherwise.
     */
    public static boolean isAgingSystemEnabled() {
        return agingSystemEnabled;
    }

    /**
     * @return True if the inventory system is enabled on the server.
     */
    public static boolean isInventorySystemEnabled() {
        return inventorySystemEnabled;
    }

    /**
     * @return True if the location system is enabled on the server.
     */
    public static boolean isLocationSystemEnabled() {
        return locationSystemEnabled;
    }

    /**
     * @return True if Landfall addons are enabled on the server.
     */
    public static boolean isLandfallAddonsEnabled() {
        return landfallAddonsEnabled;
    }

    /**
     * Resets to default values, e.g., when disconnecting from a server.
     */
    public static void resetToDefaults() {
        agingSystemEnabled = true; // Match server default
        inventorySystemEnabled = true;
        locationSystemEnabled = true;
        landfallAddonsEnabled = true;
        Persona.LOGGER.debug("[Persona] Client synced config reset to defaults.");
    }
} 