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
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import com.minecraftserver.stonecutterfix.StonecutterFixPlugin;
import com.minecraftserver.stonecutterfix.managers.RecipeManager;
import com.minecraftserver.stonecutterfix.utils.BedrockPlayerChecker;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class StonecutterListener implements Listener {
    
    private final StonecutterFixPlugin plugin;
    private final Map<UUID, Inventory> activeStonecutters = new HashMap<>();
    private final Map<UUID, ItemStack> currentInput = new HashMap<>();
    
    public StonecutterListener(StonecutterFixPlugin plugin) {
        this.plugin = plugin;
    }
    
    @EventHandler(priority = EventPriority.HIGH)
    public void onStonecutterOpen(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        
        Block block = event.getClickedBlock();
        if (block == null || block.getType() != Material.STONECUTTER) return;
        
        Player player = event.getPlayer();
        
        // Kontrola, zda je hráč z Bedrocku
        if (!BedrockPlayerChecker.isBedrockPlayer(player)) {
            return; // Nechť Java hráči používají vanilla stonecutter
        }
        
        // Zrušení vanilla otevření
        event.setCancelled(true);
        
        // Otevření custom GUI
        openCustomStonecutter(player);
    }
    
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInventoryOpen(InventoryOpenEvent event) {
        if (!(event.getPlayer() instanceof Player)) return;
        
        Player player = (Player) event.getPlayer();
        
        // Pokud je to vanilla stonecutter a hráč je z Bedrocku, zrušíme
        if (event.getInventory().getType() == InventoryType.STONECUTTER 
            && BedrockPlayerChecker.isBedrockPlayer(player)) {
            event.setCancelled(true);
        }
    }
    
    private void openCustomStonecutter(Player player) {
        Inventory inv = Bukkit.createInventory(null, 54, "§8Stonecutter");
        
        // Slot 0 je pro vstupní materiál
        ItemStack info = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        org.bukkit.inventory.meta.ItemMeta meta = info.getItemMeta();
        if (meta != null) {
            meta.setDisplayName("§7Vlož materiál zde");
            info.setItemMeta(meta);
        }
        
        // Vyplnění pozadí
        for (int i = 1; i < 54; i++) {
            inv.setItem(i, info);
        }
        
        activeStonecutters.put(player.getUniqueId(), inv);
        player.openInventory(inv);
        player.playSound(player.getLocation(), Sound.BLOCK_STONE_BREAK, 1.0f, 1.0f);
    }
    
    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) return;
        
        Player player = (Player) event.getWhoClicked();
        UUID uuid = player.getUniqueId();
        
        if (!activeStonecutters.containsKey(uuid)) return;
        
        Inventory inv = activeStonecutters.get(uuid);
        if (!event.getInventory().equals(inv)) return;
        
        int slot = event.getRawSlot();
        
        // Klik mimo inventář
        if (slot < 0 || slot >= 54) {
            return;
        }
        
        // Slot 0 je vstupní slot
        if (slot == 0) {
            // Povolíme vkládání/vyjímání materiálu
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                updateRecipeDisplay(player, inv);
            }, 1L);
            return;
        }
        
        // Ostatní sloty - klik na recept
        event.setCancelled(true);
        
        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || clicked.getType() == Material.GRAY_STAINED_GLASS_PANE) {
            return;
        }
        
        // Zpracování craftění
        boolean isShiftClick = event.getClick() == ClickType.SHIFT_LEFT || 
                               event.getClick() == ClickType.SHIFT_RIGHT;
        
        craftItem(player, inv, clicked, isShiftClick);
    }
    
    private void updateRecipeDisplay(Player player, Inventory inv) {
        ItemStack input = inv.getItem(0);
        
        // Vyčištění staré nabídky receptů
        for (int i = 1; i < 54; i++) {
            inv.setItem(i, null);
        }
        
        if (input == null || input.getType() == Material.AIR) {
            currentInput.remove(player.getUniqueId());
            
            // Vyplnění pozadí
            ItemStack info = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
            org.bukkit.inventory.meta.ItemMeta meta = info.getItemMeta();
            if (meta != null) {
                meta.setDisplayName("§7Vlož materiál");
                info.setItemMeta(meta);
            }
            for (int i = 1; i < 54; i++) {
                inv.setItem(i, info);
            }
            return;
        }
        
        currentInput.put(player.getUniqueId(), input.clone());
        
        // Získání receptů pro tento materiál
        RecipeManager rm = plugin.getRecipeManager();
        List<ItemStack> recipes = rm.getRecipesForMaterial(input.getType());
        
        if (recipes.isEmpty()) {
            ItemStack noRecipe = new ItemStack(Material.BARRIER);
            org.bukkit.inventory.meta.ItemMeta meta = noRecipe.getItemMeta();
            if (meta != null) {
                meta.setDisplayName("§cŽádné recepty");
                noRecipe.setItemMeta(meta);
            }
            inv.setItem(22, noRecipe);
            return;
        }
        
        // Zobrazení receptů
        int slot = 10;
        for (ItemStack recipe : recipes) {
            if (slot >= 44) break;
            
            // Přeskočení slotů okrajů
            if (slot % 9 == 0 || slot % 9 == 8) {
                slot++;
            }
            
            inv.setItem(slot, recipe);
            slot++;
        }
    }
    
    private void craftItem(Player player, Inventory inv, ItemStack result, boolean shiftClick) {
        ItemStack input = inv.getItem(0);
        if (input == null || input.getType() == Material.AIR) {
            return;
        }
        
        int amount = shiftClick ? input.getAmount() : 1;
        
        if (amount > input.getAmount()) {
            amount = input.getAmount();
        }
        
        // Vytvoření výsledného itemu
        ItemStack craftedItem = result.clone();
        craftedItem.setAmount(amount);
        
        // Kontrola místa v inventáři
        if (!hasSpace(player, craftedItem)) {
            player.sendMessage("§cNemáš dostatek místa v inventáři!");
            return;
        }
        
        // Odebrání vstupního materiálu
        if (input.getAmount() > amount) {
            input.setAmount(input.getAmount() - amount);
            inv.setItem(0, input);
        } else {
            inv.setItem(0, null);
        }
        
        // Přidání výsledku
        player.getInventory().addItem(craftedItem);
        player.playSound(player.getLocation(), Sound.BLOCK_STONE_BREAK, 1.0f, 1.2f);
        
        // Aktualizace zobrazení receptů
        if (inv.getItem(0) == null || inv.getItem(0).getType() == Material.AIR) {
            updateRecipeDisplay(player, inv);
        }
    }
    
    private boolean hasSpace(Player player, ItemStack item) {
        Inventory inv = player.getInventory();
        int remaining = item.getAmount();
        
        for (ItemStack slot : inv.getStorageContents()) {
            if (slot == null || slot.getType() == Material.AIR) {
                return true;
            }
            if (slot.isSimilar(item)) {
                int space = slot.getMaxStackSize() - slot.getAmount();
                remaining -= space;
                if (remaining <= 0) return true;
            }
        }
        
        return remaining <= 0;
    }
    
    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player)) return;
        
        Player player = (Player) event.getPlayer();
        UUID uuid = player.getUniqueId();
        
        if (!activeStonecutters.containsKey(uuid)) return;
        
        Inventory inv = activeStonecutters.get(uuid);
        if (!event.getInventory().equals(inv)) return;
        
        // Vrácení materiálu ze slotu 0
        ItemStack input = inv.getItem(0);
        if (input != null && input.getType() != Material.AIR) {
            player.getInventory().addItem(input);
        }
        
        activeStonecutters.remove(uuid);
        currentInput.remove(uuid);
    }
}