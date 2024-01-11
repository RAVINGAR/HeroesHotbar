package com.elmakers.mine.bukkit.heroes.listener;

import com.elmakers.mine.bukkit.heroes.controller.HotbarController;
import com.elmakers.mine.bukkit.heroes.controller.SkillSelector;
import com.herocraftonline.heroes.api.events.AfterClassChangeEvent;
import com.herocraftonline.heroes.api.events.HeroChangeLevelEvent;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.ItemSpawnEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.*;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.scheduler.BukkitScheduler;

public class PlayerListener implements Listener {
    private final HotbarController controller;
    private final BukkitScheduler scheduler;

    public PlayerListener(HotbarController controller) {
        this.controller = controller;
        this.scheduler = controller.getPlugin().getServer().getScheduler();
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        controller.clearActiveSkillSelector(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerJoin(PlayerJoinEvent event) {
        controller.addActiveSkillSelector(event.getPlayer());
    }

    @EventHandler
    public void onPlayerDropItem(PlayerDropItemEvent event) {
        final Player player = event.getPlayer();

        // Unprepare skills when dropped
        ItemStack droppedItem = event.getItemDrop().getItemStack();
        controller.unprepareSkill(player, droppedItem);

        // Catch lag-related glitches dropping items from GUIs
        SkillSelector selector = controller.getActiveSkillSelector(player);
        if (selector.isGuiOpen()) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onItemSpawn(ItemSpawnEvent event) {
        Item itemEntity = event.getEntity();
        ItemStack spawnedItem = itemEntity.getItemStack();
        if (controller.isSkill(spawnedItem) || controller.isLegacySkill(spawnedItem)) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onPlayerEquip(PlayerItemHeldEvent event) {
        if(controller.isUseRightClicks()) {
            return;
        }
        Player player = event.getPlayer();
        useSkill(player, player.getInventory().getItem(event.getNewSlot()), event);
    }

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event)
    {
        if (event.getHand() != EquipmentSlot.HAND) return;
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) return;

        Player player = event.getPlayer();
        useSkill(player, player.getInventory().getItemInMainHand(), event);
    }

    private void useSkill(Player player, ItemStack item, Cancellable event) {
        String skillKey = controller.getSkillKey(item);
        if (skillKey != null && !skillKey.isEmpty()) {
            event.setCancelled(true);
            scheduler.runTask(controller.getPlugin(), () -> controller.useSkill(player, skillKey, item));
        }
    }

    @EventHandler
    public void onBlockPlace(BlockPlaceEvent event)
    {
        ItemStack itemStack = event.getItemInHand();
        if (controller.isSkill(itemStack) || controller.isLegacySkill(itemStack)) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onLevelUp(HeroChangeLevelEvent event) {
        scheduler.scheduleSyncDelayedTask(controller.getPlugin(), () -> {
            controller.getActiveSkillSelector(event.getHero().getPlayer()).updateSkillsForLevelUp();
        });
    }

    @EventHandler
    public void onClassChange(AfterClassChangeEvent event) {
        Player player = event.getHero().getPlayer();
        controller.removeAllSkillItems(player);
        controller.getActiveSkillSelector(player).refreshAllSkills();

    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        SkillSelector selector = controller.getActiveSkillSelectorOrNull(event.getPlayer());
        if(selector == null) {
            return;
        }
        selector.setGuiState(false);
    }
}
