package com.jblors.rlm.plugins.bosses;

import com.google.inject.Provides;
import javax.inject.Inject;
import net.runelite.api.Actor;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.NPC;
import net.runelite.api.Player;
import net.runelite.api.Projectile;
import net.runelite.api.events.AnimationChanged;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GraphicsObjectCreated;
import net.runelite.api.events.GameTick;
import net.runelite.api.GameObject;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.events.GameObjectDespawned;
import net.runelite.api.events.GameObjectSpawned;
import net.runelite.api.events.NpcChanged;
import net.runelite.api.events.NpcSpawned;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.overlay.OverlayManager;

@PluginDescriptor(
	name = "Boss Helper",
	description = "Shows the prayer against bosses' attacks and marks tiles about to be hit (Scurrius, Jad, Dagannoth Kings, Phantom Muspah, Sarachnis, wilderness bosses)",
	tags = {"boss", "prayer", "protect", "tiles", "jad", "dks", "muspah", "sarachnis", "scurrius", "fanatic", "archaeologist", "rlm"}
)
public class BossHelperPlugin extends Plugin
{
	@Inject
	private Client client;

	@Inject
	private BossHelperConfig config;

	@Inject
	private OverlayManager overlayManager;

	@Inject
	private BossPrayerOverlay prayerOverlay;

	@Inject
	private BossTileOverlay tileOverlay;

	/** The prayer against the last attack seen; until a different one comes or the boss is gone. */
	private volatile Style style;

	/** Hazard objects on the floor (venom clouds), from all bosses' lists */
	private final Set<GameObject> objects = ConcurrentHashMap.newKeySet();

	/** Tiles where a lingering ground projectile landed -> game tick until which they stay marked */
	private final Map<LocalPoint, Integer> lingering = new ConcurrentHashMap<>();

	Set<GameObject> getObjects()
	{
		return objects;
	}

	Map<LocalPoint, Integer> getLingering()
	{
		return lingering;
	}

	int tick()
	{
		return client.getTickCount();
	}

	@Override
	protected void startUp()
	{
		overlayManager.add(prayerOverlay);
		overlayManager.add(tileOverlay);
	}

	@Override
	protected void shutDown()
	{
		overlayManager.remove(prayerOverlay);
		overlayManager.remove(tileOverlay);
		style = null;
		objects.clear();
		lingering.clear();
	}

	Style getStyle()
	{
		return style;
	}

	/** The enabled boss an NPC is, or null. */
	Boss bossOf(Actor a)
	{
		if (!(a instanceof NPC))
		{
			return null;
		}
		Boss b = Boss.of(((NPC) a).getId());
		return b != null && config.enabled(b) ? b : null;
	}

	// ------------------------------------------------------------ debug log

	private final Set<Projectile> seenProjectiles = java.util.Collections.newSetFromMap(new java.util.WeakHashMap<>());
	private int debugLines;

	/** True for things close enough to the player to be part of a fight. */
	private boolean near(net.runelite.api.coords.WorldPoint p)
	{
		Player me = client.getLocalPlayer();
		return me != null && p != null && me.getWorldLocation().distanceTo(p) <= 20;
	}

	private void debug(String what)
	{
		if (config.debugLog() && debugLines++ < 60) // at most 60 lines a tick
		{
			System.out.println("rlm-boss: t" + client.getTickCount() + " " + what);
		}
	}

	private static String describe(Actor a)
	{
		if (a == null)
		{
			return "-";
		}
		if (a instanceof NPC)
		{
			return "npc " + ((NPC) a).getId() + " '" + a.getName() + "'";
		}
		return a == null ? "-" : "player '" + a.getName() + "'";
	}

	@Subscribe
	public void onGraphicsObjectCreated(GraphicsObjectCreated e)
	{
		if (config.debugLog() && e.getGraphicsObject().getLocation() != null)
		{
			net.runelite.api.coords.WorldPoint w = net.runelite.api.coords.WorldPoint.fromLocal(client, e.getGraphicsObject().getLocation());
			if (near(w))
			{
				debug("graphics " + e.getGraphicsObject().getId() + " at " + w);
			}
		}
	}

	private void debugTick()
	{
		debugLines = 0;
		if (!config.debugLog())
		{
			return;
		}
		Player me = client.getLocalPlayer();
		for (Projectile p : client.getProjectiles())
		{
			if (seenProjectiles.add(p))
			{
				Actor src = p.getSourceActor();
				if (src != null && src != me && near(src.getWorldLocation()) || p.getTargetActor() == me)
				{
					debug("projectile " + p.getId() + " from " + describe(src) + " to "
						+ (p.getTargetActor() == me ? "you" : p.getTargetActor() != null ? describe(p.getTargetActor())
						: "ground " + p.getTargetPoint()));
				}
			}
		}
	}

	@Subscribe
	public void onAnimationChanged(AnimationChanged e)
	{
		if (config.debugLog() && e.getActor() instanceof NPC && e.getActor().getAnimation() != -1
			&& near(e.getActor().getWorldLocation()))
		{
			debug(describe(e.getActor()) + " animation " + e.getActor().getAnimation()
				+ (e.getActor().getInteracting() == client.getLocalPlayer() ? " (targeting you)" : ""));
		}
		Boss b = bossOf(e.getActor());
		if (b == null)
		{
			return;
		}
		Style s = b.def.attackAnimations.get(e.getActor().getAnimation());
		if (s != null)
		{
			style = s;
		}
	}

	@Subscribe
	public void onNpcChanged(NpcChanged e)
	{
		if (config.debugLog() && near(e.getNpc().getWorldLocation()))
		{
			debug("changed to " + describe(e.getNpc()));
		}
		formChange(e.getNpc());
	}

	@Subscribe
	public void onNpcSpawned(NpcSpawned e)
	{
		if (config.debugLog() && near(e.getNpc().getWorldLocation()))
		{
			debug("spawned " + describe(e.getNpc()) + " at " + e.getNpc().getWorldLocation());
		}
		formChange(e.getNpc());
	}

	/** A boss appearing in, or changing to, a single-style form (Zulrah, Phantom Muspah). */
	private void formChange(NPC npc)
	{
		Boss b = bossOf(npc);
		Style s = b == null ? null : b.def.forms.get(npc.getId());
		if (s != null)
		{
			style = s;
		}
	}

	@Subscribe
	public void onGameObjectSpawned(GameObjectSpawned e)
	{
		int id = e.getGameObject().getId();
		for (Boss b : Boss.values())
		{
			if (b.def.markedObjects.contains(id) && config.enabled(b))
			{
				objects.add(e.getGameObject());
				return;
			}
		}
	}

	@Subscribe
	public void onGameObjectDespawned(GameObjectDespawned e)
	{
		objects.remove(e.getGameObject());
	}

	@Subscribe
	public void onGameTick(GameTick e)
	{
		debugTick();
		Player me = client.getLocalPlayer();
		boolean present = false;
		for (NPC npc : client.getTopLevelWorldView().npcs())
		{
			Boss b = bossOf(npc);
			if (b == null || npc.isDead())
			{
				continue;
			}
			present = true;
			// Single-style forms: while it fights you
			Style form = b.def.forms.get(npc.getId());
			if (form != null && me != null && npc.getInteracting() == me && style == null)
			{
				style = form;
			}
		}
		int now = client.getTickCount();
		lingering.values().removeIf(until -> until < now);
		if (!present)
		{
			style = null;
			return;
		}
		// Ground projectiles that leave something behind (acid): remember where they land
		for (Projectile p : client.getProjectiles())
		{
			if (p.getTargetActor() != null || p.getTarget() == null || p.getRemainingCycles() <= 0)
			{
				continue;
			}
			Boss b = bossOf(p.getSourceActor());
			Integer ticks = b == null ? null : b.def.linger.get(p.getId());
			if (ticks != null)
			{
				// client cycles are 20 ms; a game tick is 600 ms
				int lands = now + p.getRemainingCycles() / 30 + 1;
				lingering.merge(p.getTarget(), lands + ticks, Math::max);
			}
		}
		// Projectiles aimed at you
		if (me != null)
		{
			for (Projectile p : client.getProjectiles())
			{
				if (p.getTargetActor() != me || p.getRemainingCycles() <= 0)
				{
					continue;
				}
				Boss b = bossOf(p.getSourceActor());
				Style s = b == null ? null : b.def.projectiles.get(p.getId());
				if (s != null)
				{
					style = s;
				}
			}
		}
	}

	@Subscribe
	public void onGameStateChanged(GameStateChanged e)
	{
		if (e.getGameState() != GameState.LOGGED_IN)
		{
			style = null;
			lingering.clear();
		}
		if (e.getGameState() == GameState.LOADING)
		{
			objects.clear(); // the scene is rebuilt; objects spawn again
		}
	}

	@Provides
	BossHelperConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(BossHelperConfig.class);
	}
}
