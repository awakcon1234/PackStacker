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

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerMoveEvent;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tracks temporary player protection while resource packs are being requested.
 * Protection is lifted only after all requested packs finish and the player has moved at least once.
 */
public final class PlayerProtectionManager {

    private static final PlayerProtectionManager INSTANCE = new PlayerProtectionManager();

    private final Map<UUID, ProtectionState> states = new ConcurrentHashMap<>();

    private PlayerProtectionManager() {}

    public static PlayerProtectionManager getInstance() {
        return INSTANCE;
    }

    public void beginProtection(Player player, List<AbstractResourcePack> packs) {
        if (player == null || packs == null || packs.isEmpty()) {
            return;
        }

        ProtectionState state = states.computeIfAbsent(player.getUniqueId(), id -> new ProtectionState());
        if (!state.active) {
            state.originalInvulnerable = player.isInvulnerable();
            state.originalCollidable = player.isCollidable();
        }

        packs.forEach(pack -> state.pendingPackIds.add(pack.getUuid()));
        state.allPacksFinished = state.pendingPackIds.isEmpty();
        state.active = true;

        applyProtection(player);
    }

    public void onPackProcessed(UUID playerId, UUID packId) {
        ProtectionState state = states.get(playerId);
        if (state == null || !state.active) {
            return;
        }

        if (packId != null) {
            state.pendingPackIds.remove(packId);
        }
        state.allPacksFinished = state.pendingPackIds.isEmpty();
        attemptRelease(playerId, state);
    }

    public void onPlayerMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        ProtectionState state = states.get(player.getUniqueId());
        if (state == null || !state.active || event.getTo() == null) {
            return;
        }

        if (!hasMoved(event)) {
            return;
        }

        state.movementSeen = true;
        attemptRelease(player.getUniqueId(), state);
    }

    public boolean isProtected(UUID playerId) {
        ProtectionState state = states.get(playerId);
        return state != null && state.active;
    }

    public void clear(UUID playerId) {
        ProtectionState state = states.remove(playerId);
        if (state == null) {
            return;
        }

        Player player = Bukkit.getPlayer(playerId);
        if (player != null) {
            restore(player, state);
        }
    }

    private void applyProtection(Player player) {
        player.setInvulnerable(true);
        player.setCollidable(false);
        player.setFireTicks(0);
        player.setRemainingAir(player.getMaximumAir());
    }

    private void maintainSafety(Player player) {
        player.setFireTicks(0);
        player.setRemainingAir(player.getMaximumAir());
    }

    private void restore(Player player, ProtectionState state) {
        player.setInvulnerable(state.originalInvulnerable);
        player.setCollidable(state.originalCollidable);
    }

    private void attemptRelease(UUID playerId, ProtectionState state) {
        if (state.allPacksFinished && state.movementSeen) {
            Player player = Bukkit.getPlayer(playerId);
            if (player != null) {
                restore(player, state);
            }
            states.remove(playerId);
            return;
        }

        Player player = Bukkit.getPlayer(playerId);
        if (player != null) {
            maintainSafety(player);
        }
    }

    private boolean hasMoved(PlayerMoveEvent event) {
        return event.getFrom().getX() != event.getTo().getX()
                || event.getFrom().getY() != event.getTo().getY()
                || event.getFrom().getZ() != event.getTo().getZ();
    }

    private static final class ProtectionState {
        private final Set<UUID> pendingPackIds = new HashSet<>();
        private boolean movementSeen = false;
        private boolean allPacksFinished = false;
        private boolean active = false;
        private boolean originalInvulnerable = false;
        private boolean originalCollidable = true;
    }
}
