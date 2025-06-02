package world.landfall.persona.features.landfalladdon.shells;

import com.mojang.logging.LogUtils;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import org.slf4j.Logger;
import world.landfall.persona.Persona;
import world.landfall.persona.data.CharacterProfile;
import world.landfall.persona.data.PlayerCharacterCapability;
import world.landfall.persona.data.PlayerCharacterData;
import world.landfall.persona.features.landfalladdon.LandfallAddonData;
import world.landfall.persona.registry.PersonaEvents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.core.Holder;
import java.util.UUID;

import java.util.List;
import java.util.Map;
import java.util.EnumMap;
import java.util.Collections;

/**
 * Applies and cleans up shell-based attribute modifiers for players.
 */
@EventBusSubscriber(modid = Persona.MODID)
public class ShellEffectManager {
    private static final Logger LOGGER = LogUtils.getLogger();

    private record EffectSpec(Holder<Attribute> attribute, double amount, AttributeModifier.Operation op) {}

    private static final Map<Shell, List<EffectSpec>> EFFECTS;
    static {
        Map<Shell, List<EffectSpec>> m = new EnumMap<>(Shell.class);
        m.put(Shell.SWIFT, java.util.List.<EffectSpec>of(new EffectSpec(Attributes.MOVEMENT_SPEED, 0.02, AttributeModifier.Operation.ADD_VALUE)));
        m.put(Shell.SLUGGISH, java.util.List.<EffectSpec>of(new EffectSpec(Attributes.MOVEMENT_SPEED, -0.015, AttributeModifier.Operation.ADD_VALUE)));
        m.put(Shell.ROBUST, java.util.List.<EffectSpec>of(new EffectSpec(Attributes.MAX_HEALTH, 2.0, AttributeModifier.Operation.ADD_VALUE)));
        m.put(Shell.FRAIL, java.util.List.<EffectSpec>of(new EffectSpec(Attributes.MAX_HEALTH, -4.0, AttributeModifier.Operation.ADD_VALUE)));
        m.put(Shell.KEEN_EYES, java.util.List.<EffectSpec>of(new EffectSpec(Attributes.LUCK, 1.0, AttributeModifier.Operation.ADD_VALUE)));
        m.put(Shell.CLUMSY, java.util.List.<EffectSpec>of(new EffectSpec(Attributes.LUCK, -1.0, AttributeModifier.Operation.ADD_VALUE)));
        m.put(Shell.NEUTRAL, List.of());
        EFFECTS = Collections.unmodifiableMap(m);
    }

    /* --------------------------- Public API --------------------------- */

    public static void applyShell(ServerPlayer player, Shell shell) {
        removeAllShellModifiers(player);
        List<EffectSpec> specs = EFFECTS.getOrDefault(shell, List.of());
        for (EffectSpec spec : specs) {
            AttributeInstance inst = player.getAttribute(spec.attribute);
            if (inst == null) continue;
            ResourceLocation rl = getRL(shell, spec.attribute);
            inst.removeModifier(rl);
            AttributeModifier mod = new AttributeModifier(rl, spec.amount, spec.op);
            inst.addPermanentModifier(mod);
        }
        // Sync health to prevent over-heal issues if MAX_HEALTH changed
        player.setHealth(Math.min(player.getHealth(), player.getMaxHealth()));
    }

    /* --------------------------- Internal helpers --------------------------- */

    private static void removeAllShellModifiers(ServerPlayer player) {
        for (Shell s : Shell.values()) {
            List<EffectSpec> specs = EFFECTS.getOrDefault(s, List.of());
            for (EffectSpec spec : specs) {
                AttributeInstance inst = player.getAttribute(spec.attribute);
                if (inst == null) continue;
                ResourceLocation rl = getRL(s, spec.attribute);
                if (inst.getModifier(rl) != null) {
                    inst.removeModifier(rl);
                }
            }
        }
    }

    private static ResourceLocation getRL(Shell shell, Holder<Attribute> attrHolder) {
        String attrPath = attrHolder.unwrapKey().map(k -> k.location().getPath()).orElse("unknown");
        String path = "shell_" + shell.name().toLowerCase() + "_" + attrPath;
        return ResourceLocation.fromNamespaceAndPath(Persona.MODID, path);
    }

    /* --------------------------- Event hooks --------------------------- */

    @SubscribeEvent
    public static void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        PlayerCharacterData data = player.getData(PlayerCharacterCapability.CHARACTER_DATA);
        if (data == null) return;
        UUID active = data.getActiveCharacterId();
        if (active == null) return;
        CharacterProfile profile = data.getCharacter(active);
        if (profile == null) return;
        Shell shell = LandfallAddonData.getCurrentShell(profile);
        applyShell(player, shell);
        LOGGER.debug("[ShellEffects] Applied shell {} for player {} on login.", shell, player.getName().getString());
    }

    @SubscribeEvent
    public static void onCharacterSwitch(PersonaEvents.CharacterSwitchEvent event) {
        if (!(event.getPlayer() instanceof ServerPlayer player)) return;
        PlayerCharacterData data = player.getData(PlayerCharacterCapability.CHARACTER_DATA);
        if (data == null) return;
        CharacterProfile profile = data.getCharacter(event.getToCharacterId());
        if (profile == null) return;
        Shell shell = LandfallAddonData.getCurrentShell(profile);
        applyShell(player, shell);
        LOGGER.debug("[ShellEffects] Applied shell {} for player {} on character switch.", shell, player.getName().getString());
    }

    @SubscribeEvent
    public static void onPlayerRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        PlayerCharacterData data = player.getData(PlayerCharacterCapability.CHARACTER_DATA);
        if (data == null) return;
        UUID active = data.getActiveCharacterId();
        if (active == null) return;
        CharacterProfile profile = data.getCharacter(active);
        if (profile == null) return;
        Shell shell = LandfallAddonData.getCurrentShell(profile);
        applyShell(player, shell);
        LOGGER.debug("[ShellEffects] Applied shell {} for player {} on respawn.", shell, player.getName().getString());
    }
} 