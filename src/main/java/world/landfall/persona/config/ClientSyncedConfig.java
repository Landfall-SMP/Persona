package world.landfall.persona.config;

import world.landfall.persona.Persona;

/**
 * Stores client-side synchronized configuration values from the server.
 */
public class ClientSyncedConfig {
    private static boolean agingSystemEnabled = true; // Default, should match server default

    /**
     * Called by network handler when server sends its config.
     * @param isEnabled Whether the aging system is enabled on the server.
     */
    public static void updateAgingSystemStatus(boolean isEnabled) {
        Persona.LOGGER.debug("[Persona] Client received server config: Aging System Enabled = {}", isEnabled);
        agingSystemEnabled = isEnabled;
    }

    /**
     * Checks if the aging system is reported as enabled by the server.
     * @return True if the aging system is enabled on the server, false otherwise.
     */
    public static boolean isAgingSystemEnabled() {
        return agingSystemEnabled;
    }

    /**
     * Resets to default values, e.g., when disconnecting from a server.
     */
    public static void resetToDefaults() {
        agingSystemEnabled = true; // Match server default
        Persona.LOGGER.debug("[Persona] Client synced config reset to defaults.");
    }
} 