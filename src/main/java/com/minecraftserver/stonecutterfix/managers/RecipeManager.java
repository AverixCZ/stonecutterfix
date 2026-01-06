package com.minecraftserver.stonecutterfix.managers;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.Recipe;
import org.bukkit.inventory.RecipeChoice;
import org.bukkit.inventory.StonecuttingRecipe;

import java.util.*;

public class RecipeManager {
    
    // Mapa: Vstupní Materiál -> Seznam možných výsledků
    private final Map<Material, List<ItemStack>> recipeMap = new HashMap<>();
    
    public void loadRecipes() {
        recipeMap.clear();
        Iterator<Recipe> recipeIterator = Bukkit.recipeIterator();
        
        int count = 0;
        while (recipeIterator.hasNext()) {
            Recipe recipe = recipeIterator.next();
            
            // Hledáme pouze recepty pro Stonecutter
            if (recipe instanceof StonecuttingRecipe) {
                StonecuttingRecipe stonecutterRecipe = (StonecuttingRecipe) recipe;
                
                // OPRAVA CHYBY: Musíme ověřit, zda je volba typu MaterialChoice
                RecipeChoice choice = stonecutterRecipe.getInputChoice();
                if (choice instanceof RecipeChoice.MaterialChoice) {
                    RecipeChoice.MaterialChoice materialChoice = (RecipeChoice.MaterialChoice) choice;
                    
                    // Projdeme všechny materiály, které tento recept akceptuje
                    for (Material inputMat : materialChoice.getChoices()) {
                        ItemStack result = stonecutterRecipe.getResult();
                        recipeMap.computeIfAbsent(inputMat, k -> new ArrayList<>()).add(result.clone());
                    }
                    count++;
                }
            }
        }
        
        Bukkit.getLogger().info("StonecutterFix: Nacteno " + count + " receptu.");
    }
    
    public List<ItemStack> getRecipesForMaterial(Material material) {
        return recipeMap.getOrDefault(material, new ArrayList<>());
    }
}