package com.minecraftserver.stonecutterfix.listeners;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import com.minecraftserver.stonecutterfix.StonecutterFixPlugin;
import com.minecraftserver.stonecutterfix.managers.RecipeManager;
import com.minecraftserver.stonecutterfix.utils.BedrockPlayerChecker;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class StonecutterListener implements Listener {
    
    private final StonecutterFixPlugin plugin;
    private final Map<UUID, Inventory> activeStonecutters = new HashMap<>();
    
    public StonecutterListener(StonecutterFixPlugin plugin) {
        this.plugin = plugin;
    }
    
    // --- OPEN GUI ---
    @EventHandler(priority = EventPriority.HIGH)
    public void onStonecutterOpen(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        Block block = event.getClickedBlock();
        if (block == null || block.getType() != Material.STONECUTTER) return;
        
        Player player = event.getPlayer();
        if (!BedrockPlayerChecker.isBedrockPlayer(player)) return; 
        
        event.setCancelled(true);
        openCustomStonecutter(player);
    }
    
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInventoryOpen(InventoryOpenEvent event) {
        if (!(event.getPlayer() instanceof Player)) return;
        Player player = (Player) event.getPlayer();
        if (event.getInventory().getType() == InventoryType.STONECUTTER 
            && BedrockPlayerChecker.isBedrockPlayer(player)) {
            event.setCancelled(true);
        }
    }
    
    private void openCustomStonecutter(Player player) {
        Inventory inv = Bukkit.createInventory(null, 54, "§8Stonecutter (Auto-Craft)");
        
        ItemStack info = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta meta = info.getItemMeta();
        if (meta != null) {
            meta.setDisplayName("§7Place material in the top slot");
            info.setItemMeta(meta);
        }
        for (int i = 1; i < 54; i++) inv.setItem(i, info);
        
        activeStonecutters.put(player.getUniqueId(), inv);
        player.openInventory(inv);
        player.playSound(player.getLocation(), Sound.BLOCK_STONE_BREAK, 1.0f, 1.0f);
    }
    
    // --- CLICK LOGIC ---
    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) return;
        Player player = (Player) event.getWhoClicked();
        
        if (!activeStonecutters.containsKey(player.getUniqueId())) return;
        Inventory inv = activeStonecutters.get(player.getUniqueId());
        if (!event.getInventory().equals(inv)) return;
        
        int slot = event.getRawSlot();
        
        // 1. Click in Player Inventory (Bottom part)
        if (slot >= 54) {
            if (event.isShiftClick()) {
                event.setCancelled(true);
                ItemStack current = event.getCurrentItem();
                if (current != null) {
                    ItemStack inputSlot = inv.getItem(0);
                    if (inputSlot == null || inputSlot.getType() == Material.AIR) {
                        inv.setItem(0, current.clone());
                        event.setCurrentItem(null);
                    } else if (inputSlot.isSimilar(current)) {
                        int space = inputSlot.getMaxStackSize() - inputSlot.getAmount();
                        if (space > 0) {
                            int toAdd = Math.min(space, current.getAmount());
                            inputSlot.setAmount(inputSlot.getAmount() + toAdd);
                            current.setAmount(current.getAmount() - toAdd);
                            inv.setItem(0, inputSlot);
                            event.setCurrentItem(current.getAmount() > 0 ? current : null);
                        }
                    }
                    Bukkit.getScheduler().runTaskLater(plugin, () -> {
                        updateRecipeDisplay(player, inv);
                        player.updateInventory();
                    }, 2L);
                }
            }
            return;
        }
        
        // 2. Click on INPUT (Slot 0)
        if (slot == 0) {
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                updateRecipeDisplay(player, inv);
                player.updateInventory();
            }, 2L);
            return;
        }
        
        // 3. Click on GUI
        event.setCancelled(true);
        
        ItemStack clicked = event.getCurrentItem();
        if (clicked != null && clicked.getType() != Material.AIR && clicked.getType() != Material.GRAY_STAINED_GLASS_PANE) {
            craftSafe(player, inv, clicked);
        }
    }
    
    @EventHandler
    public void onInventoryDrag(InventoryDragEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) return;
        Player player = (Player) event.getWhoClicked();
        
        if (!activeStonecutters.containsKey(player.getUniqueId())) return;
        Inventory inv = activeStonecutters.get(player.getUniqueId());
        if (!event.getInventory().equals(inv)) return;
        
        boolean affectsGui = false;
        for (int slot : event.getRawSlots()) {
            if (slot > 0 && slot < 54) {
                affectsGui = true;
                break;
            }
        }
        
        if (affectsGui) {
            event.setCancelled(true);
        } else {
            if (event.getRawSlots().contains(0)) {
                Bukkit.getScheduler().runTaskLater(plugin, () -> {
                    updateRecipeDisplay(player, inv);
                    player.updateInventory();
                }, 2L);
            }
        }
    }

    // --- CRAFTING LOGIC ---
    private void craftSafe(Player player, Inventory inv, ItemStack clickedResult) {
        ItemStack input = inv.getItem(0);
        if (input == null || input.getType() == Material.AIR) {
            updateRecipeDisplay(player, inv);
            return;
        }

        RecipeManager rm = plugin.getRecipeManager();
        List<ItemStack> recipes = rm.getRecipesForMaterial(input.getType());
        
        // Find the ORIGINAL recipe item (Clean, no lore)
        ItemStack originalResult = null;
        int outputPerCraft = 1;
        
        for (ItemStack r : recipes) {
            if (r.getType() == clickedResult.getType()) {
                outputPerCraft = r.getAmount();
                originalResult = r; // Store the clean item
                break;
            }
        }

        if (originalResult == null) return; // Should not happen

        int inputAmount = input.getAmount();
        int maxPossibleOutput = inputAmount * outputPerCraft;
        
        // Check free space using the CLEAN item
        int freeSpace = getFreeSpace(player, originalResult);
        
        if (freeSpace <= 0) {
            player.sendMessage("§cNot enough inventory space!");
            return;
        }

        int realOutputAmount = Math.min(maxPossibleOutput, freeSpace);
        int craftsToPerform = realOutputAmount / outputPerCraft;
        
        if (craftsToPerform <= 0) {
            player.sendMessage("§cNot enough space for more crafting!");
            return;
        }

        int finalOutputAmount = craftsToPerform * outputPerCraft;
        int inputToConsume = craftsToPerform;

        // Consume input
        if (inputAmount <= inputToConsume) {
            inv.setItem(0, null);
        } else {
            input.setAmount(inputAmount - inputToConsume);
            inv.setItem(0, input);
        }

        // Give CLEAN item to player
        ItemStack resultItem = originalResult.clone(); // Clone from recipe, NOT from GUI
        resultItem.setAmount(finalOutputAmount);
        player.getInventory().addItem(resultItem);
        
        player.playSound(player.getLocation(), Sound.UI_STONECUTTER_TAKE_RESULT, 1.0f, 1.0f);
        
        // Refresh
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            updateRecipeDisplay(player, inv);
            player.updateInventory();
        }, 2L);
    }

    private int getFreeSpace(Player player, ItemStack item) {
        int freeSpace = 0;
        int maxStack = item.getMaxStackSize();
        
        for (ItemStack slot : player.getInventory().getStorageContents()) {
            if (slot == null || slot.getType() == Material.AIR) {
                freeSpace += maxStack;
            } else if (slot.isSimilar(item)) {
                freeSpace += Math.max(0, maxStack - slot.getAmount());
            }
        }
        return freeSpace;
    }

    private void updateRecipeDisplay(Player player, Inventory inv) {
        ItemStack input = inv.getItem(0);
        
        ItemStack bg = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta meta = bg.getItemMeta();
        meta.setDisplayName("§7---");
        bg.setItemMeta(meta);
        
        for (int i = 1; i < 54; i++) inv.setItem(i, bg);
        
        if (input == null || input.getType() == Material.AIR) return;
        
        RecipeManager rm = plugin.getRecipeManager();
        List<ItemStack> recipes = rm.getRecipesForMaterial(input.getType());
        
        if (recipes.isEmpty()) {
            ItemStack barrier = new ItemStack(Material.BARRIER);
            ItemMeta bMeta = barrier.getItemMeta();
            bMeta.setDisplayName("§cNo recipes found");
            barrier.setItemMeta(bMeta);
            inv.setItem(22, barrier);
            return;
        }
        
        int inputAmount = input.getAmount();
        int slot = 10;
        
        for (ItemStack originalRecipe : recipes) {
            if (slot >= 44) break;
            if (slot % 9 == 0 || slot % 9 == 8) slot++;
            
            ItemStack visualItem = originalRecipe.clone();
            int totalCount = inputAmount * originalRecipe.getAmount();
            visualItem.setAmount(Math.min(totalCount, 64));
            
            ItemMeta vMeta = visualItem.getItemMeta();
            List<String> lore = new ArrayList<>();
            lore.add("§7");
            lore.add("§7Input: §e" + inputAmount);
            lore.add("§7Possible Output: §a" + totalCount);
            lore.add("§7");
            lore.add("§eClick to craft!");
            vMeta.setLore(lore);
            visualItem.setItemMeta(vMeta);
            
            inv.setItem(slot, visualItem);
            slot++;
        }
    }
    
    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player)) return;
        Player player = (Player) event.getPlayer();
        if (!activeStonecutters.containsKey(player.getUniqueId())) return;
        Inventory inv = activeStonecutters.get(player.getUniqueId());
        if (!event.getInventory().equals(inv)) return;
        
        ItemStack input = inv.getItem(0);
        if (input != null && input.getType() != Material.AIR) {
            HashMap<Integer, ItemStack> leftOver = player.getInventory().addItem(input);
            for (ItemStack drop : leftOver.values()) {
                player.getWorld().dropItem(player.getLocation(), drop);
            }
        }
        activeStonecutters.remove(player.getUniqueId());
    }
}
