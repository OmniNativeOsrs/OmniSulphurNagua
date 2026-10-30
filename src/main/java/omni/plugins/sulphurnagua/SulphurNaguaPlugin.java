/* Copyright (c) 2026, Omni. All rights reserved. */
package omni.plugins.sulphurnagua;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import omni.osrs.action.OmniActionResult;
import omni.osrs.api.MakeX;
import omni.osrs.api.OmniContext;
import omni.osrs.api.OmniGroundItem;
import omni.osrs.api.OmniItem;
import omni.osrs.api.OmniLocalPlayer;
import omni.osrs.api.OmniNPC;
import omni.osrs.api.OmniObject;
import omni.osrs.model.OmniBankLocation;
import omni.osrs.model.OmniPrayer;
import omni.osrs.model.OmniSkill;
import omni.osrs.model.OmniWorldPoint;
import omni.osrs.model.OmniWorldRect;
import omni.osrs.plugin.OmniOverlayBuilder;
import omni.osrs.plugin.OmniPlugin;
import omni.osrs.plugin.OmniPluginConfigChange;
import omni.osrs.plugin.OmniPluginRuntime;
import omni.osrs.plugin.OmniTickResult;

/**
 * Sulphur Nagua Fighter: fights Sulphur Naguas in Cam Torum, drinks Moonlight potions, and brews new ones (grubs,
 * paste, vials) when they run out. Banks for a pestle and mortar, loots runes and ore, and can trade essence for
 * Runecrafting experience.
 *
 * <p>{@link #onTick()} runs once per game tick. Waits are pause counters or
 * {@link OmniTickResult#waitUntil}, and a world click is not sent again while the previous one is in flight.
 */
public class SulphurNaguaPlugin implements OmniPlugin
{
	private static final int PESTLE_AND_MORTAR_ID = 233;
	private static final int VIAL_OF_WATER_ID = 227;
	private static final int MOONLIGHT_GRUB_ID = 29078;
	private static final int MOONLIGHT_GRUB_PASTE_ID = 29079;
	private static final int[] MOONLIGHT_POTION_IDS = {29080, 29081, 29082, 29083};
	private static final int SUPPLY_CRATE_ID = 51371;
	private static final int GRUB_SAPLING_ID = 51365;
	private static final int EYTALLALI_ID = 12870;
	private static final OmniWorldPoint EYTALLALI_LOCATION = new OmniWorldPoint(1521, 9577, 0);
	private static final String NAGUA = "Sulphur Nagua";
	private static final String TAKE_SUPPLIES = "Take herblore supplies.";
	/** Radius for ground item searches. */
	private static final int NEARBY = 8;
	private static final int PREP_RADIUS = 10;
	private static final int BANK_RADIUS = 10;
	private static final int WALK_RADIUS = 3;

	private final OmniPluginRuntime runtime;
	private final OmniContext ctx;
	private final SulphurNaguaConfig config;

	private SulphurNaguaState currentState = SulphurNaguaState.IDLE;
	private int totalNaguaKills;
	private long startTotalExp;
	private boolean hasInitialized;
	private OmniWorldPoint dropLocation;
	private int potionsToPickup;
	private boolean pickupReady;
	private boolean processedBatch;
	private int dropsLeft;
	private int pickupPending = -1;
	private int missingPestle;
	private boolean stopped;
	private int pause;
	private Instant startedAt = Instant.now();

	/** The last world click or interface action, so it is not sent again while the game catches up. */
	private CompletableFuture<OmniActionResult> inFlight;

	/**
	 * Starts a session, running at once.
	 *
	 * @param runtime the session's runtime
	 * @param config the config, already filled from the plugin's settings
	 */
	public SulphurNaguaPlugin(OmniPluginRuntime runtime, SulphurNaguaConfig config)
	{
		this.runtime = runtime;
		this.ctx = new OmniContext(runtime.game());
		this.config = config;
		pushOverlay();
	}

	@Override
	public void close()
	{
		currentState = SulphurNaguaState.IDLE;
		SulphurNaguaOverlay.clear();
	}

	@Override
	public void reset()
	{
		// A login, logout or world hop: drop what was read from the old scene and keep running.
		inFlight = null;
		pause = 0;
		dropsLeft = 0;
		pickupPending = -1;
		hasInitialized = false;
	}

	@Override
	public String status()
	{
		return stopped ? "Stopped: no pestle and mortar" : currentState.name();
	}

	@Override
	public boolean onConfigChanged(OmniPluginConfigChange change)
	{
		config.load(change.getCurrent());
		return true;
	}

	@Override
	public void overlay(OmniOverlayBuilder overlay)
	{
		SulphurNaguaOverlay.render(overlay);
	}

	// ==================================================================
	//  Main loop
	// ==================================================================

	@Override
	public OmniTickResult onTick()
	{
		OmniTickResult result = OmniTickResult.next();
		try
		{
			result = serverTick();
		}
		catch (RuntimeException failure)
		{
			runtime.log().warn("Sulphur Nagua tick failed: {}", String.valueOf(failure.getMessage()));
		}
		pushOverlay();
		return result;
	}

	private OmniTickResult serverTick()
	{
		if (!ctx.game.isAutomationReady() || stopped)
		{
			return OmniTickResult.next();
		}
		OmniLocalPlayer player = ctx.localPlayer.current();
		if (player == null || ctx.walker.isWalking() || busy())
		{
			return OmniTickResult.next();
		}
		if (pause > 0)
		{
			pause--;
			return OmniTickResult.next();
		}
		if (!hasInitialized)
		{
			startTotalExp = totalExperience();
			if (startTotalExp > 0)
			{
				hasInitialized = true;
				startedAt = Instant.now();
			}
			return OmniTickResult.next();
		}
		if (!ctx.movement.isRunEnabled() && ctx.movement.runEnergy() >= 1000)
		{
			send(ctx.movement.setRunEnabled(true));
			return OmniTickResult.next();
		}

		determineState(player);
		switch (currentState)
		{
			case BANKING:
				return handleBanking();
			case WALKING_TO_BANK:
				walkToBank();
				return OmniTickResult.next();
			case WALKING_TO_PREP:
				ctx.walker.walkAsync(config.naguaLocation().getPrepArea(), WALK_RADIUS);
				return OmniTickResult.next();
			case PREPARATION:
				return handlePreparation(player);
			case PICKUP:
				return pickupDroppedPotions();
			case WALKING_TO_FIGHT:
				ctx.walker.walkAsync(config.naguaLocation().getFightAreaCenter(), WALK_RADIUS);
				return OmniTickResult.next();
			case FIGHTING:
				return handleFighting(player);
			case LOOTING:
				return handleLooting();
			case GETTING_RUNE_CRAFTING_XP:
				return handleGettingRunecraftingXp(player);
			default:
				return OmniTickResult.next();
		}
	}

	// ==================================================================
	//  State choice
	// ==================================================================

	private void determineState(OmniLocalPlayer player)
	{
		Set<Integer> lootIds = config.lootIds();
		SulphurNaguaStates.Facts facts = new SulphurNaguaStates.Facts();
		facts.current = currentState;
		facts.hasPestle = ctx.inventory.contains(PESTLE_AND_MORTAR_ID);
		OmniWorldPoint here = player.getLocation();
		facts.nearBank = ctx.banks.isNearUnlocked(here, BANK_RADIUS);
		facts.inCombatZone = config.naguaLocation().getCombatArea().contains(here);
		facts.inventoryFull = ctx.inventory.isFull();
		facts.hasIntermediate = hasIngredientsToProcess();

		// A batch that was ground and mixed is done once nothing is left to process: the dropped potions may be taken.
		if (processedBatch && !facts.hasIntermediate)
		{
			processedBatch = false;
			if (potionsToPickup > 0)
			{
				pickupReady = true;
			}
		}
		if (!facts.hasPestle)
		{
			resetPreparationState();
		}
		facts.potionsToPickup = potionsToPickup;
		facts.pickupReady = pickupReady;

		OmniNPC target = interactingNpc(player);
		facts.available = !player.isInteracting() || target == null || target.isDead();
		facts.stackableLootNearby = !lootIds.isEmpty() && ctx.groundItems.query()
			.withId(lootIds.stream().mapToInt(Integer::intValue).toArray()).withinTiles(NEARBY).exists();
		facts.lootBlades = config.lootSulphurousBlades();
		facts.bladeNearby = ctx.groundItems.query().withId(SulphurNaguaConfig.SULPHUR_BLADE_ID)
			.withinTiles(NEARBY).exists();

		int potions = countMoonlightPotions();
		facts.hasPotions = potions > 0;
		facts.totalOwnedPotions = potions + potionsToPickup;
		facts.targetPotions = SulphurNaguaStates.targetPotions(config.moonlightPotionsMinimum(),
			countFixedItems(lootIds));
		facts.changeEssence = config.changeInSulphurousEssence();
		facts.hasEssence = ctx.inventory.contains(SulphurNaguaConfig.SULPHUROUS_ESSENCE_ID);
		facts.atPrep = config.naguaLocation().getPrepArea().distanceTo(here) < PREP_RADIUS;

		SulphurNaguaState next = SulphurNaguaStates.decide(facts);
		boolean fightNext = next == SulphurNaguaState.FIGHTING || next == SulphurNaguaState.WALKING_TO_FIGHT;
		if (facts.hasPestle && facts.potionsToPickup > 0 && facts.pickupReady && facts.inventoryFull && fightNext)
		{
			runtime.log().info("Inventory is full, cannot pick up remaining potions. Starting to fight.");
			resetPreparationState();
		}
		boolean prepNext = next == SulphurNaguaState.PREPARATION || next == SulphurNaguaState.WALKING_TO_PREP
			|| next == SulphurNaguaState.GETTING_RUNE_CRAFTING_XP;
		if (currentState == SulphurNaguaState.FIGHTING && prepNext)
		{
			runtime.log().info("All potions used. Starting preparation for a new batch.");
			disablePrayers();
		}
		currentState = next;
	}

	private void disablePrayers()
	{
		Set<OmniPrayer> active = ctx.prayers.active();
		if (!active.isEmpty())
		{
			send(ctx.prayers.deactivateAll(active.toArray(new OmniPrayer[0])));
		}
	}

	// ==================================================================
	//  Banking
	// ==================================================================

	private void walkToBank()
	{
		Optional<OmniBankLocation> bank = ctx.banks.nearest();
		if (bank.isPresent())
		{
			ctx.walker.walkAsync(bank.get().getPoint(), WALK_RADIUS);
		}
	}

	private OmniTickResult handleBanking()
	{
		if (!ctx.bank.isOpen())
		{
			send(ctx.bank.open());
			return OmniTickResult.waitUntil(ctx.bank::isOpen, 8);
		}
		boolean holdsOther = ctx.inventory.query().where(item -> item.getId() != PESTLE_AND_MORTAR_ID).exists();
		if (holdsOther)
		{
			send(ctx.bank.depositInventory());
			return OmniTickResult.next();
		}
		if (!ctx.inventory.contains(PESTLE_AND_MORTAR_ID))
		{
			if (ctx.bank.contains(PESTLE_AND_MORTAR_ID))
			{
				missingPestle = 0;
				send(ctx.bank.withdraw(PESTLE_AND_MORTAR_ID, 1));
				return OmniTickResult.waitUntil(() -> ctx.inventory.contains(PESTLE_AND_MORTAR_ID), 4);
			}
			if (++missingPestle > 3)
			{
				runtime.log().warn("No pestle and mortar in the bank: stopping");
				stopped = true;
			}
			return OmniTickResult.next();
		}
		send(ctx.bank.close());
		resetPreparationState();
		return OmniTickResult.waitUntil(() -> !ctx.bank.isOpen(), 4);
	}

	// ==================================================================
	//  Preparation
	// ==================================================================

	private OmniTickResult handlePreparation(OmniLocalPlayer player)
	{
		if (dropsLeft > 0)
		{
			dropOnePotion();
			return OmniTickResult.next();
		}
		if (hasIngredientsToProcess())
		{
			return processAllIngredients(player);
		}
		int target = SulphurNaguaStates.targetPotions(config.moonlightPotionsMinimum(),
			countFixedItems(config.lootIds()));
		int currentPotions = countMoonlightPotions();
		int totalOwned = currentPotions + potionsToPickup;
		if (totalOwned >= target)
		{
			if (cleanupLeftoverIngredients() && potionsToPickup > 0)
			{
				pickupReady = true;
			}
			return OmniTickResult.next();
		}
		int needed = target - totalOwned;
		int freeSlots = ctx.inventory.emptySlots();
		int vials = (int) ctx.inventory.count(VIAL_OF_WATER_ID);
		int grubs = (int) ctx.inventory.count(MOONLIGHT_GRUB_ID);
		if (vials > 0)
		{
			if (freeSlots >= vials)
			{
				return getSupplies(MOONLIGHT_GRUB_ID, grubs + vials);
			}
			if (freeSlots > 0)
			{
				return getSupplies(MOONLIGHT_GRUB_ID, grubs + freeSlots);
			}
			int toDrop = Math.min(vials, currentPotions);
			if (toDrop <= 0)
			{
				runtime.log().info("Stuck: 0 free slots, 0 potions to drop, but need grubs.");
				return OmniTickResult.next();
			}
			runtime.log().info("Dropping {} potions to make space for grubs.", toDrop);
			dropPotions(toDrop);
			return OmniTickResult.next();
		}
		if (freeSlots > 0)
		{
			int reservedForVials = Math.max(1, freeSlots / 2);
			return getSupplies(VIAL_OF_WATER_ID, Math.min(needed, reservedForVials));
		}
		int toDrop = Math.min(needed, currentPotions);
		if (toDrop <= 0)
		{
			runtime.log().info("Stuck: 0 free slots, 0 vials, 0 potions to drop, but need more potions.");
			return OmniTickResult.next();
		}
		runtime.log().info("Dropping {} potions to make space for vials.", toDrop);
		dropPotions(toDrop);
		return OmniTickResult.next();
	}

	private OmniTickResult processAllIngredients(OmniLocalPlayer player)
	{
		if (ctx.makeX.isOpen())
		{
			List<MakeX.Product> products = ctx.makeX.products();
			if (!products.isEmpty())
			{
				MakeX.Product product = products.get(0);
				send(ctx.makeX.selectQuantity(MakeX.Quantity.ALL).thenCompose(selected -> ctx.makeX.make(product)));
			}
			return OmniTickResult.next();
		}
		if (player.isAnimating())
		{
			return OmniTickResult.next();
		}
		OmniItem pestle = ctx.inventory.query().withId(PESTLE_AND_MORTAR_ID).first();
		OmniItem grub = ctx.inventory.query().withId(MOONLIGHT_GRUB_ID).first();
		if (pestle != null && grub != null)
		{
			runtime.log().info("Grinding all available grubs...");
			send(pestle.useOn(grub));
			processedBatch = true;
			return OmniTickResult.waitUntil(() -> !ctx.inventory.contains(MOONLIGHT_GRUB_ID) || ctx.makeX.isOpen()
				|| ctx.dialogue.isOpen(), 30, 1);
		}
		OmniItem paste = ctx.inventory.query().withId(MOONLIGHT_GRUB_PASTE_ID).first();
		OmniItem vial = ctx.inventory.query().withId(VIAL_OF_WATER_ID).first();
		if (paste != null && vial != null)
		{
			runtime.log().info("Mixing all available paste...");
			send(paste.useOn(vial));
			processedBatch = true;
			return OmniTickResult.waitUntil(() -> !ctx.inventory.contains(MOONLIGHT_GRUB_PASTE_ID)
				|| ctx.makeX.isOpen() || ctx.dialogue.isOpen(), 30, 1);
		}
		return OmniTickResult.next();
	}

	private OmniTickResult getSupplies(int itemId, int requiredAmount)
	{
		if (ctx.inventory.count(itemId) >= requiredAmount)
		{
			return OmniTickResult.next();
		}
		if (itemId == VIAL_OF_WATER_ID)
		{
			if (ctx.inventory.isFull())
			{
				return OmniTickResult.next();
			}
			Optional<String> option = ctx.dialogue.findOption(TAKE_SUPPLIES);
			if (option.isPresent())
			{
				send(ctx.dialogue.select(option.get()));
			}
			else if (!isAnimating())
			{
				OmniObject crate = ctx.objects.query().withId(SUPPLY_CRATE_ID).nearest();
				if (crate != null)
				{
					send(crate.interact("Take-from"));
				}
			}
			return OmniTickResult.next();
		}
		if (isAnimating())
		{
			return OmniTickResult.next();
		}
		OmniObject sapling = ctx.objects.query().withId(GRUB_SAPLING_ID).nearest();
		if (sapling != null)
		{
			send(sapling.interact("Collect-from"));
			// The next states' clicks stop the collecting.
			return OmniTickResult.waitUntil(() -> ctx.inventory.count(itemId) >= requiredAmount
				|| ctx.inventory.isFull(), 25);
		}
		return OmniTickResult.next();
	}

	private void dropPotions(int count)
	{
		if (count <= 0)
		{
			return;
		}
		if (dropLocation == null)
		{
			OmniLocalPlayer player = ctx.localPlayer.current();
			dropLocation = player == null ? null : player.getLocation();
		}
		potionsToPickup = count;
		pickupReady = false;
		processedBatch = false;
		dropsLeft = count;
		runtime.log().info("Dropping {} potions at {}", count, dropLocation);
	}

	/** Drops one Moonlight potion per tick. */
	private void dropOnePotion()
	{
		dropsLeft--;
		OmniItem potion = ctx.inventory.query().withId(MOONLIGHT_POTION_IDS).first();
		if (potion == null)
		{
			dropsLeft = 0;
			return;
		}
		send(potion.drop());
	}

	private OmniTickResult pickupDroppedPotions()
	{
		if (pickupPending >= 0 && countMoonlightPotions() > pickupPending)
		{
			potionsToPickup--;
		}
		pickupPending = -1;
		if (ctx.inventory.isFull())
		{
			runtime.log().info("Inventory is full, cannot pick up.");
			return OmniTickResult.next();
		}
		if (potionsToPickup <= 0)
		{
			resetPreparationState();
			return OmniTickResult.next();
		}
		OmniGroundItem potion = ctx.groundItems.query().withId(MOONLIGHT_POTION_IDS).withinTiles(NEARBY).first();
		if (potion == null)
		{
			runtime.log().info("Could not find any more dropped potions. Resetting.");
			resetPreparationState();
			return OmniTickResult.next();
		}
		int before = countMoonlightPotions();
		pickupPending = before;
		send(potion.take());
		return OmniTickResult.waitUntil(() -> countMoonlightPotions() > before, 5);
	}

	/** Drops one kind of leftover ingredient per tick; true once none is left. */
	private boolean cleanupLeftoverIngredients()
	{
		OmniItem leftover = ctx.inventory.query()
			.withId(VIAL_OF_WATER_ID, MOONLIGHT_GRUB_PASTE_ID, MOONLIGHT_GRUB_ID).first();
		if (leftover == null)
		{
			return true;
		}
		runtime.log().info("Cleaning up leftover ingredients...");
		send(leftover.drop());
		return false;
	}

	private void resetPreparationState()
	{
		dropLocation = null;
		potionsToPickup = 0;
		pickupReady = false;
		processedBatch = false;
		dropsLeft = 0;
		pickupPending = -1;
	}

	// ==================================================================
	//  Fighting, looting, essence
	// ==================================================================

	private OmniTickResult handleFighting(OmniLocalPlayer player)
	{
		int basePrayerLevel = ctx.skills.level(OmniSkill.PRAYER);
		int herbloreLevel = ctx.skills.boostedLevel(OmniSkill.HERBLORE);
		int prayerBasedRestore = (int) Math.floor(basePrayerLevel * 0.25) + 7;
		int herbloreBasedRestore = (int) Math.floor(herbloreLevel * 0.3) + 7;
		int dynamicThreshold = Math.max(prayerBasedRestore, herbloreBasedRestore);
		if (ctx.prayers.points() <= dynamicThreshold && ctx.inventory.potionDelayTicks() == 0)
		{
			OmniItem potion = ctx.inventory.query().withId(MOONLIGHT_POTION_IDS).first();
			if (potion != null)
			{
				send(potion.drink());
				return OmniTickResult.next();
			}
		}
		if (ctx.prayers.points() > 0 && !ctx.prayers.isActive(OmniPrayer.PROTECT_FROM_MELEE))
		{
			send(ctx.prayers.setActive(OmniPrayer.PROTECT_FROM_MELEE, true));
			return OmniTickResult.next();
		}
		if (config.useOffensivePrayers() && ctx.prayers.points() > 0)
		{
			OmniPrayer best = bestMeleePrayer();
			if (best != null && !ctx.prayers.isActive(best))
			{
				send(ctx.prayers.setActive(best, true));
				return OmniTickResult.next();
			}
		}
		if (player.isInteracting())
		{
			return OmniTickResult.next();
		}
		OmniWorldRect area = config.naguaLocation().getCombatArea();
		if (!area.contains(player.getLocation()))
		{
			runtime.log().info("Outside combat zone, walking back to center...");
			ctx.walker.walkAsync(config.naguaLocation().getFightAreaCenter(), WALK_RADIUS);
			return OmniTickResult.next();
		}
		OmniNPC attacking = ctx.npcs.query().withName(NAGUA).where(npc -> !npc.isDead())
			.where(npc -> npc.isInteractingWith(player)).nearest();
		OmniNPC victim = attacking != null ? attacking : ctx.npcs.query().withName(NAGUA)
			.where(npc -> !npc.isDead()).where(npc -> area.contains(npc.getLocation()))
			.where(npc -> !npc.isInteracting() || npc.isInteractingWith(player)).nearest();
		if (victim == null)
		{
			runtime.log().info("Waiting for Sulphur Nagua in the zone...");
			return OmniTickResult.next();
		}
		if (attacking != null)
		{
			runtime.log().info("Attacking the Nagua that is attacking us!");
		}
		send(victim.attack());
		totalNaguaKills++;
		return OmniTickResult.waitUntil(() ->
		{
			OmniLocalPlayer self = ctx.localPlayer.current();
			return self != null && self.isInteracting();
		}, 5);
	}

	/** Piety, else Chivalry, else Ultimate Strength: the best melee prayer the account can use. */
	private OmniPrayer bestMeleePrayer()
	{
		for (OmniPrayer prayer : new OmniPrayer[] {OmniPrayer.PIETY, OmniPrayer.CHIVALRY,
			OmniPrayer.ULTIMATE_STRENGTH})
		{
			if (ctx.prayers.isUnlocked(prayer))
			{
				return prayer;
			}
		}
		return null;
	}

	private OmniTickResult handleLooting()
	{
		OmniGroundItem blade = ctx.groundItems.query().withId(SulphurNaguaConfig.SULPHUR_BLADE_ID)
			.withinTiles(NEARBY).first();
		if (blade != null)
		{
			long before = ctx.inventory.count(SulphurNaguaConfig.SULPHUR_BLADE_ID);
			send(blade.take());
			return OmniTickResult.waitUntil(
				() -> ctx.inventory.count(SulphurNaguaConfig.SULPHUR_BLADE_ID) > before, 5);
		}
		for (int itemId : config.lootIds())
		{
			OmniGroundItem item = ctx.groundItems.query().withId(itemId).withinTiles(NEARBY).first();
			if (item != null)
			{
				long before = ctx.inventory.count(itemId);
				send(item.take());
				return OmniTickResult.waitUntil(() -> ctx.inventory.count(itemId) > before, 5);
			}
		}
		return OmniTickResult.next();
	}

	private OmniTickResult handleGettingRunecraftingXp(OmniLocalPlayer player)
	{
		if (ctx.dialogue.canContinue())
		{
			send(ctx.dialogue.clickContinue());
			return OmniTickResult.next();
		}
		if (!ctx.inventory.contains(SulphurNaguaConfig.SULPHUROUS_ESSENCE_ID))
		{
			runtime.log().info("No essence to exchange. Returning to preparation.");
			return OmniTickResult.next();
		}
		if (player.getLocation().distanceTo(EYTALLALI_LOCATION) > 5)
		{
			ctx.walker.walkAsync(EYTALLALI_LOCATION, 2);
			return OmniTickResult.next();
		}
		OmniNPC eytallali = ctx.npcs.query().withId(EYTALLALI_ID).nearest();
		if (eytallali == null)
		{
			runtime.log().info("Waiting for Eytallali to appear...");
			return OmniTickResult.next();
		}
		if (player.isAnimating())
		{
			return OmniTickResult.next();
		}
		OmniItem essence = ctx.inventory.query().withId(SulphurNaguaConfig.SULPHUROUS_ESSENCE_ID).first();
		if (essence != null)
		{
			runtime.log().info("Exchanging essence...");
			send(essence.useOn(eytallali));
			return OmniTickResult.waitUntil(ctx.dialogue::canContinue, 8);
		}
		return OmniTickResult.next();
	}

	// ==================================================================
	//  Helpers
	// ==================================================================

	private boolean isAnimating()
	{
		OmniLocalPlayer player = ctx.localPlayer.current();
		return player != null && player.isAnimating();
	}

	private OmniNPC interactingNpc(OmniLocalPlayer player)
	{
		int index = player.getInteractingNpcIndex();
		if (index < 0)
		{
			return null;
		}
		return ctx.npcs.query().where(npc -> npc.getIndex() == index).first();
	}

	private int countMoonlightPotions()
	{
		return (int) ctx.inventory.count(MOONLIGHT_POTION_IDS);
	}

	private boolean hasIngredientsToProcess()
	{
		return ctx.inventory.contains(MOONLIGHT_GRUB_ID)
			|| ctx.inventory.contains(MOONLIGHT_GRUB_PASTE_ID) && ctx.inventory.contains(VIAL_OF_WATER_ID);
	}

	/** Slots taken by the pestle, each lootable kind held and the blades. */
	private int countFixedItems(Set<Integer> lootIds)
	{
		int fixed = 0;
		if (ctx.inventory.contains(PESTLE_AND_MORTAR_ID))
		{
			fixed++;
		}
		for (int lootId : lootIds)
		{
			if (ctx.inventory.contains(lootId))
			{
				fixed++;
			}
		}
		return fixed + (int) ctx.inventory.count(SulphurNaguaConfig.SULPHUR_BLADE_ID);
	}

	private long totalExperience()
	{
		long total = 0;
		for (OmniSkill skill : OmniSkill.values())
		{
			total += ctx.skills.experience(skill);
		}
		return total;
	}

	private boolean busy()
	{
		return inFlight != null && !inFlight.isDone();
	}

	private void send(CompletableFuture<OmniActionResult> action)
	{
		inFlight = action;
	}

	private void pushOverlay()
	{
		long seconds = Math.max(0, Duration.between(startedAt, Instant.now()).getSeconds());
		long gained = hasInitialized ? Math.max(0, totalExperience() - startTotalExp) : 0;
		SulphurNaguaOverlay.setRuntime(String.format("%02d:%02d:%02d", seconds / 3600, seconds / 60 % 60,
			seconds % 60));
		SulphurNaguaOverlay.setState(currentState.name());
		SulphurNaguaOverlay.setKills(String.valueOf(totalNaguaKills));
		SulphurNaguaOverlay.setXpGained(String.format("%,d", gained));
		SulphurNaguaOverlay.setXpPerHour(String.format("%,d", seconds <= 0 ? 0 : gained * 3600 / seconds));
		List<OmniWorldPoint> border = new ArrayList<>(config.naguaLocation().getCombatArea().border());
		SulphurNaguaOverlay.setArena(border);
	}
}
