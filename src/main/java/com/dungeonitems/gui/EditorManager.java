package com.dungeonitems.gui;

import com.bountysmp.configurableitems.api.ConfigurableItemsAPI;
import com.bountysmp.configurableitems.api.EditorSection;
import com.bountysmp.configurableitems.model.CustomItemDefinition;
import com.bountysmp.configurableitems.model.TriggerType;
import com.dungeonitems.model.*;
import com.dungeonitems.service.DungeonItemService;
import com.dungeonitems.storage.KitRepository;
import io.papermc.paper.event.player.AsyncChatEvent;
import java.io.IOException;
import java.util.*;
import java.util.function.Consumer;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.*;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.Plugin;

public final class EditorManager implements Listener {
    private final Plugin plugin;
    private final DungeonItemService items;
    private final ConfigurableItemsAPI ci;
    private final KitRepository kits;
    private final com.dungeonitems.service.KitGiver kitGiver = new com.dungeonitems.service.KitGiver();
    private final Map<UUID, ItemDraft> itemDrafts = new HashMap<>();
    private final Map<UUID, Consumer<String>> prompts = new HashMap<>();

    public EditorManager(Plugin plugin, DungeonItemService items, ConfigurableItemsAPI ci, KitRepository kits) {
        this.plugin = plugin; this.items = items; this.ci = ci; this.kits = kits;
    }

    public void openMain(Player player, int page) {
        Menu menu = new Menu("DungeonItems"); frame(menu.inventory);
        button(menu, 4, Material.NETHER_STAR, "DungeonItems", List.of("RPG item templates"), e -> {});
        button(menu, 45, Material.CHEST, "Kits", List.of("Browse and manage kits"), e -> openKits(player, 0));
        int start = Math.max(0, page) * 28, slot = 10, index = 0;
        for (DungeonItemTemplate template : items.templates()) {
            if (index++ < start) continue; if (slot >= 44) break; if (slot % 9 == 8) slot += 2;
            ItemStack preview = items.createItem(template.id(), 1).orElse(new ItemStack(Material.BARRIER));
            int target = slot++; menu.inventory.setItem(target, preview); menu.actions.put(target, e -> openItem(player, template.id()));
        }
        if (page > 0) button(menu, 46, Material.ARROW, "Previous", List.of(), e -> openMain(player, page - 1));
        if (items.templates().size() > start + 28) button(menu, 53, Material.ARROW, "Next", List.of(), e -> openMain(player, page + 1));
        player.openInventory(menu.inventory);
    }

    public void createItem(Player player, String id) {
        if (!replaceableSession(player, id)) return;
        try {
            DungeonItemService.Draft source = items.createDraft(id);
            itemDrafts.put(player.getUniqueId(), new ItemDraft(source.template(), source.definition(), true));
            openItemEditor(player);
        } catch (IllegalArgumentException ex) { error(player, ex.getMessage()); }
    }

    public void openItem(Player player, String id) {
        ItemDraft active = draft(player);
        if (active != null && active.template.id().equals(id)) { openItemEditor(player); return; }
        if (!replaceableSession(player, id)) return;
        DungeonItemTemplate template = items.template(id); CustomItemDefinition definition = items.definition(id).orElse(null);
        if (template == null || definition == null) { error(player, "Unknown or incomplete item: " + id); return; }
        itemDrafts.put(player.getUniqueId(), new ItemDraft(template, definition, false)); openItemEditor(player);
    }

    private boolean replaceableSession(Player player, String requestedId) {
        ItemDraft active = draft(player);
        if (active == null || active.template.id().equals(requestedId)) return true;
        if (active.dirty) { error(player, "Finish or discard the open draft '" + active.template.id() + "' first."); openItemEditor(player); return false; }
        itemDrafts.remove(player.getUniqueId()); return true;
    }

    private void openItemEditor(Player player) {
        ItemDraft draft = draft(player); if (draft == null) return;
        DungeonItemTemplate template = draft.template; Menu menu = new Menu("DI: " + template.id()); frame(menu.inventory);
        menu.inventory.setItem(4, items.preview(template, draft.definition));
        button(menu, 10, Material.NAME_TAG, "Name", List.of(template.name()), e -> prompt(player, "Enter the item name", value -> mutate(player, () -> template.name(value), () -> openItemEditor(player))));
        button(menu, 11, Material.NETHER_STAR, "Rarity", List.of(template.rarity().name()), e -> mutate(player, () -> template.rarity(next(template.rarity())), () -> openItemEditor(player)));
        button(menu, 12, Material.ITEM_FRAME, "Type", List.of(template.type().name()), e -> mutate(player, () -> { template.type(next(template.type())); template.statOverrides().entrySet().removeIf(row -> row.getKey().type() != template.type()); }, () -> openItemEditor(player)));
        button(menu, 13, Material.WRITABLE_BOOK, "Description", List.of(template.description().isBlank() ? "None" : template.description()), e ->
            prompt(player, "Enter a description. Use \\n for line breaks, or 'clear'", value -> mutate(player, () -> template.description(value.equalsIgnoreCase("clear") ? "" : value.replace("\\n", "\n")), () -> openItemEditor(player))));
        button(menu, 14, draft.definition.material(), "Material", List.of(draft.definition.material().name()), e -> openMaterialSelector(player, 0, ""));
        button(menu, 15, template.type() == DungeonItemType.ARMOR ? Material.IRON_CHESTPLATE : Material.IRON_SWORD, "Stats", List.of("Edit final RPG values"), e -> openStats(player));
        button(menu, 16, Material.ANVIL, "Unbreakable", List.of(onOff(draft.definition.extras().unbreakable), "Tooltip: " + shown(draft.definition.extras().showUnbreakable)), e -> openUnbreakable(player));
        button(menu, 19, Material.ENCHANTED_BOOK, "Enchantments", List.of(draft.definition.enchantments().size() + " configured"), e -> openFocused(player, EditorSection.ENCHANTMENTS));
        button(menu, 20, Material.BLAZE_POWDER, "Abilities", List.of(template.abilities().size() + " configured"), e -> openAbilities(player));
        button(menu, 21, Material.COMPARATOR, "Advanced", List.of("Less common mechanics", "No duplicate presentation settings"), e -> openAdvanced(player));
        button(menu, 31, Material.PLAYER_HEAD, "Preview / Give", List.of("Give the current live preview"), e -> player.getInventory().addItem(items.preview(template, draft.definition)));
        button(menu, 45, Material.ARROW, "Back", List.of(draft.dirty ? "Unsaved changes" : "Return to list"), e -> { if (draft.dirty) openExitConfirmation(player); else { itemDrafts.remove(player.getUniqueId()); openMain(player, 0); } });
        button(menu, 46, Material.RED_DYE, "Delete", List.of("Delete this template"), e -> deleteItem(player, template.id()));
        button(menu, 53, Material.LIME_DYE, "Save", List.of(draft.dirty ? "Save all changes" : "No unsaved changes"), e -> saveItem(player));
        player.openInventory(menu.inventory);
    }

    private void openMaterialSelector(Player player, int page, String filter) {
        ItemDraft draft = draft(player); if (draft == null) return;
        String query = filter == null ? "" : filter.trim().toLowerCase(Locale.ROOT);
        List<Material> materials = Arrays.stream(Material.values()).filter(Material::isItem).filter(value -> !value.isAir())
            .filter(value -> query.isBlank() || value.name().toLowerCase(Locale.ROOT).contains(query)).toList();
        Menu menu = new Menu("DI: Select Material"); frame(menu.inventory); int start = Math.max(0, page) * 28, slot = 10;
        for (int i = start; i < materials.size() && i < start + 28; i++) {
            if (slot % 9 == 8) slot += 2; Material material = materials.get(i); int target = slot++;
            button(menu, target, material, friendly(material.name()), List.of(material.name()), e -> mutate(player, () -> draft.definition.material(material), () -> openItemEditor(player)));
        }
        if (page > 0) button(menu, 45, Material.ARROW, "Previous", List.of(), e -> openMaterialSelector(player, page - 1, query));
        button(menu, 48, Material.COMPASS, "Search", List.of(query.isBlank() ? "All materials" : query), e -> prompt(player, "Enter a material search, or 'clear'", raw -> openMaterialSelector(player, 0, raw.equalsIgnoreCase("clear") ? "" : raw)));
        button(menu, 49, Material.ARROW, "Back", List.of(), e -> openItemEditor(player));
        if (start + 28 < materials.size()) button(menu, 53, Material.ARROW, "Next", List.of(), e -> openMaterialSelector(player, page + 1, query));
        player.openInventory(menu.inventory);
    }

    private void openStats(Player player) {
        ItemDraft draft = draft(player); if (draft == null) return; Menu menu = new Menu("DI: Final Stats"); frame(menu.inventory); int slot = 10;
        for (StatKey key : StatKey.values()) if (key.type() == draft.template.type()) {
            double vanilla = items.vanillaValue(draft.template, draft.definition, key); Double override = draft.template.statOverrides().get(key); int target = slot++;
            button(menu, target, statIcon(key), key.label(), List.of("Current: " + format(override == null ? vanilla : override), "Material default: " + format(vanilla), "Left-click to set", "Right-click to reset"), e -> {
                if (e.isRightClick()) mutate(player, () -> draft.template.statOverrides().remove(key), () -> openStats(player));
                else prompt(player, "Enter final " + key.label() + " value", raw -> {
                    try { double value = Double.parseDouble(raw.trim()); if (!Double.isFinite(value)) throw new NumberFormatException(); mutate(player, () -> draft.template.statOverrides().put(key, value), () -> openStats(player)); }
                    catch (NumberFormatException ex) { error(player, "Enter a finite number."); openStats(player); }
                });
            });
        }
        menu.inventory.setItem(4, items.preview(draft.template, draft.definition));
        button(menu, 49, Material.ARROW, "Back", List.of(), e -> openItemEditor(player)); player.openInventory(menu.inventory);
    }

    private void openUnbreakable(Player player) {
        ItemDraft draft = draft(player); if (draft == null) return; Menu menu = new Menu("DI: Unbreakable"); frame(menu.inventory);
        button(menu, 10, Material.ANVIL, "Unbreakable", List.of(onOff(draft.definition.extras().unbreakable)), e -> mutate(player, () -> draft.definition.extras().unbreakable = !draft.definition.extras().unbreakable, () -> openUnbreakable(player)));
        button(menu, 11, Material.BOOK, "Show Tooltip", List.of(shown(draft.definition.extras().showUnbreakable)), e -> mutate(player, () -> draft.definition.extras().showUnbreakable = !draft.definition.extras().showUnbreakable, () -> openUnbreakable(player)));
        menu.inventory.setItem(4, items.preview(draft.template, draft.definition));
        button(menu, 49, Material.ARROW, "Back", List.of(), e -> openItemEditor(player)); player.openInventory(menu.inventory);
    }

    private void openAdvanced(Player player) {
        Menu menu = new Menu("DI: Advanced"); frame(menu.inventory);
        advancedButton(menu, player, 10, Material.COOKED_BEEF, "Consumable", EditorSection.FOOD);
        advancedButton(menu, player, 11, Material.IRON_PICKAXE, "Tool / Mining", EditorSection.TOOL);
        advancedButton(menu, player, 12, Material.ELYTRA, "Equipment", EditorSection.EQUIP);
        advancedButton(menu, player, 13, Material.CHEST, "Durability / Item Data", EditorSection.EXTRAS);
        advancedButton(menu, player, 14, Material.BARRIER, "Restrictions", EditorSection.RESTRICTIONS);
        advancedButton(menu, player, 15, Material.ARMOR_STAND, "Uncommon Attributes", EditorSection.ATTRIBUTES);
        button(menu, 49, Material.ARROW, "Back", List.of(), e -> openItemEditor(player)); player.openInventory(menu.inventory);
    }

    private void advancedButton(Menu menu, Player player, int slot, Material icon, String name, EditorSection section) { button(menu, slot, icon, name, List.of(), e -> openFocused(player, section)); }

    private void openFocused(Player player, EditorSection section) {
        ItemDraft draft = draft(player); if (draft == null) return;
        ci.openSectionEditor(player, draft.definition, section, updated -> { draft.definition = updated; draft.dirty = true; },
            () -> { if (section == EditorSection.ENCHANTMENTS) openItemEditor(player); else openAdvanced(player); });
    }

    private void openAbilities(Player player) {
        ItemDraft draft = draft(player); if (draft == null) return; Menu menu = new Menu("DI: Abilities"); frame(menu.inventory);
        button(menu, 10, Material.LIME_DYE, "Create Ability", List.of("Choose a trigger, then configure it"), e -> openTriggerPicker(player));
        int slot = 19;
        for (Ability ability : new ArrayList<>(draft.template.abilities().values())) {
            if (slot >= 44) break; int target = slot++;
            button(menu, target, triggerIcon(ability.trigger()), friendly(ability.trigger().name()) + ": " + ability.name(), List.of(ability.actions().size() + " actions", "Left-click to edit", "Right-click to remove"), e -> {
                if (e.isRightClick()) mutate(player, () -> { draft.template.abilities().remove(ability.trigger()); draft.definition.triggers().remove(ability.trigger()); draft.definition.triggerSettings().remove(ability.trigger()); }, () -> openAbilities(player));
                else openAbilityEditor(player, ability.trigger());
            });
        }
        button(menu, 49, Material.ARROW, "Back", List.of(), e -> openItemEditor(player)); player.openInventory(menu.inventory);
    }

    private void openTriggerPicker(Player player) {
        ItemDraft draft = draft(player); if (draft == null) return; Menu menu = new Menu("DI: Choose Trigger"); frame(menu.inventory); int slot = 10;
        for (TriggerType trigger : TriggerType.values()) {
            if (draft.template.abilities().containsKey(trigger)) continue; if (slot >= 44) break; if (slot % 9 == 8) slot += 2; int target = slot++;
            button(menu, target, triggerIcon(trigger), friendly(trigger.name()), List.of("Create an ability for this event"), e -> mutate(player,
                () -> draft.template.abilities().put(trigger, new Ability(trigger, friendly(trigger.name()) + " Ability", "", 0, "", List.of())),
                () -> openAbilityEditor(player, trigger)));
        }
        button(menu, 49, Material.ARROW, "Back", List.of(), e -> openAbilities(player)); player.openInventory(menu.inventory);
    }

    private void openAbilityEditor(Player player, TriggerType trigger) {
        ItemDraft draft = draft(player); if (draft == null) return; Ability ability = draft.template.abilities().get(trigger); if (ability == null) { openAbilities(player); return; }
        Menu menu = new Menu("DI: " + friendly(trigger.name())); frame(menu.inventory);
        button(menu, 10, Material.NAME_TAG, "Ability Name", List.of(ability.name()), e -> prompt(player, "Enter the ability name", raw -> updateAbility(player, trigger, value -> value.withName(raw))));
        button(menu, 11, Material.WRITABLE_BOOK, "Description", List.of(ability.description().isBlank() ? "None" : ability.description()), e -> prompt(player, "Enter the ability description, or 'clear'", raw -> updateAbility(player, trigger, value -> value.withDescription(raw.equalsIgnoreCase("clear") ? "" : raw.replace("\\n", "\n")))));
        button(menu, 12, Material.CLOCK, "Cooldown", List.of(format(ability.cooldownTicks() / 20.0) + " seconds"), e -> prompt(player, "Enter cooldown in seconds, or 'clear'", raw -> {
            try { double seconds = raw.equalsIgnoreCase("clear") ? 0 : Double.parseDouble(raw); updateAbility(player, trigger, value -> value.withCooldown((int) Math.round(Math.max(0, seconds) * 20))); }
            catch (NumberFormatException ex) { error(player, "Enter a valid number of seconds."); openAbilityEditor(player, trigger); }
        }));
        button(menu, 13, Material.PAPER, "Cooldown Message", List.of(ability.cooldownMessage().isBlank() ? "Default" : ability.cooldownMessage()), e -> prompt(player, "Enter the cooldown message, or 'clear'", raw -> updateAbility(player, trigger, value -> value.withCooldownMessage(raw.equalsIgnoreCase("clear") ? "" : raw))));
        button(menu, 14, Material.COMMAND_BLOCK, "Actions", List.of(ability.actions().size() + " configured", "CI-powered guided action editor"), e -> openAbilityActions(player, trigger));
        button(menu, 15, triggerIcon(trigger), "Trigger", List.of(friendly(trigger.name()), "Click to change"), e -> openChangeTrigger(player, trigger));
        button(menu, 49, Material.ARROW, "Back", List.of(), e -> openAbilities(player)); player.openInventory(menu.inventory);
    }

    private void openChangeTrigger(Player player, TriggerType currentTrigger) {
        ItemDraft draft = draft(player); if (draft == null) return; Menu menu = new Menu("DI: Change Trigger"); frame(menu.inventory); int slot = 10;
        for (TriggerType candidate : TriggerType.values()) {
            if (candidate != currentTrigger && draft.template.abilities().containsKey(candidate)) continue;
            if (slot >= 44) break; if (slot % 9 == 8) slot += 2; int target = slot++;
            button(menu, target, triggerIcon(candidate), friendly(candidate.name()), List.of(candidate == currentTrigger ? "Current trigger" : "Move this ability here"), e -> {
                if (candidate == currentTrigger) { openAbilityEditor(player, currentTrigger); return; }
                mutate(player, () -> {
                    Ability moved = draft.template.abilities().remove(currentTrigger).withTrigger(candidate);
                    draft.template.abilities().put(candidate, moved);
                    draft.definition.triggers().remove(currentTrigger); draft.definition.triggerSettings().remove(currentTrigger);
                }, () -> openAbilityEditor(player, candidate));
            });
        }
        button(menu, 49, Material.ARROW, "Back", List.of(), e -> openAbilityEditor(player, currentTrigger)); player.openInventory(menu.inventory);
    }

    private void openAbilityActions(Player player, TriggerType trigger) {
        ItemDraft draft = draft(player); if (draft == null) return; Ability ability = draft.template.abilities().get(trigger); if (ability == null) return;
        List<CustomItemDefinition.TriggerCommandDef> commands = draft.definition.commands(trigger); commands.clear(); commands.addAll(ability.actions().stream().map(CustomItemDefinition.TriggerCommandDef::copy).toList());
        ci.openTriggerActionEditor(player, draft.definition, trigger, updated -> {
            draft.definition = updated; Ability current = draft.template.abilities().get(trigger);
            if (current != null) draft.template.abilities().put(trigger, current.withActions(updated.commands(trigger))); draft.dirty = true;
        }, () -> {
            Ability current = draft.template.abilities().get(trigger);
            if (current != null) draft.template.abilities().put(trigger, current.withActions(draft.definition.commands(trigger)));
            openAbilityEditor(player, trigger);
        });
    }

    private void updateAbility(Player player, TriggerType trigger, java.util.function.UnaryOperator<Ability> update) {
        ItemDraft draft = draft(player); if (draft == null) return; mutate(player, () -> draft.template.abilities().computeIfPresent(trigger, (ignored, value) -> update.apply(value)), () -> openAbilityEditor(player, trigger));
    }

    private void openExitConfirmation(Player player) {
        Menu menu = new Menu("DI: Unsaved Changes"); frame(menu.inventory);
        button(menu, 20, Material.LIME_DYE, "Save", List.of("Commit the entire item"), e -> { saveItem(player); ItemDraft draft = draft(player); if (draft != null && !draft.dirty) { itemDrafts.remove(player.getUniqueId()); openMain(player, 0); } });
        button(menu, 22, Material.RED_DYE, "Discard", List.of("Lose unsaved changes"), e -> { itemDrafts.remove(player.getUniqueId()); openMain(player, 0); });
        button(menu, 24, Material.ARROW, "Continue Editing", List.of(), e -> openItemEditor(player)); player.openInventory(menu.inventory);
    }

    private void mutate(Player player, Runnable change, Runnable reopen) { ItemDraft draft = draft(player); if (draft == null) return; change.run(); draft.dirty = true; reopen.run(); }
    private ItemDraft draft(Player player) { return itemDrafts.get(player.getUniqueId()); }

    private void saveItem(Player player) {
        ItemDraft draft = draft(player); if (draft == null) return;
        if (draft.template.name().isBlank()) { error(player, "Name is required"); return; }
        try { items.save(draft.template, draft.definition); draft.dirty = false; player.sendMessage(Component.text("Saved " + draft.template.id(), NamedTextColor.GREEN)); openItemEditor(player); }
        catch (IOException ex) { error(player, "Save failed: " + ex.getMessage()); }
    }

    public void deleteItem(Player player, String id) {
        try { items.delete(id); itemDrafts.remove(player.getUniqueId()); player.sendMessage(Component.text("Deleted " + id, NamedTextColor.GREEN)); openMain(player, 0); }
        catch (IOException | IllegalArgumentException ex) { error(player, "Delete failed: " + ex.getMessage()); }
    }

    public void openKit(Player player, String id, boolean create) {
        KitDefinition kit = kits.get(id);
        if (create && kit != null) { error(player, "Kit already exists: " + id); return; }
        if (!create && kit == null) { error(player, "Unknown kit: " + id); return; }
        try { openKitEditor(player, kit == null ? new KitDefinition(id) : kit); } catch (IllegalArgumentException ex) { error(player, ex.getMessage()); }
    }

    public void openKits(Player player, int page) {
        List<KitDefinition> all = kits.all().stream().toList();
        int start = Math.max(0, page) * 28;
        if (start >= all.size() && page > 0) { openKits(player, page - 1); return; }
        Menu menu = new Menu("DI: Kits"); frame(menu.inventory);
        button(menu, 4, Material.CHEST, "Kits", List.of(all.size() + " saved kit" + (all.size() == 1 ? "" : "s")), e -> {});
        int slot = 10;
        for (int index = start; index < all.size() && index < start + 28; index++) {
            if (slot % 9 == 8) slot += 2;
            KitDefinition kit = all.get(index); int target = slot++;
            Material thumbnail = kit.slots().get("inventory.0") == null || kit.slots().get("inventory.0").getType().isAir()
                ? Material.STRUCTURE_VOID : kit.slots().get("inventory.0").getType();
            int equipment = (int) kit.slots().keySet().stream().filter(key -> !key.startsWith("inventory.")).count();
            button(menu, target, thumbnail, friendly(kit.id()), List.of(
                kit.slots().size() + " item" + (kit.slots().size() == 1 ? "" : "s") + " (" + equipment + " equipment)",
                "Left Click: Edit", "Right Click: Give to yourself", "Shift + Right Click: Delete"), e -> {
                if (e.isShiftClick() && e.isRightClick()) openKitDeleteConfirmation(player, kit.id(), page);
                else if (e.isRightClick()) giveKit(player, kit);
                else openKit(player, kit.id(), false);
            });
        }
        button(menu, 45, Material.ARROW, "Back", List.of("Return to item templates"), e -> openMain(player, 0));
        button(menu, 49, Material.ANVIL, "Create Kit", List.of("Create a new empty kit"), e -> prompt(player, "Enter a kit ID ([a-z0-9_-]+)", value -> createKitFromBrowser(player, value)));
        if (page > 0) button(menu, 46, Material.ARROW, "Previous", List.of(), e -> openKits(player, page - 1));
        if (all.size() > start + 28) button(menu, 53, Material.ARROW, "Next", List.of(), e -> openKits(player, page + 1));
        player.openInventory(menu.inventory);
    }

    private void createKitFromBrowser(Player player, String rawId) {
        try {
            String id = rawId.toLowerCase(Locale.ROOT);
            if (kits.get(id) != null) throw new IllegalArgumentException("Kit already exists: " + id);
            openKitEditor(player, new KitDefinition(id));
        } catch (IllegalArgumentException ex) { error(player, ex.getMessage()); openKits(player, 0); }
    }

    private void giveKit(Player player, KitDefinition kit) {
        int dropped = kitGiver.give(player, kit);
        player.sendMessage(Component.text("Gave kit " + kit.id() + (dropped == 0 ? "" : " (dropped " + dropped + " overflow items)"), NamedTextColor.GREEN));
    }

    private void openKitDeleteConfirmation(Player player, String id, int returnPage) {
        Menu menu = new Menu("DI: Delete Kit?"); frame(menu.inventory);
        button(menu, 4, Material.RED_DYE, "Delete " + friendly(id) + "?", List.of("This permanently removes the kit."), e -> {});
        button(menu, 20, Material.LIME_DYE, "Confirm Delete", List.of("Permanently delete " + id), e -> {
            try { kits.delete(id); player.sendMessage(Component.text("Deleted kit " + id, NamedTextColor.GREEN)); openKits(player, returnPage); }
            catch (IOException ex) { error(player, ex.getMessage()); openKits(player, returnPage); }
        });
        button(menu, 24, Material.GRAY_DYE, "Cancel", List.of("Keep this kit"), e -> openKits(player, returnPage));
        player.openInventory(menu.inventory);
    }

    private void openKitEditor(Player player, KitDefinition kit) {
        KitMenu menu = new KitMenu(kit.copy());
        setKitSlot(menu, 0, "helmet", Material.DIAMOND_HELMET, "Helmet"); setKitSlot(menu, 1, "chestplate", Material.DIAMOND_CHESTPLATE, "Chestplate");
        setKitSlot(menu, 2, "leggings", Material.DIAMOND_LEGGINGS, "Leggings"); setKitSlot(menu, 3, "boots", Material.DIAMOND_BOOTS, "Boots"); setKitSlot(menu, 4, "offhand", Material.SHIELD, "Offhand");
        for (int i = 0; i < 27; i++) setKitSlot(menu, 18 + i, "inventory." + (9 + i), Material.GRAY_STAINED_GLASS_PANE, "Inventory " + (i + 1));
        for (int i = 0; i < 9; i++) setKitSlot(menu, 45 + i, "inventory." + i, Material.BLACK_STAINED_GLASS_PANE, "Hotbar " + (i + 1));
        menu.inventory.setItem(9, icon(Material.LIME_DYE, "Save Kit", List.of("Save current contents"))); menu.inventory.setItem(10, icon(Material.ARROW, "Back", List.of("Return to kits")));
        menu.inventory.setItem(11, icon(Material.NAME_TAG, "Rename Kit", List.of("Save current contents under a new ID")));
        menu.inventory.setItem(12, icon(Material.WRITABLE_BOOK, "Duplicate Kit", List.of("Create a copy of current contents")));
        menu.inventory.setItem(17, icon(Material.RED_DYE, "Delete Kit", List.of("Permanently delete this kit")));
        player.openInventory(menu.inventory);
    }

    private void setKitSlot(KitMenu menu, int slot, String key, Material placeholder, String name) {
        menu.keys.put(slot, key); ItemStack item = menu.kit.slots().get(key); menu.inventory.setItem(slot, item == null ? icon(placeholder, name, List.of("Place an item here")) : item.clone());
    }

    @EventHandler public void onClick(InventoryClickEvent event) {
        if (event.getInventory().getHolder() instanceof Menu menu) {
            event.setCancelled(true); if (event.getWhoClicked() instanceof Player && event.getRawSlot() >= 0 && event.getRawSlot() < event.getInventory().getSize()) {
                Consumer<InventoryClickEvent> action = menu.actions.get(event.getRawSlot()); if (action != null) action.accept(event);
            }
            return;
        }
        if (!(event.getInventory().getHolder() instanceof KitMenu menu) || !(event.getWhoClicked() instanceof Player player)) return;
        if (event.getRawSlot() < 0) return;
        if (event.getRawSlot() >= 54) { if (event.isShiftClick()) event.setCancelled(true); return; }
        event.setCancelled(true); int slot = event.getRawSlot();
        if (slot == 9) { try { kits.save(menu.kit); player.sendMessage(Component.text("Saved kit " + menu.kit.id(), NamedTextColor.GREEN)); openKitEditor(player, menu.kit); } catch (IOException ex) { error(player, ex.getMessage()); } return; }
        if (slot == 10) { openKits(player, 0); return; }
        if (slot == 11) { prompt(player, "Enter the new kit ID ([a-z0-9_-]+)", value -> renameKitFromEditor(player, menu.kit, value)); return; }
        if (slot == 12) { prompt(player, "Enter the duplicate kit ID ([a-z0-9_-]+)", value -> duplicateKitFromEditor(player, menu.kit, value)); return; }
        if (slot == 17) { openKitDeleteConfirmation(player, menu.kit.id(), 0); return; }
        String key = menu.keys.get(slot); if (key == null) return;
        ItemStack old = menu.kit.slots().remove(key); ItemStack cursor = event.getCursor();
        if (cursor != null && !cursor.getType().isAir()) { menu.kit.slots().put(key, cursor.clone()); event.setCursor(old); } else event.setCursor(old);
        openKitEditor(player, menu.kit);
    }

    private void renameKitFromEditor(Player player, KitDefinition current, String rawId) {
        try {
            KitDefinition renamed = current.copyAs(rawId.toLowerCase(Locale.ROOT));
            if (kits.get(current.id()) == null) kits.duplicate(renamed); else kits.rename(current.id(), renamed);
            player.sendMessage(Component.text("Renamed kit to " + renamed.id(), NamedTextColor.GREEN)); openKitEditor(player, renamed);
        } catch (IOException | IllegalArgumentException ex) { error(player, ex.getMessage()); openKitEditor(player, current); }
    }

    private void duplicateKitFromEditor(Player player, KitDefinition current, String rawId) {
        try {
            KitDefinition duplicate = current.copyAs(rawId.toLowerCase(Locale.ROOT)); kits.duplicate(duplicate);
            player.sendMessage(Component.text("Duplicated kit as " + duplicate.id(), NamedTextColor.GREEN)); openKitEditor(player, duplicate);
        } catch (IOException | IllegalArgumentException ex) { error(player, ex.getMessage()); openKitEditor(player, current); }
    }

    @EventHandler public void onChat(AsyncChatEvent event) {
        Consumer<String> action = prompts.remove(event.getPlayer().getUniqueId()); if (action == null) return; event.setCancelled(true);
        String value = PlainTextComponentSerializer.plainText().serialize(event.message()); Bukkit.getScheduler().runTask(plugin, () -> action.accept(value));
    }

    @EventHandler public void onQuit(PlayerQuitEvent event) { prompts.remove(event.getPlayer().getUniqueId()); itemDrafts.remove(event.getPlayer().getUniqueId()); }

    private void prompt(Player player, String message, Consumer<String> action) { prompts.put(player.getUniqueId(), action); player.closeInventory(); player.sendMessage(Component.text(message, NamedTextColor.YELLOW)); }
    private static Rarity next(Rarity value) { Rarity[] values = Rarity.values(); return values[(value.ordinal() + 1) % values.length]; }
    private static DungeonItemType next(DungeonItemType value) { DungeonItemType[] values = DungeonItemType.values(); return values[(value.ordinal() + 1) % values.length]; }
    private static Material statIcon(StatKey key) { return key.type() == DungeonItemType.ARMOR ? Material.IRON_CHESTPLATE : key == StatKey.ENTITY_REACH ? Material.TRIDENT : Material.IRON_SWORD; }
    private static Material triggerIcon(TriggerType trigger) { return trigger.name().contains("HIT") ? Material.IRON_SWORD : trigger.name().contains("BREAK") ? Material.IRON_PICKAXE : Material.BLAZE_POWDER; }
    private static String onOff(boolean value) { return value ? "Enabled" : "Disabled"; }
    private static String shown(boolean value) { return value ? "Shown" : "Hidden"; }
    private static String format(double value) { return java.math.BigDecimal.valueOf(value).setScale(2, java.math.RoundingMode.HALF_UP).stripTrailingZeros().toPlainString(); }
    private static String friendly(String raw) { StringBuilder result = new StringBuilder(); for (String word : raw.toLowerCase(Locale.ROOT).split("_")) { if (!result.isEmpty()) result.append(' '); result.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1)); } return result.toString(); }
    private static void error(Player player, String message) { player.sendMessage(Component.text(message == null ? "Unknown error" : message, NamedTextColor.RED)); }

    private static ItemStack icon(Material material, String name, List<String> lore) {
        ItemStack item = new ItemStack(material); ItemMeta meta = item.getItemMeta(); meta.displayName(Component.text(name).decoration(TextDecoration.ITALIC, false));
        meta.lore(lore.stream().map(line -> Component.text(line, NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false)).toList()); meta.addItemFlags(ItemFlag.values()); item.setItemMeta(meta); return item;
    }
    private static void button(Menu menu, int slot, Material material, String name, List<String> lore, Consumer<InventoryClickEvent> action) { menu.inventory.setItem(slot, icon(material, name, lore)); menu.actions.put(slot, action); }
    private static void frame(Inventory inventory) { ItemStack frame = icon(Material.GRAY_STAINED_GLASS_PANE, " ", List.of()); for (int i = 0; i < inventory.getSize(); i++) if (i < 9 || i >= 45 || i % 9 == 0 || i % 9 == 8) inventory.setItem(i, frame); }

    private final class Menu implements InventoryHolder {
        private final Inventory inventory; private final Map<Integer, Consumer<InventoryClickEvent>> actions = new HashMap<>();
        private Menu(String title) { inventory = Bukkit.createInventory(this, 54, title); }
        @Override public Inventory getInventory() { return inventory; }
    }
    private final class KitMenu implements InventoryHolder {
        private final Inventory inventory; private final KitDefinition kit; private final Map<Integer, String> keys = new HashMap<>();
        private KitMenu(KitDefinition kit) { this.kit = kit; inventory = Bukkit.createInventory(this, 54, "DI Kit: " + kit.id()); }
        @Override public Inventory getInventory() { return inventory; }
    }
    private static final class ItemDraft {
        private final DungeonItemTemplate template; private CustomItemDefinition definition; private boolean dirty;
        private ItemDraft(DungeonItemTemplate template, CustomItemDefinition definition, boolean isNew) { this.template = template; this.definition = definition; this.dirty = isNew; }
    }
}
