package com.jblors.rlm.plugins.lazyflick;

import com.google.gson.Gson;
import com.google.inject.Provides;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.inject.Inject;
import net.runelite.api.Actor;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.NPC;
import net.runelite.api.Player;
import net.runelite.api.events.AnimationChanged;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.HitsplatApplied;
import net.runelite.api.events.NpcDespawned;
import net.runelite.client.RuneLite;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.overlay.OverlayManager;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;

/**
 * Lazy flicking, for any NPC: highlights the protection prayer on the tick before the NPC
 * fighting you attacks, and stops on the attack tick (turn it off then).
 *
 * <p>Attack speeds come from the plugin's own database ({@link NpcDatabase}), built up as you
 * fight: the ticks between an NPC's attacks on you (the most common gap of the last few, once
 * two agree) are saved for its NPC ID. An NPC not in the database yet is looked up once on the
 * OSRS wiki (attack speed and style), so the highlight can come from its second attack. What is
 * timed in fights wins over the wiki.
 *
 * <p>An attack is an animation the NPC starts while targeting you on a tick it didn't take a hit
 * (its flinch animation plays on the tick a hit lands); once an animation is known as an attack
 * it counts on any tick. The highlight lasts from that GameTick to the next one: a click in that
 * window turns the prayer on for the attack tick.
 */
@PluginDescriptor(
	name = RlmName.PREFIX + "Lazy Flick",
	description = "Highlights your protection prayer on the tick before the NPC fighting you attacks; learns attack speeds as you fight, or from the OSRS wiki",
	tags = {"prayer", "flick", "lazy", "tick", "attack speed", "protect", "wiki", "rlm"}
)
public class LazyFlickPlugin extends Plugin
{
	/** Gaps longer than this aren't an attack speed (it stopped, walked, or something else) */
	static final int MAX_SPEED = 12;
	private static final int GAPS_KEPT = 6;
	private static final int SAVE_EVERY_TICKS = 50;

	@Inject
	private Client client;

	@Inject
	private LazyFlickConfig config;

	@Inject
	private ConfigManager configManager;

	@Inject
	private OverlayManager overlayManager;

	@Inject
	private LazyFlickOverlay overlay;

	@Inject
	private OkHttpClient http;

	@Inject
	private Gson gson;

	/** Set before startUp to use another file (tests); default ~/.runelite/rlm-lazyflick-npcs.json */
	File dbFile;
	NpcDatabase db;

	/** Our own game tick counter: events between two GameTicks belong to the second */
	private int tick;

	/** An NPC fighting you: its last attack (tick and animation) */
	static final class Fight
	{
		int lastAttack = Integer.MIN_VALUE / 2;
		int lastAnim = -1;
	}

	private final Map<NPC, Fight> fights = new HashMap<>();
	/** Animations started this tick by NPCs targeting you, and NPCs hit this tick */
	private final Map<NPC, Integer> started = new HashMap<>();
	private final Set<NPC> hitThisTick = java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>());
	/** Recent gaps between attacks, per NPC ID (this session) */
	private final Map<Integer, List<Integer>> gaps = new HashMap<>();
	/** NPC IDs asked of the wiki this session */
	private final Set<Integer> asked = java.util.concurrent.ConcurrentHashMap.newKeySet();

	/** What to show now (set each GameTick): prayers to press, and ticks until each NPC's attack */
	private volatile Set<Style> flickNow = EnumSet.noneOf(Style.class);
	private volatile Map<NPC, Integer> ticksLeft = new HashMap<>();
	/** The countdown per prayer: the soonest of the NPCs it's against */
	private volatile Map<Style, Integer> prayerCount = new java.util.EnumMap<>(Style.class);

	Set<Style> getFlickNow()
	{
		return flickNow;
	}

	/**
	 * The countdown above each NPC: ticks until the tick to press the prayer, so a 4-tick NPC
	 * shows 3 on its attack tick, then 2, 1, and 0 on the tick to press (shown as PRAY).
	 */
	Map<NPC, Integer> getTicksLeft()
	{
		return ticksLeft;
	}

	/** The same countdown per prayer, for its button: the soonest NPC's */
	Map<Style, Integer> getPrayerCount()
	{
		return prayerCount;
	}

	@Provides
	LazyFlickConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(LazyFlickConfig.class);
	}

	@Override
	protected void startUp()
	{
		if (db == null)
		{
			db = new NpcDatabase(dbFile != null ? dbFile : new File(RuneLite.RUNELITE_DIR, "rlm-lazyflick-npcs.json"),
				gson != null ? gson : new Gson());
			db.load();
		}
		if (overlayManager != null)
		{
			overlayManager.add(overlay);
		}
	}

	@Override
	protected void shutDown()
	{
		if (overlayManager != null)
		{
			overlayManager.remove(overlay);
		}
		if (db != null)
		{
			db.saveIfChanged();
		}
		reset();
	}

	private void reset()
	{
		fights.clear();
		started.clear();
		hitThisTick.clear();
		flickNow = EnumSet.noneOf(Style.class);
		ticksLeft = new HashMap<>();
		prayerCount = new java.util.EnumMap<>(Style.class);
	}

	@Subscribe
	public void onGameStateChanged(GameStateChanged e)
	{
		if (e.getGameState() != GameState.LOGGED_IN)
		{
			reset();
			if (db != null)
			{
				db.saveIfChanged();
			}
		}
	}

	@Subscribe
	public void onConfigChanged(ConfigChanged e)
	{
		if (LazyFlickConfig.GROUP.equals(e.getGroup()) && "forget".equals(e.getKey()) && config.forget())
		{
			db.clear();
			db.saveIfChanged();
			gaps.clear();
			asked.clear();
			configManager.setConfiguration(LazyFlickConfig.GROUP, "forget", false);
		}
	}

	@Subscribe
	public void onNpcDespawned(NpcDespawned e)
	{
		fights.remove(e.getNpc());
	}

	@Subscribe
	public void onAnimationChanged(AnimationChanged e)
	{
		Actor a = e.getActor();
		Player me = client.getLocalPlayer();
		if (a instanceof NPC && me != null && a.getInteracting() == me && a.getAnimation() != -1 && !((NPC) a).isDead())
		{
			started.put((NPC) a, a.getAnimation());
			lookUp(((NPC) a).getId());
		}
	}

	@Subscribe
	public void onInteractingChanged(net.runelite.api.events.InteractingChanged e)
	{
		Player me = client.getLocalPlayer();
		if (e.getSource() instanceof NPC && me != null && e.getTarget() == me)
		{
			lookUp(((NPC) e.getSource()).getId()); // usually in before its first attack
		}
	}

	@Subscribe
	public void onHitsplatApplied(HitsplatApplied e)
	{
		if (e.getActor() instanceof NPC)
		{
			hitThisTick.add((NPC) e.getActor());
		}
	}

	@Subscribe
	public void onGameTick(GameTick e)
	{
		tick++;
		Player me = client.getLocalPlayer();
		for (Map.Entry<NPC, Integer> s : started.entrySet())
		{
			NPC npc = s.getKey();
			int anim = s.getValue();
			NpcDatabase.Entry known = db.get(npc.getId());
			boolean attack = (known != null && known.attacks.contains(anim)) || !hitThisTick.contains(npc);
			if (attack)
			{
				attacked(npc, anim);
			}
		}
		started.clear();
		hitThisTick.clear();

		// Forget NPCs that stopped fighting you
		fights.entrySet().removeIf(f -> tick - f.getValue().lastAttack > 2 * MAX_SPEED
			|| (me != null && f.getKey().getInteracting() != me && tick - f.getValue().lastAttack > MAX_SPEED));

		Set<Style> now = EnumSet.noneOf(Style.class);
		Map<NPC, Integer> left = new HashMap<>();
		Map<Style, Integer> count = new java.util.EnumMap<>(Style.class);
		for (Map.Entry<NPC, Fight> f : fights.entrySet())
		{
			NPC npc = f.getKey();
			Integer speed = speed(npc.getId());
			if (speed == null || npc.isDead())
			{
				continue;
			}
			int next = f.getValue().lastAttack + speed;
			Style st = style(npc.getId(), f.getValue().lastAnim);
			int toPress = next - 1 - tick; // 0: press the prayer now
			if (toPress >= 0)
			{
				left.put(npc, toPress);
				count.merge(st, toPress, Math::min);
			}
			if (toPress == 0)
			{
				now.add(st);
			}
		}
		flickNow = now;
		ticksLeft = left;
		prayerCount = count;
		if (tick % SAVE_EVERY_TICKS == 0)
		{
			db.saveIfChanged();
		}
	}

	private void attacked(NPC npc, int anim)
	{
		int id = npc.getId();
		NpcDatabase.Entry entry = db.entry(id);
		Fight f = fights.computeIfAbsent(npc, n -> new Fight());
		int gap = tick - f.lastAttack;
		if (gap >= 2 && gap <= MAX_SPEED)
		{
			List<Integer> g = gaps.computeIfAbsent(id, k -> new ArrayList<>());
			g.add(gap);
			if (g.size() > GAPS_KEPT)
			{
				g.remove(0);
			}
			Integer mode = mode(g);
			if (mode != null && (!mode.equals(entry.speed) || !"seen".equals(entry.speedFrom)))
			{
				entry.speed = mode;
				entry.speedFrom = "seen";
				db.changed();
			}
		}
		f.lastAttack = tick;
		f.lastAnim = anim;
		if (entry.attacks.add(anim))
		{
			db.changed();
		}
		// What you prayed when it hit
		for (Style s : Style.values())
		{
			if (client.isPrayerActive(s.prayer))
			{
				entry.prayed.put(anim, s.name());
				entry.prayedLast = s.name();
				db.changed();
				break;
			}
		}
	}

	/** The NPC's attack speed: timed in this fight (two gaps alike), else from the database. */
	Integer speed(int npcId)
	{
		List<Integer> g = gaps.get(npcId);
		Integer m = g == null ? null : mode(g);
		if (m != null)
		{
			return m;
		}
		NpcDatabase.Entry e = db.get(npcId);
		return e == null ? null : e.speed;
	}

	/** The most common gap (ties: the latest), once two gaps agree */
	static Integer mode(List<Integer> g)
	{
		Integer best = null;
		int bestCount = 1;
		for (int i = g.size() - 1; i >= 0; i--)
		{
			int c = 0;
			for (int x : g)
			{
				if (x == g.get(i))
				{
					c++;
				}
			}
			if (c > bestCount)
			{
				best = g.get(i);
				bestCount = c;
			}
		}
		return best;
	}

	/**
	 * The prayer to highlight: the setting, or (Auto) the wiki's single style, else what you
	 * prayed against that attack, else against the NPC, else Protect from Melee.
	 */
	Style style(int npcId, int anim)
	{
		switch (config.prayer())
		{
			case MELEE:
				return Style.MELEE;
			case MISSILES:
				return Style.RANGED;
			case MAGIC:
				return Style.MAGIC;
			default:
				break;
		}
		NpcDatabase.Entry e = db.get(npcId);
		if (e != null)
		{
			Style s = styleOf(e.wikiStyle);
			if (s == null)
			{
				s = styleOf(e.prayed.get(anim));
			}
			if (s == null)
			{
				s = styleOf(e.prayedLast);
			}
			if (s != null)
			{
				return s;
			}
		}
		return Style.MELEE;
	}

	private static Style styleOf(String name)
	{
		try
		{
			return name == null ? null : Style.valueOf(name);
		}
		catch (IllegalArgumentException ex)
		{
			return null;
		}
	}

	// ------------------------------------------------------------ the wiki

	/** Asks the wiki about an NPC not in the database yet (once). */
	private void lookUp(int npcId)
	{
		NpcDatabase.Entry e = db.get(npcId);
		if (!config.wiki() || http == null || (e != null && (e.wikiChecked || e.speed != null)) || !asked.add(npcId))
		{
			return;
		}
		HttpUrl url = HttpUrl.parse(WikiLookup.API).newBuilder()
			.addQueryParameter("action", "bucket")
			.addQueryParameter("format", "json")
			.addQueryParameter("query", WikiLookup.query(npcId))
			.build();
		Request req = new Request.Builder().url(url).header("User-Agent", WikiLookup.USER_AGENT).build();
		http.newCall(req).enqueue(new Callback()
		{
			@Override
			public void onFailure(Call call, IOException ex)
			{
				asked.remove(npcId); // try again next time it fights you
			}

			@Override
			public void onResponse(Call call, Response response) throws IOException
			{
				try (ResponseBody body = response.body())
				{
					if (!response.isSuccessful() || body == null)
					{
						asked.remove(npcId);
						return;
					}
					wikiAnswered(npcId, body.string());
				}
				catch (RuntimeException ex)
				{
					System.out.println("rlm-lazyflick: wiki answer for " + npcId + " not understood: " + ex);
				}
			}
		});
	}

	/** Stores the wiki's answer (attack speed only if the NPC hasn't been timed in a fight). */
	void wikiAnswered(int npcId, String json)
	{
		WikiLookup.Result r = WikiLookup.parse(json, npcId);
		NpcDatabase.Entry e = db.entry(npcId);
		e.wikiChecked = true;
		if (r != null)
		{
			e.name = r.name;
			Style s = WikiLookup.style(r.styles);
			e.wikiStyle = s == null ? null : s.name();
			if (r.speed != null && !"seen".equals(e.speedFrom))
			{
				e.speed = r.speed;
				e.speedFrom = "wiki";
			}
		}
		db.changed();
	}
}
