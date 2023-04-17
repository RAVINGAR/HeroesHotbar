package com.elmakers.mine.bukkit.heroes.controller;

import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import com.herocraftonline.heroes.characters.Hero;
import com.herocraftonline.heroes.characters.skill.Skill;
import com.herocraftonline.heroes.characters.skill.SkillConfigManager;
import com.herocraftonline.heroes.characters.skill.SkillSetting;
import org.bukkit.inventory.meta.ItemMeta;

public class HotbarUpdateTask implements Runnable {
    private final HotbarController controller;

    public HotbarUpdateTask(HotbarController controller) {
        this.controller = controller;
    }

    @Override
    public void run() {
        for (Player player : controller.getServer().getOnlinePlayers()) {
            updateHotbar(player);
        }
    }

    private long getRemainingCooldown(Player player, String skillKey) {
        if (player == null) return 0;
        Hero hero = controller.getHero(player);
        if (hero == null) return 0;
        Long cooldown = hero.getCooldown(skillKey);
        if (cooldown == null) return 0;
        long now = System.currentTimeMillis();
        return Math.max(0, cooldown - now);
    }

    public int getRequiredMana(Player player, String skillKey) {
        if (player == null) return 0;
        Hero hero = controller.getHero(player);
        if (hero == null) return 0;
        Skill skill = controller.getSkill(skillKey);
        if (skill == null) return 0;
        return SkillConfigManager.getUseSetting(hero, skill, SkillSetting.MANA, 0, true);
    }

    public int getRequiredStamina(Player player, String skillKey) {
        if (player == null) return 0;
        Hero hero = controller.getHero(player);
        if (hero == null) return 0;
        Skill skill = controller.getSkill(skillKey);
        if (skill == null) return 0;
        return SkillConfigManager.getUseSetting(hero, skill, SkillSetting.STAMINA, 0, true);
    }

    private void updateHotbar(Player player) {
        Inventory inv = player.getInventory();
        for (int i = 0; i < 9; i++) {
            ItemStack skillItem = inv.getItem(i);
            if(skillItem == null || skillItem.getType().isAir()) continue;
            String skillKey = controller.getSkillKey(skillItem);
            if (skillKey == null || skillKey.isEmpty()) continue;

            int targetAmount = 1;
            long remainingCooldown = getRemainingCooldown(player, skillKey);
            int requiredMana = getRequiredMana(player, skillKey);
            int requiredStamina = getRequiredStamina(player, skillKey);
            boolean canUse = controller.canUseSkill(player, skillKey);

            Hero hero = controller.getHero(player);
            ItemMeta meta = skillItem.getItemMeta();
            int data = meta.getCustomModelData();
            if(requiredMana > hero.getMana() || requiredStamina > hero.getStamina()) {
                if(data != 5) {
                    meta.setCustomModelData(5);
                    skillItem.setItemMeta(meta);
                }
            }
            else if(data != 7) {
                meta.setCustomModelData(7);
                skillItem.setItemMeta(meta);
            }

            if(remainingCooldown > 0) {
                targetAmount = (int)Math.min(Math.ceil((double)remainingCooldown / 1000), 99);
                canUse = false;
            }

            if(skillItem.getAmount() != targetAmount) {
                skillItem.setAmount(targetAmount);
            }
            controller.getSkillDescription(player, skillKey).setProfileState(skillItem, canUse);
        }
    }
}
