package world.landfall.persona.util;

import net.minecraft.server.MinecraftServer;
import net.neoforged.fml.loading.FMLPaths;
import org.apache.commons.io.FileUtils;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

public class NameListManager {
    private static final String BLACKLIST_FILE = "blacklist.txt";
    private static final String OVERRIDE_FILE = "blacklist-override.txt";
    private static final Path CONFIG_DIR = FMLPaths.CONFIGDIR.get().resolve("persona");
    
    private static List<String> blacklist = new ArrayList<>();
    private static List<String> overrides = new ArrayList<>();
    
    public static void init() {
        CONFIG_DIR.toFile().mkdirs();
        createDefaultListIfNotExists(BLACKLIST_FILE, getDefaultBlacklist());
        createDefaultListIfNotExists(OVERRIDE_FILE, getDefaultOverrides());
        reloadLists();
    }
    
    private static List<String> getDefaultBlacklist() {
        List<String> defaults = new ArrayList<>();
        defaults.add("admin");
        defaults.add("moderator");
        defaults.add("owner");
        defaults.add("staff");
        defaults.add("ass");  // Example for demonstration
        return defaults;
    }
    
    private static List<String> getDefaultOverrides() {
        List<String> defaults = new ArrayList<>();
        defaults.add("# Format: blacklisted_word:allowed_word");
        defaults.add("# Example: ass:cassie");
        defaults.add("ass:cassie");
        defaults.add("ass:bass");
        return defaults;
    }
    
    private static void createDefaultListIfNotExists(String filename, List<String> defaults) {
        File file = CONFIG_DIR.resolve(filename).toFile();
        if (!file.exists()) {
            try {
                String content = String.join("\n", defaults);
                FileUtils.writeStringToFile(file, content, StandardCharsets.UTF_8);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
    
    public static void reloadLists() {
        try {
            blacklist = loadList(BLACKLIST_FILE);
            overrides = loadOverrides(OVERRIDE_FILE);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
    
    private static List<String> loadList(String filename) throws IOException {
        Path path = CONFIG_DIR.resolve(filename);
        return Files.readAllLines(path).stream()
            .map(String::trim)
            .filter(s -> !s.isEmpty() && !s.startsWith("#"))
            .collect(Collectors.toList());
    }

    private static List<String> loadOverrides(String filename) throws IOException {
        Path path = CONFIG_DIR.resolve(filename);
        return Files.readAllLines(path).stream()
            .map(String::trim)
            .filter(s -> !s.isEmpty() && !s.startsWith("#"))
            .filter(s -> s.contains(":"))  // Only include valid override entries
            .collect(Collectors.toList());
    }
    
    public static boolean isNameAllowed(String name) {
        if (name == null) {
            return false;
        }
        
        String lowerName = name.toLowerCase();
        
        // Find any blacklisted word in the name
        Optional<String> blacklistedWord = blacklist.stream()
            .map(String::toLowerCase)
            .filter(lowerName::contains)
            .findFirst();
        
        // If no blacklisted word is found, the name is allowed
        if (blacklistedWord.isEmpty()) {
            return true;
        }
        
        // If a blacklisted word is found, check if there's a valid override
        String foundBlacklisted = blacklistedWord.get();
        return overrides.stream()
            .map(override -> override.split(":", 2))
            .filter(parts -> parts.length == 2)
            .filter(parts -> parts[0].toLowerCase().equals(foundBlacklisted))
            .anyMatch(parts -> lowerName.contains(parts[1].toLowerCase()));
    }
} 