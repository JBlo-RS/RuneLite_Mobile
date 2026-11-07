package com.jblors.rlm.plugins.mobile;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;

@ConfigGroup("rlmmobilecontrols")
public interface MobileControlsConfig extends Config
{
	@ConfigItem(keyName = "closeOnLogout", name = "Back to the launcher on logout",
		description = "When you log out, close RuneLite and go back to RuneLite Mobile's launch screen (to share the log, change settings or start again)",
		position = 1)
	default boolean closeOnLogout()
	{
		return true;
	}
}
