package world.landfall.persona.features.figura;

import com.mojang.logging.LogUtils;
import net.minecraft.client.Minecraft;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import org.slf4j.Logger;
import world.landfall.persona.features.figura.event.ClientPersonaSwitchedEvent;

/**
 * Handles client-side events for Figura integration with the Persona system.
 * - Initializes Figura availability check during client setup.
 * - Listens for Persona character switches to trigger Figura avatar changes.
 */
public class FiguraClientEventListener {

    private static final Logger LOGGER = LogUtils.getLogger();

    /**
     * Called on the MOD event bus during client setup.
     * Enqueues work to check for Figura availability.
     */
    public static void onClientSetup(final FMLClientSetupEvent event) {
        // Logging for Figura availability is now handled directly in Persona.java's onClientSetup
        // to allow conditional registration of the persona switch listener.
    }

    @SubscribeEvent
    public static void onPersonaSwitched(final ClientPersonaSwitchedEvent event) {
        if (!FiguraReflector.isFiguraAvailable()) {
            // This check is a safeguard, actual registration should prevent this if Figura isn't available.
            LOGGER.debug("ClientPersonaSwitchedEvent received, but Figura integration is not active. Skipping.");
            return;
        }

        if (Minecraft.getInstance().player == null) {
            LOGGER.debug("ClientPersonaSwitchedEvent received, but player is null. Skipping Figura avatar switch.");
            return;
        }

        String characterDisplayName = event.getNewPersonaDisplayName();
 
        if (characterDisplayName != null && !characterDisplayName.isEmpty()) {
            LOGGER.info("ClientPersonaSwitchedEvent: Persona switched to: '{}'. Attempting to apply corresponding Figura avatar.", characterDisplayName);
            FiguraReflector.performAvatarSwitch(characterDisplayName);
        } else {
            LOGGER.warn("ClientPersonaSwitchedEvent: Persona switched, but character display name from event was null or empty. Cannot apply Figura avatar.");
        }
    }

    // The triggerFiguraAvatarSwitch method can be kept if direct calls are ever needed for other reasons,
    // but for automatic switching, the event-based approach is now primary.
    public static void triggerFiguraAvatarSwitch(String characterDisplayName) {
        if (!FiguraReflector.isFiguraAvailable()) {
            LOGGER.debug("Request to switch Figura avatar to '{}', but Figura integration is disabled.", characterDisplayName);
            return;
        }

        if (Minecraft.getInstance().player == null) {
            LOGGER.warn("Request to switch Figura avatar to '{}', but player is null. Skipping.", characterDisplayName);
            return;
        }

        if (characterDisplayName != null && !characterDisplayName.isEmpty()) {
            LOGGER.info("Persona system triggered Figura avatar switch for: '{}'.", characterDisplayName);
            FiguraReflector.performAvatarSwitch(characterDisplayName);
        } else {
            LOGGER.warn("Persona system triggered Figura avatar switch, but character display name was null or empty.");
        }
    }
} 