package com.dungeonitems.command;

import com.dungeonitems.gui.EditorManager;
import com.dungeonitems.model.DungeonItemTemplate;
import com.dungeonitems.model.KitDefinition;
import com.dungeonitems.service.DungeonItemService;
import com.dungeonitems.service.KitGiver;
import com.dungeonitems.storage.KitRepository;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

public final class DungeonItemsCommand implements CommandExecutor, TabCompleter {
    public static final String PERMISSION = "dungeonitems.admin";
    private final DungeonItemService items; private final KitRepository kits; private final KitGiver kitGiver; private final EditorManager editor;
    public DungeonItemsCommand(DungeonItemService items, KitRepository kits, KitGiver kitGiver, EditorManager editor) { this.items = items; this.kits = kits; this.kitGiver = kitGiver; this.editor = editor; }

    @Override public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission(PERMISSION)) { sender.sendMessage(Component.text("You do not have permission to use DungeonItems", NamedTextColor.RED)); return true; }
        if (args.length == 0) { Player player = player(sender); if (player != null) editor.openMain(player, 0); return true; }
        try {
            return switch (args[0].toLowerCase(Locale.ROOT)) {
                case "create" -> { if (args.length < 2) usage(sender, label); else { Player player = player(sender); if (player != null) editor.createItem(player, args[1].toLowerCase(Locale.ROOT)); } yield true; }
                case "edit" -> { if (args.length < 2) usage(sender, label); else { Player player = player(sender); if (player != null) editor.openItem(player, args[1]); } yield true; }
                case "give" -> { give(sender, args); yield true; }
                case "delete" -> { if (args.length < 2) usage(sender, label); else { items.delete(args[1]); sender.sendMessage(Component.text("Deleted " + args[1], NamedTextColor.GREEN)); } yield true; }
                case "list" -> { sender.sendMessage(Component.text("Items: " + String.join(", ", items.templates().stream().map(DungeonItemTemplate::id).toList()), NamedTextColor.AQUA)); yield true; }
                case "reload" -> { items.reload(); kits.reload(); sender.sendMessage(Component.text("Reloaded DungeonItems", NamedTextColor.GREEN)); yield true; }
                case "kit" -> { kit(sender, label, args); yield true; }
                case "kits" -> { Player player = player(sender); if (player != null) editor.openKits(player, 0); yield true; }
                default -> { usage(sender, label); yield true; }
            };
        } catch (IOException | IllegalArgumentException ex) { sender.sendMessage(Component.text(ex.getMessage(), NamedTextColor.RED)); return true; }
    }

    private void give(CommandSender sender, String[] args) {
        if (args.length < 2) { sender.sendMessage(Component.text("Usage: /di give <id> [player] [amount]", NamedTextColor.GRAY)); return; }
        Player target = args.length >= 3 ? Bukkit.getPlayerExact(args[2]) : sender instanceof Player player ? player : null;
        if (target == null) { sender.sendMessage(Component.text("Player not found or console omitted player", NamedTextColor.RED)); return; }
        int amount = args.length >= 4 ? amount(args[3]) : 1;
        ItemStack item = items.createItem(args[1], amount).orElse(null); if (item == null) { sender.sendMessage(Component.text("Unknown item: " + args[1], NamedTextColor.RED)); return; }
        target.getInventory().addItem(item).values().forEach(overflow -> target.getWorld().dropItemNaturally(target.getLocation(), overflow));
        sender.sendMessage(Component.text("Gave " + amount + "x " + args[1] + " to " + target.getName(), NamedTextColor.GREEN));
    }

    private void kit(CommandSender sender, String label, String[] args) throws IOException {
        if (args.length < 2) { Player player = player(sender); if (player != null) editor.openKits(player, 0); return; }
        String action = args[1].toLowerCase(Locale.ROOT);
        if (action.equals("list")) { sender.sendMessage(Component.text("Kits: " + String.join(", ", kits.all().stream().map(KitDefinition::id).toList()), NamedTextColor.AQUA)); return; }
        if (args.length < 3) { kitUsage(sender, label); return; }
        String id = args[2].toLowerCase(Locale.ROOT);
        switch (action) {
            case "create", "edit" -> { Player player = player(sender); if (player != null) editor.openKit(player, id, action.equals("create")); }
            case "delete" -> { kits.delete(id); sender.sendMessage(Component.text("Deleted kit " + id, NamedTextColor.GREEN)); }
            case "rename" -> {
                if (args.length < 4) { kitUsage(sender, label); return; }
                KitDefinition source = kits.get(id); if (source == null) throw new IllegalArgumentException("Unknown kit: " + id);
                String target = args[3].toLowerCase(Locale.ROOT); kits.rename(id, source.copyAs(target)); sender.sendMessage(Component.text("Renamed kit " + id + " to " + target, NamedTextColor.GREEN));
            }
            case "duplicate" -> {
                if (args.length < 4) { kitUsage(sender, label); return; }
                KitDefinition source = kits.get(id); if (source == null) throw new IllegalArgumentException("Unknown kit: " + id);
                String target = args[3].toLowerCase(Locale.ROOT); kits.duplicate(source.copyAs(target)); sender.sendMessage(Component.text("Duplicated kit " + id + " as " + target, NamedTextColor.GREEN));
            }
            case "give" -> {
                KitDefinition kit = kits.get(id); if (kit == null) throw new IllegalArgumentException("Unknown kit: " + id);
                Player target = args.length >= 4 ? Bukkit.getPlayerExact(args[3]) : sender instanceof Player player ? player : null;
                if (target == null) throw new IllegalArgumentException("Player not found or console omitted player");
                int dropped = kitGiver.give(target, kit); sender.sendMessage(Component.text("Gave kit " + id + " to " + target.getName() + (dropped == 0 ? "" : " (dropped " + dropped + " overflow items)"), NamedTextColor.GREEN));
            }
            default -> kitUsage(sender, label);
        }
    }

    private static Player player(CommandSender sender) { if (sender instanceof Player player) return player; sender.sendMessage(Component.text("Only players can open editors", NamedTextColor.RED)); return null; }
    private static int amount(String raw) { try { return Math.max(1, Math.min(99, Integer.parseInt(raw))); } catch (NumberFormatException ex) { return 1; } }
    private static void usage(CommandSender sender, String label) { sender.sendMessage(Component.text("Usage: /" + label + " [create|edit|give|delete|list|reload|kit|kits]", NamedTextColor.GRAY)); }
    private static void kitUsage(CommandSender sender, String label) { sender.sendMessage(Component.text("Usage: /" + label + " kit <create|edit|give|delete|rename|duplicate|list> [id] [player/new-id]", NamedTextColor.GRAY)); }

    @Override public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!sender.hasPermission(PERMISSION)) return List.of();
        if (args.length == 1) return prefix(args[0], List.of("create", "edit", "give", "delete", "list", "reload", "kit", "kits"));
        if (args[0].equalsIgnoreCase("kit")) {
            if (args.length == 2) return prefix(args[1], List.of("create", "edit", "give", "delete", "rename", "duplicate", "list"));
            if (args.length == 3 && !args[1].equalsIgnoreCase("create") && !args[1].equalsIgnoreCase("list")) return prefix(args[2], kits.all().stream().map(KitDefinition::id).toList());
            if (args.length == 4 && args[1].equalsIgnoreCase("give")) return prefix(args[3], Bukkit.getOnlinePlayers().stream().map(Player::getName).toList());
        }
        if (args.length == 2 && List.of("edit", "give", "delete").contains(args[0].toLowerCase(Locale.ROOT))) return prefix(args[1], items.templates().stream().map(DungeonItemTemplate::id).toList());
        if (args.length == 3 && args[0].equalsIgnoreCase("give")) return prefix(args[2], Bukkit.getOnlinePlayers().stream().map(Player::getName).toList());
        return List.of();
    }
    private static List<String> prefix(String raw, List<String> values) { String prefix = raw.toLowerCase(Locale.ROOT); List<String> result = new ArrayList<>(); for (String value : values) if (value.toLowerCase(Locale.ROOT).startsWith(prefix)) result.add(value); return result; }
}
