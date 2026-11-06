package com.jblors.rlm.plugins.mobile;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import javax.inject.Inject;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.Skill;
import net.runelite.api.gameval.VarPlayerID;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayPosition;

/** Draws the bars stacked; a normal overlay, so it can be moved (hold Alt and drag it). */
class BigBarsOverlay extends Overlay
{
	private static final Color BACK = new Color(0, 0, 0, 160);
	private static final Color EMPTY = new Color(60, 60, 60, 200);
	private static final Color HP = new Color(200, 30, 30);
	private static final Color POISON = new Color(30, 160, 40);
	private static final Color VENOM = new Color(20, 90, 40);
	private static final Color PRAYER = new Color(40, 170, 220);
	private static final Color SPECIAL = new Color(220, 170, 30);
	private static final Color RUN = new Color(235, 120, 40);
	private static final int GAP = 4, PAD = 4;

	private final Client client;
	private final BigBarsConfig config;

	@Inject
	BigBarsOverlay(Client client, BigBarsConfig config)
	{
		this.client = client;
		this.config = config;
		setPosition(OverlayPosition.TOP_CENTER);
		setMovable(true);
	}

	@Override
	public Dimension render(Graphics2D g)
	{
		if (client.getGameState() != GameState.LOGGED_IN)
		{
			return null;
		}
		int w = config.width(), h = config.height();
		int rows = (config.hitpoints() ? 1 : 0) + (config.prayer() ? 1 : 0) + (config.special() ? 1 : 0) + (config.run() ? 1 : 0);
		if (rows == 0)
		{
			return null;
		}
		g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
		g.setFont(new Font("Dialog", Font.BOLD, Math.max(10, h * 2 / 3)));
		int totalH = PAD * 2 + rows * h + (rows - 1) * GAP;
		g.setColor(BACK);
		g.fillRoundRect(0, 0, w + PAD * 2, totalH, 8, 8);

		boolean flashOn = (System.currentTimeMillis() / 300) % 2 == 0;
		int y = PAD;
		if (config.hitpoints())
		{
			int cur = client.getBoostedSkillLevel(Skill.HITPOINTS), max = client.getRealSkillLevel(Skill.HITPOINTS);
			int poison = client.getVarpValue(VarPlayerID.POISON);
			Color c = poison >= 1_000_000 ? VENOM : poison > 0 ? POISON : HP;
			boolean low = config.lowHp() > 0 && cur * 100 < max * config.lowHp();
			bar(g, y, w, h, "HP", cur, max, c, low && flashOn);
			y += h + GAP;
		}
		if (config.prayer())
		{
			int cur = client.getBoostedSkillLevel(Skill.PRAYER), max = client.getRealSkillLevel(Skill.PRAYER);
			boolean low = config.lowPrayer() > 0 && cur * 100 < max * config.lowPrayer();
			bar(g, y, w, h, "Prayer", cur, max, PRAYER, low && flashOn);
			y += h + GAP;
		}
		if (config.special())
		{
			bar(g, y, w, h, "Spec", client.getVarpValue(VarPlayerID.SA_ENERGY) / 10, 100, SPECIAL, false);
			y += h + GAP;
		}
		if (config.run())
		{
			bar(g, y, w, h, "Run", client.getEnergy() / 100, 100, RUN, false);
		}
		return new Dimension(w + PAD * 2, totalH);
	}

	private void bar(Graphics2D g, int y, int w, int h, String label, int cur, int max, Color color, boolean flash)
	{
		int x = PAD;
		g.setColor(EMPTY);
		g.fillRect(x, y, w, h);
		int fill = max <= 0 ? 0 : (int) ((long) w * Math.max(0, Math.min(cur, max)) / max);
		g.setColor(flash ? Color.WHITE : color);
		g.fillRect(x, y, fill, h);
		g.setColor(flash ? Color.RED : new Color(0, 0, 0, 200));
		g.setStroke(new BasicStroke(flash ? 2 : 1));
		g.drawRect(x, y, w - 1, h - 1);

		String text = config.numbers() ? label + "  " + cur + " / " + max : label;
		FontMetrics fm = g.getFontMetrics();
		int tx = x + 6, ty = y + (h + fm.getAscent() - fm.getDescent()) / 2;
		g.setColor(Color.BLACK);
		g.drawString(text, tx + 1, ty + 1);
		g.setColor(Color.WHITE);
		g.drawString(text, tx, ty);
	}
}
