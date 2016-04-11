package com.harbo.kitnotifications;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;

import net.ess3.api.IEssentials;
import com.earth2me.essentials.Essentials;
import com.earth2me.essentials.Kit;
import com.earth2me.essentials.User;

import com.google.common.base.Joiner;
import com.google.common.collect.Lists;
import org.bukkit.scheduler.BukkitScheduler;

public class Main extends JavaPlugin implements Listener {

    private IEssentials essentials;

    private Kit k;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        getServer().getPluginManager().registerEvents(this, this);
        essentials = JavaPlugin.getPlugin(Essentials.class);
        BukkitScheduler scheduler = Bukkit.getServer().getScheduler();
        scheduler.scheduleSyncRepeatingTask(this, () -> {
            ConfigurationSection kitSection = essentials.getSettings().getKits();
            Map<String, Map<String, Object>> kits = new HashMap<>();
            kitSection.getKeys(false).stream().filter(kitSection::isConfigurationSection).forEach(key -> kits.put(key, kitSection.getConfigurationSection(key).getValues(true)));
            for (Player player : Bukkit.getOnlinePlayers()) {
                User user = essentials.getUser(player);
                StringBuilder sb = new StringBuilder();
                kits.entrySet().stream().filter(entry -> player.hasPermission("essentials.kits." + entry.getKey())).forEach(entry -> {
                    try {
                        k = new Kit(entry.getKey(), essentials);
                    } catch (Exception ex) {
                        ex.printStackTrace();
                    }
                    try {
                        long nextUse = k.getNextUse(user);
                        if (nextUse == 0L) {
                            sb.append(", ").append(entry.getKey());
                        }
                    } catch (Exception ex) {
                        ex.printStackTrace();
                    }
                });
                if (sb.length() > 0) {
                    player.sendMessage(ChatColor.GOLD + "You can perform kits: " + sb.substring(2) + "!");
                    player.sendMessage(ChatColor.RED + "Customize the notifications using /kitsnotify");
                }
            }
        }, 0, 10 * 60 * 20);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (sender instanceof Player)
        {
            Player player = (Player) sender;
            openInventory(player);
        }
        else sender.sendMessage(ChatColor.RED + "You must be a player for that!");
        return true;
    }

    private ItemStack createItem(Material type, String name, String... lore)
    {
        ItemStack item = new ItemStack(type);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(name);
        meta.setLore(Arrays.asList(lore));
        item.setItemMeta(meta);
        return item;
    }

    private void openInventory(HumanEntity he)
    {
        List<String> kits = new ArrayList<>();
        ConfigurationSection kitSection = essentials.getSettings().getKits();
        kits.addAll(kitSection.getKeys(false).stream().filter(kitSection::isConfigurationSection).collect(Collectors.toList()));
        List<ItemStack> items = new ArrayList<>();
        String disabled = getConfig().getString("Disabled." + he.getUniqueId());
        List<String> list = disabled == null ? new ArrayList<>() : Lists.newArrayList(disabled.split(";;"));
        kits.stream().filter(kit -> he.hasPermission("essentials.kits." + kit)).forEach(kit -> {
            if (list.contains(kit))
                items.add(createItem(Material.REDSTONE_BLOCK, ChatColor.RED + kit, ChatColor.GRAY + "Click to enable"));
            else
                items.add(createItem(Material.EMERALD_BLOCK, ChatColor.GREEN + kit, ChatColor.GRAY + "Click to disable"));
        });
        int size = items.size() + 2;
        Inventory inv = getServer().createInventory(null, size - (size % 9) + 9, ChatColor.GOLD + "Kit Notifications");
        items.forEach(inv::addItem);
        inv.setItem(inv.getSize() - 1, createItem(Material.TNT, ChatColor.RED + "Disable all"));
        inv.setItem(inv.getSize() - 2, createItem(Material.DIAMOND_BLOCK, ChatColor.GREEN + "Enable all"));
        he.openInventory(inv);
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent e)
    {
        if (e.getCurrentItem() != null && (ChatColor.GOLD + "Kit Notifications").equals(e.getInventory().getTitle()))
        {
            e.setCancelled(true);
            InventoryView view = e.getView();
            int rawSlot = e.getRawSlot();
            boolean top = view.getTopInventory() != null && rawSlot >= 0 && rawSlot < view.getTopInventory().getSize();
            if (top)
            {
                Material type = e.getCurrentItem().getType();
                switch (type)
                {
                    case REDSTONE_BLOCK:
                        String kitName = e.getCurrentItem().getItemMeta().getDisplayName().substring(2);
                        String disabled = getConfig().getString("Disabled." + e.getWhoClicked().getUniqueId());
                        List<String> list = disabled == null ? new ArrayList<>() : Lists.newArrayList(disabled.split(";;"));
                        list.remove(kitName);
                        getConfig().set("Disabled." + e.getWhoClicked().getUniqueId(), Joiner.on(";;").join(list));
                        break;
                    case EMERALD_BLOCK:
                        kitName = e.getCurrentItem().getItemMeta().getDisplayName().substring(2);
                        disabled = getConfig().getString("Disabled." + e.getWhoClicked().getUniqueId());
                        list = disabled == null ? new ArrayList<>() : Lists.newArrayList(disabled.split(";;"));
                        list.add(kitName);
                        getConfig().set("Disabled." + e.getWhoClicked().getUniqueId(), Joiner.on(";;").join(list));
                        break;
                    case TNT:
                        List<String> kits = new ArrayList<>();
                        ConfigurationSection kitSection = essentials.getSettings().getKits();
                        kits.addAll(kitSection.getKeys(false).stream().filter(kitSection::isConfigurationSection).collect(Collectors.toList()));
                        getConfig().set("Disabled." + e.getWhoClicked().getUniqueId(), Joiner.on(";;").join(kits));
                        break;
                    case DIAMOND_BLOCK:
                        getConfig().set("Disabled." + e.getWhoClicked().getUniqueId(), null);
                        break;
                    default:
                        break;
                }
                saveConfig();
                openInventory(e.getWhoClicked());
            }
        }
    }
}
