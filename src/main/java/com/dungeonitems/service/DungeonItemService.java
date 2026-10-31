package com.dungeonitems.service;

import com.bountysmp.configurableitems.api.ConfigurableItemsAPI;
import com.bountysmp.configurableitems.model.CustomItemDefinition;
import com.dungeonitems.api.DungeonItemInfo;
import com.dungeonitems.api.DungeonItemsAPI;
import com.dungeonitems.model.DungeonItemTemplate;
import com.dungeonitems.model.Ability;
import com.dungeonitems.model.StatKey;
import com.dungeonitems.presentation.PresentationRenderer;
import com.dungeonitems.storage.TemplateRepository;
import java.io.IOException;
import java.util.Collection;
import java.util.Optional;
import java.util.logging.Logger;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

public final class DungeonItemService implements DungeonItemsAPI {
    private final ConfigurableItemsAPI ci;
    private final TemplateRepository templates;
    private final PresentationRenderer renderer;
    private final Logger logger;
    private com.dungeonitems.storage.KitRepository kits;

    public void kitRepository(com.dungeonitems.storage.KitRepository kits) { this.kits = kits; }
    @Override public Collection<String> kits() { return kits == null ? java.util.List.of() : kits.all().stream().map(com.dungeonitems.model.KitDefinition::id).toList(); }
    @Override public Optional<com.dungeonitems.model.KitDefinition> kit(String id) { return kits == null ? Optional.empty() : Optional.ofNullable(kits.get(id)); }

    public DungeonItemService(ConfigurableItemsAPI ci, TemplateRepository templates, PresentationRenderer renderer, Logger logger) {
        this.ci = ci; this.templates = templates; this.renderer = renderer; this.logger = logger;
    }

    public void reload() {
        templates.reload();
        for (DungeonItemTemplate template : templates.all()) {
            ci.definition(template.id()).ifPresentOrElse(definition -> {
                try {
                    migrateAbilities(template, definition);
                    definition.managedBy("DungeonItems");
                    templates.save(template);
                    ci.saveDefinition(renderer.apply(template, definition));
                }
                catch (IOException ex) { logger.warning("Could not synchronize " + template.id() + ": " + ex.getMessage()); }
            }, () -> logger.warning("DungeonItems template " + template.id() + " has no ConfigurableItems definition"));
        }
    }

    public DungeonItemTemplate template(String id) { DungeonItemTemplate value = templates.get(id); return value == null ? null : value.copy(); }
    public Collection<DungeonItemTemplate> templates() { return templates.all(); }
    public Optional<CustomItemDefinition> definition(String id) { return ci.definition(id); }

    public Draft createDraft(String id) {
        if (id == null || !id.matches("[a-z0-9_-]+")) throw new IllegalArgumentException("IDs must match [a-z0-9_-]+");
        if (templates.get(id) != null || ci.definition(id).isPresent()) throw new IllegalArgumentException("Item id already exists: " + id);
        DungeonItemTemplate template = new DungeonItemTemplate(id, friendly(id), com.dungeonitems.model.Rarity.COMMON, com.dungeonitems.model.DungeonItemType.UTILITY, "");
        CustomItemDefinition definition = new CustomItemDefinition(id); definition.managedBy("DungeonItems");
        return new Draft(template, definition);
    }

    public void save(DungeonItemTemplate template, CustomItemDefinition definition) throws IOException {
        Optional<CustomItemDefinition> oldDefinition = ci.definition(template.id());
        definition.managedBy("DungeonItems");
        CustomItemDefinition rendered = renderer.apply(template, definition);
        rendered.managedBy("DungeonItems");
        ci.saveDefinition(rendered);
        try { templates.save(template); }
        catch (IOException ex) {
            try { if (oldDefinition.isPresent()) ci.saveDefinition(oldDefinition.get()); else ci.deleteDefinition(template.id()); }
            catch (IOException rollback) { ex.addSuppressed(rollback); }
            throw ex;
        }
    }

    public void delete(String id) throws IOException {
        if (templates.get(id) == null) throw new IllegalArgumentException("Unknown DungeonItems id: " + id);
        templates.delete(id); ci.deleteDefinition(id);
    }

    @Override public Collection<DungeonItemInfo> items() {
        return templates.all().stream().map(template -> new DungeonItemInfo(template.id(), template.name(), template.rarity(), template.type(),
            ci.definition(template.id()).map(CustomItemDefinition::material).orElse(Material.BARRIER))).toList();
    }

    @Override public Optional<ItemStack> createItem(String id, int amount) {
        DungeonItemTemplate template = templates.get(id); if (template == null) return Optional.empty();
        return ci.definition(id).map(definition -> ci.createItem(renderer.apply(template, definition), Math.max(1, Math.min(99, amount))));
    }

    public ItemStack preview(DungeonItemTemplate template, CustomItemDefinition definition) {
        return ci.createItem(renderer.apply(template, definition), 1);
    }

    public double vanillaValue(DungeonItemTemplate template, CustomItemDefinition definition, StatKey key) {
        return renderer.vanillaValue(template, definition, key);
    }

    private void migrateAbilities(DungeonItemTemplate template, CustomItemDefinition definition) {
        for (var trigger : com.bountysmp.configurableitems.model.TriggerType.values()) {
            var commands = definition.triggers().getOrDefault(trigger, java.util.List.of());
            var existing = template.abilities().get(trigger);
            if (existing != null && existing.actions().isEmpty() && !commands.isEmpty()) {
                template.abilities().put(trigger, existing.withActions(commands));
            } else if (existing == null && !commands.isEmpty()) {
                var settings = definition.settings(trigger);
                template.abilities().put(trigger, new Ability(trigger, friendly(trigger.name().toLowerCase(java.util.Locale.ROOT)) + " Ability", "",
                    settings.cooldownTicks(), settings.cooldownMessage(), commands));
            }
        }
    }

    public record Draft(DungeonItemTemplate template, CustomItemDefinition definition) { }
    private static String friendly(String id) {
        StringBuilder value = new StringBuilder();
        for (String word : id.split("_")) { if (!value.isEmpty()) value.append(' '); value.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1)); }
        return value.toString();
    }
}
