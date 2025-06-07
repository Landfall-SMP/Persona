package world.landfall.persona.client.network;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.common.NeoForge;
import world.landfall.persona.client.gui.CharacterCreationScreen;
import world.landfall.persona.client.gui.NotificationToast;
import world.landfall.persona.features.figura.event.ClientPersonaSwitchedEvent;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Objects;

/**
 * Handles network responses from the server for character-related operations.
 */
@OnlyIn(Dist.CLIENT)
public final class ClientNetworkHandler {
    
    // Private constructor to prevent instantiation
    private ClientNetworkHandler() {
        throw new UnsupportedOperationException("Utility class cannot be instantiated");
    }
    
    /**
     * Handles character creation responses from the server.
     * 
     * This method processes both successful and failed character creation attempts,
     * updating the appropriate GUI screens and displaying feedback
     * 
     * @param success true if the character creation was successful, false otherwise
     * @param messageKey the translation key for the response message, must not be null
     * @param messageArgs arguments for the message translation, must not be null
     * @throws IllegalArgumentException if messageKey or messageArgs is null
     */
    public static void handleCharacterCreationResponse(boolean success, @Nonnull String messageKey, @Nonnull String[] messageArgs) {
        Objects.requireNonNull(messageKey, "Message key cannot be null");
        Objects.requireNonNull(messageArgs, "Message arguments cannot be null");
        
        try {
            Minecraft minecraft = Minecraft.getInstance();
            if (minecraft == null) {
                return; // Minecraft instance not available
            }
            
            Screen currentScreen = minecraft.screen;
            
            if (currentScreen instanceof CharacterCreationScreen creationScreen) {
                if (success) {
                    creationScreen.handleSuccessfulCreation();
                } else {
                    creationScreen.handleFailedCreation(messageKey, (Object[]) messageArgs);
                }
            }
            // If not on creation screen, the response is ignored (user may have navigated away)
            
        } catch (Exception e) {
            // Log error but don't crash - this is client-side UI handling
            System.err.println("[ClientNetworkHandler] Error handling character creation response: " + e.getMessage());
        }
    }
    
    /**
     * Handles character switch responses from the server.
     * 
     * This method processes both successful and failed character switch attempts,
     * displaying appropriate toast notifications and firing client-side events for successful switches.
     * 
     * @param success true if the character switch was successful, false otherwise
     * @param messageKey the translation key for the response message, must not be null
     * @param messageArgs arguments for the message translation, must not be null
     * @throws IllegalArgumentException if messageKey or messageArgs is null
     */
    public static void handleCharacterSwitchResponse(boolean success, @Nonnull String messageKey, @Nonnull String[] messageArgs) {
        Objects.requireNonNull(messageKey, "Message key cannot be null");
        Objects.requireNonNull(messageArgs, "Message arguments cannot be null");
        
        try {
            Minecraft minecraft = Minecraft.getInstance();
            if (minecraft == null) {
                return; // Minecraft instance not available
            }
            
            if (success) {
                handleSuccessfulSwitch(minecraft, messageKey, messageArgs);
            } else {
                handleFailedSwitch(minecraft, messageKey, messageArgs);
            }
            
        } catch (Exception e) {
            // Log error but don't crash - this is client-side UI handling
            System.err.println("[ClientNetworkHandler] Error handling character switch response: " + e.getMessage());
        }
    }
    
    /**
     * Handles successful character switch responses.
     * 
     * @param minecraft the Minecraft instance, must not be null
     * @param messageKey the translation key for the success message, must not be null
     * @param messageArgs arguments for the message translation, must not be null
     */
    private static void handleSuccessfulSwitch(@Nonnull Minecraft minecraft, @Nonnull String messageKey, @Nonnull String[] messageArgs) {
        try {
            // Show success toast
            if (messageArgs.length > 0) {
                String characterName = messageArgs[0];
                
                // Validate character name
                if (characterName != null && !characterName.trim().isEmpty()) {
                    Component message = Component.translatable(messageKey, (Object[]) messageArgs);
                    minecraft.getToasts().addToast(NotificationToast.success(message));
                    
                    // Fire the ClientPersonaSwitchedEvent after successful switch
                    NeoForge.EVENT_BUS.post(new ClientPersonaSwitchedEvent(characterName));
                } else {
                    // Fallback: show success without character name
                    Component message = Component.translatable("command.persona.success.switch_generic");
                    minecraft.getToasts().addToast(NotificationToast.success(message));
                }
            } else {
                // Fallback: show generic success message
                Component message = Component.translatable("command.persona.success.switch_generic");
                minecraft.getToasts().addToast(NotificationToast.success(message));
            }
        } catch (Exception e) {
            System.err.println("[ClientNetworkHandler] Error handling successful switch: " + e.getMessage());
        }
    }
    
    /**
     * Handles failed character switch responses.
     * 
     * @param minecraft the Minecraft instance, must not be null
     * @param messageKey the translation key for the error message, must not be null
     * @param messageArgs arguments for the message translation, must not be null
     */
    private static void handleFailedSwitch(@Nonnull Minecraft minecraft, @Nonnull String messageKey, @Nonnull String[] messageArgs) {
        try {
            Component message = Component.translatable(messageKey, (Object[]) messageArgs);
            minecraft.getToasts().addToast(NotificationToast.error(message));
        } catch (Exception e) {
            System.err.println("[ClientNetworkHandler] Error handling failed switch: " + e.getMessage());
            
            // Fallback: show generic error message
            try {
                Component fallbackMessage = Component.translatable("gui.persona.error.generic_switch_fail");
                minecraft.getToasts().addToast(NotificationToast.error(fallbackMessage));
            } catch (Exception fallbackException) {
                // If even the fallback fails, just log it
                System.err.println("[ClientNetworkHandler] Fallback error handling also failed: " + fallbackException.getMessage());
            }
        }
    }
} 