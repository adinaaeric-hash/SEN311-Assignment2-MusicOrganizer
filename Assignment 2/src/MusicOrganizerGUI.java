import javax.swing.*;
import javax.swing.border.Border;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.Arc2D;
import java.awt.geom.Path2D;
import java.io.OutputStream;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Optional, cute Swing front end for MusicOrganizer (not required by the assignment).
 * Every button calls the same methods as the exercises. What those methods print is
 * shown in the Console card; the Library card shows the tracks as little cards.
 */
public class MusicOrganizerGUI extends JFrame
{
    // Palette
    static final Color MAROON   = new Color(0x4C0519);
    static final Color ROSE     = new Color(0x9F1239);
    static final Color YELLOW   = new Color(0xF1C40F);
    static final Color LAVENDER = new Color(0xF5F3FF);
    static final Color NAVY     = new Color(0x1E293B);
    static final Color CREAM    = new Color(0xFFF1B8);

    /** How long (in milliseconds) the "Now playing" bar stays lit after Play by artist. */
    static final int ARTIST_DISPLAY_MS = 20000;

    public MusicOrganizerGUI()
    {
        super("Music Organizer");
        setContentPane(new ContentPanel());
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        pack();
        setMinimumSize(getSize());
        setLocationRelativeTo(null);
    }

    public static void main(String[] args)
    {
        SwingUtilities.invokeLater(() -> new MusicOrganizerGUI().setVisible(true));
    }

    // ---------- shared drawing helpers ----------

    static Font font(int style, int size) { return new Font("Segoe UI", style, size); }

    static Graphics2D g2(Graphics g0)
    {
        Graphics2D g = (Graphics2D) g0.create();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        return g;
    }

    static String fit(String s, FontMetrics fm, int max)
    {
        if (fm.stringWidth(s) <= max) return s;
        while (s.length() > 1 && fm.stringWidth(s + "...") > max) s = s.substring(0, s.length() - 1);
        return s + "...";
    }

    static void sparkle(Graphics2D g, double cx, double cy, double s, Color c)
    {
        Path2D.Double p = new Path2D.Double();
        p.moveTo(cx, cy - s);
        p.lineTo(cx + s * .25, cy - s * .25);
        p.lineTo(cx + s, cy);
        p.lineTo(cx + s * .25, cy + s * .25);
        p.lineTo(cx, cy + s);
        p.lineTo(cx - s * .25, cy + s * .25);
        p.lineTo(cx - s, cy);
        p.lineTo(cx - s * .25, cy - s * .25);
        p.closePath();
        g.setColor(c);
        g.fill(p);
    }

    static final Color[] SERIES = {
        ROSE, YELLOW, NAVY, MAROON, new Color(0xE11D48), new Color(0xD97706),
        new Color(0x64748B), new Color(0xA78BFA) };

    /** A little heart drawn as a shape (so it never depends on a font having the symbol). */
    static void heart(Graphics2D g, double cx, double cy, double s, Color c)
    {
        Path2D.Double p = new Path2D.Double();
        p.moveTo(cx, cy + s);
        p.curveTo(cx - s * 1.6, cy - s * 0.2, cx - s * 0.7, cy - s * 1.3, cx, cy - s * 0.35);
        p.curveTo(cx + s * 0.7, cy - s * 1.3, cx + s * 1.6, cy - s * 0.2, cx, cy + s);
        p.closePath();
        g.setColor(c);
        g.fill(p);
    }

    static void paintDots(Graphics2D g, int w, int h)
    {
        g.setColor(new Color(0x9F, 0x12, 0x39, 22));
        for (int y = 0, row = 0; y < h + 28; y += 28, row++) {
            for (int x = (row % 2 == 0) ? 0 : 14; x < w + 28; x += 28) {
                g.fillOval(x, y, 6, 6);
            }
        }
    }

    // =====================================================================
    //  The whole window content
    // =====================================================================
    public static class ContentPanel extends JPanel
    {
        private final MusicOrganizer organizer = new MusicOrganizer();
        private final JTextArea output = new JTextArea();
        private final JTextField fileField = new JTextField("audio/Artist-Title.mp3", 20);
        private final JTextField searchField = new JTextField(12);
        private final JTextField yearField = new JTextField(5);
        private final JSpinner indexSpinner = new JSpinner(new SpinnerNumberModel(0, -5, 999, 1));
        private final DefaultListModel<Track> model = new DefaultListModel<Track>();
        private final JList<Track> library;
        private final NowPlayingBar nowBar = new NowPlayingBar();
        private boolean syncing = false;

        // Bumped whenever something else takes over the "Now playing" bar, so an
        // older "Play by artist" timer never switches the bar off by mistake.
        private int playToken = 0;

        // extra windows: they redraw whenever something changes
        private final List<Runnable> observers = new ArrayList<Runnable>();
        private final Map<Track, Integer> lastCounts = new IdentityHashMap<Track, Integer>();
        private final List<String> playLog = new ArrayList<String>();
        private JFrame chartsWin, detailsWin, historyWin;

        public ContentPanel()
        {
            setLayout(new BorderLayout());
            redirectConsoleToTextArea();

            // ----- library (left) -----
            library = new JList<Track>(model) {
                @Override protected void paintComponent(Graphics g0) {
                    super.paintComponent(g0);
                    if (model.isEmpty()) {
                        Graphics2D g = g2(g0);
                        g.setFont(font(Font.BOLD, 13));
                        g.setColor(ROSE);
                        String a = "No tracks yet";
                        String b = "load the samples to begin \u2665";
                        g.drawString(a, (getWidth() - g.getFontMetrics().stringWidth(a)) / 2, 70);
                        g.setFont(font(Font.PLAIN, 12));
                        g.drawString(b, (getWidth() - g.getFontMetrics().stringWidth(b)) / 2, 90);
                        sparkle(g, getWidth() / 2.0, 40, 10, YELLOW);
                        g.dispose();
                    }
                }
            };
            library.setOpaque(false);
            library.setCellRenderer(new TrackRow());
            library.setFixedCellHeight(60);
            library.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
            library.addListSelectionListener(e -> {
                if (!syncing && library.getSelectedIndex() >= 0) {
                    indexSpinner.setValue(library.getSelectedIndex());
                }
            });
            library.addMouseListener(new MouseAdapter() {
                @Override public void mouseClicked(MouseEvent e) {
                    if (e.getClickCount() == 2 && library.getSelectedIndex() >= 0) {
                        organizer.playTrack(library.getSelectedIndex());
                        showNowPlaying(library.getSelectedIndex());
                        refresh();
                    }
                }
            });
            JScrollPane libScroll = new JScrollPane(library);
            libScroll.setOpaque(false);
            libScroll.getViewport().setOpaque(false);
            libScroll.setBorder(BorderFactory.createEmptyBorder(0, 8, 6, 8));
            RoundPanel libraryCard = card("My Library", Color.WHITE, ROSE, libScroll);
            libraryCard.setPreferredSize(new Dimension(380, 330));

            indexSpinner.addChangeListener(e -> {
                if (syncing) return;
                int i = index();
                syncing = true;
                if (organizer.validIndex(i)) {
                    library.setSelectedIndex(i);
                    library.ensureIndexIsVisible(i);
                } else {
                    library.clearSelection();
                }
                syncing = false;
                notifyObservers();
            });

            // ----- controls (right) -----
            styleField(fileField);
            styleField(searchField);
            styleField(yearField);
            styleSpinner(indexSpinner);

            RoundPanel addCard = card("Add files", Color.WHITE, ROSE, stack(
                row(label("File name"), fileField,
                    button("Add file", YELLOW, MAROON, () -> {
                        String name = fileField.getText().trim();
                        // format check: optional folder, then Artist-Title.mp3
                        if (!name.matches("^(.*[/\\\\])?[^/\\\\-]+-[^/\\\\]+\\.mp3$")) {
                            System.out.println("Invalid file name: \"" + name + "\"");
                            System.out.println("Use the format audio/Artist-Title.mp3");
                            return;
                        }
                        organizer.addFile(name);
                        System.out.println("Added. Files stored: " + organizer.numberOfFiles());
                    }),
                    button("Load samples", YELLOW, MAROON, () -> {
                        organizer.addFile("audio/Beethoven-FurElise.mp3");
                        organizer.addFile("audio/Beethoven-MoonlightSonata.mp3");
                        organizer.addFile("audio/BlindBlake-EarlyMorningBlues.mp3");
                        organizer.addFile("audio/Mozart-EineKleineNachtmusik.mp3");
                        System.out.println("Sample files loaded. Files stored: " + organizer.numberOfFiles());
                    }))));

            RoundPanel indexCard = card("By index", Color.WHITE, ROSE, stack(
                row(label("Index"), indexSpinner,
                    button("List file", ROSE, LAVENDER, () -> organizer.listFile(index())),
                    button("Remove file", MAROON, LAVENDER, () -> organizer.removeFile(index())),
                    button("Play", NAVY, YELLOW, () -> {
                        organizer.playTrack(index());
                        showNowPlaying(index());
                    }),
                    button("\u25A0 Stop", NAVY, YELLOW, () -> {
                        organizer.stopPlaying();
                        playToken++;
                        nowBar.setNow(null);
                    })),
                row(button("Check index", ROSE, LAVENDER, () -> organizer.checkIndex(index())),
                    button("Valid index?", ROSE, LAVENDER, () ->
                        System.out.println("validIndex(" + index() + ") = " + organizer.validIndex(index()))),
                    label("Year"), yearField,
                    button("Set year", ROSE, LAVENDER, () -> {
                        try {
                            organizer.setYear(index(), Integer.parseInt(yearField.getText().trim()));
                            organizer.listFile(index());
                        } catch (NumberFormatException e) {
                            System.out.println("Please enter the year as a whole number.");
                        }
                    }))));

            RoundPanel listCard = card("Whole collection", Color.WHITE, ROSE, stack(
                row(button("Number of files", ROSE, LAVENDER, () ->
                        System.out.println("numberOfFiles() = " + organizer.numberOfFiles())),
                    button("List all", ROSE, LAVENDER, () -> organizer.listAllFiles()),
                    button("With positions", ROSE, LAVENDER, () -> organizer.listAllFilesWithPosition()),
                    button("Clear console", MAROON, LAVENDER, () -> output.setText("")))));

            RoundPanel searchCard = card("Search text", Color.WHITE, ROSE, stack(
                row(label("Text"), searchField,
                    button("List matching", ROSE, LAVENDER, () -> organizer.listMatching(search())),
                    button("Matching + message", ROSE, LAVENDER, () -> organizer.listMatchingWithMessage(search()))),
                row(button("Play by artist", NAVY, YELLOW, () -> {
                        final String artist = search();
                        final int myToken = ++playToken;
                        nowBar.setNow("samples by " + artist);
                        final long start = System.currentTimeMillis();
                        // playAndWait blocks, so run it off the GUI thread
                        new Thread(() -> {
                            organizer.playByArtist(artist);
                            // keep the bar lit for at least ARTIST_DISPLAY_MS in total
                            long left = ARTIST_DISPLAY_MS - (System.currentTimeMillis() - start);
                            if (left > 0) {
                                try { Thread.sleep(left); } catch (InterruptedException ex) { }
                            }
                            SwingUtilities.invokeLater(() -> {
                                // only switch the bar off if nothing else took it over meanwhile
                                if (myToken == playToken) nowBar.setNow(null);
                                refresh();
                            });
                        }).start();
                    }),
                    button("Remove by title", MAROON, LAVENDER, () -> organizer.removeByTitle(search())))));

            JPanel controls = new JPanel();
            controls.setLayout(new BoxLayout(controls, BoxLayout.Y_AXIS));
            controls.setOpaque(false);
            for (RoundPanel c : new RoundPanel[] { addCard, indexCard, listCard, searchCard }) {
                c.setAlignmentX(Component.LEFT_ALIGNMENT);
                controls.add(c);
                controls.add(Box.createVerticalStrut(4));
            }

            // ----- console -----
            output.setEditable(false);
            output.setLineWrap(true);
            output.setWrapStyleWord(true);
            output.setOpaque(false);
            output.setForeground(LAVENDER);
            output.setCaretColor(YELLOW);
            output.setMargin(new Insets(2, 8, 2, 8));
            output.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
            JScrollPane outScroll = new JScrollPane(output);
            outScroll.setOpaque(false);
            outScroll.getViewport().setOpaque(false);
            outScroll.setBorder(BorderFactory.createEmptyBorder(0, 8, 6, 8));
            RoundPanel console = card("Console  >_", NAVY, YELLOW, outScroll);
            console.setPreferredSize(new Dimension(380, 190));

            JPanel right = new JPanel(new BorderLayout(0, 4));
            right.setOpaque(false);
            right.add(controls, BorderLayout.NORTH);

            JPanel body = new JPanel(new BorderLayout(10, 0));
            body.setOpaque(false);
            body.setBorder(BorderFactory.createEmptyBorder(2, 14, 2, 14));
            // library on top, console below; drag the gap between them to resize
            JSplitPane left = new JSplitPane(JSplitPane.VERTICAL_SPLIT, libraryCard, console);
            left.setUI(new javax.swing.plaf.basic.BasicSplitPaneUI() {
                @Override public javax.swing.plaf.basic.BasicSplitPaneDivider createDefaultDivider() {
                    return new javax.swing.plaf.basic.BasicSplitPaneDivider(this) {
                        @Override public void paint(Graphics g) { }
                    };
                }
            });
            left.setBorder(null);
            left.setOpaque(false);
            left.setDividerSize(8);
            left.setResizeWeight(0.7);
            left.setContinuousLayout(true);
            body.add(left, BorderLayout.WEST);
            body.add(right, BorderLayout.CENTER);

            JPanel south = new JPanel(new BorderLayout(10, 0));
            south.setOpaque(false);
            south.setBorder(BorderFactory.createEmptyBorder(2, 14, 12, 14));
            south.add(nowBar, BorderLayout.CENTER);
            JPanel winButtons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
            winButtons.setOpaque(false);
            winButtons.add(windowButton("Dashboard", "Opens a window with graphs of your songs", () -> openWindow(1)));
            winButtons.add(windowButton("Track details", "Opens a window showing the selected song up close", () -> openWindow(2)));
            winButtons.add(windowButton("History", "Opens a window listing every play", () -> openWindow(3)));
            JPanel winWrap = new JPanel(new GridBagLayout());
            winWrap.setOpaque(false);
            winWrap.add(winButtons);
            south.add(winWrap, BorderLayout.EAST);

            add(new Header(), BorderLayout.NORTH);
            add(body, BorderLayout.CENTER);
            add(south, BorderLayout.SOUTH);

            System.out.println("Welcome! Press \"Load samples\" to get started.");
            refresh();
        }

        @Override
        protected void paintComponent(Graphics g0)
        {
            Graphics2D g = g2(g0);
            g.setColor(LAVENDER);
            g.fillRect(0, 0, getWidth(), getHeight());
            g.setColor(new Color(0x9F, 0x12, 0x39, 22));
            for (int y = 0, row = 0; y < getHeight() + 28; y += 28, row++) {
                for (int x = (row % 2 == 0) ? 0 : 14; x < getWidth() + 28; x += 28) {
                    g.fillOval(x, y, 6, 6);
                }
            }
            g.dispose();
        }


        // =================================================================
        //  Extra windows
        // =================================================================

        private void notifyObservers()
        {
            for (Runnable r : new ArrayList<Runnable>(observers)) r.run();
        }

        private Track selectedTrack()
        {
            int i = index();
            return organizer.validIndex(i) ? organizer.getTracks().get(i) : null;
        }

        private JButton windowButton(String text, String tip, Runnable action)
        {
            RoundButton b = new RoundButton(text, ROSE, LAVENDER);
            b.setToolTipText(tip);
            b.addActionListener(e -> action.run());
            return b;
        }

        private void openWindow(int which)
        {
            JFrame f;
            if (which == 1) {
                if (chartsWin == null) chartsWin = makeWindow("Music Organizer - Charts", createChartsPanel());
                f = chartsWin;
            } else if (which == 2) {
                if (detailsWin == null) detailsWin = makeWindow("Music Organizer - Track details", createDetailsPanel());
                f = detailsWin;
            } else {
                if (historyWin == null) historyWin = makeWindow("Music Organizer - Play history", createHistoryPanel());
                f = historyWin;
            }
            if (!f.isVisible()) {
                Window owner = SwingUtilities.getWindowAncestor(this);
                Dimension screen = Toolkit.getDefaultToolkit().getScreenSize();
                int x = 60 + (which - 1) * 40, y = 50 + (which - 1) * 40;
                if (owner != null) {
                    int side = owner.getX() + owner.getWidth() + 8;
                    if (which != 1 && side + f.getWidth() <= screen.width) { x = side; y = owner.getY(); }
                    else { x = owner.getX() + 40 + (which - 1) * 40; y = owner.getY() + 40 + (which - 1) * 40; }
                }
                f.setLocation(Math.max(0, x), Math.max(0, y));
            }
            f.setVisible(true);
            f.toFront();
        }

        private JFrame makeWindow(String title, JPanel content)
        {
            JFrame f = new JFrame(title);
            f.setContentPane(content);
            f.setDefaultCloseOperation(JFrame.HIDE_ON_CLOSE);
            f.pack();
            f.setMinimumSize(new Dimension(f.getWidth() * 3 / 4, f.getHeight() * 3 / 4));
            return f;
        }

        private JPanel pad(JComponent c)
        {
            JPanel q = new JPanel(new BorderLayout());
            q.setOpaque(false);
            q.setBorder(BorderFactory.createEmptyBorder(0, 8, 6, 8));
            q.add(c, BorderLayout.CENTER);
            return q;
        }

        // ---------- Charts window ----------

        public JPanel createChartsPanel()
        {
            BackgroundPanel p = new BackgroundPanel();
            p.add(new MiniHeader("Dashboard", "see how your songs are doing"), BorderLayout.NORTH);
            JPanel center = new JPanel(new BorderLayout(0, 8));
            center.setOpaque(false);
            center.setBorder(BorderFactory.createEmptyBorder(2, 14, 8, 14));
            StatsStrip strip = new StatsStrip();
            strip.setPreferredSize(new Dimension(100, 62));
            center.add(strip, BorderLayout.NORTH);
            JPanel grid = new JPanel(new GridLayout(2, 2, 6, 6));
            grid.setOpaque(false);
            grid.add(card("Plays per song", Color.WHITE, ROSE, pad(playsChart())));
            grid.add(card("Songs per artist", Color.WHITE, ROSE, pad(artistChart())));
            grid.add(card("Plays over time", Color.WHITE, ROSE, pad(raceChart())));
            grid.add(card("Songs by year", Color.WHITE, ROSE, pad(yearsChart())));
            center.add(grid, BorderLayout.CENTER);
            p.add(center, BorderLayout.CENTER);
            p.setPreferredSize(new Dimension(880, 690));
            observers.add(() -> p.repaint());
            return p;
        }

        private class StatsStrip extends ChartCanvas
        {
            @Override void draw(Graphics2D g, int w, int h)
            {
                List<Track> ts = organizer.getTracks();
                int plays = 0;
                Track top = null;
                java.util.Set<String> artists = new java.util.HashSet<String>();
                for (Track t : ts) {
                    plays += t.getPlayCount();
                    artists.add(t.getArtist());
                    if (top == null || t.getPlayCount() > top.getPlayCount()) top = t;
                }
                String topName = (top == null || top.getPlayCount() == 0) ? "-" : top.getTitle();
                String[] labels = { "Songs", "Total plays", "Artists", "Most played" };
                String[] values = { String.valueOf(ts.size()), String.valueOf(plays),
                                    String.valueOf(artists.size()), topName };
                int gap = 10;
                int cw = (w - 3 * gap - 6) / 4;
                for (int i = 0; i < 4; i++) {
                    int x = i * (cw + gap);
                    g.setColor(new Color(0x4C, 0x05, 0x19, 30));
                    g.fillRoundRect(x + 3, 5, cw, h - 8, 24, 24);
                    g.setColor(Color.WHITE);
                    g.fillRoundRect(x, 2, cw, h - 8, 24, 24);
                    g.setColor(new Color(0x9F, 0x12, 0x39, 80));
                    g.drawRoundRect(x, 2, cw, h - 8, 24, 24);
                    g.setColor(SERIES[i]);
                    g.fillOval(x + 12, (h - 8) / 2 - 6, 16, 16);
                    g.setFont(font(Font.PLAIN, 11));
                    g.setColor(ROSE);
                    g.drawString(labels[i], x + 38, 22);
                    g.setFont(font(Font.BOLD, 18));
                    g.setColor(MAROON);
                    g.drawString(fit(values[i], g.getFontMetrics(), cw - 48), x + 38, 46);
                }
            }
        }

        private ChartCanvas playsChart()
        {
            return new ChartCanvas() {
                @Override void draw(Graphics2D g, int w, int h)
                {
                    List<Track> ts = organizer.getTracks();
                    if (ts.isEmpty()) { hint(g, w, h, "No songs yet", "load the samples to begin"); return; }
                    int left = 34, right = 10, top = 16, bottom = 44;
                    int pw = w - left - right, ph = h - top - bottom;
                    int max = 1;
                    for (Track t : ts) max = Math.max(max, t.getPlayCount());
                    int step = Math.max(1, (int) Math.ceil(max / 4.0));
                    int topVal = ((max + step - 1) / step) * step;
                    g.setFont(font(Font.PLAIN, 10));
                    FontMetrics fm = g.getFontMetrics();
                    for (int v = 0; v <= topVal; v += step) {
                        int y = top + ph - (int) ((long) ph * v / topVal);
                        g.setColor(new Color(0x9F, 0x12, 0x39, 40));
                        g.drawLine(left, y, w - right, y);
                        g.setColor(ROSE);
                        String label = String.valueOf(v);
                        g.drawString(label, left - 6 - fm.stringWidth(label), y + 4);
                    }
                    int n = ts.size();
                    int slot = pw / n;
                    int bw = Math.min(46, (int) (slot * 0.62));
                    for (int i = 0; i < n; i++) {
                        Track t = ts.get(i);
                        int bh = (int) ((long) ph * t.getPlayCount() / topVal);
                        int x = left + i * slot + (slot - bw) / 2;
                        int y = top + ph - Math.max(bh, 4);
                        g.setColor(SERIES[i % SERIES.length]);
                        g.fillRoundRect(x, y, bw, Math.max(bh, 4), 10, 10);
                        g.setFont(font(Font.BOLD, 11));
                        g.setColor(MAROON);
                        String count = String.valueOf(t.getPlayCount());
                        g.drawString(count, x + bw / 2 - g.getFontMetrics().stringWidth(count) / 2, y - 4);
                        g.setFont(font(Font.BOLD, 10));
                        String title = fit(t.getTitle(), g.getFontMetrics(), slot - 4);
                        g.drawString(title, left + i * slot + (slot - g.getFontMetrics().stringWidth(title)) / 2, top + ph + 16);
                        g.setFont(font(Font.PLAIN, 10));
                        g.setColor(ROSE);
                        String art = fit(t.getArtist(), g.getFontMetrics(), slot - 4);
                        g.drawString(art, left + i * slot + (slot - g.getFontMetrics().stringWidth(art)) / 2, top + ph + 29);
                    }
                    g.setColor(ROSE);
                    g.drawLine(left, top + ph, w - right, top + ph);
                }
            };
        }

        private ChartCanvas artistChart()
        {
            return new ChartCanvas() {
                @Override void draw(Graphics2D g, int w, int h)
                {
                    List<Track> ts = organizer.getTracks();
                    if (ts.isEmpty()) { hint(g, w, h, "No songs yet", "load the samples to begin"); return; }
                    Map<String, Integer> byArtist = new LinkedHashMap<String, Integer>();
                    for (Track t : ts) byArtist.merge(t.getArtist(), 1, Integer::sum);
                    int total = ts.size();
                    int d = Math.max(40, Math.min(h - 24, (int) (w * 0.46)));
                    int cx = 14, cy = (h - d) / 2;
                    double start = 90;
                    int i = 0;
                    for (Map.Entry<String, Integer> e : byArtist.entrySet()) {
                        double extent = -360.0 * e.getValue() / total;
                        Arc2D.Double arc = new Arc2D.Double(cx, cy, d, d, start, extent, Arc2D.PIE);
                        g.setColor(SERIES[i % SERIES.length]);
                        g.fill(arc);
                        g.setColor(Color.WHITE);
                        g.setStroke(new BasicStroke(2.5f));
                        g.draw(arc);
                        g.setStroke(new BasicStroke(1f));
                        start += extent;
                        i++;
                    }
                    int hd = (int) (d * 0.56);
                    g.setColor(Color.WHITE);
                    g.fillOval(cx + (d - hd) / 2, cy + (d - hd) / 2, hd, hd);
                    g.setColor(MAROON);
                    g.setFont(font(Font.BOLD, 22));
                    String big = String.valueOf(total);
                    g.drawString(big, cx + d / 2 - g.getFontMetrics().stringWidth(big) / 2, cy + d / 2 + 4);
                    g.setFont(font(Font.PLAIN, 11));
                    g.setColor(ROSE);
                    String small = total == 1 ? "song" : "songs";
                    g.drawString(small, cx + d / 2 - g.getFontMetrics().stringWidth(small) / 2, cy + d / 2 + 20);
                    int lx = cx + d + 22, rowH = 24;
                    int ly = Math.max(8, h / 2 - (byArtist.size() * rowH) / 2);
                    i = 0;
                    for (Map.Entry<String, Integer> e : byArtist.entrySet()) {
                        g.setColor(SERIES[i % SERIES.length]);
                        g.fillRoundRect(lx, ly + i * rowH, 13, 13, 5, 5);
                        g.setFont(font(Font.BOLD, 12));
                        g.setColor(MAROON);
                        g.drawString(fit(e.getKey() + "  (" + e.getValue() + ")", g.getFontMetrics(), w - lx - 24),
                            lx + 22, ly + i * rowH + 12);
                        i++;
                    }
                }
            };
        }

        private ChartCanvas raceChart()
        {
            return new ChartCanvas() {
                @Override void draw(Graphics2D g, int w, int h)
                {
                    List<Track> ts = organizer.getTracks();
                    int n = playLog.size();
                    if (ts.isEmpty() || n == 0) { hint(g, w, h, "No plays yet", "press Play on a song to start the race"); return; }
                    int series = Math.min(ts.size(), 6);
                    int[][] cum = new int[series][n + 1];
                    int max = 1;
                    for (int s = 0; s < series; s++) {
                        int running = 0;
                        for (int e = 1; e <= n; e++) {
                            if (playLog.get(e - 1).equals(ts.get(s).getTitle())) running++;
                            cum[s][e] = running;
                        }
                        max = Math.max(max, running);
                    }
                    int left = 34, right = 16, top = 16, bottom = 50;
                    int pw = w - left - right, ph = h - top - bottom;
                    int step = Math.max(1, (int) Math.ceil(max / 4.0));
                    int topVal = ((max + step - 1) / step) * step;
                    g.setFont(font(Font.PLAIN, 10));
                    FontMetrics fm = g.getFontMetrics();
                    for (int v = 0; v <= topVal; v += step) {
                        int y = top + ph - (int) ((long) ph * v / topVal);
                        g.setColor(new Color(0x9F, 0x12, 0x39, 40));
                        g.drawLine(left, y, w - right, y);
                        g.setColor(ROSE);
                        String label = String.valueOf(v);
                        g.drawString(label, left - 6 - fm.stringWidth(label), y + 4);
                    }
                    int xstep = Math.max(1, (int) Math.ceil(n / 6.0));
                    for (int e = 0; e <= n; e += xstep) {
                        int x = left + (int) ((long) pw * e / n);
                        g.setColor(ROSE);
                        String label = String.valueOf(e);
                        g.drawString(label, x - fm.stringWidth(label) / 2, top + ph + 14);
                    }
                    g.setFont(font(Font.PLAIN, 10));
                    g.drawString("plays so far", left + pw / 2 - 28, top + ph + 27);
                    g.setStroke(new BasicStroke(2.6f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                    for (int s = 0; s < series; s++) {
                        g.setColor(SERIES[s % SERIES.length]);
                        int px = left, py = top + ph;
                        for (int e = 1; e <= n; e++) {
                            int x = left + (int) ((long) pw * e / n);
                            int y = top + ph - (int) ((long) ph * cum[s][e] / topVal);
                            g.drawLine(px, py, x, y);
                            px = x; py = y;
                        }
                        g.fillOval(px - 4, py - 4, 8, 8);
                    }
                    g.setStroke(new BasicStroke(1f));
                    int lx = left;
                    g.setFont(font(Font.BOLD, 10));
                    for (int s = 0; s < series; s++) {
                        String name = fit(ts.get(s).getTitle(), g.getFontMetrics(), 66);
                        int wd = g.getFontMetrics().stringWidth(name);
                        if (lx + wd + 18 > w - right) break;
                        g.setColor(SERIES[s % SERIES.length]);
                        g.fillOval(lx, top + ph + 33, 9, 9);
                        g.setColor(MAROON);
                        g.drawString(name, lx + 13, top + ph + 42);
                        lx += wd + 22;
                    }
                }
            };
        }

        private ChartCanvas yearsChart()
        {
            return new ChartCanvas() {
                @Override void draw(Graphics2D g, int w, int h)
                {
                    List<Track> ys = new ArrayList<Track>();
                    for (Track t : organizer.getTracks()) if (t.getYear() > 0) ys.add(t);
                    if (ys.isEmpty()) { hint(g, w, h, "No years set yet", "use Set year on a song"); return; }
                    ys.sort((a, b) -> Integer.compare(a.getYear(), b.getYear()));
                    int lo = ys.get(0).getYear(), hi = ys.get(ys.size() - 1).getYear();
                    int padYears = (hi == lo) ? 5 : Math.max(2, (hi - lo) / 10);
                    lo -= padYears; hi += padYears;
                    int left = 34, right = 34, axisY = (int) (h * 0.66);
                    int pw = w - left - right;
                    g.setColor(new Color(0x9F, 0x12, 0x39, 120));
                    g.setStroke(new BasicStroke(3f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                    g.drawLine(left, axisY, w - right, axisY);
                    g.setStroke(new BasicStroke(1f));
                    int[] steps = { 1, 2, 5, 10, 20, 25, 50, 100, 200 };
                    int step = steps[steps.length - 1];
                    for (int sv : steps) if ((hi - lo) / (double) sv <= 6) { step = sv; break; }
                    g.setFont(font(Font.PLAIN, 10));
                    FontMetrics fm = g.getFontMetrics();
                    for (int yv = ((lo + step - 1) / step) * step; yv <= hi; yv += step) {
                        int x = left + (int) ((long) pw * (yv - lo) / (hi - lo));
                        g.setColor(new Color(0x9F, 0x12, 0x39, 120));
                        g.drawLine(x, axisY - 4, x, axisY + 4);
                        g.setColor(ROSE);
                        String label = String.valueOf(yv);
                        g.drawString(label, x - fm.stringWidth(label) / 2, axisY + 18);
                    }
                    for (int i = 0; i < ys.size(); i++) {
                        Track t = ys.get(i);
                        int x = left + (int) ((long) pw * (t.getYear() - lo) / (hi - lo));
                        int level = i % 3;
                        int labelY = axisY - 34 - level * 28;
                        g.setColor(new Color(0x9F, 0x12, 0x39, 90));
                        g.drawLine(x, axisY, x, labelY + 6);
                        g.setColor(SERIES[i % SERIES.length]);
                        g.fillOval(x - 8, axisY - 8, 16, 16);
                        g.setColor(Color.WHITE);
                        g.setStroke(new BasicStroke(2f));
                        g.drawOval(x - 8, axisY - 8, 16, 16);
                        g.setStroke(new BasicStroke(1f));
                        g.setFont(font(Font.BOLD, 11));
                        g.setColor(MAROON);
                        String name = fit(t.getTitle(), g.getFontMetrics(), 120);
                        int tx = Math.max(4, Math.min(w - 4 - g.getFontMetrics().stringWidth(name),
                                x - g.getFontMetrics().stringWidth(name) / 2));
                        g.drawString(name, tx, labelY);
                    }
                }
            };
        }

        // ---------- Track details window ----------

        public JPanel createDetailsPanel()
        {
            BackgroundPanel p = new BackgroundPanel();
            p.add(new MiniHeader("Track details", "the selected song, up close"), BorderLayout.NORTH);

            ChartCanvas view = new ChartCanvas() {
                @Override void draw(Graphics2D g, int w, int h)
                {
                    Track t = selectedTrack();
                    if (t == null) { hint(g, w, h, "No song selected", "click a song in My Library"); return; }
                    Color[] avatars = { ROSE, MAROON, NAVY, YELLOW };
                    Color av = avatars[Math.abs(t.getArtist().hashCode()) % avatars.length];
                    g.setColor(av);
                    g.fillOval(24, 20, 84, 84);
                    g.setColor(av == YELLOW ? MAROON : LAVENDER);
                    g.setFont(font(Font.BOLD, 40));
                    String letter = t.getArtist().isEmpty() ? "?" : t.getArtist().substring(0, 1).toUpperCase();
                    FontMetrics fm = g.getFontMetrics();
                    g.drawString(letter, 66 - fm.stringWidth(letter) / 2, 62 + (fm.getAscent() - fm.getDescent()) / 2);

                    g.setFont(font(Font.BOLD, 22));
                    g.setColor(MAROON);
                    g.drawString(fit(t.getTitle(), g.getFontMetrics(), w - 142), 126, 52);
                    g.setFont(font(Font.PLAIN, 15));
                    g.setColor(ROSE);
                    g.drawString(fit(t.getArtist(), g.getFontMetrics(), w - 142), 126, 76);
                    g.setFont(font(Font.BOLD, 11));
                    String pos = "#" + index() + " of " + organizer.numberOfFiles();
                    int cw = g.getFontMetrics().stringWidth(pos) + 18;
                    g.setColor(YELLOW);
                    g.fillRoundRect(126, 86, cw, 20, 20, 20);
                    g.setColor(MAROON);
                    g.drawString(pos, 135, 100);

                    g.setColor(new Color(0x9F, 0x12, 0x39, 70));
                    g.drawLine(24, 126, w - 24, 126);

                    String[] labels = { "File", "Year", "Play count" };
                    int[] ys = { 158, 192, 226 };
                    for (int i = 0; i < 3; i++) {
                        g.setFont(font(Font.BOLD, 12));
                        g.setColor(MAROON);
                        g.drawString(labels[i], 24, ys[i]);
                    }
                    g.setFont(font(Font.PLAIN, 13));
                    g.setColor(NAVY);
                    g.drawString(fit(t.getFilename(), g.getFontMetrics(), w - 150), 124, 158);
                    if (t.getYear() > 0) {
                        g.drawString(String.valueOf(t.getYear()), 124, 192);
                    } else {
                        g.setColor(ROSE);
                        g.setFont(font(Font.ITALIC, 13));
                        g.drawString("not set", 124, 192);
                    }
                    g.setFont(font(Font.BOLD, 16));
                    g.setColor(MAROON);
                    String count = String.valueOf(t.getPlayCount());
                    g.drawString(count, 124, 227);
                    int hx = 124 + g.getFontMetrics().stringWidth(count) + 16;
                    int shown = Math.min(t.getPlayCount(), 12);
                    for (int i = 0; i < shown; i++) heart(g, hx + i * 17, 221, 6, ROSE);
                    if (t.getPlayCount() > shown) {
                        g.setFont(font(Font.BOLD, 12));
                        g.setColor(ROSE);
                        g.drawString("+" + (t.getPlayCount() - shown), hx + shown * 17, 226);
                    }
                }
            };
            view.setPreferredSize(new Dimension(420, 250));

            JPanel buttons = row(
                button("Prev", ROSE, LAVENDER, () -> { if (index() > 0) indexSpinner.setValue(index() - 1); }),
                button("Next", ROSE, LAVENDER, () -> {
                    if (index() + 1 < organizer.numberOfFiles()) indexSpinner.setValue(index() + 1);
                }),
                button("Play", NAVY, YELLOW, () -> {
                    if (organizer.validIndex(index())) {
                        organizer.playTrack(index());
                        showNowPlaying(index());
                    }
                }),
                button("Reset play count", MAROON, LAVENDER, () -> {
                    Track t = selectedTrack();
                    if (t != null) t.resetPlayCount();
                }));
            JPanel inner = new JPanel(new BorderLayout());
            inner.setOpaque(false);
            inner.setBorder(BorderFactory.createEmptyBorder(0, 8, 6, 8));
            inner.add(view, BorderLayout.CENTER);
            inner.add(buttons, BorderLayout.SOUTH);

            JPanel center = new JPanel(new BorderLayout());
            center.setOpaque(false);
            center.setBorder(BorderFactory.createEmptyBorder(2, 14, 12, 14));
            center.add(card("Selected song", Color.WHITE, ROSE, inner), BorderLayout.CENTER);
            p.add(center, BorderLayout.CENTER);
            p.setPreferredSize(new Dimension(480, 440));
            observers.add(() -> p.repaint());
            return p;
        }

        // ---------- Play history window ----------

        public JPanel createHistoryPanel()
        {
            BackgroundPanel p = new BackgroundPanel();
            p.add(new MiniHeader("Play history", "every song you have played"), BorderLayout.NORTH);

            final DefaultListModel<String> hm = new DefaultListModel<String>();
            JList<String> hl = new JList<String>(hm) {
                @Override protected void paintComponent(Graphics g0) {
                    super.paintComponent(g0);
                    if (hm.isEmpty()) {
                        Graphics2D g = g2(g0);
                        ChartCanvas.hint(g, getWidth(), getHeight(), "Nothing played yet", "press Play on a song");
                        g.dispose();
                    }
                }
            };
            hl.setOpaque(false);
            hl.setFixedCellHeight(34);
            hl.setCellRenderer(new DefaultListCellRenderer() {
                @Override public Component getListCellRendererComponent(JList<?> list, Object value,
                        int index, boolean isSelected, boolean cellHasFocus) {
                    JLabel l = (JLabel) super.getListCellRendererComponent(list, value, index, false, false);
                    l.setFont(font(Font.BOLD, 13));
                    l.setForeground(MAROON);
                    l.setOpaque(true);
                    l.setBackground(index % 2 == 0 ? Color.WHITE : LAVENDER);
                    l.setBorder(BorderFactory.createEmptyBorder(6, 16, 6, 12));
                    return l;
                }
            });
            JScrollPane sp = new JScrollPane(hl);
            sp.setOpaque(false);
            sp.getViewport().setOpaque(false);
            sp.setBorder(BorderFactory.createEmptyBorder(0, 8, 4, 8));

            final JLabel count = label("0 plays");
            JPanel bottom = row(count, button("Clear history", MAROON, LAVENDER, () -> playLog.clear()));
            JPanel inner = new JPanel(new BorderLayout());
            inner.setOpaque(false);
            inner.add(sp, BorderLayout.CENTER);
            inner.add(bottom, BorderLayout.SOUTH);

            JPanel center = new JPanel(new BorderLayout());
            center.setOpaque(false);
            center.setBorder(BorderFactory.createEmptyBorder(2, 14, 12, 14));
            center.add(card("Latest first", Color.WHITE, ROSE, inner), BorderLayout.CENTER);
            p.add(center, BorderLayout.CENTER);
            p.setPreferredSize(new Dimension(380, 500));
            observers.add(() -> {
                hm.clear();
                for (int i = playLog.size() - 1; i >= 0; i--) hm.addElement("#" + (i + 1) + "     " + playLog.get(i));
                count.setText(playLog.size() + (playLog.size() == 1 ? " play" : " plays"));
            });
            return p;
        }

        // ---------- behaviour helpers ----------

        private int index()     { return (Integer) indexSpinner.getValue(); }
        private String search() { return searchField.getText().trim(); }

        private void showNowPlaying(int i)
        {
            if (organizer.validIndex(i)) {
                playToken++;   // this play takes over the bar from any "Play by artist" timer
                Track t = organizer.getTracks().get(i);
                nowBar.setNow(t.getTitle() + "  -  " + t.getArtist());
            }
        }

        private void refresh()
        {
            syncing = true;
            int sel = library.getSelectedIndex();
            model.clear();
            for (Track t : organizer.getTracks()) model.addElement(t);
            int i = index();
            if (organizer.validIndex(i)) library.setSelectedIndex(i);
            else if (sel >= 0 && sel < model.size()) library.setSelectedIndex(sel);
            syncing = false;
            nowBar.setCount(organizer.numberOfFiles());
            library.repaint();

            // remember every new play so the History and Charts windows can show it
            for (Track t : organizer.getTracks()) {
                int before = lastCounts.containsKey(t) ? lastCounts.get(t) : 0;
                for (int k = before; k < t.getPlayCount(); k++) playLog.add(t.getTitle());
                lastCounts.put(t, t.getPlayCount());
            }
            lastCounts.keySet().retainAll(new ArrayList<Track>(organizer.getTracks()));
            notifyObservers();
        }

        // ---------- widget factories ----------

        private JButton button(String text, Color base, Color fg, Runnable action)
        {
            RoundButton b = new RoundButton(text, base, fg);
            b.addActionListener(e -> { action.run(); refresh(); });
            return b;
        }

        private JLabel label(String text)
        {
            JLabel l = new JLabel(text);
            l.setFont(font(Font.BOLD, 12));
            l.setForeground(MAROON);
            return l;
        }

        private JPanel row(Component... parts)
        {
            JPanel p = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 3));
            p.setOpaque(false);
            p.setAlignmentX(Component.LEFT_ALIGNMENT);
            for (Component c : parts) p.add(c);
            return p;
        }

        private JPanel stack(JPanel... rows)
        {
            JPanel p = new JPanel();
            p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));
            p.setOpaque(false);
            p.setBorder(BorderFactory.createEmptyBorder(0, 8, 4, 8));
            for (JPanel r : rows) p.add(r);
            return p;
        }

        private RoundPanel card(String title, Color fill, Color titleColor, JComponent content)
        {
            RoundPanel p = new RoundPanel(fill);
            JLabel t = new JLabel(title);
            t.setFont(font(Font.BOLD, 14));
            t.setForeground(titleColor);
            t.setBorder(BorderFactory.createEmptyBorder(8, 16, 2, 14));
            p.add(t, BorderLayout.NORTH);
            p.add(content, BorderLayout.CENTER);
            return p;
        }

        private void styleField(JTextField f)
        {
            f.setFont(font(Font.PLAIN, 12));
            f.setBackground(LAVENDER);
            f.setForeground(MAROON);
            f.setCaretColor(ROSE);
            f.setBorder(BorderFactory.createCompoundBorder(
                new RoundedBorder(ROSE, 14, 1), BorderFactory.createEmptyBorder(2, 8, 2, 8)));
        }

        private void styleSpinner(JSpinner s)
        {
            s.setFont(font(Font.PLAIN, 12));
            s.setBorder(new RoundedBorder(ROSE, 14, 1));
            JTextField tf = ((JSpinner.DefaultEditor) s.getEditor()).getTextField();
            tf.setBackground(LAVENDER);
            tf.setForeground(MAROON);
            tf.setBorder(BorderFactory.createEmptyBorder(2, 6, 2, 6));
        }

        /** Sends everything written to System.out into the text area. */
        private void redirectConsoleToTextArea()
        {
            OutputStream toArea = new OutputStream() {
                @Override public void write(int b) {
                    append(String.valueOf((char) b));
                }
                @Override public void write(byte[] buf, int off, int len) {
                    append(new String(buf, off, len, java.nio.charset.StandardCharsets.UTF_8));
                }
                private void append(String s) {
                    SwingUtilities.invokeLater(() -> {
                        output.append(s);
                        output.setCaretPosition(output.getDocument().getLength());
                    });
                }
            };
            try {
                System.setOut(new PrintStream(toArea, true, "UTF-8"));
            } catch (java.io.UnsupportedEncodingException e) {
                System.setOut(new PrintStream(toArea, true));
            }
        }
    }

    // =====================================================================
    //  Base class for everything drawn by hand in the extra windows
    // =====================================================================
    private abstract static class ChartCanvas extends JComponent
    {
        ChartCanvas() { setOpaque(false); }

        @Override
        protected void paintComponent(Graphics g0)
        {
            Graphics2D g = g2(g0);
            draw(g, getWidth(), getHeight());
            g.dispose();
        }

        abstract void draw(Graphics2D g, int w, int h);

        static void hint(Graphics2D g, int w, int h, String line1, String line2)
        {
            sparkle(g, w / 2.0, h / 2.0 - 30, 9, YELLOW);
            g.setFont(font(Font.BOLD, 13));
            g.setColor(ROSE);
            g.drawString(line1, (w - g.getFontMetrics().stringWidth(line1)) / 2, h / 2);
            g.setFont(font(Font.PLAIN, 12));
            g.drawString(line2, (w - g.getFontMetrics().stringWidth(line2)) / 2, h / 2 + 20);
        }
    }

    // Lavender polka-dot background used by the extra windows
    private static class BackgroundPanel extends JPanel
    {
        BackgroundPanel() { super(new BorderLayout()); }

        @Override
        protected void paintComponent(Graphics g0)
        {
            Graphics2D g = g2(g0);
            g.setColor(LAVENDER);
            g.fillRect(0, 0, getWidth(), getHeight());
            paintDots(g, getWidth(), getHeight());
            g.dispose();
        }
    }

    // Smaller banner for the extra windows
    private static class MiniHeader extends JPanel
    {
        private final String title;
        private final String subtitle;

        MiniHeader(String title, String subtitle)
        {
            this.title = title;
            this.subtitle = subtitle;
            setOpaque(false);
            setPreferredSize(new Dimension(100, 72));
        }

        @Override
        protected void paintComponent(Graphics g0)
        {
            Graphics2D g = g2(g0);
            int w = getWidth(), h = getHeight();
            g.setPaint(new GradientPaint(0, 0, MAROON, w, 0, ROSE));
            g.fillRect(0, 0, w, h);
            g.setColor(LAVENDER);
            for (int x = -12; x < w + 12; x += 24) g.fillOval(x, h - 9, 24, 20);
            sparkle(g, w - 40, 22, 9, YELLOW);
            sparkle(g, w - 78, 46, 5, LAVENDER);
            sparkle(g, w - 118, 20, 4, new Color(255, 255, 255, 190));
            g.setFont(font(Font.BOLD, 26));
            g.setColor(new Color(0, 0, 0, 70));
            g.drawString(title, 24, 36);
            g.setColor(YELLOW);
            g.drawString(title, 22, 34);
            g.setFont(font(Font.PLAIN, 12));
            g.setColor(LAVENDER);
            g.drawString(subtitle, 24, 54);
            g.dispose();
        }
    }

    // =====================================================================
    //  Header banner with a little mascot
    // =====================================================================
    private static class Header extends JPanel
    {
        Header() { setOpaque(false); setPreferredSize(new Dimension(100, 96)); }

        @Override
        protected void paintComponent(Graphics g0)
        {
            Graphics2D g = g2(g0);
            int w = getWidth(), h = getHeight();
            g.setPaint(new GradientPaint(0, 0, MAROON, w, 0, ROSE));
            g.fillRect(0, 0, w, h);

            // scalloped bottom edge
            g.setColor(LAVENDER);
            for (int x = -12; x < w + 12; x += 24) g.fillOval(x, h - 9, 24, 20);

            // sparkles
            sparkle(g, w - 56, 26, 10, YELLOW);
            sparkle(g, w - 104, 56, 6, LAVENDER);
            sparkle(g, w - 160, 28, 5, new Color(255, 255, 255, 190));
            sparkle(g, w - 230, 54, 7, YELLOW);
            sparkle(g, 430, 22, 5, new Color(241, 196, 15, 200));

            drawMascot(g, 58, 46);

            g.setFont(font(Font.BOLD, 30));
            g.setColor(new Color(0, 0, 0, 70));
            g.drawString("Music Organizer", 111, 47);
            g.setColor(YELLOW);
            g.drawString("Music Organizer", 109, 45);
            g.setFont(font(Font.PLAIN, 13));
            g.setColor(LAVENDER);
            g.drawString("organize your tunes, one cute track at a time \u2665", 111, 68);
            g.dispose();
        }

        private void drawMascot(Graphics2D g, int cx, int cy)
        {
            int r = 27;
            // headphone band
            g.setStroke(new BasicStroke(6, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g.setColor(LAVENDER);
            g.drawArc(cx - r - 4, cy - r - 4, 2 * (r + 4), 2 * (r + 4), 0, 180);
            g.setStroke(new BasicStroke(1));
            // face
            g.setColor(YELLOW);
            g.fillOval(cx - r, cy - r, 2 * r, 2 * r);
            // ear cups
            g.setColor(LAVENDER);
            g.fillRoundRect(cx - r - 10, cy - 8, 12, 22, 8, 8);
            g.fillRoundRect(cx + r - 2, cy - 8, 12, 22, 8, 8);
            g.setColor(ROSE);
            g.fillRoundRect(cx - r - 7, cy - 4, 6, 14, 6, 6);
            g.fillRoundRect(cx + r + 1, cy - 4, 6, 14, 6, 6);
            // eyes
            g.setColor(NAVY);
            g.fillOval(cx - 13, cy - 8, 8, 11);
            g.fillOval(cx + 5, cy - 8, 8, 11);
            g.setColor(Color.WHITE);
            g.fillOval(cx - 11, cy - 6, 3, 3);
            g.fillOval(cx + 7, cy - 6, 3, 3);
            // blush
            g.setColor(new Color(0x9F, 0x12, 0x39, 90));
            g.fillOval(cx - 22, cy + 3, 11, 7);
            g.fillOval(cx + 11, cy + 3, 11, 7);
            // smile
            g.setColor(NAVY);
            g.setStroke(new BasicStroke(2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g.drawArc(cx - 7, cy + 2, 14, 10, 200, 140);
            g.setStroke(new BasicStroke(1));
        }
    }

    // =====================================================================
    //  One track as a cute card in the library list
    // =====================================================================
    private static class TrackRow extends JComponent implements ListCellRenderer<Track>
    {
        private Track track;
        private boolean selected;
        private int idx;

        @Override
        public Component getListCellRendererComponent(JList<? extends Track> list, Track value,
                int index, boolean isSelected, boolean cellHasFocus)
        {
            track = value;
            selected = isSelected;
            idx = index;
            return this;
        }

        @Override public Dimension getPreferredSize() { return new Dimension(300, 60); }

        @Override
        protected void paintComponent(Graphics g0)
        {
            if (track == null) return;
            Graphics2D g = g2(g0);
            int w = getWidth(), h = getHeight();

            g.setColor(selected ? CREAM : Color.WHITE);
            g.fillRoundRect(2, 3, w - 5, h - 7, 22, 22);
            g.setColor(selected ? ROSE : new Color(0x9F, 0x12, 0x39, 60));
            g.setStroke(new BasicStroke(selected ? 2f : 1f));
            g.drawRoundRect(2, 3, w - 6, h - 8, 22, 22);

            // avatar
            Color[] avatars = { ROSE, MAROON, NAVY, YELLOW };
            Color av = avatars[Math.abs(track.getArtist().hashCode()) % avatars.length];
            g.setColor(av);
            g.fillOval(12, 11, 38, 38);
            g.setColor(av == YELLOW ? MAROON : LAVENDER);
            g.setFont(font(Font.BOLD, 18));
            String letter = track.getArtist().isEmpty() ? "?" : track.getArtist().substring(0, 1).toUpperCase();
            FontMetrics fm = g.getFontMetrics();
            g.drawString(letter, 31 - fm.stringWidth(letter) / 2, 30 + (fm.getAscent() - fm.getDescent()) / 2);

            // right-hand info: hearts for play count, year chip
            g.setFont(font(Font.BOLD, 13));
            String hearts = "\u2665 " + track.getPlayCount();
            int hw = g.getFontMetrics().stringWidth(hearts);
            g.setColor(ROSE);
            g.drawString(hearts, w - 16 - hw, 24);
            int rightEdge = w - 16 - hw - 8;
            if (track.getYear() > 0) {
                g.setFont(font(Font.BOLD, 11));
                String y = String.valueOf(track.getYear());
                int yw = g.getFontMetrics().stringWidth(y);
                g.setColor(YELLOW);
                g.fillRoundRect(w - 16 - yw - 12, 33, yw + 12, 17, 17, 17);
                g.setColor(MAROON);
                g.drawString(y, w - 16 - yw - 6, 46);
            }

            // title and artist
            g.setFont(font(Font.BOLD, 14));
            g.setColor(MAROON);
            g.drawString(fit(track.getTitle(), g.getFontMetrics(), rightEdge - 62), 60, 28);
            g.setFont(font(Font.PLAIN, 12));
            g.setColor(ROSE);
            g.drawString(fit("#" + idx + "  \u00B7  " + track.getArtist(), g.getFontMetrics(),
                (track.getYear() > 0 ? w - 100 : w - 24) - 60), 60, 46);
            g.dispose();
        }
    }

    // =====================================================================
    //  "Now playing" pill with an animated equalizer
    // =====================================================================
    private static class NowPlayingBar extends JPanel
    {
        private String now = null;
        private int count = 0;
        private double phase = 0;

        NowPlayingBar()
        {
            setOpaque(false);
            setPreferredSize(new Dimension(100, 46));
            new javax.swing.Timer(90, e -> {
                if (now != null) { phase += 0.6; repaint(); }
            }).start();
        }

        void setNow(String text) { now = text; repaint(); }
        void setCount(int n)     { count = n; repaint(); }

        @Override
        protected void paintComponent(Graphics g0)
        {
            Graphics2D g = g2(g0);
            int w = getWidth(), h = getHeight();
            g.setColor(NAVY);
            g.fillRoundRect(0, 0, w - 1, h - 1, h, h);

            // equalizer bars
            for (int i = 0; i < 5; i++) {
                int bh = (now != null) ? 8 + (int) (16 * (0.5 + 0.5 * Math.sin(phase + i * 1.3))) : 6;
                g.setColor(now == null ? new Color(245, 243, 255, 90) : (i % 2 == 0 ? YELLOW : new Color(0xE11D48)));
                g.fillRoundRect(24 + i * 11, h / 2 + 12 - bh, 6, bh, 6, 6);
            }

            g.setFont(font(Font.BOLD, 13));
            g.setColor(YELLOW);
            String lead = (now != null) ? "Now playing:" : "Nothing playing";
            g.drawString(lead, 90, h / 2 + 5);
            if (now != null) {
                g.setColor(LAVENDER);
                g.setFont(font(Font.PLAIN, 13));
                g.drawString(fit(now, g.getFontMetrics(), w - 400), 90 + 100, h / 2 + 5);
            }

            g.setFont(font(Font.BOLD, 12));
            g.setColor(LAVENDER);
            String c = count + (count == 1 ? " track" : " tracks") + " in library \u2665";
            g.drawString(c, w - 26 - g.getFontMetrics().stringWidth(c), h / 2 + 5);
            g.dispose();
        }
    }

    // =====================================================================
    //  Rounded card panel with a soft shadow
    // =====================================================================
    private static class RoundPanel extends JPanel
    {
        private final Color fill;

        RoundPanel(Color fill)
        {
            super(new BorderLayout());
            this.fill = fill;
            setOpaque(false);
            setBorder(BorderFactory.createEmptyBorder(0, 0, 6, 6));
        }

        @Override
        protected void paintComponent(Graphics g0)
        {
            Graphics2D g = g2(g0);
            int w = getWidth(), h = getHeight();
            g.setColor(new Color(0x4C, 0x05, 0x19, 30));
            g.fillRoundRect(4, 5, w - 7, h - 6, 28, 28);
            g.setColor(fill);
            g.fillRoundRect(0, 0, w - 7, h - 7, 28, 28);
            g.setColor(new Color(0x9F, 0x12, 0x39, 80));
            g.drawRoundRect(0, 0, w - 8, h - 8, 28, 28);
            g.dispose();
        }
    }

    // =====================================================================
    //  Rounded "pill" button
    // =====================================================================
    private static class RoundButton extends JButton
    {
        private final Color base;
        private boolean over = false;
        private boolean down = false;

        RoundButton(String text, Color base, Color fg)
        {
            super(text);
            this.base = base;
            setForeground(fg);
            setFont(font(Font.BOLD, 12));
            setContentAreaFilled(false);
            setFocusPainted(false);
            setBorderPainted(false);
            setOpaque(false);
            setMargin(new Insets(6, 14, 6, 14));
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            addMouseListener(new MouseAdapter() {
                @Override public void mouseEntered(MouseEvent e)  { over = true;  repaint(); }
                @Override public void mouseExited(MouseEvent e)   { over = false; down = false; repaint(); }
                @Override public void mousePressed(MouseEvent e)  { down = true;  repaint(); }
                @Override public void mouseReleased(MouseEvent e) { down = false; repaint(); }
            });
        }

        @Override
        protected void paintComponent(Graphics g0)
        {
            Graphics2D g = g2(g0);
            Color fill = base;
            if (down)      fill = base.darker().darker();
            else if (over) fill = base.brighter();
            g.setColor(new Color(0x4C, 0x05, 0x19, 35));
            g.fillRoundRect(0, 2, getWidth() - 1, getHeight() - 3, getHeight(), getHeight());
            g.setColor(fill);
            g.fillRoundRect(0, 0, getWidth() - 1, getHeight() - 3, getHeight(), getHeight());
            g.dispose();
            super.paintComponent(g0);
        }
    }

    // =====================================================================
    //  Rounded outline border
    // =====================================================================
    private static class RoundedBorder implements Border
    {
        private final Color color;
        private final int arc;
        private final int thickness;

        RoundedBorder(Color color, int arc, int thickness)
        {
            this.color = color;
            this.arc = arc;
            this.thickness = thickness;
        }

        @Override
        public void paintBorder(Component c, Graphics g0, int x, int y, int w, int h)
        {
            Graphics2D g = g2(g0);
            g.setColor(color);
            g.setStroke(new BasicStroke(thickness));
            int off = thickness / 2;
            g.drawRoundRect(x + off, y + off, w - thickness, h - thickness, arc, arc);
            g.dispose();
        }

        @Override public Insets getBorderInsets(Component c)
        {
            int i = Math.max(thickness + 2, arc / 3);
            return new Insets(i, i, i, i);
        }

        @Override public boolean isBorderOpaque() { return false; }
    }
}