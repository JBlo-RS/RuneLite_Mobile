package awtdemo;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GradientPaint;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GraphicsEnvironment;
import java.awt.RenderingHints;
import java.awt.Toolkit;
import java.awt.AWTEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseWheelEvent;
import java.util.ArrayList;
import java.util.List;
import javax.swing.JTextField;
import java.awt.geom.Ellipse2D;

/** Milestone 2 target: a full-screen Swing window rendered through Caciocavallo. */
public final class Main {
    public static void main(String[] args) throws Exception {
        System.out.println("awtdemo: toolkit = " + Toolkit.getDefaultToolkit().getClass().getName());
        System.out.println("awtdemo: graphicsenv = " + GraphicsEnvironment.getLocalGraphicsEnvironment().getClass().getName());
        Dimension screen = Toolkit.getDefaultToolkit().getScreenSize();
        System.out.println("awtdemo: screen = " + screen.width + "x" + screen.height);
        SwingUtilities.invokeAndWait(() -> build(screen));
        System.out.println("awtdemo: window shown");
    }

    private static void build(Dimension screen) {
        JFrame frame = new JFrame("RuneLite Mobile - Swing demo");
        frame.setUndecorated(true);
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

        JPanel root = new JPanel(new BorderLayout());
        root.setBackground(new Color(0x2B2B2B));

        JLabel title = new JLabel("Swing on Android via Caciocavallo", JLabel.CENTER);
        title.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 32));
        title.setForeground(new Color(0xDC8A00));
        title.setBorder(BorderFactory.createEmptyBorder(16, 16, 8, 16));
        root.add(title, BorderLayout.NORTH);

        Animation anim = new Animation();
        root.add(anim, BorderLayout.CENTER);

        JPanel south = new JPanel(new FlowLayout(FlowLayout.CENTER, 24, 12));
        south.setOpaque(false);
        JButton button = new JButton("Click me");
        button.setFont(new Font(Font.DIALOG, Font.PLAIN, 20));
        JLabel clicks = new JLabel("clicks: 0");
        clicks.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 20));
        clicks.setForeground(Color.WHITE);
        int[] count = {0};
        button.addActionListener(e -> clicks.setText("clicks: " + ++count[0]));
        JTextField field = new JTextField("type here", 18);
        field.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 20));
        south.add(button);
        south.add(clicks);
        south.add(field);
        root.add(south, BorderLayout.SOUTH);

        frame.setContentPane(root);
        frame.setBounds(0, 0, screen.width, screen.height);
        frame.setVisible(true);

        // Show the latest raw input event of any kind, wherever it was delivered
        Toolkit.getDefaultToolkit().addAWTEventListener(e -> anim.lastEvent = describe(e),
                AWTEvent.MOUSE_EVENT_MASK | AWTEvent.MOUSE_WHEEL_EVENT_MASK | AWTEvent.KEY_EVENT_MASK);
        new Timer(16, e -> anim.repaint()).start();
    }

    private static String describe(AWTEvent e) {
        if (e instanceof MouseWheelEvent) {
            MouseWheelEvent w = (MouseWheelEvent) e;
            return "MOUSE_WHEEL rotation=" + w.getWheelRotation() + " at " + w.getX() + "," + w.getY();
        }
        if (e instanceof MouseEvent) {
            MouseEvent m = (MouseEvent) e;
            String id = m.getID() == MouseEvent.MOUSE_PRESSED ? "PRESSED" : m.getID() == MouseEvent.MOUSE_RELEASED
                    ? "RELEASED" : m.getID() == MouseEvent.MOUSE_CLICKED ? "CLICKED" : "MOUSE#" + m.getID();
            return "MOUSE_" + id + " button=" + m.getButton() + " at " + m.getX() + "," + m.getY()
                    + " on " + m.getComponent().getClass().getSimpleName();
        }
        if (e instanceof KeyEvent) {
            KeyEvent k = (KeyEvent) e;
            String id = k.getID() == KeyEvent.KEY_TYPED ? "TYPED '" + k.getKeyChar() + "'"
                    : (k.getID() == KeyEvent.KEY_PRESSED ? "PRESSED " : "RELEASED ") + KeyEvent.getKeyText(k.getKeyCode());
            return "KEY_" + id;
        }
        return e.toString();
    }

    /** Animated custom painting plus font samples and a repaint-rate counter. */
    private static final class Animation extends JPanel {
        private final long start = System.nanoTime();
        private long lastSecond = System.nanoTime();
        private int paints, paintsPerSecond;
        private int tapX = -1, tapY = -1, tapButton;
        private final List<int[]> trail = new ArrayList<>();
        private double zoom = 1.0;
        volatile String lastEvent = "(no input yet)";

        Animation() {
            setOpaque(false);
            MouseAdapter m = new MouseAdapter() {
                @Override
                public void mousePressed(MouseEvent e) {
                    tapX = e.getX();
                    tapY = e.getY();
                    tapButton = e.getButton();
                    trail.clear();
                }

                @Override
                public void mouseDragged(MouseEvent e) {
                    trail.add(new int[]{e.getX(), e.getY()});
                    if (trail.size() > 300) trail.remove(0);
                }

                @Override
                public void mouseWheelMoved(MouseWheelEvent e) {
                    zoom = Math.max(0.3, Math.min(2.0, zoom * Math.pow(0.9, e.getWheelRotation())));
                }
            };
            addMouseListener(m);
            addMouseMotionListener(m);
            addMouseWheelListener(m);
        }

        @Override
        protected void paintComponent(Graphics g0) {
            super.paintComponent(g0);
            Graphics2D g = (Graphics2D) g0.create();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            int w = getWidth(), h = getHeight();
            double t = (System.nanoTime() - start) / 1e9;

            g.setPaint(new GradientPaint(0, 0, new Color(0x1E3A5F), w, h, new Color(0x3D1E5F)));
            g.fillRoundRect(24, 8, w - 48, h - 16, 24, 24);

            // Orbiting circles
            int cx = w / 2, cy = h / 2, r = (int) (Math.min(w, h) / 3 * zoom);
            for (int i = 0; i < 8; i++) {
                double a = t * 1.5 + i * Math.PI / 4;
                float hue = (float) ((i / 8.0 + t * 0.1) % 1.0);
                g.setColor(Color.getHSBColor(hue, 0.8f, 1f));
                double x = cx + Math.cos(a) * r, y = cy + Math.sin(a) * r * 0.6;
                g.fill(new Ellipse2D.Double(x - 18, y - 18, 36, 36));
            }
            g.setStroke(new BasicStroke(3f));
            g.setColor(new Color(255, 255, 255, 120));
            g.drawOval(cx - r, (int) (cy - r * 0.6), 2 * r, (int) (2 * r * 0.6));

            // Font samples
            String[] fams = {Font.SERIF, Font.SANS_SERIF, Font.MONOSPACED, Font.DIALOG};
            int y = 56;
            for (String f : fams) {
                g.setFont(new Font(f, Font.PLAIN, 22));
                g.setColor(Color.WHITE);
                g.drawString(f + ": The quick brown fox 0123456789", 56, y);
                y += 32;
            }

            // Repaint rate
            paints++;
            long now = System.nanoTime();
            if (now - lastSecond >= 1_000_000_000L) {
                paintsPerSecond = paints;
                paints = 0;
                lastSecond = now;
            }
            g.setFont(new Font(Font.MONOSPACED, Font.BOLD, 20));
            g.setColor(new Color(0x7CFC00));
            g.drawString(String.format("Swing repaints: %d/s   t=%.1fs   zoom=%.2f", paintsPerSecond, t, zoom), 56, h - 32);
            g.setColor(Color.YELLOW);
            g.drawString("last event: " + lastEvent, 56, h - 60);

            if (trail.size() > 1) {
                g.setColor(new Color(0, 255, 255, 180));
                g.setStroke(new BasicStroke(5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                for (int i = 1; i < trail.size(); i++)
                    g.drawLine(trail.get(i - 1)[0], trail.get(i - 1)[1], trail.get(i)[0], trail.get(i)[1]);
            }
            if (tapX >= 0) {
                // red = left, green = middle, orange = right
                g.setColor(tapButton == 3 ? Color.ORANGE : tapButton == 2 ? Color.GREEN : Color.RED);
                g.setStroke(new BasicStroke(4f));
                g.drawLine(tapX - 20, tapY, tapX + 20, tapY);
                g.drawLine(tapX, tapY - 20, tapX, tapY + 20);
            }
            g.dispose();
        }
    }
}
