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
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;

/**
 * Tracks temporary player protection while resource packs are being requested.
 * Protection is lifted only after all requested packs finish and the player has moved at least once.
 */
public final class PlayerProtectionManager {

    private static final PlayerProtectionManager INSTANCE = new PlayerProtectionManager();
    private static final long FALLBACK_TIMEOUT_TICKS = 20L * 20; // 20s safety release
    private static final long BOSSBAR_HIDE_TICKS = 20L * 5; // 5s after protection ends
    private static final boolean PROTECTION_INVULNERABLE = true;
    private static final boolean PROTECTION_COLLIDABLE = false;

    private final Map<UUID, ProtectionState> states = new ConcurrentHashMap<>();
    private final Map<UUID, BossBar> bossBars = new ConcurrentHashMap<>();
    private final Map<UUID, BukkitTask> bossBarHideTasks = new ConcurrentHashMap<>();

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
            player.sendMessage(mm("🛡 <green>Đã bật chế độ bảo vệ khi tải gói.</green> <gray>Bạn tạm thời an toàn trong lúc tải.</gray>"));
        }

        packs.forEach(pack -> state.pendingPackIds.add(pack.getUuid()));
        state.allPacksFinished = state.pendingPackIds.isEmpty();
        state.active = true;

        scheduleFallback(player.getUniqueId(), state);
        applyProtection(player);
        showProtectionBar(player);
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

        hideBossBarNow(playerId);

        if (state == null) {
            return;
        }

        cancelFallback(state);
        Player player = Bukkit.getPlayer(playerId);
        if (player != null) {
            restoreDefaults(player);
        }
    }

    private void applyProtection(Player player) {
        player.setInvulnerable(PROTECTION_INVULNERABLE);
        player.setCollidable(PROTECTION_COLLIDABLE);
        player.setFireTicks(0);
    }

    private void restoreDefaults(Player player) {
        player.setInvulnerable(false);
        player.setCollidable(true);
    }

    private void attemptRelease(UUID playerId, ProtectionState state) {
        if (state.allPacksFinished && state.movementSeen) {
            release(playerId, state, false);
            return;
        }

        Player player = Bukkit.getPlayer(playerId);
        if (player != null) {
            player.setFireTicks(0);
        }
    }

    private void release(UUID playerId, ProtectionState state, boolean timedOut) {
        states.remove(playerId);
        cancelFallback(state);

        Player player = Bukkit.getPlayer(playerId);
        if (player == null) {
            return;
        }

        restoreDefaults(player);
        if (timedOut) {
            player.sendMessage(mm("⚠ <red>Đã tắt chế độ bảo vệ sau thời gian chờ.</red> <gray>Bạn có thể bị sát thương và bị đẩy.</gray>"));
        } else {
            player.sendMessage(mm("⚠ <yellow>Đã tắt chế độ bảo vệ.</yellow> <gray>Bạn có thể bị sát thương và bị đẩy.</gray>"));
        }

        showReleaseBar(player);
    }

    private void handleFallback(UUID playerId) {
        ProtectionState state = states.get(playerId);
        if (state == null || !state.active) {
            return;
        }

        release(playerId, state, true);
    }

    private void scheduleFallback(UUID playerId, ProtectionState state) {
        cancelFallback(state);
        state.fallbackTask = Bukkit.getScheduler().runTaskLater(PackStacker.getPlugin(), () -> handleFallback(playerId), FALLBACK_TIMEOUT_TICKS);
    }

    private void cancelFallback(ProtectionState state) {
        if (state.fallbackTask != null) {
            state.fallbackTask.cancel();
            state.fallbackTask = null;
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
        private BukkitTask fallbackTask;
    }

    private void showProtectionBar(Player player) {
        cancelBossBarHide(player.getUniqueId());
        BossBar bar = bossBars.computeIfAbsent(player.getUniqueId(), id -> Bukkit.createBossBar("", BarColor.GREEN, BarStyle.SOLID));
        bar.setColor(BarColor.GREEN);
        bar.setTitle("🛡 Đang bật bảo vệ khi tải gói");
        bar.setVisible(true);
        bar.setProgress(1.0);
        if (!bar.getPlayers().contains(player)) {
            bar.addPlayer(player);
        }
    }

    private void showReleaseBar(Player player) {
        UUID playerId = player.getUniqueId();
        BossBar bar = bossBars.computeIfAbsent(playerId, id -> Bukkit.createBossBar("", BarColor.YELLOW, BarStyle.SOLID));
        bar.setColor(BarColor.YELLOW);
        bar.setTitle("⚠ Bảo vệ đã tắt");
        bar.setVisible(true);
        bar.setProgress(1.0);
        if (!bar.getPlayers().contains(player)) {
            bar.addPlayer(player);
        }

        cancelBossBarHide(playerId);
        BukkitTask task = Bukkit.getScheduler().runTaskTimer(PackStacker.getPlugin(), new Runnable() {
            private long remaining = BOSSBAR_HIDE_TICKS;

            @Override
            public void run() {
                remaining--;
                double progress = Math.max(0.0, (double) remaining / (double) BOSSBAR_HIDE_TICKS);
                bar.setProgress(progress);

                if (remaining <= 0) {
                    hideBossBarNow(playerId);
                }
            }
        }, 1L, 1L);
        bossBarHideTasks.put(playerId, task);
    }

    private void hideBossBarNow(UUID playerId) {
        cancelBossBarHide(playerId);
        BossBar bar = bossBars.remove(playerId);
        if (bar != null) {
            bar.removeAll();
            bar.setVisible(false);
        }
    }

    private void cancelBossBarHide(UUID playerId) {
        BukkitTask task = bossBarHideTasks.remove(playerId);
        if (task != null) {
            task.cancel();
        }
    }

    private Component mm(String text) {
        return MiniMessage.miniMessage().deserialize(text);
    }
}
