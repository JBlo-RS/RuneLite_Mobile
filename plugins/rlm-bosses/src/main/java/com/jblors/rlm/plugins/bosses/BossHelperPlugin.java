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
import net.runelite.api.events.OverheadTextChanged;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.overlay.OverlayManager;
import net.runelite.client.util.Text;

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

	@Inject
	private BossTellOverlay tellOverlay;

	@Inject
	private ConfigManager configManager;

	@Inject
	private net.runelite.client.callback.ClientThread clientThread;

	// ------------------------------------------------------------ attack warnings (overhead text)

	/** The boss that just announced an attack, what it said, and when */
	private volatile NPC tellNpc;
	private volatile String tellText, tellKey;
	private volatile int tellTick;

	/** The warning to show above tellNpc, or null */
	private volatile String warning;

	NPC getWarningNpc()
	{
		return warning == null ? null : tellNpc;
	}

	String getWarning()
	{
		return config.attackWarnings() ? warning : null;
	}

	/** Learned "which text comes before which attack", also kept in RuneLite's settings */
	private final Map<String, String> learned = new ConcurrentHashMap<>();

	private String learnedAttack(String key)
	{
		String v = learned.get(key);
		if (v == null && configManager != null)
		{
			v = configManager.getConfiguration(BossHelperConfig.GROUP, key);
			if (v != null)
			{
				learned.put(key, v);
			}
		}
		return v;
	}

	/** "*Snort*" -> "snort" */
	private static String tellKeyOf(String text)
	{
		return Text.removeTags(text).toLowerCase().replaceAll("[^a-z ]", "").trim().replace(' ', '_');
	}

	/** Each announcing boss's overhead text as last seen, to notice when it changes */
	private final Map<NPC, String> lastOverhead = new java.util.WeakHashMap<>();

	@Subscribe
	public void onOverheadTextChanged(OverheadTextChanged e)
	{
		tell(e.getActor(), e.getOverheadText(), "overhead event");
	}

	/**
	 * Overhead text is also read directly each client frame: the OverheadTextChanged event isn't
	 * reliable for NPCs (RuneLite's own plugins only use it for players), and Brutus' growl
	 * didn't arrive through it.
	 */
	@Subscribe
	public void onClientTick(net.runelite.api.events.ClientTick e)
	{
		try
		{
			readOverheads();
		}
		catch (RuntimeException ex)
		{
			error("client tick", ex);
		}
	}

	private void readOverheads()
	{
		for (NPC npc : tracked)
		{
			Boss b = bossOf(npc);
			if (b == null || b.def.tells.isEmpty())
			{
				continue;
			}
			String text = npc.getOverheadText();
			String before = lastOverhead.put(npc, text);
			if (text != null && !text.isEmpty() && !text.equals(before))
			{
				tell(npc, text, "overhead text");
			}
		}
	}

	/** NPC speech in the chatbox, in case a boss announces its attack there. */
	@Subscribe
	public void onChatMessage(net.runelite.api.events.ChatMessage e)
	{
		if (e.getType() != net.runelite.api.ChatMessageType.NPC_SAY || e.getName() == null)
		{
			return;
		}
		String name = Text.removeTags(e.getName());
		for (NPC npc : tracked)
		{
			Boss b = bossOf(npc);
			if (b != null && !b.def.tells.isEmpty() && name.equalsIgnoreCase(npc.getName()))
			{
				tell(npc, e.getMessage(), "chat");
				return;
			}
		}
	}

	private void tell(Actor a, String text, String via)
	{
		Boss b = bossOf(a);
		if (b == null || b.def.tells.isEmpty() || text == null || text.isEmpty())
		{
			return;
		}
		String key = tellKeyOf(text);
		if (a == tellNpc && key.equals(tellKey) && client.getTickCount() - tellTick <= 1)
		{
			return; // the same announcement seen another way
		}
		debug("say:" + idOf(a) + ":" + text, describe(a) + " says '" + text + "' (" + via + ")");
		seen((NPC) a);
		tellNpc = (NPC) a;
		tellText = Text.removeTags(text);
		tellKey = key;
		tellTick = client.getTickCount();
		String attack = learnedAttack("tell." + b.name() + "." + tellKey);
		warning = attack != null ? attack + " incoming!" : tellText + " (attack coming)";
	}

	/** An announced attack started: remember which text came before it, and clear the warning. */
	private void tellAttack(Boss b, Actor a, int animation)
	{
		String attack = b.def.tells.get(animation);
		if (attack == null || a != tellNpc || tellKey == null)
		{
			return;
		}
		if (client.getTickCount() - tellTick <= 8)
		{
			String key = "tell." + b.name() + "." + tellKey;
			if (!attack.equals(learnedAttack(key)))
			{
				learned.put(key, attack);
				if (configManager != null)
				{
					configManager.setConfiguration(BossHelperConfig.GROUP, key, attack);
				}
				System.out.println("rlm-boss: learned: '" + tellText + "' comes before " + attack);
			}
		}
		warning = null;
		tellKey = null;
	}

	/**
	 * Bosses and floor-hazard NPCs that are here, from spawn/despawn events. Not a loop over the
	 * top-level world view's NPCs: newer fights (Brutus) run in their own world view, whose NPCs
	 * aren't in that list, though their events still arrive.
	 */
	private final Set<NPC> tracked = ConcurrentHashMap.newKeySet();

	Set<NPC> getTracked()
	{
		return tracked;
	}

	private boolean isDangerNpc(int id)
	{
		for (Boss b : Boss.values())
		{
			if (b.def.dangerNpcs.contains(id) && config.enabled(b))
			{
				return true;
			}
		}
		return false;
	}

	/** When each tracked NPC was last seen (any event or list): bosses not seen for a while are gone */
	private final Map<NPC, Integer> lastSeen = new ConcurrentHashMap<>();
	private static final int GONE_AFTER_TICKS = 50;

	/** What the prayer overlay last did, for the debug status line */
	volatile String prayerUi = "-";

	private void seen(NPC npc)
	{
		tracked.add(npc);
		lastSeen.put(npc, client.getTickCount());
	}

	private void track(NPC npc)
	{
		if (bossOf(npc) != null || isDangerNpc(npc.getId()))
		{
			seen(npc);
		}
		else
		{
			tracked.remove(npc);
		}
	}

	private void error(String where, RuntimeException ex)
	{
		if (logged.add("error:" + where + ":" + ex))
		{
			System.out.println("rlm-boss: error in " + where + ": " + ex);
			StackTraceElement[] st = ex.getStackTrace();
			for (int i = 0; i < Math.min(6, st.length); i++)
			{
				System.out.println("rlm-boss:     at " + st[i]);
			}
		}
	}

	/** Every NPC in the top-level world view and in the world views inside it. */
	private java.util.List<NPC> allNpcs()
	{
		java.util.List<NPC> out = new java.util.ArrayList<>();
		net.runelite.api.WorldView top = client.getTopLevelWorldView();
		if (top == null)
		{
			return out;
		}
		if (top.npcs() != null)
		{
			for (NPC n : top.npcs())
			{
				out.add(n);
			}
		}
		if (top.worldEntities() != null)
		{
			for (net.runelite.api.WorldEntity we : top.worldEntities())
			{
				if (we != null && we.getWorldView() != null && we.getWorldView().npcs() != null)
				{
					for (NPC n : we.getWorldView().npcs())
					{
						out.add(n);
					}
				}
			}
		}
		return out;
	}

	@Subscribe
	public void onNpcDespawned(net.runelite.api.events.NpcDespawned e)
	{
		tracked.remove(e.getNpc());
	}

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
		// Started mid-fight: pick up what's already there (spawn events come later only for new ones)
		clientThread.invoke(() ->
		{
			if (client.getGameState() == GameState.LOGGED_IN)
			{
				for (NPC n : allNpcs())
				{
					track(n);
				}
			}
		});
		overlayManager.add(prayerOverlay);
		overlayManager.add(tileOverlay);
		overlayManager.add(tellOverlay);
	}

	@Override
	protected void shutDown()
	{
		overlayManager.remove(prayerOverlay);
		overlayManager.remove(tileOverlay);
		overlayManager.remove(tellOverlay);
		tracked.clear();
		warning = null;
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

	/** What has been logged already: each distinct thing once, so a whole fight stays short */
	private final Set<String> logged = ConcurrentHashMap.newKeySet();

	/** Logs a line the first time its key is seen (e.g. "anim:15627:13783"). */
	private void debug(String key, String what)
	{
		if (config.debugLog() && logged.size() < 2000 && logged.add(key) && debugLines++ < 60)
		{
			System.out.println("rlm-boss: t" + client.getTickCount() + " " + what);
		}
	}

	private static String idOf(Actor a)
	{
		return a instanceof NPC ? Integer.toString(((NPC) a).getId()) : a == null ? "-" : "player";
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
				debug("gfx:" + e.getGraphicsObject().getId(), "graphics " + e.getGraphicsObject().getId() + " at " + w);
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
					String to = p.getTargetActor() == me ? "you" : p.getTargetActor() != null ? "actor" : "ground";
					debug("proj:" + p.getId() + ":" + idOf(src) + ":" + to, "projectile " + p.getId() + " from " + describe(src) + " to "
						+ (p.getTargetActor() == me ? "you" : p.getTargetActor() != null ? describe(p.getTargetActor())
						: "ground " + p.getTargetPoint()));
				}
			}
		}
	}

	@Subscribe
	public void onAnimationChanged(AnimationChanged e)
	{
		try
		{
			animationChanged(e);
		}
		catch (RuntimeException ex)
		{
			error("animation", ex);
		}
	}

	private void animationChanged(AnimationChanged e)
	{
		if (config.debugLog() && e.getActor() instanceof NPC && e.getActor().getAnimation() != -1
			&& near(e.getActor().getWorldLocation()))
		{
			debug("anim:" + idOf(e.getActor()) + ":" + e.getActor().getAnimation(),
				describe(e.getActor()) + " animation " + e.getActor().getAnimation()
				+ (e.getActor().getInteracting() == client.getLocalPlayer() ? " (targeting you)" : ""));
		}
		Boss b = bossOf(e.getActor());
		if (b == null)
		{
			return;
		}
		seen((NPC) e.getActor());
		tellAttack(b, e.getActor(), e.getActor().getAnimation());
		Style s = b.def.attackAnimations.get(e.getActor().getAnimation());
		if (s != null)
		{
			style = s;
		}
	}

	@Subscribe
	public void onNpcChanged(NpcChanged e)
	{
		track(e.getNpc());
		if (config.debugLog() && near(e.getNpc().getWorldLocation()))
		{
			debug("changed:" + e.getNpc().getId(), "changed to " + describe(e.getNpc()));
		}
		formChange(e.getNpc());
	}

	@Subscribe
	public void onNpcSpawned(NpcSpawned e)
	{
		track(e.getNpc());
		if (config.debugLog() && near(e.getNpc().getWorldLocation()))
		{
			debug("spawn:" + e.getNpc().getId(), "spawned " + describe(e.getNpc()) + " at " + e.getNpc().getWorldLocation());
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
		if (config.debugLog() && client.getGameState() == GameState.LOGGED_IN && near(e.getGameObject().getWorldLocation()))
		{
			debug("obj:" + id, "object " + id + " at " + e.getGameObject().getWorldLocation());
		}
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
		try
		{
			gameTick();
		}
		catch (RuntimeException ex)
		{
			error("game tick", ex);
		}
	}

	private void gameTick()
	{
		debugTick();
		int tickNow = client.getTickCount();
		// Bosses in any world view we can list; and forget ones not seen for a while
		for (NPC n : allNpcs())
		{
			if (bossOf(n) != null || isDangerNpc(n.getId()))
			{
				seen(n);
			}
		}
		tracked.removeIf(n -> tickNow - lastSeen.getOrDefault(n, tickNow) > GONE_AFTER_TICKS);
		lastSeen.keySet().retainAll(tracked);
		if (config.debugLog() && (!tracked.isEmpty() || style != null) && tickNow % 10 == 0)
		{
			StringBuilder ids = new StringBuilder();
			for (NPC n : tracked)
			{
				ids.append(n.getId()).append(n.isDead() ? "(dead) " : " ");
			}
			debug("status:" + tickNow, "status: tracking [" + ids.toString().trim() + "], prayer " + style
				+ ", prayer tab: " + prayerUi + ", warning " + warning);
		}
		Player me = client.getLocalPlayer();
		boolean present = false;
		for (NPC npc : tracked)
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
		if (warning != null && now - tellTick > 8)
		{
			warning = null; // nothing came of it
		}
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
				if (b != null)
				{
					seen((NPC) p.getSourceActor());
				}
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
			if (e.getGameState() == GameState.LOGIN_SCREEN || e.getGameState() == GameState.HOPPING)
			{
				tracked.clear();
			}
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
