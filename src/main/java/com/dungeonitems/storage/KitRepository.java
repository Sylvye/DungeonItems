package com.dungeonitems.storage;

import com.dungeonitems.model.KitDefinition;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;

public final class KitRepository {
    private static final int SCHEMA_VERSION = 1;
    private static final String[] SPECIAL_SLOTS = {"helmet", "chestplate", "leggings", "boots", "offhand"};
    private final Path directory;
    private final Map<String, KitDefinition> kits = new LinkedHashMap<>();
    public KitRepository(Path directory) { this.directory = directory; }

    public void reload() {
        try {
            Files.createDirectories(directory);
            Map<String, KitDefinition> loaded = new LinkedHashMap<>();
            try (var paths = Files.list(directory)) {
                paths.filter(path -> path.getFileName().toString().endsWith(".yml")).sorted().forEach(path -> {
                    YamlConfiguration yaml = YamlConfiguration.loadConfiguration(path.toFile());
                    String fallback = path.getFileName().toString().replaceFirst("\\.yml$", "");
                    KitDefinition kit = new KitDefinition(yaml.getString("id", fallback));
                    for (int slot = 0; slot < 36; slot++) loadSlot(yaml, kit, "inventory." + slot, path);
                    for (String key : SPECIAL_SLOTS) loadSlot(yaml, kit, key, path);
                    if (loaded.putIfAbsent(kit.id(), kit) != null) throw new IllegalStateException("Duplicate kit id '" + kit.id() + "' in " + path.getFileName());
                });
            }
            kits.clear(); kits.putAll(loaded);
        } catch (IOException ex) { throw new IllegalStateException("Could not load kits: " + ex.getMessage(), ex); }
    }

    private static void loadSlot(YamlConfiguration yaml, KitDefinition kit, String key, Path source) {
        String path = "slots." + key;
        if (!yaml.contains(path)) return;
        ItemStack item = yaml.getItemStack(path);
        if (item == null) throw new IllegalStateException("Invalid item in kit " + source.getFileName() + " at " + path);
        kit.slots().put(key, item);
    }

    public KitDefinition get(String id) { KitDefinition kit = kits.get(normalize(id)); return kit == null ? null : kit.copy(); }
    public Collection<KitDefinition> all() { return kits.values().stream().sorted(Comparator.comparing(KitDefinition::id)).map(KitDefinition::copy).toList(); }
    public void save(KitDefinition kit) throws IOException {
        Files.createDirectories(directory); YamlConfiguration yaml = new YamlConfiguration(); yaml.set("schema-version", SCHEMA_VERSION); yaml.set("id", kit.id());
        kit.slots().forEach((key, item) -> yaml.set("slots." + key, item.clone())); TemplateRepository.atomicSave(yaml, directory.resolve(kit.id() + ".yml")); kits.put(kit.id(), kit.copy());
    }
    public void delete(String id) throws IOException { String key = normalize(id); kits.remove(key); Files.deleteIfExists(directory.resolve(key + ".yml")); }
    public void rename(String sourceId, KitDefinition replacement) throws IOException {
        String source = normalize(sourceId);
        if (!kits.containsKey(source)) throw new IllegalArgumentException("Unknown kit: " + sourceId);
        if (source.equals(replacement.id())) { save(replacement); return; }
        if (kits.containsKey(replacement.id()) || Files.exists(directory.resolve(replacement.id() + ".yml"))) throw new IllegalArgumentException("Kit already exists: " + replacement.id());
        save(replacement);
        try { Files.delete(directory.resolve(source + ".yml")); }
        catch (IOException ex) { Files.deleteIfExists(directory.resolve(replacement.id() + ".yml")); kits.remove(replacement.id()); throw new IOException("Could not remove old kit '" + source + "'; rename was rolled back", ex); }
        kits.remove(source);
    }
    public void duplicate(KitDefinition kit) throws IOException {
        if (kits.containsKey(kit.id()) || Files.exists(directory.resolve(kit.id() + ".yml"))) throw new IllegalArgumentException("Kit already exists: " + kit.id());
        save(kit);
    }
    private static String normalize(String id) { return id == null ? "" : id.toLowerCase(Locale.ROOT); }
}
