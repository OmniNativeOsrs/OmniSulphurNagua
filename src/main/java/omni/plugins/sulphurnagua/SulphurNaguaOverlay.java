/* Copyright (c) 2026, Omni. All rights reserved. */
package omni.plugins.sulphurnagua;

import java.util.List;
import omni.osrs.model.OmniWorldPoint;
import omni.osrs.plugin.OmniOverlayBuilder;
import omni.osrs.plugin.OmniOverlayStyle;

/**
 * Info panel for the Sulphur Nagua bot. State is pushed in by the plugin each tick.
 */
public final class SulphurNaguaOverlay
{
	private static volatile String runtime = "00:00:00";
	private static volatile String state = "IDLE";
	private static volatile String kills = "0";
	private static volatile String xpGained = "0";
	private static volatile String xpPerHour = "0";
	private static volatile List<OmniWorldPoint> arena = List.of();

	private SulphurNaguaOverlay()
	{
	}

	/**
	 * Writes the panel's rows and outlines the fight area.
	 *
	 * @param overlay the client's overlay builder
	 */
	public static void render(OmniOverlayBuilder overlay)
	{
		overlay.line("Runtime", runtime)
			.line("Status", state)
			.line("Kills", kills)
			.line("XP Gained", xpGained)
			.line("XP/Hour", xpPerHour)
			.mainSkill("HITPOINTS");
		for (OmniWorldPoint tile : arena)
		{
			overlay.highlightTile(tile, OmniOverlayStyle.DANGER);
		}
	}

	public static void setRuntime(String value)
	{
		runtime = value;
	}

	public static void setState(String value)
	{
		state = value;
	}

	public static void setKills(String value)
	{
		kills = value;
	}

	public static void setXpGained(String value)
	{
		xpGained = value;
	}

	public static void setXpPerHour(String value)
	{
		xpPerHour = value;
	}

	public static void setArena(List<OmniWorldPoint> value)
	{
		arena = List.copyOf(value);
	}

	/** Clears everything the plugin pushed, when it stops. */
	public static void clear()
	{
		runtime = "00:00:00";
		state = "IDLE";
		kills = "0";
		xpGained = "0";
		xpPerHour = "0";
		arena = List.of();
	}
}
