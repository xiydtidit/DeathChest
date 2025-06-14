package com.example.deathchest;

import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.Chest;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.CompassMeta;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.*;

public class DeathChest extends JavaPlugin implements Listener {

    private final Map<UUID, Location> deathLocations = new HashMap<>();
    private final Set<Location> activeChests = new HashSet<>();

    @Override
    public void onEnable() {
        getServer().getPluginManager().registerEvents(this, this);
        saveDefaultConfig();
        
        // Auto-cleanup every 10 minutes
        getServer().getScheduler().scheduleSyncRepeatingTask(this, () -> {
            activeChests.removeIf(loc -> {
                if (System.currentTimeMillis() - loc.getWorld().getFullTime() > 
                    getConfig().getLong("chest-duration-hours", 24) * 60 * 60 * 1000) {
                    loc.getBlock().setType(Material.AIR);
                    return true;
                }
                return false;
            });
        }, 12000L, 12000L);
    }

    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player player = event.getEntity();
        Location deathLoc = findSafeLocation(player.getLocation());
        
        // Store items in chest
        deathLoc.getBlock().setType(Material.CHEST);
        Chest chest = (Chest) deathLoc.getBlock().getState();
        chest.getInventory().setContents(event.getDrops().toArray(new ItemStack[0]));
        event.getDrops().clear();
        
        // Save data
        deathLocations.put(player.getUniqueId(), deathLoc);
        activeChests.add(deathLoc);
        
        // Give tracking compass
        player.getInventory().addItem(createTrackingCompass(deathLoc));
        
        // Effects
        player.playSound(deathLoc, Sound.BLOCK_ANVIL_USE, 1, 1);
        player.spawnParticle(Particle.FLAME, deathLoc.add(0.5, 0.5, 0.5), 30);
    }

    @EventHandler
    public void onChestOpen(PlayerInteractEvent event) {
        if (event.getAction() != org.bukkit.event.block.Action.RIGHT_CLICK_BLOCK || 
            !(event.getClickedBlock().getState() instanceof Chest)) return;
            
        Location chestLoc = event.getClickedBlock().getLocation();
        Player player = event.getPlayer();
        
        if (activeChests.contains(chestLoc)) {
            // Remove compass when opening their own chest
            player.getInventory().removeItem(createTrackingCompass(chestLoc));
            
            // Schedule chest removal check
            getServer().getScheduler().scheduleSyncDelayedTask(this, () -> {
                if (isChestEmpty((Chest) chestLoc.getBlock().getState())) {
                    chestLoc.getBlock().setType(Material.AIR);
                    activeChests.remove(chestLoc);
                    deathLocations.remove(player.getUniqueId());
                }
            }, 20L);
        }
    }

    private Location findSafeLocation(Location loc) {
        Block block = loc.getBlock();
        if (block.getType() == Material.AIR || block.isPassable()) {
            return loc;
        }
        
        // Search upwards for air
        for (int y = loc.getBlockY(); y < loc.getWorld().getMaxHeight(); y++) {
            Block current = loc.getWorld().getBlockAt(loc.getBlockX(), y, loc.getBlockZ());
            if (current.getType() == Material.AIR) {
                return current.getLocation();
            }
        }
        return loc; // Fallback
    }

    private ItemStack createTrackingCompass(Location loc) {
        ItemStack compass = new ItemStack(Material.COMPASS);
        CompassMeta meta = (CompassMeta) compass.getItemMeta();
        
        meta.setDisplayName(ChatColor.GOLD + "Death Chest Tracker");
        meta.setLodestone(loc);
        meta.setLodestoneTracked(false);
        meta.setLore(Arrays.asList(
            ChatColor.GRAY + "World: " + getWorldName(loc.getWorld()),
            ChatColor.GRAY + "X: " + loc.getBlockX(),
            ChatColor.GRAY + "Y: " + loc.getBlockY(),
            ChatColor.GRAY + "Z: " + loc.getBlockZ(),
            ChatColor.RED + "Disappears when retrieved"
        ));
        
        compass.setItemMeta(meta);
        return compass;
    }

    private boolean isChestEmpty(Chest chest) {
        return Arrays.stream(chest.getInventory().getContents()).allMatch(Objects::isNull);
    }

    private String getWorldName(World world) {
        return switch (world.getEnvironment()) {
            case NETHER -> "Nether";
            case THE_END -> "End";
            default -> "Overworld";
        };
    }
}