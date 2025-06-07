package world.landfall.persona.features.landfalladdon.origins;

import com.mojang.logging.LogUtils;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import org.slf4j.Logger;
import world.landfall.persona.Persona;
import world.landfall.persona.client.gui.input.CharacterCreationInputRegistry;

/**
 * Manager class for the Origin system.
 * Handles initialization and registration of Origin-related components.
 */
public class OriginManager {
    private static final Logger LOGGER = LogUtils.getLogger();

    static {
        LOGGER.debug("[Persona] OriginManager loaded for Landfall Addon features.");
    }

    @EventBusSubscriber(modid = Persona.MODID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
    public static class ClientRegistration {
        @SubscribeEvent
        public static void onClientSetup(FMLClientSetupEvent event) {
            event.enqueueWork(() -> {
                CharacterCreationInputRegistry.register(new OriginInputProvider());
                LOGGER.debug("[Persona] Registered OriginInputProvider for character creation GUI.");
            });
        }
    }
} 