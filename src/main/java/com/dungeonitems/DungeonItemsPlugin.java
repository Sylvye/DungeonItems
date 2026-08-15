package com.dungeonitems;

import com.bountysmp.configurableitems.api.ConfigurableItemsAPI;
import com.dungeonitems.api.DungeonItemsAPI;
import com.dungeonitems.command.DungeonItemsCommand;
import com.dungeonitems.gui.EditorManager;
import com.dungeonitems.presentation.PresentationRenderer;
import com.dungeonitems.service.DungeonItemService;
import com.dungeonitems.service.KitGiver;
import com.dungeonitems.storage.KitRepository;
import com.dungeonitems.storage.TemplateRepository;
import java.nio.file.Path;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;

public class DungeonItemsPlugin extends JavaPlugin {
    private DungeonItemService service;

    @Override public void onEnable() {
        saveDefaultConfig();
        ConfigurableItemsAPI ci = getServer().getServicesManager().load(ConfigurableItemsAPI.class);
        if (ci == null) throw new IllegalStateException("ConfigurableItems API is unavailable");
        Path data = getDataFolder().toPath(); TemplateRepository templates = new TemplateRepository(data.resolve("items")); KitRepository kits = new KitRepository(data.resolve("kits"));
        service = new DungeonItemService(ci, templates, new PresentationRenderer(getConfig().getInt("lore-wrap-width", 40)), getLogger());
        service.reload(); kits.reload();
        EditorManager editor = new EditorManager(this, service, ci, kits); getServer().getPluginManager().registerEvents(editor, this);
        DungeonItemsCommand command = new DungeonItemsCommand(service, kits, new KitGiver(), editor); PluginCommand di = getCommand("dungeonitems");
        if (di == null) throw new IllegalStateException("Missing /dungeonitems registration"); di.setExecutor(command); di.setTabCompleter(command);
        getServer().getServicesManager().register(DungeonItemsAPI.class, service, this, ServicePriority.Normal);
        getLogger().info("Loaded " + service.templates().size() + " DungeonItems templates.");
    }

    @Override public void onDisable() { getServer().getServicesManager().unregisterAll(this); }
    public DungeonItemsAPI api() { return service; }
}
