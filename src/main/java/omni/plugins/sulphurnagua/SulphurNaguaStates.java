/* Copyright (c) 2026, Omni. All rights reserved. */
package omni.plugins.sulphurnagua;

import static omni.plugins.sulphurnagua.SulphurNaguaState.BANKING;
import static omni.plugins.sulphurnagua.SulphurNaguaState.FIGHTING;
import static omni.plugins.sulphurnagua.SulphurNaguaState.GETTING_RUNE_CRAFTING_XP;
import static omni.plugins.sulphurnagua.SulphurNaguaState.IDLE;
import static omni.plugins.sulphurnagua.SulphurNaguaState.LOOTING;
import static omni.plugins.sulphurnagua.SulphurNaguaState.PICKUP;
import static omni.plugins.sulphurnagua.SulphurNaguaState.PREPARATION;
import static omni.plugins.sulphurnagua.SulphurNaguaState.WALKING_TO_BANK;
import static omni.plugins.sulphurnagua.SulphurNaguaState.WALKING_TO_FIGHT;
import static omni.plugins.sulphurnagua.SulphurNaguaState.WALKING_TO_PREP;

/**
 * Decides the state, as a pure function of what the plugin read from the game this tick.
 */
public final class SulphurNaguaStates
{
	/** Inventory slots. */
	public static final int INVENTORY_SLOTS = 28;

	private SulphurNaguaStates()
	{
	}

	/**
	 * Everything the state choice depends on.
	 */
	public static final class Facts
	{
		SulphurNaguaState current = IDLE;
		boolean hasPestle;
		boolean nearBank;
		int potionsToPickup;
		boolean pickupReady;
		boolean inventoryFull;
		boolean inCombatZone;
		/** Not in combat, or the target is gone or dead. */
		boolean available;
		boolean stackableLootNearby;
		boolean lootBlades;
		boolean bladeNearby;
		boolean hasPotions;
		int totalOwnedPotions;
		int targetPotions;
		boolean hasIntermediate;
		boolean changeEssence;
		boolean hasEssence;
		boolean atPrep;
	}

	/**
	 * The potions to keep, capped by the free inventory space.
	 *
	 * @param minimum the configured minimum
	 * @param fixedItems slots taken by the pestle, loot and blades
	 * @return the target potion count
	 */
	public static int targetPotions(int minimum, int fixedItems)
	{
		return Math.min(minimum, INVENTORY_SLOTS - fixedItems);
	}

	/**
	 * Chooses the state for this tick.
	 *
	 * @param f what was read from the game
	 * @return the state to run
	 */
	public static SulphurNaguaState decide(Facts f)
	{
		if (!f.hasPestle)
		{
			return f.nearBank ? BANKING : WALKING_TO_BANK;
		}
		SulphurNaguaState fightState = f.inCombatZone ? FIGHTING : WALKING_TO_FIGHT;
		if (f.potionsToPickup > 0 && f.pickupReady)
		{
			return f.inventoryFull ? fightState : PICKUP;
		}
		if (f.available && !f.inventoryFull && (f.stackableLootNearby || f.lootBlades && f.bladeNearby))
		{
			return LOOTING;
		}
		boolean fighting = f.current == FIGHTING || f.current == WALKING_TO_FIGHT
			|| f.current == IDLE && f.inCombatZone || f.current == LOOTING;
		if (fighting && f.hasPotions)
		{
			return fightState;
		}
		if (f.totalOwnedPotions < f.targetPotions || f.hasIntermediate)
		{
			if (f.changeEssence && f.hasEssence && f.totalOwnedPotions == 0)
			{
				return GETTING_RUNE_CRAFTING_XP;
			}
			return f.atPrep ? PREPARATION : WALKING_TO_PREP;
		}
		return fightState;
	}
}
