/* Copyright (c) 2026, Omni. All rights reserved. */
package omni.plugins.sulphurnagua;

import omni.osrs.model.OmniWorldPoint;
import omni.osrs.model.OmniWorldRect;

/**
 * Where the Naguas are fought: the combat area and the tile where potions are prepared.
 */
public enum SulphurNaguaLocation
{
	CIVITAS_ILLA_FORTIS_WEST("West", new OmniWorldRect(1372, 9553, 8, 14, 0), new OmniWorldPoint(1376, 9712, 0)),
	CIVITAS_ILLA_FORTIS_EAST("East", new OmniWorldRect(1351, 9549, 10, 25, 0), new OmniWorldPoint(1376, 9712, 0));

	private final String label;
	private final OmniWorldRect combatArea;
	private final OmniWorldPoint prepArea;

	SulphurNaguaLocation(String label, OmniWorldRect combatArea, OmniWorldPoint prepArea)
	{
		this.label = label;
		this.combatArea = combatArea;
		this.prepArea = prepArea;
	}

	/** The middle of the combat area, where the bot walks to start fighting. */
	public OmniWorldPoint getFightAreaCenter()
	{
		return new OmniWorldPoint(combatArea.getX() + combatArea.getWidth() / 2,
			combatArea.getY() + combatArea.getHeight() / 2, combatArea.getPlane());
	}

	public String getName()
	{
		return label;
	}

	public OmniWorldRect getCombatArea()
	{
		return combatArea;
	}

	public OmniWorldPoint getPrepArea()
	{
		return prepArea;
	}

	@Override
	public String toString()
	{
		return label;
	}
}
