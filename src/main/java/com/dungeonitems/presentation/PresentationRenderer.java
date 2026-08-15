package com.dungeonitems.presentation;

import com.bountysmp.configurableitems.model.CustomItemDefinition;
import com.dungeonitems.model.Ability;
import com.dungeonitems.model.DungeonItemTemplate;
import com.dungeonitems.model.DungeonItemType;
import com.dungeonitems.model.StatKey;
import io.papermc.paper.datacomponent.DataComponentTypes;
import io.papermc.paper.datacomponent.item.AttackRange;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.EntityType;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.EquipmentSlotGroup;

public final class PresentationRenderer {
    private final int wrapWidth;
    public PresentationRenderer(int wrapWidth) { this.wrapWidth = Math.max(10, wrapWidth); }

    public CustomItemDefinition apply(DungeonItemTemplate template, CustomItemDefinition source) {
        CustomItemDefinition definition = source.copy();
        applyStatOverrides(template, definition);
        definition.customName(template.rarity().decorateName(template.name()));
        definition.lore().clear(); definition.lore().addAll(lore(template, definition)); definition.showAttributes(false);
        definition.triggerSettings().clear();
        definition.triggers().clear();
        template.abilities().forEach((trigger, ability) -> {
            CustomItemDefinition.TriggerSettings settings = definition.settings(trigger);
            settings.cooldownTicks(ability.cooldownTicks()); settings.cooldownMessage(ability.cooldownMessage());
            definition.commands(trigger).addAll(ability.actions().stream().map(CustomItemDefinition.TriggerCommandDef::copy).toList());
        });
        return definition;
    }

    public List<String> lore(DungeonItemTemplate template, CustomItemDefinition definition) {
        List<String> lines = new ArrayList<>();
        List<String> description = wrap(template.description());
        description.forEach(line -> lines.add("&7" + line));
        List<String> stats = statLines(template.type(), definition);
        if (!description.isEmpty() && (!stats.isEmpty() || !template.abilities().isEmpty())) lines.add("");
        lines.addAll(stats);
        if (!template.abilities().isEmpty()) {
            if (!lines.isEmpty() && !lines.getLast().isEmpty()) lines.add("");
            template.abilities().values().stream().sorted(Comparator.comparingInt(a -> a.trigger().ordinal())).forEach(ability -> appendAbility(lines, ability));
        }
        if (!lines.isEmpty() && !lines.getLast().isEmpty()) lines.add("");
        lines.add(template.rarity().legacyColor() + template.rarity().name() + " " + template.type().name());
        return lines;
    }

    private List<String> statLines(DungeonItemType type, CustomItemDefinition definition) {
        EquipmentSlot slot = intendedSlot(type, definition);
        Set<Attribute> attributes = new LinkedHashSet<>();
        if (type == DungeonItemType.WEAPON) { attributes.add(Attribute.ATTACK_DAMAGE); attributes.add(Attribute.ATTACK_SPEED); }
        if (type == DungeonItemType.ARMOR) { attributes.add(Attribute.ARMOR); attributes.add(Attribute.ARMOR_TOUGHNESS); }
        try {
            var defaults = definition.material().asItemType().getDefaultAttributeModifiers();
            for (Attribute attribute : defaults.keySet()) if (defaults.get(attribute).stream().anyMatch(modifier -> modifier.getSlotGroup().test(slot))) attributes.add(attribute);
        }
        catch (RuntimeException ignored) { /* Lightweight server mocks may not provide vanilla item attributes. */ }
        for (CustomItemDefinition.AttributeDef value : definition.attributes()) {
            Attribute attribute = resolve(value.key());
            if (attribute != null && value.slot().test(slot)) attributes.add(attribute);
        }
        List<String> lines = new ArrayList<>();
        for (Attribute attribute : attributes) {
            double vanilla = effective(attribute, definition, slot, false);
            double configured = effective(attribute, definition, slot, true);
            boolean mandatory = isMandatory(type, attribute);
            double playerBase = playerBase(attribute);
            if (!mandatory && nearlyEqual(vanilla, configured) && nearlyEqual(configured, playerBase)) continue;
            boolean beneficial = mandatory && nearlyEqual(vanilla, configured) || beneficial(attribute, configured - vanilla);
            lines.add((beneficial ? "&9" : "&c") + format(configured) + " " + label(attribute));
        }
        AttackRange attackRange = defaultAttackRange(definition);
        if (attackRange != null) {
            double configured = definition.extras().attackRangeMax == null ? attackRange.maxReach() : definition.extras().attackRangeMax;
            lines.add((configured >= attackRange.maxReach() ? "&9" : "&c") + format(configured) + " Entity Reach");
        }
        return lines;
    }

    private double effective(Attribute attribute, CustomItemDefinition definition, EquipmentSlot slot, boolean includeConfigured) {
        double base = playerBase(attribute);
        List<AttributeModifier> modifiers;
        try { modifiers = definition.material().asItemType().getDefaultAttributeModifiers().get(attribute).stream().filter(modifier -> modifier.getSlotGroup().test(slot)).collect(java.util.stream.Collectors.toCollection(ArrayList::new)); }
        catch (RuntimeException ignored) { modifiers = new ArrayList<>(); }
        if (includeConfigured) for (CustomItemDefinition.AttributeDef value : definition.attributes()) {
            if (value.slot().test(slot) && attribute.equals(resolve(value.key()))) {
                modifiers.add(new AttributeModifier(new NamespacedKey("dungeonitems", "preview_" + modifiers.size()), value.value(), value.operation(), value.slot()));
            }
        }
        double result = base;
        for (AttributeModifier modifier : modifiers) if (modifier.getOperation() == AttributeModifier.Operation.ADD_NUMBER) result += modifier.getAmount();
        double afterAddition = result;
        for (AttributeModifier modifier : modifiers) if (modifier.getOperation() == AttributeModifier.Operation.ADD_SCALAR) result += afterAddition * modifier.getAmount();
        for (AttributeModifier modifier : modifiers) if (modifier.getOperation() == AttributeModifier.Operation.MULTIPLY_SCALAR_1) result *= 1.0d + modifier.getAmount();
        return result;
    }

    private void applyStatOverrides(DungeonItemTemplate template, CustomItemDefinition definition) {
        Set<Attribute> managed = new LinkedHashSet<>();
        for (StatKey key : StatKey.values()) managed.add(key.attribute());
        definition.attributes().removeIf(value -> managed.contains(resolve(value.key())));
        definition.extras().attackRangeMax = null;
        EquipmentSlot slot = intendedSlot(template.type(), definition);
        EquipmentSlotGroup group = slotGroup(slot);
        for (var entry : template.statOverrides().entrySet()) {
            if (entry.getKey().type() != template.type() || !Double.isFinite(entry.getValue())) continue;
            Attribute attribute = entry.getKey().attribute();
            if (entry.getKey() == StatKey.ENTITY_REACH && defaultAttackRange(definition) != null) {
                definition.extras().attackRangeMax = (float) Math.max(0, Math.min(64, entry.getValue()));
                continue;
            }
            double vanilla = effective(attribute, definition, slot, false);
            double unit = effectiveWithExtra(attribute, definition, slot, 1.0d) - vanilla;
            double amount = nearlyEqual(unit, 0) ? entry.getValue() - vanilla : (entry.getValue() - vanilla) / unit;
            definition.attributes().add(new CustomItemDefinition.AttributeDef(attribute.getKey().toString(), AttributeModifier.Operation.ADD_NUMBER, amount, group));
        }
    }

    private double effectiveWithExtra(Attribute attribute, CustomItemDefinition definition, EquipmentSlot slot, double amount) {
        definition.attributes().add(new CustomItemDefinition.AttributeDef(attribute.getKey().toString(), AttributeModifier.Operation.ADD_NUMBER, amount, slotGroup(slot)));
        double result = effective(attribute, definition, slot, true);
        definition.attributes().removeLast();
        return result;
    }

    public double vanillaValue(DungeonItemTemplate template, CustomItemDefinition definition, StatKey key) {
        AttackRange attackRange = defaultAttackRange(definition);
        if (key == StatKey.ENTITY_REACH && attackRange != null) return attackRange.maxReach();
        CustomItemDefinition copy = definition.copy();
        copy.attributes().removeIf(value -> java.util.Arrays.stream(StatKey.values()).anyMatch(stat -> stat.attribute().equals(resolve(value.key()))));
        return effective(key.attribute(), copy, intendedSlot(template.type(), copy), false);
    }

    private static AttackRange defaultAttackRange(CustomItemDefinition definition) {
        try { return definition.material().asItemType().getDefaultData(DataComponentTypes.ATTACK_RANGE); }
        catch (RuntimeException ignored) { return null; }
    }

    private static double playerBase(Attribute attribute) {
        AttributeInstance playerAttribute = EntityType.PLAYER.getDefaultAttributes().getAttribute(attribute);
        return playerAttribute == null ? 0.0d : playerAttribute.getBaseValue();
    }

    private static EquipmentSlotGroup slotGroup(EquipmentSlot slot) {
        return switch (slot) {
            case HAND -> EquipmentSlotGroup.HAND;
            case OFF_HAND -> EquipmentSlotGroup.OFFHAND;
            case FEET -> EquipmentSlotGroup.FEET;
            case LEGS -> EquipmentSlotGroup.LEGS;
            case CHEST -> EquipmentSlotGroup.CHEST;
            case HEAD -> EquipmentSlotGroup.HEAD;
            case BODY -> EquipmentSlotGroup.BODY;
            case SADDLE -> EquipmentSlotGroup.SADDLE;
        };
    }

    private void appendAbility(List<String> lines, Ability ability) {
        String cooldown = ability.cooldownTicks() <= 0 ? "" : " (" + format(ability.cooldownTicks() / 20.0d) + "s)";
        lines.add("&f" + friendly(ability.trigger().name()) + ": " + ability.name() + "&7" + cooldown);
        wrap(ability.description()).forEach(line -> lines.add("&7" + line));
    }

    private List<String> wrap(String value) {
        List<String> result = new ArrayList<>();
        if (value == null || value.isBlank()) return result;
        for (String paragraph : value.split("\\R", -1)) {
            if (paragraph.isBlank()) { result.add(""); continue; }
            StringBuilder line = new StringBuilder();
            for (String word : paragraph.trim().split("\\s+")) {
                if (!line.isEmpty() && line.length() + 1 + word.length() > wrapWidth) { result.add(line.toString()); line.setLength(0); }
                if (!line.isEmpty()) line.append(' '); line.append(word);
            }
            if (!line.isEmpty()) result.add(line.toString());
        }
        return result;
    }

    private static EquipmentSlot intendedSlot(DungeonItemType type, CustomItemDefinition definition) {
        if (definition.equip().enabled) return definition.equip().slot;
        if (type == DungeonItemType.ARMOR) {
            String material = definition.material().name();
            if (material.endsWith("_BOOTS")) return EquipmentSlot.FEET;
            if (material.endsWith("_LEGGINGS")) return EquipmentSlot.LEGS;
            if (material.endsWith("_CHESTPLATE") || material.equals("ELYTRA")) return EquipmentSlot.CHEST;
            return EquipmentSlot.HEAD;
        }
        return EquipmentSlot.HAND;
    }

    private static Attribute resolve(String key) {
        NamespacedKey namespacedKey = NamespacedKey.fromString(key.contains(":") ? key : "minecraft:" + key.toLowerCase(Locale.ROOT));
        return namespacedKey == null ? null : Registry.ATTRIBUTE.get(namespacedKey);
    }
    private static boolean isMandatory(DungeonItemType type, Attribute attribute) {
        return type == DungeonItemType.WEAPON && (attribute == Attribute.ATTACK_DAMAGE || attribute == Attribute.ATTACK_SPEED)
            || type == DungeonItemType.ARMOR && (attribute == Attribute.ARMOR || attribute == Attribute.ARMOR_TOUGHNESS);
    }
    private static boolean beneficial(Attribute attribute, double delta) {
        if (nearlyEqual(delta, 0)) return true;
        return switch (attribute.getSentiment()) {
            case POSITIVE -> delta > 0; case NEGATIVE -> delta < 0; case NEUTRAL -> delta > 0;
        };
    }
    private static boolean nearlyEqual(double left, double right) { return Math.abs(left - right) < 0.000001d; }
    private static String format(double value) {
        if (nearlyEqual(value, 0)) value = 0;
        return BigDecimal.valueOf(value).setScale(2, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString();
    }
    private static String label(Attribute attribute) {
        if (attribute == Attribute.ATTACK_DAMAGE) return "Damage";
        if (attribute == Attribute.ATTACK_SPEED) return "Attack Speed";
        if (attribute == Attribute.ARMOR) return "Armor";
        if (attribute == Attribute.ARMOR_TOUGHNESS) return "Armor Toughness";
        return friendly(attribute.getKey().getKey());
    }
    private static String friendly(String raw) {
        StringBuilder result = new StringBuilder();
        for (String word : raw.toLowerCase(Locale.ROOT).split("_")) {
            if (!result.isEmpty()) result.append(' ');
            result.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
        }
        return result.toString();
    }
}
