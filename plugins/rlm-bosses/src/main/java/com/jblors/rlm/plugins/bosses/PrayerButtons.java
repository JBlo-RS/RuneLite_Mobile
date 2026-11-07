package com.jblors.rlm.plugins.bosses;

import net.runelite.api.Client;
import net.runelite.api.EnumComposition;
import net.runelite.api.EnumID;
import net.runelite.api.ItemComposition;
import net.runelite.api.ParamID;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.util.Text;

/** Finds the protection prayers' buttons in the prayer book (used by Boss Helper and Lazy Flick). */
final class PrayerButtons
{
	private static final int FIRST_BUTTON = InterfaceID.Prayerbook.PRAYER1, MAX_BUTTONS = 64;

	private PrayerButtons()
	{
	}

	/** The prayer's button: from the game's prayer list (as RuneLite's prayer plugin), else by name. */
	static Widget find(Client client, Style style, int[] buttonCache)
	{
		int cached = buttonCache[style.ordinal()];
		if (cached != 0)
		{
			Widget w = client.getWidget(cached);
			if (w != null)
			{
				return w;
			}
			buttonCache[style.ordinal()] = 0;
		}
		EnumComposition prayers = client.getEnum(EnumID.PRAYERS_NORMAL);
		if (prayers != null)
		{
			for (int obj : prayers.getIntVals())
			{
				ItemComposition def = client.getItemDefinition(obj);
				if (def == null || !style.prayerName.equalsIgnoreCase(Text.removeTags(def.getName()).trim()))
				{
					continue;
				}
				int component = def.getIntValue(ParamID.OC_PRAYER_COMPONENT);
				Widget w = component > 0 ? client.getWidget(component) : null;
				if (w != null)
				{
					buttonCache[style.ordinal()] = component;
					return w;
				}
			}
		}
		for (int id = FIRST_BUTTON; id < FIRST_BUTTON + MAX_BUTTONS; id++)
		{
			Widget w = client.getWidget(id);
			if (w != null && w.getName() != null && style.prayerName.equalsIgnoreCase(Text.removeTags(w.getName()).trim()))
			{
				buttonCache[style.ordinal()] = id;
				return w;
			}
		}
		return null;
	}
}
