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
 * Utility to detect players connecting via Geyser/Floodgate without
 * hard dependency on their APIs by using reflection. Returns true if
 * the player is a Bedrock/Geyser player and should be ignored.
 */
public final class GeyserDetector {

  private GeyserDetector() {}

  public static boolean isGeyserPlayer(Player player) {
    if (player == null) return false;
    UUID uuid = player.getUniqueId();

    // Try Floodgate API: org.geysermc.floodgate.api.FloodgateApi.getInstance().isFloodgatePlayer(UUID)
    try {
      Class<?> floodgateClass = Class.forName("org.geysermc.floodgate.api.FloodgateApi");
      Method getInstance = floodgateClass.getMethod("getInstance");
      Object fgApi = getInstance.invoke(null);
      Method isFg = fgApi.getClass().getMethod("isFloodgatePlayer", UUID.class);
      Object result = isFg.invoke(fgApi, uuid);
      if (result instanceof Boolean && (Boolean) result) {
        return true;
      }
    } catch (ClassNotFoundException e) {
      // Floodgate not present - ignore
    } catch (ReflectiveOperationException e) {
      // API present but call failed - log debug and continue
      Bukkit.getLogger().fine("GeyserDetector: Floodgate reflection failed: " + e.getMessage());
    }

    // Try Geyser API: org.geysermc.geyser.api.GeyserApi.getInstance().isBedrockPlayer(UUID)
    try {
      Class<?> geyserClass = Class.forName("org.geysermc.geyser.api.GeyserApi");
      Method getInstance = geyserClass.getMethod("getInstance");
      Object geyserApi = getInstance.invoke(null);
      // Method name historically 'isBedrockPlayer', try that first
      try {
        Method isBedrock = geyserApi.getClass().getMethod("isBedrockPlayer", UUID.class);
        Object result = isBedrock.invoke(geyserApi, uuid);
        if (result instanceof Boolean && (Boolean) result) {
          return true;
        }
      } catch (NoSuchMethodException inner) {
        // Fallbacks: some versions expose player lookup - try getPlayerByUuid(UUID) returning Optional/Player
        try {
          Method getPlayerByUuid = geyserApi.getClass().getMethod("playerByUuid", UUID.class);
          Object p = getPlayerByUuid.invoke(geyserApi, uuid);
          if (p != null) return true;
  } catch (ReflectiveOperationException ignored) {
        }
      }
    } catch (ClassNotFoundException e) {
      // Geyser not present
    } catch (ReflectiveOperationException e) {
      Bukkit.getLogger().fine("GeyserDetector: Geyser reflection failed: " + e.getMessage());
    }

    return false;
  }
}
