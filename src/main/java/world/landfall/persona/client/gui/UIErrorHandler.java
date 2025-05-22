package world.landfall.persona.client.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

/**
 * Centralizes UI error handling and notifications.
 * Provides consistent error display across the mod.
 */
public class UIErrorHandler {
    public static void showError(String translationKey) {
        showError(translationKey, new Object[0]);
    }
    
    public static void showError(String translationKey, Object... args) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft != null) {
            minecraft.getToasts().addToast(NotificationToast.error(
                Component.translatable(translationKey, args)
            ));
        }
    }
    
    public static void showSuccess(String translationKey) {
        showSuccess(translationKey, new Object[0]);
    }
    
    public static void showSuccess(String translationKey, Object... args) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft != null) {
            minecraft.getToasts().addToast(NotificationToast.success(
                Component.translatable(translationKey, args)
            ));
        }
    }
    
    public static void showInfo(String translationKey) {
        showInfo(translationKey, new Object[0]);
    }
    
    public static void showInfo(String translationKey, Object... args) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft != null) {
            minecraft.getToasts().addToast(NotificationToast.info(
                Component.translatable(translationKey, args)
            ));
        }
    }
} 