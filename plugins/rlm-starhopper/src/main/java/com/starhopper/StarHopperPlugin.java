package com.starhopper;

import com.google.inject.Provides;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Polygon;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import javax.inject.Inject;
import javax.swing.SwingUtilities;
import net.runelite.api.Client;
import net.runelite.api.GameObject;
import net.runelite.api.GameState;
import net.runelite.api.ObjectComposition;
import net.runelite.api.Player;
import net.runelite.api.Skill;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.GameObjectDespawned;
import net.runelite.api.events.GameObjectSpawned;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GameTick;
import net.runelite.client.Notifier;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.events.OverlayMenuClicked;
import net.runelite.client.game.WorldService;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.NavigationButton;
import net.runelite.client.ui.overlay.OverlayManager;
import net.runelite.client.util.WorldUtil;
import net.runelite.http.api.worlds.World;
import net.runelite.http.api.worlds.WorldResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@PluginDescriptor(
	name = RlmName.PREFIX + "Star Hopper",
	description = "Suggests the next shooting star and world to hop to, using Star Miners data and your filters",
	tags = {"shooting", "star", "stars", "mining", "hop", "starminers"}
)
public class StarHopperPlugin extends Plugin
{
	private static final Logger log = LoggerFactory.getLogger(StarHopperPlugin.class);

	static final String HOP_OPTION = "Hop to next star";
	private static final String STAR_NAME = "Crashed Star";
	private static final int STAR_TRACK_DISTANCE = 12;
	/** A tier drop despawns the star and spawns the next tier straight away; no respawn after this many ticks = depleted. */
	private static final int DEPLETE_CONFIRM_TICKS = 2;
	private static final int HOP_DELAY_TICKS = 2;

	@Inject
	private Client client;

	@Inject
	private ClientThread clientThread;

	@Inject
	private StarHopperConfig config;

	@Inject
	private ClientToolbar clientToolbar;

	@Inject
	private OverlayManager overlayManager;

	@Inject
	private StarHopperOverlay overlay;

	@Inject
	private StarMinersClient feed;

	@Inject
	private WorldService worldService;

	@Inject
	private ScheduledExecutorService executor;

	@Inject
	private Notifier notifier;

	private StarHopperPanel panel;
	private NavigationButton navButton;
	private ScheduledFuture<?> pollTask;

	private volatile List<StarReport> stars = Collections.emptyList();
	private volatile List<Suggestion> ranked = Collections.emptyList();
	private volatile String status = "Loading...";
	private volatile String currentLocation;
	private final Map<String, Long> depleted = new ConcurrentHashMap<>();

	// Client-thread state
	private WorldPoint trackedStar;
	private int despawnTick = -1;
	private net.runelite.api.World pendingHop;
	private int hopTicks;

	@Provides
	StarHopperConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(StarHopperConfig.class);
	}

	@Override
	protected void startUp()
	{
		panel = new StarHopperPanel(this);
		navButton = NavigationButton.builder()
			.tooltip("Star Hopper")
			.icon(createIcon())
			.priority(7)
			.panel(panel)
			.build();
		clientToolbar.addNavigation(navButton);
		overlayManager.add(overlay);
		schedulePolling();
	}

	@Override
	protected void shutDown()
	{
		if (pollTask != null)
		{
			pollTask.cancel(true);
			pollTask = null;
		}
		overlayManager.remove(overlay);
		clientToolbar.removeNavigation(navButton);
		panel = null;
		navButton = null;
		stars = Collections.emptyList();
		ranked = Collections.emptyList();
		depleted.clear();
		trackedStar = null;
		despawnTick = -1;
		pendingHop = null;
		currentLocation = null;
	}

	// ------------------------------------------------------------------ feed

	private void schedulePolling()
	{
		if (pollTask != null)
		{
			pollTask.cancel(false);
		}
		int seconds = Math.max(15, config.pollSeconds());
		pollTask = executor.scheduleWithFixedDelay(this::poll, 0, seconds, TimeUnit.SECONDS);
	}

	void refreshNow()
	{
		executor.execute(this::poll);
	}

	private void poll()
	{
		String key = config.authKey();
		if (key == null || key.trim().isEmpty())
		{
			status = "Add your Star Miners key in the plugin settings (Star Miners feed > Authorization key).";
			recompute();
			return;
		}
		try
		{
			List<StarReport> fetched = feed.fetch(config.endpoint(), key, config.debugLogResponse());
			stars = fetched;
			status = fetched.isEmpty()
				? "Connected, but no stars were found in the response. Turn on 'Log raw response' to check the format."
				: "Updated " + java.time.LocalTime.now().withNano(0) + " · " + fetched.size() + " stars";
		}
		catch (Exception e)
		{
			log.warn("Star Hopper: failed to fetch stars", e);
			status = "Fetch failed: " + e.getMessage();
		}
		recompute();
	}

	/** Re-rank on the client thread (needs world + skill), then push to the panel. */
	private void recompute()
	{
		clientThread.invokeLater(() ->
		{
			int world = client.getGameState() == GameState.LOGGED_IN ? client.getWorld() : -1;
			int miningLevel = client.getGameState() == GameState.LOGGED_IN ? client.getRealSkillLevel(Skill.MINING) : 0;

			// If we're standing at a star, learn its location name from the feed
			if (trackedStar != null && world > 0)
			{
				for (StarReport s : stars)
				{
					if (s.getWorld() == world)
					{
						currentLocation = s.getLocation();
						break;
					}
				}
			}

			Map<Integer, World> worlds = new HashMap<>();
			WorldResult wr = worldService.getWorlds();
			if (wr != null && wr.getWorlds() != null)
			{
				for (World w : wr.getWorlds())
				{
					worlds.put(w.getId(), w);
				}
			}

			ranked = StarRanker.rank(stars, worlds, depleted, config, miningLevel, world, currentLocation,
				System.currentTimeMillis());
			pushToPanel();
		});
	}

	private void pushToPanel()
	{
		final StarHopperPanel p = panel;
		if (p == null)
		{
			return;
		}
		final List<Suggestion> list = ranked;
		final String s = status;
		final String loc = currentLocation;
		SwingUtilities.invokeLater(() -> p.update(list, s, loc));
	}

	// ------------------------------------------------------------------ star tracking

	private boolean isStar(GameObject obj)
	{
		ObjectComposition comp = client.getObjectDefinition(obj.getId());
		return comp != null && STAR_NAME.equalsIgnoreCase(comp.getName());
	}

	@Subscribe
	public void onGameObjectSpawned(GameObjectSpawned event)
	{
		GameObject obj = event.getGameObject();
		Player me = client.getLocalPlayer();
		if (me == null || !isStar(obj))
		{
			return;
		}
		WorldPoint wp = obj.getWorldLocation();
		if (wp.equals(trackedStar) || me.getWorldLocation().distanceTo(wp) <= STAR_TRACK_DISTANCE)
		{
			boolean firstSighting = trackedStar == null;
			trackedStar = wp;
			despawnTick = -1; // tier dropped, star still alive
			if (firstSighting)
			{
				recompute();
			}
		}
	}

	@Subscribe
	public void onGameObjectDespawned(GameObjectDespawned event)
	{
		GameObject obj = event.getGameObject();
		if (trackedStar != null && trackedStar.equals(obj.getWorldLocation()) && isStar(obj))
		{
			despawnTick = client.getTickCount();
		}
	}

	@Subscribe
	public void onGameTick(GameTick tick)
	{
		if (despawnTick >= 0 && client.getTickCount() - despawnTick >= DEPLETE_CONFIRM_TICKS)
		{
			despawnTick = -1;
			trackedStar = null;
			onStarDepleted();
		}

		if (pendingHop != null && ++hopTicks >= HOP_DELAY_TICKS)
		{
			net.runelite.api.World target = pendingHop;
			pendingHop = null;
			client.hopToWorld(target);
		}
	}

	@Subscribe
	public void onGameStateChanged(GameStateChanged event)
	{
		GameState state = event.getGameState();
		if (state == GameState.HOPPING || state == GameState.LOGIN_SCREEN)
		{
			trackedStar = null;
			despawnTick = -1;
		}
		if (state == GameState.LOGGED_IN)
		{
			recompute();
		}
	}

	private void onStarDepleted()
	{
		int world = client.getWorld();
		if (currentLocation != null)
		{
			depleted.put(world + "|" + currentLocation.toLowerCase(), System.currentTimeMillis());
		}
		// Ignore any report for this world that came in before the depletion
		for (StarReport s : stars)
		{
			if (s.getWorld() == world)
			{
				depleted.put(s.key(), System.currentTimeMillis());
			}
		}

		clientThread.invokeLater(() ->
		{
			// Rank synchronously here so the notification uses fresh data
			recompute();
			clientThread.invokeLater(() ->
			{
				if (!config.notifyOnDeplete())
				{
					return;
				}
				Suggestion next = getSuggestion();
				if (next == null)
				{
					notifier.notify("Star depleted. No matching stars right now.");
				}
				else
				{
					notifier.notify(String.format("Star depleted. Next: W%d, %s (est. T%d)%s",
						next.getStar().getWorld(), next.getStar().getLocation(), next.getDisplayTier(),
						next.isSameLocation() ? " - same spot" : ""));
				}
			});
		});
	}

	// ------------------------------------------------------------------ hopping

	void hopToSuggestion()
	{
		Suggestion s = getSuggestion();
		if (s != null)
		{
			hopTo(s);
		}
	}

	/** User-initiated hop (panel button or overlay menu). */
	void hopTo(Suggestion s)
	{
		WorldResult wr = worldService.getWorlds();
		World target = wr == null ? null : wr.findWorld(s.getStar().getWorld());
		if (target == null)
		{
			status = "World " + s.getStar().getWorld() + " not found in the world list.";
			pushToPanel();
			return;
		}

		clientThread.invoke(() ->
		{
			net.runelite.api.World rs = client.createWorld();
			rs.setActivity(target.getActivity());
			rs.setAddress(target.getAddress());
			rs.setId(target.getId());
			rs.setPlayerCount(target.getPlayers());
			// As RuneLite's World Hopper does (World has no getLocation() in RuneLite 1.13)
			rs.setLocation(target.getRegion() != null ? target.getRegion().getId() : 0);
			rs.setTypes(WorldUtil.toWorldTypes(target.getTypes()));

			// Remember where we're heading so "same location" works when this star depletes
			currentLocation = s.getStar().getLocation();

			if (client.getGameState() == GameState.LOGIN_SCREEN)
			{
				client.changeWorld(rs);
				return;
			}
			if (client.getGameState() != GameState.LOGGED_IN)
			{
				return;
			}
			client.openWorldHopper();
			pendingHop = rs;
			hopTicks = 0;
		});
	}

	@Subscribe
	public void onOverlayMenuClicked(OverlayMenuClicked event)
	{
		if (event.getOverlay() == overlay && HOP_OPTION.equals(event.getEntry().getOption()))
		{
			hopToSuggestion();
		}
	}

	@Subscribe
	public void onConfigChanged(ConfigChanged event)
	{
		if (!StarHopperConfig.GROUP.equals(event.getGroup()))
		{
			return;
		}
		String k = event.getKey();
		if ("pollSeconds".equals(k) || "endpoint".equals(k) || "authKey".equals(k))
		{
			schedulePolling();
		}
		else
		{
			recompute();
		}
	}

	// ------------------------------------------------------------------ accessors

	Suggestion getSuggestion()
	{
		List<Suggestion> r = ranked;
		return r.isEmpty() ? null : r.get(0);
	}

	String getStatus()
	{
		return status;
	}

	StarHopperConfig getConfig()
	{
		return config;
	}

	private static BufferedImage createIcon()
	{
		BufferedImage img = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = img.createGraphics();
		g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		Polygon star = new Polygon();
		for (int i = 0; i < 10; i++)
		{
			double r = i % 2 == 0 ? 7.5 : 3.2;
			double a = Math.PI / 5 * i - Math.PI / 2;
			star.addPoint((int) Math.round(8 + r * Math.cos(a)), (int) Math.round(8 + r * Math.sin(a)));
		}
		g.setColor(new Color(255, 196, 0));
		g.fillPolygon(star);
		g.setStroke(new BasicStroke(1f));
		g.setColor(new Color(120, 80, 0));
		g.drawPolygon(star);
		g.dispose();
		return img;
	}
}
