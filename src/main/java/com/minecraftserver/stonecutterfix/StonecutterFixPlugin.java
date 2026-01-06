package com.minecraftserver.stonecutterfix;

import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;
import com.minecraftserver.stonecutterfix.listeners.StonecutterListener;
import com.minecraftserver.stonecutterfix.managers.RecipeManager;

public class StonecutterFixPlugin extends JavaPlugin {
    
    private static StonecutterFixPlugin instance;
    private RecipeManager recipeManager;
    private boolean geyserEnabled = false;
    private boolean floodgateEnabled = false;
    
    @Override
    public void onEnable() {
        instance = this;
        
        // Detekce Geyser/Floodgate
        if (Bukkit.getPluginManager().getPlugin("Geyser-Spigot") != null) {
            geyserEnabled = true;
            getLogger().info("Geyser detekován!");
        }
        
        if (Bukkit.getPluginManager().getPlugin("floodgate") != null) {
            floodgateEnabled = true;
            getLogger().info("Floodgate detekován!");
        }
        
        if (!geyserEnabled && !floodgateEnabled) {
            getLogger().warning("Ani Geyser ani Floodgate nebyly detekovány! Plugin nebude fungovat správně.");
        }
        
        // Inicializace manažerů
        recipeManager = new RecipeManager();
        recipeManager.loadRecipes();
        
        // Registrace listenerů
        getServer().getPluginManager().registerEvents(new StonecutterListener(this), this);
        
        getLogger().info("StonecutterFix plugin byl úspěšně načten!");
    }
    
    @Override
    public void onDisable() {
        getLogger().info("StonecutterFix plugin byl vypnut.");
    }
    
    public static StonecutterFixPlugin getInstance() {
        return instance;
    }
    
    public RecipeManager getRecipeManager() {
        return recipeManager;
    }
    
    public boolean isGeyserEnabled() {
        return geyserEnabled;
    }
    
    public boolean isFloodgateEnabled() {
        return floodgateEnabled;
    }
}