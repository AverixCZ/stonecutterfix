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
    
    // --- OTEVÍRÁNÍ GUI ---
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
            meta.setDisplayName("§7Vlož materiál do slotu nahoře");
            info.setItemMeta(meta);
        }
        for (int i = 1; i < 54; i++) inv.setItem(i, info);
        
        activeStonecutters.put(player.getUniqueId(), inv);
        player.openInventory(inv);
        player.playSound(player.getLocation(), Sound.BLOCK_STONE_BREAK, 1.0f, 1.0f);
    }
    
    // --- OPRAVENÁ LOGIKA KLIKÁNÍ ---
    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) return;
        Player player = (Player) event.getWhoClicked();
        
        if (!activeStonecutters.containsKey(player.getUniqueId())) return;
        Inventory inv = activeStonecutters.get(player.getUniqueId());
        if (!event.getInventory().equals(inv)) return;
        
        int slot = event.getRawSlot();
        
        // 1. Kliknutí v hráčově inventáři (spodní část) -> POVOLIT VŽDY
        if (slot >= 54) {
            // Pokud je to Shift-click, musíme pohlídat, aby se item přesunul do slotu 0
            if (event.isShiftClick()) {
                event.setCancelled(true); // Zrušíme defaultní shift
                ItemStack current = event.getCurrentItem();
                if (current != null) {
                    // Zkusíme vložit do slotu 0
                    ItemStack inputSlot = inv.getItem(0);
                    if (inputSlot == null || inputSlot.getType() == Material.AIR) {
                        inv.setItem(0, current.clone());
                        event.setCurrentItem(null); // Odebrat z inventáře hráče
                    } else if (inputSlot.isSimilar(current)) {
                        int space = inputSlot.getMaxStackSize() - inputSlot.getAmount();
                        if (space > 0) {
                            int toAdd = Math.min(space, current.getAmount());
                            inputSlot.setAmount(inputSlot.getAmount() + toAdd);
                            current.setAmount(current.getAmount() - toAdd);
                            inv.setItem(0, inputSlot); // Update slot 0
                            event.setCurrentItem(current.getAmount() > 0 ? current : null);
                        }
                    }
                    // Refresh GUI
                    Bukkit.getScheduler().runTaskLater(plugin, () -> {
                        updateRecipeDisplay(player, inv);
                        player.updateInventory();
                    }, 2L);
                }
            }
            return; // Jinak povolíme normální manipulaci v inv
        }
        
        // 2. Kliknutí na VSTUP (Slot 0) -> POVOLIT
        if (slot == 0) {
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                updateRecipeDisplay(player, inv);
                player.updateInventory();
            }, 2L);
            return; // Povolíme vkládání/vyjímání
        }
        
        // 3. Vše ostatní v GUI (Sloty 1-53) -> ZAKÁZAT (kromě craftění)
        event.setCancelled(true);
        
        // Pokud klikl na validní recept
        ItemStack clicked = event.getCurrentItem();
        if (clicked != null && clicked.getType() != Material.AIR && clicked.getType() != Material.GRAY_STAINED_GLASS_PANE) {
            craftAll(player, inv, clicked);
        }
    }
    
    // --- Povolit Drag-and-Drop (Tažení myší) ---
    @EventHandler
    public void onInventoryDrag(InventoryDragEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) return;
        Player player = (Player) event.getWhoClicked();
        
        if (!activeStonecutters.containsKey(player.getUniqueId())) return;
        Inventory inv = activeStonecutters.get(player.getUniqueId());
        if (!event.getInventory().equals(inv)) return;
        
        // Povolit drag jen do slotu 0 nebo do inventáře hráče
        boolean affectsGui = false;
        for (int slot : event.getRawSlots()) {
            if (slot > 0 && slot < 54) { // Sloty 1-53 jsou zakázané
                affectsGui = true;
                break;
            }
        }
        
        if (affectsGui) {
            event.setCancelled(true);
        } else {
            // Pokud drag ovlivnil slot 0, aktualizuj recepty
            if (event.getRawSlots().contains(0)) {
                Bukkit.getScheduler().runTaskLater(plugin, () -> {
                    updateRecipeDisplay(player, inv);
                    player.updateInventory();
                }, 2L);
            }
        }
    }

    private void craftAll(Player player, Inventory inv, ItemStack clickedResult) {
        ItemStack input = inv.getItem(0);
        if (input == null || input.getType() == Material.AIR) {
            updateRecipeDisplay(player, inv);
            return;
        }

        int inputAmount = input.getAmount();
        RecipeManager rm = plugin.getRecipeManager();
        List<ItemStack> recipes = rm.getRecipesForMaterial(input.getType());
        
        int multiplier = 1;
        for (ItemStack r : recipes) {
            if (r.getType() == clickedResult.getType()) {
                multiplier = r.getAmount();
                break;
            }
        }
        
        int totalResultAmount = inputAmount * multiplier;
        ItemStack result = clickedResult.clone();
        result.setAmount(totalResultAmount);
        
        HashMap<Integer, ItemStack> leftOver = player.getInventory().addItem(result);
        
        if (!leftOver.isEmpty()) {
            player.sendMessage("§cNemáš dost místa v inventáři!");
            // Vrátíme to, co se nevešlo (jednoduše to dropneme na zem)
            for (ItemStack drop : leftOver.values()) {
                player.getWorld().dropItem(player.getLocation(), drop);
            }
            player.sendMessage("§ePřebývající předměty spadly na zem.");
        }
        
        inv.setItem(0, null); // Vymazat vstup
        player.playSound(player.getLocation(), Sound.UI_STONECUTTER_TAKE_RESULT, 1.0f, 1.0f);
        
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            updateRecipeDisplay(player, inv);
            player.updateInventory();
        }, 2L);
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
            bMeta.setDisplayName("§cŽádné recepty");
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
            lore.add("§7Vstup: §e" + inputAmount + " ks");
            lore.add("§7Výstup: §a" + totalCount + " ks");
            lore.add("§7");
            lore.add("§eKlikni pro výrobu všeho!");
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