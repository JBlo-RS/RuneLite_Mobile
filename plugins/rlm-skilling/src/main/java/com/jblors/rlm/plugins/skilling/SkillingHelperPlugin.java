package com.jblors.rlm.plugins.skilling;

import com.google.inject.Provides;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import javax.inject.Inject;
import net.runelite.api.Client;
import net.runelite.api.GameObject;
import net.runelite.api.GameState;
import net.runelite.api.Item;
import net.runelite.api.ItemContainer;
import net.runelite.api.MenuAction;
import net.runelite.api.MenuEntry;
import net.runelite.api.NPC;
import net.runelite.api.NPCComposition;
import net.runelite.api.ObjectComposition;
import net.runelite.api.Player;
import net.runelite.api.Scene;
import net.runelite.api.Tile;
import net.runelite.api.TileObject;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.MenuOptionClicked;
import net.runelite.api.gameval.InventoryID;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.overlay.OverlayManager;
import net.runelite.client.util.Text;

/**
 * Highlights what to click next while skilling: the nearest tree, rock or fishing spot of the
 * kind you last used (when you're idle), or the nearest bank when your inventory is full.
 * Only highlights; every click is yours.
 */
@PluginDescriptor(
	name = "Skilling Helper",
	description = "Outlines the next tree, rock or fishing spot of the kind you're using, or the bank when your inventory is full",
	tags = {"skilling", "woodcutting", "mining", "fishing", "next", "highlight", "bank", "rlm"}
)
public class SkillingHelperPlugin extends Plugin
{
	/** Menu options that start each skill */
	private static final Set<String> CHOP = set("chop down", "chop", "cut");
	private static final Set<String> MINE = set("mine");
	private static final Set<String> FISH = set("net", "small net", "big net", "bait", "lure", "cage", "harpoon", "fish", "use-rod");

	enum Kind
	{
		OBJECT, NPC
	}

	@Inject
	private Client client;

	@Inject
	private SkillingHelperConfig config;

	@Inject
	private OverlayManager overlayManager;

	@Inject
	private SkillingHelperOverlay overlay;

	// What the player is skilling on: object IDs (a tree or rock kind) or an NPC ID (fishing spot)
	private Kind kind;
	private int targetId = -1;
	private String targetOption;
	private int idleTicks;
	/** For trees: the kind's name (several IDs share it; stumps are named differently) */
	private String targetName;

	/** Per object ID: name and actions (impostors resolved live, since they can change) */
	private final java.util.Map<Integer, ObjectComposition> defs = new java.util.HashMap<>();
	private final java.util.Map<Integer, Boolean> bankIds = new java.util.HashMap<>();

	// Results for the overlay (set on the client thread each tick)
	private volatile TileObject nextObject;
	private volatile NPC nextNpc;
	private volatile boolean nextIsBank;

	@Override
	protected void startUp()
	{
		overlayManager.add(overlay);
	}

	@Override
	protected void shutDown()
	{
		overlayManager.remove(overlay);
		clear();
		kind = null;
	}

	TileObject getNextObject()
	{
		return nextObject;
	}

	NPC getNextNpc()
	{
		return nextNpc;
	}

	boolean isNextBank()
	{
		return nextIsBank;
	}

	private void clear()
	{
		nextObject = null;
		nextNpc = null;
		nextIsBank = false;
	}

	@Subscribe
	public void onMenuOptionClicked(MenuOptionClicked e)
	{
		MenuEntry m = e.getMenuEntry();
		String option = Text.removeTags(m.getOption()).trim().toLowerCase();
		MenuAction type = m.getType();
		if (m.getNpc() != null && FISH.contains(option))
		{
			kind = Kind.NPC;
			targetId = m.getNpc().getId();
			targetOption = option;
		}
		else if (isObjectAction(type) && (CHOP.contains(option) || MINE.contains(option)))
		{
			kind = Kind.OBJECT;
			targetId = m.getIdentifier();
			targetOption = option;
			ObjectComposition c = def(targetId);
			targetName = CHOP.contains(option) && c != null ? c.getName() : null;
		}
	}

	private static boolean isObjectAction(MenuAction t)
	{
		return t == MenuAction.GAME_OBJECT_FIRST_OPTION || t == MenuAction.GAME_OBJECT_SECOND_OPTION
			|| t == MenuAction.GAME_OBJECT_THIRD_OPTION || t == MenuAction.GAME_OBJECT_FOURTH_OPTION
			|| t == MenuAction.GAME_OBJECT_FIFTH_OPTION;
	}

	@Subscribe
	public void onGameStateChanged(GameStateChanged e)
	{
		if (e.getGameState() != GameState.LOGGED_IN)
		{
			clear();
		}
	}

	@Subscribe
	public void onGameTick(GameTick e)
	{
		clear();
		Player me = client.getLocalPlayer();
		if (kind == null || me == null)
		{
			return;
		}
		boolean idle = me.getAnimation() == -1 && me.getPoseAnimation() == me.getIdlePoseAnimation();
		idleTicks = idle ? idleTicks + 1 : 0;
		if (config.onlyIdle() && idleTicks < 1)
		{
			return;
		}
		WorldPoint at = me.getWorldLocation();

		if (config.bankWhenFull() && inventoryFull())
		{
			nextIsBank = true;
			nextObject = nearestBankObject(at);
			nextNpc = nearestBanker(at);
			if (nextObject != null && nextNpc != null)
			{
				// keep whichever is closer
				if (nextNpc.getWorldLocation().distanceTo(at) < nextObject.getWorldLocation().distanceTo(at))
				{
					nextObject = null;
				}
				else
				{
					nextNpc = null;
				}
			}
			return;
		}

		if (kind == Kind.NPC)
		{
			nextNpc = nearestNpc(at, targetId);
		}
		else
		{
			nextObject = nearestObject(at, targetId);
		}
	}

	private boolean inventoryFull()
	{
		ItemContainer inv = client.getItemContainer(InventoryID.INV);
		if (inv == null)
		{
			return false;
		}
		int used = 0;
		for (Item i : inv.getItems())
		{
			if (i.getId() != -1)
			{
				used++;
			}
		}
		return used >= 28;
	}

	private NPC nearestNpc(WorldPoint at, int id)
	{
		NPC best = null;
		int bestD = Integer.MAX_VALUE;
		for (NPC n : client.getTopLevelWorldView().npcs())
		{
			if (n.getId() != id)
			{
				continue;
			}
			int d = n.getWorldLocation().distanceTo(at);
			if (d <= config.maxDistance() && d < bestD)
			{
				best = n;
				bestD = d;
			}
		}
		return best;
	}

	private NPC nearestBanker(WorldPoint at)
	{
		NPC best = null;
		int bestD = Integer.MAX_VALUE;
		for (NPC n : client.getTopLevelWorldView().npcs())
		{
			NPCComposition c = n.getTransformedComposition();
			if (c == null || !hasAction(c.getActions(), "bank"))
			{
				continue;
			}
			int d = n.getWorldLocation().distanceTo(at);
			if (d <= config.maxDistance() && d < bestD)
			{
				best = n;
				bestD = d;
			}
		}
		return best;
	}

	/**
	 * The nearest object of the kind: rocks by ID (a depleted rock is another object), trees by
	 * name and their chop option (a kind of tree has several IDs; stumps are named differently).
	 */
	private TileObject nearestObject(WorldPoint at, int id)
	{
		TileObject best = null;
		int bestD = Integer.MAX_VALUE;
		for (GameObject o : gameObjects())
		{
			if (o.getId() != id)
			{
				if (targetName == null)
				{
					continue;
				}
				ObjectComposition c = def(o.getId());
				if (c == null || !targetName.equals(c.getName()) || !hasAction(c.getActions(), targetOption))
				{
					continue;
				}
			}
			int d = o.getWorldLocation().distanceTo(at);
			if (d <= config.maxDistance() && d < bestD)
			{
				best = o;
				bestD = d;
			}
		}
		return best;
	}

	/** Bank booths, bank chests and deposit boxes. */
	private TileObject nearestBankObject(WorldPoint at)
	{
		TileObject best = null;
		int bestD = Integer.MAX_VALUE;
		for (GameObject o : gameObjects())
		{
			if (!isBank(o.getId()))
			{
				continue;
			}
			int d = o.getWorldLocation().distanceTo(at);
			if (d <= config.maxDistance() && d < bestD)
			{
				best = o;
				bestD = d;
			}
		}
		return best;
	}

	private boolean isBank(int id)
	{
		Boolean known = bankIds.get(id);
		if (known != null)
		{
			return known;
		}
		ObjectComposition c = def(id);
		String name = c == null || c.getName() == null ? "" : c.getName().toLowerCase();
		boolean bank = c != null && (hasAction(c.getActions(), "bank") || hasAction(c.getActions(), "deposit")
			|| name.equals("bank booth") || name.equals("bank chest") || name.equals("bank deposit box"));
		bankIds.put(id, bank);
		return bank;
	}

	/** An object's definition (cached), with its impostor resolved if it has one. */
	private ObjectComposition def(int id)
	{
		ObjectComposition c = defs.computeIfAbsent(id, client::getObjectDefinition);
		if (c != null && c.getImpostorIds() != null)
		{
			try
			{
				return c.getImpostor();
			}
			catch (RuntimeException e)
			{
				return c;
			}
		}
		return c;
	}

	private Iterable<GameObject> gameObjects()
	{
		java.util.List<GameObject> out = new java.util.ArrayList<>();
		Scene scene = client.getTopLevelWorldView().getScene();
		Tile[][] plane = scene.getTiles()[client.getTopLevelWorldView().getPlane()];
		for (Tile[] row : plane)
		{
			for (Tile t : row)
			{
				if (t == null || t.getGameObjects() == null)
				{
					continue;
				}
				for (GameObject o : t.getGameObjects())
				{
					// Large objects sit on several tiles; take them from their own tile only
					if (o != null && o.getSceneMinLocation().equals(t.getSceneLocation()))
					{
						out.add(o);
					}
				}
			}
		}
		return out;
	}

	private static boolean hasAction(String[] actions, String action)
	{
		if (actions == null)
		{
			return false;
		}
		for (String a : actions)
		{
			if (a != null && a.toLowerCase().startsWith(action))
			{
				return true;
			}
		}
		return false;
	}

	private static Set<String> set(String... s)
	{
		return new HashSet<>(Arrays.asList(s));
	}

	@Provides
	SkillingHelperConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(SkillingHelperConfig.class);
	}
}
