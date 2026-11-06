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
import net.runelite.api.events.GameTick;
import net.runelite.api.events.NpcChanged;
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

	@Subscribe
	public void onAnimationChanged(AnimationChanged e)
	{
		Boss b = bossOf(e.getActor());
		if (b == null)
		{
			return;
		}
		Style s = b.attackAnimations.get(e.getActor().getAnimation());
		if (s != null)
		{
			style = s;
		}
	}

	@Subscribe
	public void onNpcChanged(NpcChanged e)
	{
		// A boss changing form (Phantom Muspah)
		Boss b = bossOf(e.getNpc());
		if (b != null)
		{
			Style s = b.forms.get(e.getNpc().getId());
			if (s != null)
			{
				style = s;
			}
		}
	}

	@Subscribe
	public void onGameTick(GameTick e)
	{
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
			Style form = b.forms.get(npc.getId());
			if (form != null && me != null && npc.getInteracting() == me && style == null)
			{
				style = form;
			}
		}
		if (!present)
		{
			style = null;
			return;
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
				Style s = b == null ? null : b.projectiles.get(p.getId());
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
		}
	}

	@Provides
	BossHelperConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(BossHelperConfig.class);
	}
}
