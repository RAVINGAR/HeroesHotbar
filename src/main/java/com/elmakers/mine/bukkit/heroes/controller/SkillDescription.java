package com.elmakers.mine.bukkit.heroes.controller;

import com.elmakers.mine.bukkit.heroes.utilities.CompatibilityUtils;
import com.herocraftonline.heroes.characters.skill.Skill;
import com.herocraftonline.heroes.characters.skill.SkillConfigManager;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.profile.PlayerProfile;
import org.checkerframework.checker.units.qual.C;

import java.util.Optional;

public class SkillDescription implements Comparable<SkillDescription> {
    private final String skillKey;
    private final String name;
    private final String description;
    private final Skill skill;
    private final int skillLevel;
    private final ItemStack icon;

    private String iconURL;
    private PlayerProfile iconProfile = null;
    private PlayerProfile disabledProfile = null;

    private PlayerProfile currentProfile = null;

    public SkillDescription(HotbarController controller, Player player, String skillKey) {
        this.skill = controller.getSkill(skillKey);
        this.skillKey = skillKey;
        this.skillLevel = controller.getSkillLevel(player, skillKey);

        String skillDisplayName = skill == null ? null : SkillConfigManager.getRaw(skill, "name", skill.getName());
        skillDisplayName = skillDisplayName == null || skillDisplayName.isEmpty() ? skillKey : skillDisplayName;
        if(controller.isElementsEnabled() && skill != null) {
            Optional<String> e = controller.getElementFromSkill(skill);
            if(e.isPresent()) {
                skillDisplayName += " " + controller.getMessage("elements." + e.get().toLowerCase());
            }
        }
        this.name = skillDisplayName;

        this.description = skill == null ? null : SkillConfigManager.getRaw(skill, "description", "");

        iconURL = skill == null ? null : SkillConfigManager.getRaw(skill, "icon-url", SkillConfigManager.getRaw(skill, "icon_url", null));

        if(iconURL == null || iconURL.isEmpty()) {
            this.iconProfile = controller.getUnknownIcon();
        }
        else {
            this.iconProfile = CompatibilityUtils.getPlayerProfile(skillKey, iconURL);
            //CompatibilityUtils.getPlayerProfile(skillKey, iconURL).thenAcceptAsync(profile -> this.iconProfile = profile, controller::runSyncTask);
        }

        String iconDisabledURL = skill == null ? null : SkillConfigManager.getRaw(skill, "icon-disabled-url", SkillConfigManager.getRaw(skill, "icon_disabled_url", null));

        if (iconDisabledURL == null || iconDisabledURL.isEmpty()) {
            this.disabledProfile = controller.getDefaultDisabledIcon();
        }
        else {
            this.disabledProfile = CompatibilityUtils.getPlayerProfile(skillKey, iconDisabledURL);
            //CompatibilityUtils.getPlayerProfile(skillKey, iconDisabledURL).thenAcceptAsync(profile -> this.disabledProfile = profile, controller::runSyncTask);
        }

        this.icon = new ItemStack(Material.PLAYER_HEAD, 1);
        ItemMeta meta = this.icon.getItemMeta();
        meta.setCustomModelData(7);
        this.icon.setItemMeta(meta);
        currentProfile = iconProfile;
        CompatibilityUtils.setSkullProfile(icon, currentProfile);
    }

    public boolean isHeroes() {
        return skillKey != null;
    }

    @Override
    public int compareTo(SkillDescription other) {
        if (skillLevel != other.skillLevel) {
            return Integer.compare(skillLevel, other.skillLevel);
        }
        return getName().compareTo(other.getName());
    }

    public String getIconURL() { return iconURL; }

    /**
     * Gets icon associated with this skill description
     * @return A disabled icon if skill cannot be used or proper icon if it can be used
     */
    public ItemStack getIcon() {
        return icon;
    }

    public void setProfileState(ItemStack icon, boolean enabled) {
        if(icon.getType() == Material.PLAYER_HEAD) {
            if(enabled && iconProfile != null && !iconProfile.equals(currentProfile)) {
                currentProfile = iconProfile;
                CompatibilityUtils.setSkullProfile(icon, iconProfile);
            }
            if(!enabled && !disabledProfile.equals(currentProfile)) {
                currentProfile = disabledProfile;
                CompatibilityUtils.setSkullProfile(icon, disabledProfile);
            }
        }
    }

    public String getName() {
        return name;
    }

    public String getKey() {
        return skillKey;
    }

    public Skill getSkill() {
        return skill;
    }

    public String getDescription() {
        return description;
    }

    public boolean isValid() {
        return skill != null;
    }
};