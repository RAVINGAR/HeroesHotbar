package com.elmakers.mine.bukkit.heroes.controller;

import java.net.MalformedURLException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Queue;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.herocraftonline.heroes.characters.skill.SkillType;
import com.herocraftonline.heroes.util.Pair;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Server;
import org.bukkit.configuration.Configuration;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.Plugin;

import com.elmakers.mine.bukkit.heroes.utilities.CompatibilityUtils;
import com.google.common.collect.Multimap;
import com.google.common.collect.Ordering;
import com.google.common.collect.TreeMultimap;
import com.herocraftonline.heroes.Heroes;
import com.herocraftonline.heroes.characters.CharacterManager;
import com.herocraftonline.heroes.characters.Hero;
import com.herocraftonline.heroes.characters.classes.HeroClass;
import com.herocraftonline.heroes.characters.skill.ActiveSkill;
import com.herocraftonline.heroes.characters.skill.OutsourcedSkill;
import com.herocraftonline.heroes.characters.skill.PassiveSkill;
import com.herocraftonline.heroes.characters.skill.Skill;
import com.herocraftonline.heroes.characters.skill.SkillConfigManager;
import com.herocraftonline.heroes.characters.skill.SkillManager;
import com.herocraftonline.heroes.characters.skill.SkillSetting;
import org.bukkit.profile.PlayerProfile;
import org.bukkit.profile.PlayerTextures;
import org.bukkit.scheduler.BukkitScheduler;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * This class manages the centralized hotbar functionality.
 */
public class HotbarController {
    private final static List<String> emptySkillList = new ArrayList<>();
    private static final int MAX_LORE_LENGTH = 24;
    private static final DecimalFormat SECONDS_FORMATTER = new DecimalFormat("0.##");

    private final Plugin plugin;
    private BukkitScheduler scheduler;
    private final CharacterManager characters;
    private final SkillManager skills;

    private String skillNBTKey;
    private String legacyNBTKey;
    private int skillInventoryRows;

    private PlayerProfile disabledIcon;
    private PlayerProfile unknownIcon;

    private boolean useRightClicks;

    private boolean elementsEnabled;

    private final Map<UUID, SkillSelector> selectors = new HashMap<>();

    private final Map<SkillType, String> elementMap = new HashMap<>();

    private final Map<String, PlayerProfile> playerProfiles = new HashMap<>();

    public HotbarController(Plugin owningPlugin, Heroes heroesPlugin) {
        this.plugin = owningPlugin;
        this.scheduler = plugin.getServer().getScheduler();
        characters = heroesPlugin.getCharacterManager();
        skills = heroesPlugin.getSkillManager();
        elementsEnabled = !getMessage("elements.description", "").isEmpty();

        elementMap.put(SkillType.ABILITY_PROPERTY_PHYSICAL, "Physical");
        elementMap.put(SkillType.ABILITY_PROPERTY_BLEED, "Physical");
        elementMap.put(SkillType.ABILITY_PROPERTY_PROJECTILE, "Physical");

        elementMap.put(SkillType.ABILITY_PROPERTY_POISON, "Acid");
        elementMap.put(SkillType.ABILITY_PROPERTY_DISEASE, "Poison");

        elementMap.put(SkillType.ABILITY_PROPERTY_MAGICAL, "Force");
        elementMap.put(SkillType.ABILITY_PROPERTY_AIR, "Force");
        elementMap.put(SkillType.ABILITY_PROPERTY_EARTH, "Force");

        elementMap.put(SkillType.ABILITY_PROPERTY_FIRE, "Fire");
        elementMap.put(SkillType.ABILITY_PROPERTY_LIGHTNING, "Lightning");
        elementMap.put(SkillType.ABILITY_PROPERTY_ICE, "Frost");

        elementMap.put(SkillType.ABILITY_PROPERTY_ILLUSION, "Psychic");
        elementMap.put(SkillType.ABILITY_PROPERTY_TEMPORAL, "Psychic");
        elementMap.put(SkillType.ABILITY_PROPERTY_ENDER, "Psychic");

        elementMap.put(SkillType.ABILITY_PROPERTY_LIGHT, "Radiant");
        elementMap.put(SkillType.ABILITY_PROPERTY_SONG, "Radiant");

        elementMap.put(SkillType.ABILITY_PROPERTY_WITHER, "Necrotic");
        elementMap.put(SkillType.ABILITY_PROPERTY_DARK, "Necrotic");
    }

    public void initialize() {
        Configuration config = plugin.getConfig();
        skillNBTKey = config.getString("nbt_key");
        if (skillNBTKey == null || skillNBTKey.isEmpty()) {
            skillNBTKey = "heroesskill";
        }
        legacyNBTKey = config.getString("legacy_nbt_key");
        disabledIcon = CompatibilityUtils.getPlayerProfile("Disabled", config.getString("disabled_icon_url"));
        unknownIcon = null;
        CompatibilityUtils.getUnknownIcon(this, UUID.fromString("606e2ff0-ed77-4842-9d6c-e1d3321c7838"));

        skillInventoryRows = config.getInt("skill_inventory_max_rows", 6);
        useRightClicks = config.getBoolean("right-click-to-use", false);

        int hotbarUpdateInterval = config.getInt("update_interval");
        if (hotbarUpdateInterval > 0) {
            final HotbarUpdateTask updateTask = new HotbarUpdateTask(this);
            plugin.getServer().getScheduler().scheduleSyncRepeatingTask(plugin, updateTask, 20, hotbarUpdateInterval);
        }

    }

    private void loadAllSkills() {
        //todo fix this shiz
        //  load all skills and profiles here, asynchronously ish. After player profile update it adds it to the list
        Heroes.getInstance().getSkillManager().getSkills().forEach(skill -> {

        });
    }

    private PlayerProfile getProfile(String url) {
        PlayerProfile profile = playerProfiles.get(url);
        if(profile == null) {
            Optional<PlayerProfile> optional = loadProfile(url);
            if(optional.isPresent()) {
                profile = optional.get();
                playerProfiles.put(url, profile);
                profile.update().thenAcceptAsync(p -> playerProfiles.put(url, p), this::runSyncTask);
            }
            else {
                profile = unknownIcon;
            }
        }
        return profile;
    }

    private Optional<PlayerProfile> loadProfile(String url) {
        PlayerProfile profile = plugin.getServer().createPlayerProfile(UUID.nameUUIDFromBytes(url.getBytes(StandardCharsets.UTF_8)), url);
        PlayerTextures texture = profile.getTextures();
        try {
            texture.setSkin(new URL(url));
        }
        catch(MalformedURLException e) {
            plugin.getLogger().log(Level.WARNING, () -> "Url was malformed for icon url " + url);
            return Optional.empty();
        }
        profile.setTextures(texture);
        return Optional.of(profile);
    }

    public void runSyncTask(Runnable runnable) {
        this.scheduler.runTask(plugin, runnable);
    }

    public void setUnknownIcon(PlayerProfile profile) {
        unknownIcon = profile;
    }

    public void clear() {

    }

    public String getMessage(String key) {
        return getMessage(key, "");
    }

    public String getMessage(String key, String defaultValue) {
        return ChatColor.translateAlternateColorCodes('&', plugin.getConfig().getString(key, defaultValue));
    }

    public String getSkillTitle(Player player, SkillDescription skill) {
        String nameTemplate;
        String key = skill.getKey();

        boolean unavailable = !canUseSkill(player, key);
        boolean unprepared = !isPrepared(player, key);
        if (unavailable) {
            nameTemplate = getMessage("skills.item_name_unavailable", "$skill");
        } else if (unprepared) {
            nameTemplate = getMessage("skills.item_name_unprepared", "$skill");
        } else {
            nameTemplate = getMessage("skills.item_name", "$skill");
        }

        return nameTemplate.replace("$skill", skill.getName());
    }

    @Nonnull
    public SkillDescription getSkillDescription(Player player, String skillName) {
        return getActiveSkillSelector(player).getSkill(skillName);
    }

    public boolean isGuiOpen(Player player) {
        SkillSelector selector = this.selectors.get(player.getUniqueId());
        if(selector == null) {
            return false;
        }
        else {
            return selector.isGuiOpen();
        }
    }

    public boolean isElementsEnabled() {
        return elementsEnabled;
    }

    /**
     * Get's a skill item. This updates any needed metadata it may pertain to as well
     * @param skill
     * @param player
     * @return
     */
    public void updateSkillItem(SkillDescription skill, Player player) {
        ItemStack item = skill.getIcon();

        boolean passive = skill.getSkill() instanceof PassiveSkill || skill.getSkill() instanceof OutsourcedSkill;
        if (passive) {
            CompatibilityUtils.setMetaBoolean(item, "passive", true);
        }

        updateSkillItem(item, skill, player);

        CompatibilityUtils.makeUnbreakable(item);
        CompatibilityUtils.hideFlags(item);
        // Set display name
        CompatibilityUtils.setDisplayName(item, getSkillTitle(player, skill));

        // Set lore
        List<String> lore = new ArrayList<>();
        addSkillLore(skill, lore, player);
        CompatibilityUtils.setLore(item, lore);

    }

    public boolean isUseRightClicks() { return useRightClicks; }

    public void updateSkillItem(ItemStack item, SkillDescription skill, Player player) {
        boolean unavailable = !canUseSkill(player, skill.getKey());

        // Set flags and NBT data
        CompatibilityUtils.setMeta(item, skillNBTKey, skill.getKey());

        CompatibilityUtils.setMetaBoolean(item, "unavailable", unavailable);
        skill.setProfileState(item, !unavailable);
    }

    protected Skill getSkill(String key) {
        if (skills == null) return null;
        return skills.getSkill(key);
    }

    private String getTimeDescription(int time) {
        if (time > 0) {
            int timeInSeconds = time / 1000;
            if (timeInSeconds > 60 * 60) {
                int hours = timeInSeconds / (60 * 60);
                if (hours == 1) {
                    return getMessage("cooldown.description_hour");
                }
                return getMessage("cooldown.description_hours").replace("$hours", Integer.toString(hours));
            } else if (timeInSeconds > 60) {
                int minutes = timeInSeconds / 60;
                if (minutes == 1) {
                    return getMessage("cooldown.description_minute");
                }
                return getMessage("cooldown.description_minutes").replace("$minutes", Integer.toString(minutes));
            } else if (timeInSeconds > 1) {
                return getMessage("cooldown.description_seconds").replace("$seconds", Integer.toString(timeInSeconds));
            } else if (timeInSeconds == 1) {
                return getMessage("cooldown.description_second");
            } else {
                String timeDescription = getMessage("cooldown.description_moment");
                if (timeDescription.contains("$seconds")) {
                    timeDescription = timeDescription.replace("$seconds", SECONDS_FORMATTER.format(time / 1000.0D));
                }
                return timeDescription;
            }
        }
        return null;
    }
    public void addSkillLore(SkillDescription skillDescription, List<String> lore, Player player) {
        Hero hero = getHero(player);
        if (hero == null) return;
        Skill skill = skillDescription.getSkill();

        OptionalInt preparedPoints = hero.getSkillPrepareCost(skill);
        if (skill instanceof PassiveSkill) {
            lore.add(getMessage("skills.passive_description", "Passive"));
        } else {
            if (preparedPoints.isPresent()) {
                String costTemplate = getMessage("skills.prepared_lore", "Prepared cost: $points");
                lore.add(costTemplate.replace("$points", Integer.toString(preparedPoints.getAsInt())));
            }
        }

        int level = SkillConfigManager.getUseSetting(hero, skill, SkillSetting.LEVEL, 1, true);

        StringBuilder levelDescription = new StringBuilder();
        levelDescription.append(getMessage("skills.level_description", "").replace("$level", Integer.toString(level)));
        if(elementsEnabled) {
            getElementFromSkill(skill).ifPresent(element -> {
                String e = getMessage("elements.description", "");
                if(e != null && !e.isEmpty()) {
                    levelDescription.append(e.replace("$element", element));
                }
            });
        }
        if (!levelDescription.isEmpty()) {
            lore.add(levelDescription.toString());
        }
        String description = skill.getDescription(hero);
        if (description != null && description.length() > 0) {
            description = getMessage("skills.description", "$description").replace("$description", description);
            CompatibilityUtils.wrapText(description, MAX_LORE_LENGTH, lore);
        }

        int delay = SkillConfigManager.getUseSetting(hero, skill, SkillSetting.DELAY, 0, true);
        if(delay > 0) {
            String delayDescription = getTimeDescription(delay);
            if(delayDescription != null && !delayDescription.isEmpty()) {
                lore.add(getMessage("skills.delay", "$time").replace("$time", delayDescription));
            }
        }

        int cooldown = SkillConfigManager.getUseSetting(hero, skill, SkillSetting.COOLDOWN, 0, true);
        if (cooldown > 0) {
            String cooldownDescription = getTimeDescription(cooldown);
            if (cooldownDescription != null && !cooldownDescription.isEmpty()) {
                lore.add(getMessage("cooldown.description", "$time").replace("$time", cooldownDescription));
            }
        }

        int mana = SkillConfigManager.getUseSetting(hero, skill, SkillSetting.MANA, 0, true);
        if (mana > 0) {
            String manaDescription = getMessage("costs.heroes_mana").replace("$amount", Integer.toString(mana));
            lore.add(getMessage("skills.costs_description").replace("$description", manaDescription));
        }

        int stamina = SkillConfigManager.getUseSetting(hero, skill, SkillSetting.STAMINA, 0, true);
        if (stamina > 0) {
            String staminaDescription = getMessage("costs.heroes_stamina").replace("$amount", Integer.toString(stamina));
            lore.add(getMessage("skills.costs_description").replace("$description", staminaDescription));
        }

        if (preparedPoints.isPresent() && isPrepared(player, skill.getName())) {
            lore.add(getMessage("skills.unprepare_lore"));
        }
    }

    public void updateSkillLore(SkillDescription skillDescription, Player player) {
        List<String> lore = new ArrayList<>();
        addSkillLore(skillDescription, lore, player);
        CompatibilityUtils.setLore(skillDescription.getIcon(), lore);
    }

    public int getSkillLevel(Player player, String skillName) {
        Skill skill = skills.getSkill(skillName);
        if (skill == null) return 0;
        Hero hero = getHero(player);
        if (hero == null) return 0;
        return SkillConfigManager.getUseSetting(hero, skill, SkillSetting.LEVEL, 1, true);
    }

    public Optional<String> getElementFromSkill(Skill skill) {
        return skill.getTypes().stream().filter(elementMap::containsKey).map(elementMap::get).findFirst();
    }

    protected Hero getHero(Player player) {
        if (characters == null) return null;
        return characters.getHero(player);
    }

    public String getClassName(Player player) {
        Hero hero = getHero(player);
        if (hero == null) return "";
        HeroClass heroClass = hero.getHeroClass();
        if (heroClass == null) return "";
        return heroClass.getName();
    }

    public String getSecondaryClassName(Player player) {
        Hero hero = getHero(player);
        if (hero == null) return "";
        HeroClass heroClass = hero.getSecondaryClass();
        if (heroClass == null) return "";
        return heroClass.getName();
    }

    private Multimap<Integer, Skill> mapSkillsByLevel(Hero hero, Collection<String> skillNames) {

        Multimap<Integer, Skill> skillMap = TreeMultimap.create(Ordering.natural(), new Comparator<Skill>() {
            @Override
            public int compare(Skill skill1, Skill skill2) {
                return skill1.getName().compareTo(skill2.getName());
            }
        });
        for (String skillName : skillNames) {
            Skill skill = skills.getSkill(skillName);
            if (skill == null) continue;
            int level = SkillConfigManager.getUseSetting(hero, skill, SkillSetting.LEVEL, 1, true);
            skillMap.put(level, skill);
        }
        return skillMap;
    }

    private void addSkills(Hero hero, HeroClass heroClass, Collection<String> skillSet, boolean showUnuseable, boolean showPassive) {
        if (heroClass != null) {
            Set<String> classSkills = heroClass.getSkillNames();
            for (String classSkill : classSkills) {
                Skill skill = skills.getSkill(classSkill);
                if (!showUnuseable && !hero.canUseSkill(skill)) continue;
                if (!showPassive && !(skill instanceof ActiveSkill)) continue;
                // getRaw's boolean default value is ignored! :(
                if (SkillConfigManager.getRaw(skill, "wand", "true").equalsIgnoreCase("true")) {
                    skillSet.add(classSkill);
                }
            }
        }
    }

    public List<String> getSkillList(Player player, boolean showUnuseable, boolean showPassive) {
        if (skills == null) return emptySkillList;
        Hero hero = getHero(player);
        if (hero == null) return emptySkillList;

        HeroClass heroClass = hero.getHeroClass();
        HeroClass secondClass = hero.getSecondaryClass();
        HeroClass raceClass = hero.getRaceClass();
        Set<String> primarySkills = new HashSet<>();
        Set<String> secondarySkills = new HashSet<>();
        Set<String> raceSkills = new HashSet<>();
        addSkills(hero, heroClass, primarySkills, showUnuseable, showPassive);
        addSkills(hero, secondClass, secondarySkills, showUnuseable, showPassive);
        addSkills(hero, raceClass, raceSkills, showUnuseable, showPassive);
        secondarySkills.removeAll(primarySkills);
        raceSkills.removeAll(primarySkills);

        Multimap<Integer, Skill> primaryMap = mapSkillsByLevel(hero, primarySkills);
        Multimap<Integer, Skill> secondaryMap = mapSkillsByLevel(hero, secondarySkills);
        Multimap<Integer, Skill> raceMap = mapSkillsByLevel(hero, raceSkills);
        List<String> skillNames = new ArrayList<>();
        for (Skill skill : primaryMap.values()) {
            skillNames.add(skill.getName());
        }
        for (Skill skill : secondaryMap.values()) {
            skillNames.add(skill.getName());
        }
        for (Skill skill : raceMap.values()) {
            skillNames.add(skill.getName());
        }
        return skillNames;
    }

    public int getSkillInventoryRows() {
        return skillInventoryRows;
    }

    public boolean canUseSkill(Player player, String skillName) {
        Hero hero = getHero(player);
        if (hero == null) return false;
        return hero.canUseSkill(skillName, true);
    }

    public boolean isPrepared(Player player, String skillName) {
        Hero hero = getHero(player);
        if (hero == null) return false;
        return hero.isSkillPrepared(skillName) || !hero.getSkillPrepareCost(skillName).isPresent();
    }

    /**
     * Gets active selector for player
     * @param player The player to add for
     * @return Skill selector for player
     */
    @Nonnull
    public SkillSelector getActiveSkillSelector(HumanEntity player) {
        SkillSelector selector = selectors.get(player.getUniqueId());
        if(selector == null) {
            selector = addActiveSkillSelector(player);
        }
        return selector;
    }

    @Nullable
    public SkillSelector getActiveSkillSelectorOrNull(HumanEntity player) {
        return selectors.get(player.getUniqueId());
    }

    @Nonnull
    public SkillSelector addActiveSkillSelector(HumanEntity player) {
        SkillSelector selector = new SkillSelector(this, (Player) player);
        selectors.put(player.getUniqueId(), selector);
        return selector;
    }

    public void clearActiveSkillSelector(Player player) {
        selectors.remove(player.getUniqueId());
    }

    public boolean isSkill(ItemStack item) {
        return CompatibilityUtils.hasMeta(item, skillNBTKey);
    }

    public boolean isLegacySkill(ItemStack item) {
        return CompatibilityUtils.hasMeta(item, legacyNBTKey);
    }

    public void useSkill(Player player, String skillKey, ItemStack item) {
        plugin.getServer().dispatchCommand(player, "skill " + skillKey);
    }

    public void removeAllSkillItems(Player player) {
        Inventory inventory = player.getInventory();
        for (int i = 0; i < inventory.getSize(); i++) {
            ItemStack slotItem = inventory.getItem(i);
            String slotKey = getSkillKey(slotItem);
            if (slotKey != null) {
                unprepareSkill(slotKey, player, slotItem);
                inventory.setItem(i, null);
            }
        }
    }

    /**
     * Attempts to unprepare a skill IF the item is a skill item. Does not remove the item
     * @param player
     * @param item The item which may or may not be a skill
     */
    public void unprepareSkill(Player player, ItemStack item) {
        String skillKey = getSkillKey(item);
        if (skillKey != null && !skillKey.isEmpty()) {
            unprepareSkill(skillKey, player, item);
        }
    }

    public void unprepareSkill(String skillKey, Player player, ItemStack item) {
        // Always take all of the items away here, players can use this to
        // "unprepare" skills that don't need preparing just to clean them out of their inventory
        // Only do this if the skill selector is active.
        SkillSelector activeSelector = getActiveSkillSelector(player);

        // Make sure this skill can be unprepared
        Hero hero = getHero(player);
        SkillDescription description = activeSelector.getSkill(skillKey);
        Skill skill = description.getSkill();
        OptionalInt preparedPoints = hero.getSkillPrepareCost(skill);
        if (preparedPoints.isPresent() && hero.isSkillPrepared(skillKey)) {
            // Unprepare it, update item name
            hero.unprepareSkill(skill);
            CompatibilityUtils.setDisplayName(item, getSkillTitle(player, description));

            updateSkillLore(activeSelector.getSkill(skillKey), player);

            // Message the player
            int usedPoints = hero.getUsedSkillPreparePoints();
            int maxPoints = hero.getTotalSkillPreparePoints();
            int maxPrepared = hero.getPreparedSkillLimit();
            int currentPrepared = hero.getPreparedSkillCount();
            int remainingPoints = maxPoints - usedPoints;
            int remainingSlots = maxPrepared - currentPrepared;
            player.sendMessage(getMessage("skills.unprepared")
                    .replace("$skill", skillKey)
                    .replace("$points", Integer.toString(remainingPoints))
                    .replace("$slots", Integer.toString(remainingSlots)));
        }
    }

    public boolean hasSkillItem(Player player, String skillKey) {
        Inventory inventory = player.getInventory();
        for (int i = 0; i < inventory.getSize(); i++) {
            ItemStack slotItem = inventory.getItem(i);
            String slotKey = getSkillKey(slotItem);
            if (slotKey != null && slotKey.equals(skillKey)) {
                return true;
            }
        }
        return false;
    }

    public boolean prepareSkill(Player player, ItemStack item) {
        String skillKey = getSkillKey(item);
        if (skillKey != null && !skillKey.isEmpty()) {
            SkillDescription description = getSkillDescription(player, skillKey);
            Skill skill = description.getSkill();
            Hero hero = getHero(player);
            OptionalInt preparedPoints = hero.getSkillPrepareCost(skill);
            if (preparedPoints.isPresent()) {
                if (!hero.isSkillPrepared(skillKey)) {
                    int usedPoints = hero.getUsedSkillPreparePoints();
                    int maxPoints = hero.getTotalSkillPreparePoints();
                    int maxPrepared = hero.getPreparedSkillLimit();
                    int currentPrepared = hero.getPreparedSkillCount();
                    if (currentPrepared + 1 > maxPrepared || usedPoints + preparedPoints.getAsInt() > maxPoints) {
                        player.sendMessage(getMessage("skills.prepare_limit"));
                        return false;
                    } else {
                        hero.prepareSkill(skillKey);
                        CompatibilityUtils.setDisplayName(item, getSkillTitle(player, description));

                        int remainingPoints = maxPoints - usedPoints - preparedPoints.getAsInt();
                        int remainingSlots = maxPrepared - currentPrepared - 1;
                        player.sendMessage(getMessage("skills.prepared")
                            .replace("$skill", skillKey)
                            .replace("$points", Integer.toString(remainingPoints))
                            .replace("$slots", Integer.toString(remainingSlots)));

                        updateSkillLore(description, player);
                    }
                }
            }
        }
        return true;
    }

    public String getSkillKey(ItemStack item) {
        return CompatibilityUtils.getMetaString(item, skillNBTKey);
    }

    public Logger getLogger() {
        return plugin.getLogger();
    }

    public Plugin getPlugin() {
        return plugin;
    }

    public Server getServer() {
        return plugin.getServer();
    }

    public PlayerProfile getDefaultDisabledIcon() {
        return disabledIcon;
    }
    public PlayerProfile getUnknownIcon() {
        return unknownIcon;
    }

    public void delayedInventoryUpdate(final Player player) {
        plugin.getServer().getScheduler().scheduleSyncDelayedTask(plugin, new Runnable() {
            @Override
            public void run() {
                player.updateInventory();
            }
        }, 1);
    }
}
