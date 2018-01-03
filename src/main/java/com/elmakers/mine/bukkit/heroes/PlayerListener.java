package com.elmakers.mine.bukkit.heroes;

import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.ItemSpawnEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

public class PlayerListener implements Listener {
    private final HotbarController controller;

    public PlayerListener(HotbarController controller) {
        this.controller = controller;
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        controller.clearActiveSkillSelector(event.getPlayer());
    }

    @EventHandler
    public void onPlayerDropItem(PlayerDropItemEvent event) {
        final Player player = event.getPlayer();

        // Unprepare skills when dropped
        ItemStack droppedItem = event.getItemDrop().getItemStack();
        controller.unprepareSkill(player, droppedItem);

        // Catch lag-related glitches dropping items from GUIs
        SkillSelector selector = controller.getActiveSkillSelector(player);
        if (selector != null) {
            event.setCancelled(true);
            return;
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onItemSpawn(ItemSpawnEvent event) {
        Item itemEntity = event.getEntity();
        ItemStack spawnedItem = itemEntity.getItemStack();
        if (controller.isSkill(spawnedItem) || controller.isLegacySkill(spawnedItem)) {
            event.setCancelled(true);
            return;
        }
    }

    @EventHandler
    public void onPlayerEquip(PlayerItemHeldEvent event) {
        Player player = event.getPlayer();
        PlayerInventory inventory = player.getInventory();
        ItemStack next = inventory.getItem(event.getNewSlot());

        boolean isSkill = controller.isSkill(next);
        if (isSkill) {
            controller.useSkill(player, next);
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event)
    {
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) return;

        Player player = event.getPlayer();
        PlayerInventory inventory = player.getInventory();
        ItemStack itemInHand = inventory.getItemInMainHand();

        boolean isSkill = controller.isSkill(itemInHand);
        if (isSkill) {
            controller.useSkill(player, itemInHand);
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onBlockPlace(BlockPlaceEvent event)
    {
        ItemStack itemStack = event.getItemInHand();
        if (controller.isSkill(itemStack) || controller.isLegacySkill(itemStack)) {
            event.setCancelled(true);
            return;
        }
    }
}
