package com.dungeonitems.storage;

import com.bountysmp.configurableitems.model.TriggerType;
import com.dungeonitems.model.Ability;
import com.dungeonitems.model.DungeonItemTemplate;
import com.dungeonitems.model.DungeonItemType;
import com.dungeonitems.model.Rarity;
import com.dungeonitems.model.StatKey;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

public final class TemplateRepository {
    private final Path directory;
    private final Map<String, DungeonItemTemplate> templates = new LinkedHashMap<>();

    public TemplateRepository(Path directory) { this.directory = directory; }

    public void reload() {
        templates.clear();
        try {
            Files.createDirectories(directory);
            try (var paths = Files.list(directory)) {
                paths.filter(path -> path.getFileName().toString().endsWith(".yml")).sorted().forEach(path -> {
                    try { DungeonItemTemplate value = read(path); templates.put(value.id(), value); }
                    catch (RuntimeException ex) { throw new IllegalStateException("Could not load " + path.getFileName() + ": " + ex.getMessage(), ex); }
                });
            }
        } catch (IOException ex) { throw new IllegalStateException("Could not load DungeonItems: " + ex.getMessage(), ex); }
    }

    public DungeonItemTemplate get(String id) { return templates.get(normalize(id)); }
    public Collection<DungeonItemTemplate> all() { return templates.values().stream().sorted(Comparator.comparing(DungeonItemTemplate::id)).map(DungeonItemTemplate::copy).toList(); }

    public void save(DungeonItemTemplate template) throws IOException {
        Files.createDirectories(directory);
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("schema-version", 2); yaml.set("id", template.id()); yaml.set("name", template.name()); yaml.set("rarity", template.rarity().name());
        yaml.set("type", template.type().name()); yaml.set("description", template.description());
        template.statOverrides().forEach((key, value) -> yaml.set("stats." + key.name(), value));
        ConfigurationSection abilities = yaml.createSection("abilities");
        template.abilities().forEach((trigger, ability) -> {
            ConfigurationSection section = abilities.createSection(trigger.name());
            section.set("name", ability.name()); section.set("description", ability.description());
            section.set("cooldown-ticks", ability.cooldownTicks()); section.set("cooldown-message", ability.cooldownMessage());
            java.util.List<java.util.Map<String, Object>> actions = new java.util.ArrayList<>();
            for (var action : ability.actions()) {
                java.util.Map<String, Object> row = new java.util.LinkedHashMap<>();
                row.put("command", action.command()); row.put("cooldown-ticks", action.cooldownTicks());
                row.put("cooldown-message", action.cooldownMessage()); actions.add(row);
            }
            section.set("actions", actions);
        });
        atomicSave(yaml, file(template.id()));
        templates.put(template.id(), template.copy());
    }

    public void delete(String id) throws IOException {
        String normalized = normalize(id); templates.remove(normalized); Files.deleteIfExists(file(normalized));
    }

    private DungeonItemTemplate read(Path path) {
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(path.toFile());
        String fallback = path.getFileName().toString().replaceFirst("\\.yml$", "");
        DungeonItemTemplate template = new DungeonItemTemplate(yaml.getString("id", fallback), yaml.getString("name", fallback),
            enumValue(Rarity.class, yaml.getString("rarity"), Rarity.COMMON),
            enumValue(DungeonItemType.class, yaml.getString("type"), DungeonItemType.UTILITY), yaml.getString("description", ""));
        ConfigurationSection stats = yaml.getConfigurationSection("stats");
        if (stats != null) for (String key : stats.getKeys(false)) try {
            double value = stats.getDouble(key); if (Double.isFinite(value)) template.statOverrides().put(StatKey.valueOf(key.toUpperCase(Locale.ROOT)), value);
        } catch (IllegalArgumentException ignored) { }
        ConfigurationSection abilities = yaml.getConfigurationSection("abilities");
        if (abilities != null) for (String key : abilities.getKeys(false)) {
            try {
                TriggerType trigger = TriggerType.valueOf(key.toUpperCase(Locale.ROOT)); ConfigurationSection section = abilities.getConfigurationSection(key);
                if (section != null) {
                    java.util.List<com.bountysmp.configurableitems.model.CustomItemDefinition.TriggerCommandDef> actions = new java.util.ArrayList<>();
                    for (java.util.Map<?, ?> row : section.getMapList("actions")) actions.add(new com.bountysmp.configurableitems.model.CustomItemDefinition.TriggerCommandDef(
                        text(row.get("command")), number(row.get("cooldown-ticks")), text(row.get("cooldown-message"))));
                    template.abilities().put(trigger, new Ability(trigger, section.getString("name", trigger.name()),
                        section.getString("description", ""), section.getInt("cooldown-ticks", 0), section.getString("cooldown-message", ""), actions));
                }
            } catch (IllegalArgumentException ignored) { }
        }
        return template;
    }

    private Path file(String id) { return directory.resolve(id + ".yml"); }
    private static String normalize(String id) { return id == null ? "" : id.toLowerCase(Locale.ROOT); }
    private static <T extends Enum<T>> T enumValue(Class<T> type, String raw, T fallback) {
        try { return Enum.valueOf(type, raw == null ? "" : raw.toUpperCase(Locale.ROOT)); } catch (IllegalArgumentException ex) { return fallback; }
    }
    private static int number(Object value) { return value instanceof Number number ? number.intValue() : 0; }
    private static String text(Object value) { return value == null ? "" : String.valueOf(value); }
    static void atomicSave(YamlConfiguration yaml, Path target) throws IOException {
        Path temporary = target.resolveSibling(target.getFileName() + ".tmp"); yaml.save(temporary.toFile());
        try { Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING); }
        catch (java.nio.file.AtomicMoveNotSupportedException ex) { Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING); }
    }
}
