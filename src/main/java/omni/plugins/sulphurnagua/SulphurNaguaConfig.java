/* Copyright (c) 2026, Omni. All rights reserved. */
package omni.plugins.sulphurnagua;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import omni.osrs.plugin.OmniPluginSetting;
import omni.osrs.plugin.OmniPluginValues;

/**
 * The plugin's settings, read by the logic through the accessors ({@code config.x()}).
 */
public class SulphurNaguaConfig
{
	private static final String LOOT = "Loot Settings";

	/** Sulphur blades. */
	public static final int SULPHUR_BLADE_ID = 29084;
	/** Sulphurous essence. */
	public static final int SULPHUROUS_ESSENCE_ID = 29087;

	/** A lootable stackable: its setting key, label, item id and starting value. */
	private enum Loot
	{
		FIRE_RUNES("lootFireRunes", "Loot Fire runes", "Loot Fire runes (554)", 554),
		CHAOS_RUNES("lootChaosRunes", "Loot Chaos runes", "Loot Chaos runes (562)", 562),
		NATURE_RUNES("lootNatureRunes", "Loot Nature runes", "Loot Nature runes (561)", 561),
		DEATH_RUNES("lootDeathRunes", "Loot Death runes", "Loot Death runes (560)", 560),
		IRON_ORE("lootIronOre", "Loot Iron ore (noted)", "Loot noted Iron ore (441)", 441),
		COAL("lootCoal", "Loot Coal (noted)", "Loot noted Coal (454)", 454),
		COPPER_ORE("lootCopperOre", "Loot Copper ore (noted)", "Loot noted Copper ore (437)", 437),
		TIN_ORE("lootTinOre", "Loot Tin ore (noted)", "Loot noted Tin ore (439)", 439),
		MITHRIL_ORE("lootMithrilOre", "Loot Mithril ore (noted)", "Loot noted Mithril ore (448)", 448),
		SILVER_ORE("lootSilverOre", "Loot Silver ore (noted)", "Loot noted Silver ore (443)", 443),
		SULPHUROUS_ESSENCE("lootSulphurousEssence", "Loot SulphurousEssence", "Loot Sulphurous Essence (29087)",
			SULPHUROUS_ESSENCE_ID);

		private final String key;
		private final String label;
		private final String description;
		private final int itemId;

		Loot(String key, String label, String description, int itemId)
		{
			this.key = key;
			this.label = label;
			this.description = description;
			this.itemId = itemId;
		}
	}

	private int moonlightPotionsMinimum = 2;
	private boolean useOffensivePrayers;
	private SulphurNaguaLocation naguaLocation = SulphurNaguaLocation.CIVITAS_ILLA_FORTIS_WEST;
	private boolean changeInSulphurousEssence;
	private boolean lootSulphurousBlades = true;
	private final Set<Integer> lootIds = new LinkedHashSet<>();

	/** A config with every loot toggle at its default (on). */
	public SulphurNaguaConfig()
	{
		for (Loot loot : Loot.values())
		{
			lootIds.add(loot.itemId);
		}
	}

	/**
	 * The settings the client shows, one per config field.
	 *
	 * @return the settings
	 */
	public static List<OmniPluginSetting> settings()
	{
		List<OmniPluginSetting> settings = new ArrayList<>();
		settings.add(OmniPluginSetting.info("guide", "Guide",
			"Fights Sulphur Nagua in Cam Torum, Varlamore. Needs Perilous Moons, 43 Prayer, 38 Herblore and a "
				+ "Pestle and mortar. Drinks Moonlight potions and brews new ones when supplies run out."));
		settings.add(OmniPluginSetting.number("moonlightPotionsMinimum", "Minimum Moonlight Potions",
			"If the number of potions falls below this value, new ones will be made.", 2, 1, 28));
		settings.add(OmniPluginSetting.toggle("useOffensivePrayers", "Use Offensive Prayers?",
			"When enabled, the bot will use the best offensive melee prayer.", false));
		settings.add(OmniPluginSetting.choice("naguaLocation", "Nagua Location",
			"Select the Nagua fighting location.", SulphurNaguaLocation.class,
			SulphurNaguaLocation.CIVITAS_ILLA_FORTIS_WEST));
		settings.add(OmniPluginSetting.toggle("changeInSulphurousEssence", "Change Sulphurous Essence?",
			"When enabled, the bot will change in your Sulphurous Essence for Runecrafting xp.", false));
		for (Loot loot : Loot.values())
		{
			settings.add(OmniPluginSetting.toggle(loot.key, loot.label, loot.description, true).inGroup(LOOT));
		}
		settings.add(OmniPluginSetting.toggle("lootSulphurBlades", "Loot Sulphur Blades",
			"Loot Sulphur Blades (29084)", true).inGroup(LOOT));
		return settings;
	}

	/**
	 * A config filled from the plugin's settings.
	 *
	 * @param values the settings
	 * @return the config
	 */
	public static SulphurNaguaConfig from(OmniPluginValues values)
	{
		SulphurNaguaConfig config = new SulphurNaguaConfig();
		config.load(values);
		return config;
	}

	/**
	 * Reads every setting present in {@code values} into this config (a live settings change).
	 *
	 * @param values the settings
	 */
	public void load(OmniPluginValues values)
	{
		if (values.contains("moonlightPotionsMinimum"))
		{
			moonlightPotionsMinimum = values.getInt("moonlightPotionsMinimum");
		}
		if (values.contains("useOffensivePrayers"))
		{
			useOffensivePrayers = values.getBoolean("useOffensivePrayers");
		}
		if (values.contains("naguaLocation"))
		{
			naguaLocation = values.getEnum("naguaLocation", SulphurNaguaLocation.class);
		}
		if (values.contains("changeInSulphurousEssence"))
		{
			changeInSulphurousEssence = values.getBoolean("changeInSulphurousEssence");
		}
		if (values.contains("lootSulphurBlades"))
		{
			lootSulphurousBlades = values.getBoolean("lootSulphurBlades");
		}
		for (Loot loot : Loot.values())
		{
			if (values.contains(loot.key))
			{
				if (values.getBoolean(loot.key))
				{
					lootIds.add(loot.itemId);
				}
				else
				{
					lootIds.remove(loot.itemId);
				}
			}
		}
	}

	public int moonlightPotionsMinimum()
	{
		return Math.max(1, Math.min(28, moonlightPotionsMinimum));
	}

	public boolean useOffensivePrayers()
	{
		return useOffensivePrayers;
	}

	public SulphurNaguaLocation naguaLocation()
	{
		return naguaLocation;
	}

	public boolean changeInSulphurousEssence()
	{
		return changeInSulphurousEssence;
	}

	public boolean lootSulphurousBlades()
	{
		return lootSulphurousBlades;
	}

	/** The item ids of the stackables to loot. */
	public Set<Integer> lootIds()
	{
		return Set.copyOf(lootIds);
	}
}
