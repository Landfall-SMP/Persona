package world.landfall.persona.client.gui;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.toasts.Toast;
import net.minecraft.client.gui.components.toasts.ToastComponent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.client.gui.Font;
import javax.annotation.Nonnull;

public class NotificationToast implements Toast {
    private final Component title;
    private final Component message;
    private final String icon;
    private final Type type;
    private final long startTime;
    private final int displayTime;
    private int cachedWidth = -1; // Store calculated width for correct right alignment
    
    private static final int PADDING = 5;
    private static final int MAX_WIDTH = 240;
    private static final int MIN_WIDTH = 160;
    private static final int HEIGHT = 32;
    
    public enum Type {
        ERROR("⚠", 0xFF5555, Style.EMPTY.withBold(true).withColor(0xFF5555), Style.EMPTY.withColor(0xFFFFFF)),    // Bold bright red title, white message
        INFO("ℹ", 0x55FFFF, Style.EMPTY.withColor(0x55FFFF), Style.EMPTY.withColor(0xFFFFFF)),     // Cyan title, white message
        SUCCESS("✓", 0x55FF55, Style.EMPTY.withColor(0x55FF55), Style.EMPTY.withColor(0xFFFFFF));  // Lime title, white message
        
        public final String icon;
        public final int color;
        public final Style titleStyle;
        public final Style messageStyle;
        
        Type(String icon, int color, Style titleStyle, Style messageStyle) {
            this.icon = icon;
            this.color = color;
            this.titleStyle = titleStyle;
            this.messageStyle = messageStyle;
        }
    }
    
    private NotificationToast(Component title, Component message, Type type, int displayTime) {
        this.title = title.copy().withStyle(type.titleStyle);
        this.message = message.copy().withStyle(type.messageStyle);
        this.icon = type.icon;
        this.type = type;
        this.startTime = System.currentTimeMillis();
        this.displayTime = displayTime;
    }
    
    public static NotificationToast error(Component message) {
        return new NotificationToast(
            Component.translatable("gui.persona.dialog.error"),
            message,
            Type.ERROR,
            5000
        );
    }
    
    public static NotificationToast info(Component message) {
        return new NotificationToast(
            Component.translatable("gui.persona.dialog.info"),
            message,
            Type.INFO,
            3000
        );
    }
    
    public static NotificationToast success(Component message) {
        return new NotificationToast(
            Component.translatable("gui.persona.dialog.success"),
            message,
            Type.SUCCESS,
            3000
        );
    }
    
    @Override
    public Toast.Visibility render(@Nonnull GuiGraphics graphics, @Nonnull ToastComponent toastComponent, long timestamp) {
        if (graphics == null || toastComponent == null) {
            return Toast.Visibility.HIDE;
        }
        long elapsed = System.currentTimeMillis() - startTime;
        if (elapsed >= displayTime) {
            return Toast.Visibility.HIDE;
        }

        Font font = toastComponent.getMinecraft().font;

        // Calculate width based on the longest line (title or message)
        int titleWidth = font.width(Component.literal(icon + " " + title.getString()).withStyle(type.titleStyle));
        int messageWidth = font.width(message);
        int toastWidth = Math.max(MIN_WIDTH, Math.min(MAX_WIDTH, Math.max(titleWidth, messageWidth) + 2 * PADDING));
        this.cachedWidth = toastWidth;

        // Calculate height based on message lines
        int messageLines = font.split(message, toastWidth - 2 * PADDING).size();
        int toastHeight = HEIGHT + (messageLines - 1) * font.lineHeight;

        // Draw background at (0,0) - let Minecraft handle stacking/placement
        graphics.fill(0, 0, toastWidth, toastHeight, 0xE0000000);
        // Draw colored stripe on the left
        graphics.fill(0, 0, 2, toastHeight, type.color);
        // Draw icon and title
        String titleText = icon + " " + title.getString();
        graphics.drawString(font, Component.literal(titleText).withStyle(type.titleStyle), PADDING, PADDING, 0xFFFFFF);
        // Draw message (word-wrapped)
        graphics.drawWordWrap(font, message, PADDING, PADDING + 12, toastWidth - (PADDING * 2), 0xFFFFFF);

        return Toast.Visibility.SHOW;
    }
    
    @Override
    public int width() {
        return cachedWidth > 0 ? cachedWidth : MIN_WIDTH;
    }
} 