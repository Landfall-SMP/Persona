package world.landfall.persona.client;

import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.InputEvent;
import world.landfall.persona.Persona;
import world.landfall.persona.client.gui.CharacterManagementScreen;

@EventBusSubscriber(modid = Persona.MODID, value = Dist.CLIENT)
public class ClientEvents {
    @SubscribeEvent
    public static void onKeyInput(InputEvent.Key event) {
        if (KeyBindings.OPEN_CHARACTER_MANAGEMENT.consumeClick()) {
            Minecraft minecraft = Minecraft.getInstance();
            if (minecraft.player != null) {
                minecraft.setScreen(new CharacterManagementScreen(minecraft.player));
            }
        }
    }
} 