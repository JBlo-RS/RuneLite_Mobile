package com.jblors.rlm.plugins.scurrius;

import com.google.inject.Provides;
import javax.inject.Inject;
import net.runelite.api.Actor;
import net.runelite.api.NPC;
import net.runelite.api.events.AnimationChanged;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.NpcDespawned;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.overlay.OverlayManager;

// The class name stays ScurriusBricksPlugin so the plugin's on/off state and settings carry over
@PluginDescriptor(
	name = "Scurrius Helper",
	description = "Marks where Scurrius' falling bricks land and shows the prayer against his last attack",
	tags = {"scurrius", "rat", "boss", "bricks", "debris", "falling", "tiles", "prayer", "protect", "rlm"}
)
public class ScurriusBricksPlugin extends Plugin
{
	/** NpcID.RAT_BOSS_NORMAL, NpcID.RAT_BOSS_INSTANCE */
	private static final int SCURRIUS = 7221, SCURRIUS_INSTANCE = 7222;

	@Inject
	private OverlayManager overlayManager;

	@Inject
	private ScurriusBricksOverlay bricksOverlay;

	@Inject
	private PrayerOverlay prayerOverlay;

	@Inject
	private ScurriusBricksConfig config;

	/** Style of Scurrius' last attack: pray against it until he attacks differently */
	private volatile AttackStyle nextStyle;

	@Override
	protected void startUp()
	{
		overlayManager.add(bricksOverlay);
		overlayManager.add(prayerOverlay);
	}

	@Override
	protected void shutDown()
	{
		overlayManager.remove(bricksOverlay);
		overlayManager.remove(prayerOverlay);
		nextStyle = null;
	}

	AttackStyle getNextStyle()
	{
		return config.prayerHelper() ? nextStyle : null;
	}

	@Subscribe
	public void onAnimationChanged(AnimationChanged e)
	{
		Actor a = e.getActor();
		if (!(a instanceof NPC) || !isScurrius((NPC) a))
		{
			return;
		}
		AttackStyle style = AttackStyle.ofAnimation(a.getAnimation());
		if (style != null)
		{
			nextStyle = style;
		}
	}

	@Subscribe
	public void onNpcDespawned(NpcDespawned e)
	{
		if (isScurrius(e.getNpc()))
		{
			nextStyle = null;
		}
	}

	@Subscribe
	public void onGameStateChanged(GameStateChanged e)
	{
		nextStyle = null; // logging out, hopping, or leaving the area (loading)
	}

	private static boolean isScurrius(NPC npc)
	{
		int id = npc.getId();
		return id == SCURRIUS || id == SCURRIUS_INSTANCE;
	}

	@Provides
	ScurriusBricksConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(ScurriusBricksConfig.class);
	}
}
