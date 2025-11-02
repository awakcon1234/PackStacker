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
import org.geysermc.geyser.api.GeyserApi;
import org.geysermc.floodgate.api.FloodgateApi;

import java.util.UUID;

/**
 * Simple utility to detect players connecting via Geyser/Floodgate.
 * Returns true if the player is a Bedrock player and should be ignored.
 */
public final class GeyserDetector {

    public static boolean isGeyserPlayer(Player player) {
        if (player == null) return false;
        UUID uuid = player.getUniqueId();
        
        // First try Geyser's API
        try {
            if (GeyserApi.api().isBedrockPlayer(uuid)) {
                Bukkit.getLogger().info("GeyserDetector: Player " + player.getName() + " is a Bedrock player (via GeyserApi)");
                return true;
            }
        } catch (Exception ignored) {
            // Geyser not present or unavailable
        }

        // Fallback to Floodgate
        try {
            FloodgateApi floodgate = FloodgateApi.getInstance();
            if (floodgate.isFloodgatePlayer(uuid) || floodgate.isFloodgateId(uuid)) {
                Bukkit.getLogger().info("GeyserDetector: Player " + player.getName() + " is a Bedrock player (via FloodgateApi)");
                return true;
            }
        } catch (Exception ignored) {
            // Floodgate not present or unavailable
        }

        return false;
    }
}
