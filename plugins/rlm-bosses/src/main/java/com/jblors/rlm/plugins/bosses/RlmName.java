package com.jblors.rlm.plugins.bosses;

/**
 * Plugin names start with this so RuneLite Mobile's own plugins stand out in the plugin list:
 * the list's labels show HTML, so the name is drawn in cyan, and the list is sorted by name, so
 * "<html>" (before any letter) puts them at the top, under only the pinned plugins. No closing
 * </html>: RuneLite wraps the name in its own <html> for the tooltip.
 */
final class RlmName
{
	static final String PREFIX = "<html><font color='#5CE1E6'>";

	private RlmName()
	{
	}
}
