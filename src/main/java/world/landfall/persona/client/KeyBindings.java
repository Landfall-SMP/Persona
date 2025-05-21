package world.landfall.persona.client;

import net.minecraft.client.KeyMapping;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import world.landfall.persona.Persona;

@EventBusSubscriber(modid = Persona.MODID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
public class KeyBindings {
    public static final KeyMapping OPEN_CHARACTER_MANAGEMENT = new KeyMapping(
        "key.persona.open_character_management",
        org.lwjgl.glfw.GLFW.GLFW_KEY_P,
        "key.categories.persona"
    );
    
    @SubscribeEvent
    public static void registerKeyMappings(RegisterKeyMappingsEvent event) {
        event.register(OPEN_CHARACTER_MANAGEMENT);
    }
} 