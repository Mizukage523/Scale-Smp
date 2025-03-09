package me.mizu.scalesmp;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@SuppressWarnings("all")
public class Scalesmp extends JavaPlugin implements Listener, CommandExecutor {
    private Map<Player, Double> playerScales = new HashMap<>();
    private final String SCALE_ATTRIBUTE_NAME = "scale";
    private Method setScaleMethod = null;

    @Override
    public void onEnable() {
        getServer().getPluginManager().registerEvents(this, this);
        getCommand("setscale").setExecutor(this);
        saveDefaultConfig();
        loadPlayerScales();

        // Check if Player#setScale method is available
        try {
            setScaleMethod = Player.class.getMethod("setScale", double.class);
        } catch (NoSuchMethodException | SecurityException e) {
            getLogger().warning("Player#setScale method is not available. Falling back to /attribute command.");
        }

        new BukkitRunnable() {
            @Override
            public void run() {
                for (Player player : Bukkit.getOnlinePlayers()) {
                    double scale = playerScales.getOrDefault(player, 1.0);
                    sendScaleActionBar(player, scale);
                }
            }
        }.runTaskTimer(this, 0, 20); // 20 ticks = 1 second

        // Remove PlayerJoinListener for action bar (task handles it now)
    }

    @Override
    public void onDisable() {
        savePlayerScales();
    }

    private void loadPlayerScales() {
        if (!getConfig().contains("playerScales")) {
            getConfig().createSection("playerScales");
            saveConfig();
        }
        for (String uuid : getConfig().getConfigurationSection("playerScales").getKeys(false)) {
            double scale = getConfig().getDouble("playerScales." + uuid);
            Player player = Bukkit.getPlayer(UUID.fromString(uuid));
            if (player != null) {
                playerScales.put(player, scale);
                applyScale(player, scale);
                sendScaleActionBar(player, scale);
            }
        }
    }

    private void savePlayerScales() {
        getConfig().set("playerScales", null);
        for (Map.Entry<Player, Double> entry : playerScales.entrySet()) {
            getConfig().set("playerScales." + entry.getKey().getUniqueId().toString(), entry.getValue());
        }
        saveConfig();
    }

    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player player = event.getEntity();
        double currentScale = playerScales.getOrDefault(player, 1.0);
        double newScale = Math.min(currentScale + 0.1, 2.5);
        playerScales.put(player, newScale);
        applyScale(player, newScale);
        sendMessage(player, newScale);
        sendScaleActionBar(player, newScale);
    }

    @EventHandler
    public void onPlayerKill(EntityDamageByEntityEvent event) {
        if (event.getEntity() instanceof Player && event.getDamager() instanceof Player) {
            Player victim = (Player) event.getEntity();
            Player killer = (Player) event.getDamager();
            if (victim.getHealth() - event.getFinalDamage() <= 0) {
                double newKillerScale = Math.max(playerScales.getOrDefault(killer, 1.0) - 0.1, 0.5);
                playerScales.put(killer, newKillerScale);
                applyScale(killer, newKillerScale);
                sendMessage(killer, newKillerScale);
                sendScaleActionBar(killer, newKillerScale);
                killer.playSound(killer.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_BREAK, 1.0f, 1.0f);
            }
        }
    }

    @EventHandler
    public void onPlayerRightClick(PlayerInteractEvent event) {
        if (event.getItem() != null && event.getItem().getType() == Material.DRAGON_EGG) {
            Player player = event.getPlayer();
            openScaleGUI(player);
        }
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (event.getView().getTitle().equals(ChatColor.GRAY + "Dragon Egg Scale")) { // ✅ Fixed title color
            event.setCancelled(true); // Cancel all clicks
            if (event.getCurrentItem() == null || event.getCurrentItem().getType() == Material.AIR) return;
            Player player = (Player) event.getWhoClicked();
            String displayName = event.getCurrentItem().getItemMeta().getDisplayName();
            if (displayName == null || !displayName.startsWith("Scale: ")) {
                player.sendMessage(ChatColor.RED + "Invalid item in the GUI.");
                return;
            }

            String[] parts = displayName.split(": ");
            if (parts.length != 2) {
                player.sendMessage(ChatColor.RED + "Invalid item in the GUI.");
                return;
            }

            try {
                double scale = Double.parseDouble(parts[1].trim());
                if (scale < 0.5 || scale > 2.5) {
                    player.sendMessage(ChatColor.RED + "Scale must be between 0.5 and 2.5.");
                    return;
                }
                setPlayerScale(player, scale);
                player.playSound(player.getLocation(), Sound.BLOCK_ENCHANTMENT_TABLE_USE, 1.0f, 1.0f);
            } catch (NumberFormatException e) {
                player.sendMessage(ChatColor.RED + "Invalid scale value.");
            }
            player.closeInventory();
        }
    }

    @EventHandler
    public void onBlockPlace(BlockPlaceEvent event) {
        if (event.getItemInHand().getType() == Material.DRAGON_EGG) {
            event.setCancelled(true);
            event.getPlayer().sendMessage(ChatColor.RED + "You cannot place the Dragon Egg.");
        }
    }

    private void openScaleGUI(Player player) {
        Inventory inv = Bukkit.createInventory(null, 27, ChatColor.GRAY + "Dragon Egg Scale");
        for (int i = 5; i <= 25; i++) { // Start from 0.5 to 2.5
            ItemStack item = new ItemStack(Material.PLAYER_HEAD);
            ItemMeta meta = item.getItemMeta();
            double scale = i * 0.1;
            String formattedScale = String.format("%.1f", scale); // Format to 1 decimal
            meta.setDisplayName(ChatColor.RESET + "Scale: " + formattedScale);
            meta.setUnbreakable(true); // ✅ Removed spigot()
            item.setItemMeta(meta);
            item.setAmount(1); // Prevent stacking
            inv.setItem(i - 5, item);
        }
        player.openInventory(inv);
    }

    private void setPlayerScale(Player player, double scale) {
        playerScales.put(player, scale);
        applyScale(player, scale);
        sendMessage(player, scale);
        sendScaleActionBar(player, scale);
    }

    private void applyScale(Player player, double scale) {
        if (setScaleMethod != null) {
            try {
                setScaleMethod.invoke(player, scale);
            } catch (Exception e) {
                getLogger().severe("Failed to set scale for " + player.getName());
                // Fallback to command
                String cmd = String.format("attribute %s minecraft:scale base set %f", player.getName(), scale);
                getServer().dispatchCommand(getServer().getConsoleSender(), cmd);
            }
        } else {
            String cmd = String.format("attribute %s minecraft:scale base set %f", player.getName(), scale);
            getServer().dispatchCommand(getServer().getConsoleSender(), cmd);
        }
    }

    private void sendMessage(Player player, double scale) {
        String formattedScale = String.format("%.1f", scale); // Format to 1 decimal place
        player.sendMessage(ChatColor.GOLD + "Your Scale Is Now §f" + formattedScale);
    }

    private void sendScaleActionBar(Player player, double scale) {
        String formattedScale = String.format("%.1f", scale); // Format to 1 decimal
        String actionBarMessage = ChatColor.GOLD + "Scale: " + ChatColor.WHITE + formattedScale;
        player.sendActionBar(actionBarMessage);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(ChatColor.RED + "This command can only be used by players.");
            return true;
        }
        Player player = (Player) sender;
        if (!player.hasPermission("scalesmp.setscale")) {
            player.sendMessage(ChatColor.RED + "You do not have permission.");
            return true;
        }
        if (args.length != 1) {
            player.sendMessage(ChatColor.RED + "Usage: /setscale <scale>");
            return true;
        }
        try {
            double scale = Double.parseDouble(args[0]);
            if (scale < 0.5 || scale > 2.5) {
                player.sendMessage(ChatColor.RED + "Scale must be between 0.5 and 2.5.");
                return true;
            }
            setPlayerScale(player, scale);
        } catch (NumberFormatException e) {
            player.sendMessage(ChatColor.RED + "Invalid scale.");
        }
        return true;
    }

    private class PlayerJoinListener implements Listener {
        @EventHandler
        public void onPlayerJoin(PlayerJoinEvent event) {
            Player player = event.getPlayer();
            double scale = playerScales.getOrDefault(player, 1.0);
            sendScaleActionBar(player, scale);
        }
    }
}