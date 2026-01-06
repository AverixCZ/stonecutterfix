package com.minecraftserver.stonecutterfix.utils;

import org.bukkit.entity.Player;
import org.geysermc.floodgate.api.FloodgateApi;

public class BedrockPlayerChecker {
    
    /**
     * Kontroluje, zda je hráč z Bedrock Edition
     * Používá Floodgate API, pokud je dostupné
     */
    public static boolean isBedrockPlayer(Player player) {
        try {
            // Pokus o použití Floodgate API
            FloodgateApi api = FloodgateApi.getInstance();
            return api.isFloodgatePlayer(player.getUniqueId());
        } catch (NoClassDefFoundError | Exception e) {
            // Floodgate není dostupný, alternativní detekce
            // Bedrock hráči mají často UUID začínající na "00000000-0000-0000"
            String uuid = player.getUniqueId().toString();
            if (uuid.startsWith("00000000-0000-0000")) {
                return true;
            }
            
            // Další alternativa: kontrola jména
            // Floodgate prefix je obvykle "." nebo podle konfigurace
            String name = player.getName();
            if (name.startsWith(".")) {
                return true;
            }
            
            // Pokud nemůžeme detekovat, defaultně false
            return false;
        }
    }
}