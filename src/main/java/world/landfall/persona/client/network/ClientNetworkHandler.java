package world.landfall.persona.client.network;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import world.landfall.persona.client.gui.CharacterCreationScreen;

@OnlyIn(Dist.CLIENT)
public class ClientNetworkHandler {
    public static void handleCharacterCreationResponse(boolean success, String messageKey, String[] messageArgs) {
        Minecraft minecraft = Minecraft.getInstance();
        Screen currentScreen = minecraft.screen;
        
        if (currentScreen instanceof CharacterCreationScreen creationScreen) {
            if (success) {
                creationScreen.handleSuccessfulCreation();
            } else {
                creationScreen.handleFailedCreation(messageKey, (Object[]) messageArgs);
            }
        }
    }
} 