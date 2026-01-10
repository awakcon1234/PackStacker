/*
 * PackStacker
 * Copyright (C) 2024 Timo McGrath
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.timomcgrath.packstacker;

import org.bukkit.entity.Player;
import org.bukkit.Bukkit;

import java.lang.reflect.Method;
import java.util.UUID;

/**
 * Simple utility to detect players connecting via Geyser/Floodgate.
 * Returns true if the player is a Bedrock player and should be ignored.
 */
public final class GeyserDetector {

    public static boolean isGeyserPlayer(Player player) {
        if (player == null) return false;
        UUID uuid = player.getUniqueId();
        
        // Try Floodgate first since it's more commonly available
        try {
            Class<?> floodgateClass = Class.forName("org.geysermc.floodgate.api.FloodgateApi");
            Object floodgate = floodgateClass.getMethod("getInstance").invoke(null);
            
            // Try isFloodgatePlayer first
            try {
                Method isFloodgate = floodgate.getClass().getMethod("isFloodgatePlayer", UUID.class);
                if ((Boolean) isFloodgate.invoke(floodgate, uuid)) {
                    Bukkit.getLogger().info("GeyserDetector: Player " + player.getName() + " is a Bedrock player (via FloodgateApi.isFloodgatePlayer)");
                    return true;
                }
            } catch (Exception e) {
                Bukkit.getLogger().fine("GeyserDetector: FloodgateApi.isFloodgatePlayer check failed: " + e.getMessage());
            }
            
            // Try isFloodgateId as backup
            try {
                Method isFloodgateId = floodgate.getClass().getMethod("isFloodgateId", UUID.class);
                if ((Boolean) isFloodgateId.invoke(floodgate, uuid)) {
                    Bukkit.getLogger().info("GeyserDetector: Player " + player.getName() + " is a Bedrock player (via FloodgateApi.isFloodgateId)");
                    return true;
                }
            } catch (Exception e) {
                Bukkit.getLogger().fine("GeyserDetector: FloodgateApi.isFloodgateId check failed: " + e.getMessage());
            }
        } catch (Exception e) {
            Bukkit.getLogger().fine("GeyserDetector: FloodgateApi not available: " + e.getMessage());
        }

        // Then try Geyser's API
        try {
            Class<?> geyserClass = Class.forName("org.geysermc.geyser.api.GeyserApi");
            Object geyserApi = geyserClass.getMethod("api").invoke(null);
            Method isBedrockPlayer = geyserApi.getClass().getMethod("isBedrockPlayer", UUID.class);
            
            if ((Boolean) isBedrockPlayer.invoke(geyserApi, uuid)) {
                Bukkit.getLogger().info("GeyserDetector: Player " + player.getName() + " is a Bedrock player (via GeyserApi)");
                return true;
            }
        } catch (Exception e) {
            Bukkit.getLogger().fine("GeyserDetector: GeyserApi not available: " + e.getMessage());
        }

        // Neither API detected the player as Bedrock
        Bukkit.getLogger().info("GeyserDetector: Player " + player.getName() + " is not detected as a Bedrock player");
        return false;
    }
}
