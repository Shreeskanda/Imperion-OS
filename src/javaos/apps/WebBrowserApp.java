package javaos.apps;

import javaos.ui.Theme;
import javaos.ui.ThemeManager;
import javaos.ui.components.CyberpunkIcons;
import javaos.ui.components.NeonScrollBarUI;

import javax.swing.*;
import javax.swing.event.HyperlinkEvent;
import javax.swing.text.html.HTMLEditorKit;
import javax.swing.text.html.StyleSheet;
import java.awt.*;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.net.InetAddress;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Stack;

/**
 * NetRunner Web Browser for IMPERION OS.
 * Hybrid web explorer featuring multi-tab browsing, pure Java HTML reader,
 * omnibar search with auto-text selection & sanitization, cyber developer inspector,
 * and 1-click bridge to host modern browser (Chrome, Edge, Brave).
 */
public class WebBrowserApp extends OSApplication {

    private JTabbedPane tabbedPane;
    private static final String DEFAULT_HOME_URL = "imperion://portal";

    public WebBrowserApp() {
        super(new AppDescriptor(
                "browser",
                "NetRunner Browser",
                "Cyberpunk web explorer, real HTML reader & host browser bridge",
                AppDescriptor.Category.UTILITIES,
                CyberpunkIcons.createIcon("BROWSER", 20),
                false
        ));
    }

    @Override
    public Dimension getPreferredWindowSize() {
        return new Dimension(980, 640);
    }

    @Override
    public JComponent createContent() {
        JPanel main = new JPanel(new BorderLayout());
        main.setOpaque(false);

        Theme theme = ThemeManager.getInstance().getCurrentTheme();

        tabbedPane = new JTabbedPane();
        tabbedPane.setBackground(theme.panel);
        tabbedPane.setForeground(theme.textPrimary);
        tabbedPane.setFont(theme.fontBold);

        // Open initial tab
        addNewTab(DEFAULT_HOME_URL);

        // Header panel with "+ New Tab" button on the right
        JPanel topHeader = new JPanel(new BorderLayout());
        topHeader.setOpaque(false);

        JPanel tabControls = new JPanel(new FlowLayout(FlowLayout.RIGHT, 4, 2));
        tabControls.setOpaque(false);

        JButton btnNewTab = new JButton("+ New Tab");
        btnNewTab.setFont(theme.fontSmall);
        btnNewTab.setForeground(theme.neonAccent);
        btnNewTab.setBackground(theme.panel);
        btnNewTab.setOpaque(true);
        btnNewTab.setFocusPainted(false);
        btnNewTab.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(theme.secondaryAccent),
                BorderFactory.createEmptyBorder(3, 8, 3, 8)
        ));
        btnNewTab.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        btnNewTab.addActionListener(e -> addNewTab(DEFAULT_HOME_URL));
        tabControls.add(btnNewTab);

        topHeader.add(tabControls, BorderLayout.EAST);
        topHeader.add(tabbedPane, BorderLayout.CENTER);

        main.add(topHeader, BorderLayout.CENTER);

        return main;
    }

    public void openFile(java.io.File file) {
        if (file == null || !file.exists()) return;
        String url = file.toURI().toString();
        addNewTab(url);
    }

    private void addNewTab(String initialUrl) {
        BrowserTab tab = new BrowserTab(initialUrl);
        int index = tabbedPane.getTabCount();
        tabbedPane.addTab("New Tab", CyberpunkIcons.createIcon("BROWSER", 14), tab);
        tabbedPane.setSelectedIndex(index);

        // Custom tab header with close button
        JPanel tabHeader = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        tabHeader.setOpaque(false);

        JLabel titleLbl = new JLabel("NetRunner");
        titleLbl.setFont(ThemeManager.getInstance().getCurrentTheme().fontSmall);
        titleLbl.setForeground(ThemeManager.getInstance().getCurrentTheme().textPrimary);
        tab.setTitleListener(title -> {
            titleLbl.setText(title.length() > 16 ? title.substring(0, 14) + "..." : title);
            titleLbl.setToolTipText(title);
        });

        JButton closeBtn = new JButton("×");
        closeBtn.setFont(new Font("Segoe UI", Font.BOLD, 12));
        closeBtn.setForeground(ThemeManager.getInstance().getCurrentTheme().textSecondary);
        closeBtn.setContentAreaFilled(false);
        closeBtn.setBorderPainted(false);
        closeBtn.setFocusPainted(false);
        closeBtn.setMargin(new Insets(0, 2, 0, 2));
        closeBtn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        closeBtn.addActionListener(e -> {
            if (tabbedPane.getTabCount() > 1) {
                tabbedPane.remove(tab);
            }
        });

        tabHeader.add(titleLbl);
        tabHeader.add(closeBtn);
        tabbedPane.setTabComponentAt(index, tabHeader);
    }

    /**
     * Individual web browser tab component.
     */
    private class BrowserTab extends JPanel {

        private final Stack<String> backHistory = new Stack<>();
        private final Stack<String> forwardHistory = new Stack<>();
        private String currentUrl = "";

        private JTextField urlField;
        private JComboBox<String> searchEngineSelector;
        private JEditorPane webView;
        private JProgressBar progressBar;
        private JLabel statusLabel;
        private JLabel securityBadge;
        private JButton btnBack;
        private JButton btnForward;

        // Inspector drawer components
        private JSplitPane splitPane;
        private JPanel inspectorDrawer;
        private JTextArea headersArea;
        private JTextArea sourceArea;
        private JTextArea networkAuditArea;
        private boolean inspectorVisible = false;

        private java.util.function.Consumer<String> titleListener;

        BrowserTab(String initialUrl) {
            setLayout(new BorderLayout());
            setOpaque(false);
            initUI();
            navigate(initialUrl);
        }

        void setTitleListener(java.util.function.Consumer<String> listener) {
            this.titleListener = listener;
        }

        private void initUI() {
            Theme theme = ThemeManager.getInstance().getCurrentTheme();

            // ── TOP NAVIGATION TOOLBAR ─────────────────────────────────────────
            JPanel navBar = new JPanel(new BorderLayout(6, 4));
            navBar.setOpaque(true);
            navBar.setBackground(theme.elevatedPanel);
            navBar.setBorder(BorderFactory.createEmptyBorder(6, 8, 4, 8));

            // Navigation Buttons (Back, Forward, Refresh, Home)
            JPanel navButtons = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
            navButtons.setOpaque(false);

            btnBack = makeNavButton("<", "Back");
            btnBack.addActionListener(e -> goBack());

            btnForward = makeNavButton(">", "Forward");
            btnForward.addActionListener(e -> goForward());

            JButton btnRefresh = makeNavButton("REFRESH", "Refresh");
            btnRefresh.addActionListener(e -> reload());

            JButton btnHome = makeNavButton("HOME", "Home Portal");
            btnHome.addActionListener(e -> navigate(DEFAULT_HOME_URL));

            navButtons.add(btnBack);
            navButtons.add(btnForward);
            navButtons.add(btnRefresh);
            navButtons.add(btnHome);
            navBar.add(navButtons, BorderLayout.WEST);

            // Center Address & Search Bar
            JPanel centerBar = new JPanel(new BorderLayout(4, 0));
            centerBar.setOpaque(false);

            searchEngineSelector = new JComboBox<>(new String[]{"Google", "DuckDuckGo", "Wikipedia", "GitHub", "Bing"});
            searchEngineSelector.setBackground(theme.panel);
            searchEngineSelector.setForeground(theme.neonAccent);
            searchEngineSelector.setFont(theme.fontSmall);
            searchEngineSelector.setFocusable(false);
            centerBar.add(searchEngineSelector, BorderLayout.WEST);

            urlField = new JTextField();
            urlField.setBackground(theme.background);
            urlField.setForeground(theme.neonAccent);
            urlField.setFont(theme.fontMonospace);
            urlField.setCaretColor(Color.WHITE);
            urlField.setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createLineBorder(theme.secondaryAccent),
                    BorderFactory.createEmptyBorder(4, 8, 4, 8)
            ));
            urlField.addActionListener(e -> handleAddressBarSubmit());

            // ── OMNIBAR AUTO-SELECTION & KEYBOARD SHORTCUTS ───────────────────
            // Auto-select text on focus or mouse click so user can type immediately
            urlField.addFocusListener(new FocusAdapter() {
                @Override
                public void focusGained(FocusEvent e) {
                    SwingUtilities.invokeLater(() -> urlField.selectAll());
                }
            });
            urlField.addMouseListener(new MouseAdapter() {
                private boolean selectAllNeeded = false;
                @Override
                public void mousePressed(MouseEvent e) {
                    if (!urlField.hasFocus()) {
                        selectAllNeeded = true;
                    }
                }
                @Override
                public void mouseReleased(MouseEvent e) {
                    if (selectAllNeeded) {
                        selectAllNeeded = false;
                        SwingUtilities.invokeLater(() -> urlField.selectAll());
                    }
                }
            });
            urlField.addKeyListener(new KeyAdapter() {
                @Override
                public void keyPressed(KeyEvent e) {
                    if (e.getKeyCode() == KeyEvent.VK_ESCAPE) {
                        urlField.setText("");
                    }
                }
            });

            // Action buttons on the right side of address bar (Clear '✕' & 'GO')
            JPanel addressActions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 2, 0));
            addressActions.setOpaque(false);

            JButton btnClearUrl = new JButton("✕");
            btnClearUrl.setFont(new Font("Segoe UI", Font.BOLD, 12));
            btnClearUrl.setForeground(theme.textSecondary);
            btnClearUrl.setContentAreaFilled(false);
            btnClearUrl.setBorderPainted(false);
            btnClearUrl.setFocusPainted(false);
            btnClearUrl.setToolTipText("Clear address bar (Esc)");
            btnClearUrl.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            btnClearUrl.addActionListener(e -> {
                urlField.setText("");
                urlField.requestFocusInWindow();
            });

            JButton btnGo = new JButton("GO");
            btnGo.setFont(theme.fontSmall);
            btnGo.setForeground(theme.neonAccent);
            btnGo.setBackground(theme.panel);
            btnGo.setOpaque(true);
            btnGo.setFocusPainted(false);
            btnGo.setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createLineBorder(theme.neonAccent),
                    BorderFactory.createEmptyBorder(3, 8, 3, 8)
            ));
            btnGo.setToolTipText("Navigate or Search (Enter)");
            btnGo.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            btnGo.addActionListener(e -> handleAddressBarSubmit());

            addressActions.add(btnClearUrl);
            addressActions.add(btnGo);

            centerBar.add(urlField, BorderLayout.CENTER);
            centerBar.add(addressActions, BorderLayout.EAST);

            navBar.add(centerBar, BorderLayout.CENTER);

            // Right Action Controls (Real Browser Bridge + DevTools)
            JPanel rightControls = new JPanel(new FlowLayout(FlowLayout.RIGHT, 4, 0));
            rightControls.setOpaque(false);

            JButton btnOpenInHost = new JButton("Open in Chrome/Edge", CyberpunkIcons.createIcon("EXTERNAL", 12));
            btnOpenInHost.setFont(theme.fontSmall);
            btnOpenInHost.setForeground(theme.textPrimary);
            btnOpenInHost.setBackground(theme.panel);
            btnOpenInHost.setOpaque(true);
            btnOpenInHost.setFocusPainted(false);
            btnOpenInHost.setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createLineBorder(theme.neonAccent),
                    BorderFactory.createEmptyBorder(4, 8, 4, 8)
            ));
            btnOpenInHost.setToolTipText("Launch current URL in your Windows real browser (Chrome, Edge, etc.)");
            btnOpenInHost.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            btnOpenInHost.addActionListener(e -> launchInRealBrowser());

            JButton btnInspector = new JButton("Inspector", CyberpunkIcons.createIcon("CODE", 12));
            btnInspector.setFont(theme.fontSmall);
            btnInspector.setForeground(theme.secondaryAccent);
            btnInspector.setBackground(theme.panel);
            btnInspector.setOpaque(true);
            btnInspector.setFocusPainted(false);
            btnInspector.setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createLineBorder(theme.secondaryAccent),
                    BorderFactory.createEmptyBorder(4, 8, 4, 8)
            ));
            btnInspector.setToolTipText("Toggle Cyber Web & HTTP Header Inspector");
            btnInspector.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            btnInspector.addActionListener(e -> toggleInspector());

            rightControls.add(btnOpenInHost);
            rightControls.add(btnInspector);
            navBar.add(rightControls, BorderLayout.EAST);

            // ── BOOKMARKS QUICK BAR ───────────────────────────────────────────
            JPanel bookmarksBar = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 2));
            bookmarksBar.setOpaque(true);
            bookmarksBar.setBackground(theme.panel);
            bookmarksBar.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, new Color(theme.secondaryAccent.getRed(), theme.secondaryAccent.getGreen(), theme.secondaryAccent.getBlue(), 60)));

            addBookmark(bookmarksBar, "Google", "https://www.google.com");
            addBookmark(bookmarksBar, "WhatsApp", "https://web.whatsapp.com");
            addBookmark(bookmarksBar, "YouTube", "https://www.youtube.com");
            addBookmark(bookmarksBar, "GitHub", "https://github.com");
            addBookmark(bookmarksBar, "Wikipedia", "https://www.wikipedia.org");
            addBookmark(bookmarksBar, "HackerNews", "https://news.ycombinator.com");
            addBookmark(bookmarksBar, "DuckDuckGo", "https://duckduckgo.com");

            JPanel topContainer = new JPanel(new BorderLayout());
            topContainer.setOpaque(false);
            topContainer.add(navBar, BorderLayout.NORTH);
            topContainer.add(bookmarksBar, BorderLayout.SOUTH);
            add(topContainer, BorderLayout.NORTH);

            // ── WEB CONTENT DISPLAY ENGINE ────────────────────────────────────
            webView = new JEditorPane();
            webView.setEditable(false);
            webView.setBackground(theme.background);
            webView.setContentType("text/html");

            // Apply cyberpunk dark HTML stylesheet
            HTMLEditorKit kit = new HTMLEditorKit();
            StyleSheet css = kit.getStyleSheet();
            css.addRule("body { background-color: #080B14; color: #F1F5F9; font-family: Segoe UI, sans-serif; padding: 16px; }");
            css.addRule("a { color: #22D3EE; text-decoration: none; font-weight: bold; }");
            css.addRule("a:hover { color: #3B82F6; text-decoration: underline; }");
            css.addRule("h1, h2, h3 { color: #22D3EE; border-bottom: 1px solid #1E293B; padding-bottom: 4px; }");
            css.addRule("pre, code { background-color: #101827; color: #38BDF8; font-family: Consolas, monospace; padding: 4px; border: 1px solid #1E293B; }");
            css.addRule("hr { border: 0; border-top: 1px solid #334155; }");
            webView.setEditorKit(kit);

            // Handle link clicks (regular URLs or special action triggers)
            webView.addHyperlinkListener(e -> {
                if (e.getEventType() == HyperlinkEvent.EventType.ACTIVATED) {
                    String desc = e.getDescription();
                    if ("action://launch-host".equalsIgnoreCase(desc)) {
                        launchInRealBrowser();
                        return;
                    }
                    if ("action://launch-whatsapp".equalsIgnoreCase(desc)) {
                        launchInRealBrowser("https://web.whatsapp.com");
                        return;
                    }
                    if (e.getURL() != null) {
                        navigate(e.getURL().toString());
                    } else if (desc != null) {
                        navigate(desc);
                    }
                }
            });

            JScrollPane webScroll = new JScrollPane(webView);
            webScroll.getVerticalScrollBar().setUI(new NeonScrollBarUI());
            webScroll.getHorizontalScrollBar().setUI(new NeonScrollBarUI());
            webScroll.setBorder(BorderFactory.createEmptyBorder());

            // ── CYBER DEVTOOLS INSPECTOR DRAWER ───────────────────────────────
            inspectorDrawer = buildInspectorDrawer();

            splitPane = new JSplitPane(JSplitPane.VERTICAL_SPLIT, webScroll, inspectorDrawer);
            splitPane.setResizeWeight(0.72);
            splitPane.setDividerSize(4);
            splitPane.setOpaque(false);
            splitPane.setBorder(BorderFactory.createEmptyBorder());
            splitPane.getBottomComponent().setVisible(false); // hidden by default

            add(splitPane, BorderLayout.CENTER);

            // ── BOTTOM STATUS BAR ─────────────────────────────────────────────
            JPanel statusBar = new JPanel(new BorderLayout(8, 0));
            statusBar.setOpaque(true);
            statusBar.setBackground(theme.panel);
            statusBar.setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createMatteBorder(1, 0, 0, 0, theme.secondaryAccent),
                    BorderFactory.createEmptyBorder(3, 8, 3, 8)
            ));

            securityBadge = new JLabel("SECURE");
            securityBadge.setFont(theme.fontSmall);
            securityBadge.setForeground(theme.success);

            statusLabel = new JLabel("Ready");
            statusLabel.setFont(theme.fontSmall);
            statusLabel.setForeground(theme.textSecondary);

            JPanel statusLeft = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
            statusLeft.setOpaque(false);
            statusLeft.add(securityBadge);
            statusLeft.add(statusLabel);

            progressBar = new JProgressBar();
            progressBar.setPreferredSize(new Dimension(140, 12));
            progressBar.setForeground(theme.neonAccent);
            progressBar.setBackground(theme.background);
            progressBar.setBorderPainted(false);
            progressBar.setVisible(false);

            statusBar.add(statusLeft, BorderLayout.CENTER);
            statusBar.add(progressBar, BorderLayout.EAST);

            add(statusBar, BorderLayout.SOUTH);
        }

        private JButton makeNavButton(String textOrIcon, String tooltip) {
            Theme theme = ThemeManager.getInstance().getCurrentTheme();
            JButton btn;
            if ("REFRESH".equals(textOrIcon)) {
                btn = new JButton(CyberpunkIcons.createIcon("REFRESH", 14));
            } else if ("HOME".equals(textOrIcon)) {
                btn = new JButton(CyberpunkIcons.createIcon("HOME", 14));
            } else {
                btn = new JButton(textOrIcon);
                btn.setFont(theme.fontBold);
            }

            btn.setForeground(theme.textPrimary);
            btn.setBackground(theme.panel);
            btn.setOpaque(true);
            btn.setFocusPainted(false);
            btn.setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createLineBorder(theme.secondaryAccent),
                    BorderFactory.createEmptyBorder(3, 8, 3, 8)
            ));
            btn.setToolTipText(tooltip);
            btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            return btn;
        }

        private void addBookmark(JPanel parent, String title, String url) {
            Theme theme = ThemeManager.getInstance().getCurrentTheme();
            JButton btn = new JButton(title);
            btn.setFont(theme.fontSmall);
            btn.setForeground(theme.textSecondary);
            btn.setBackground(theme.panel);
            btn.setOpaque(true);
            btn.setFocusPainted(false);
            btn.setBorder(BorderFactory.createEmptyBorder(2, 6, 2, 6));
            btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

            btn.addMouseListener(new MouseAdapter() {
                @Override public void mouseEntered(MouseEvent e) {
                    btn.setForeground(theme.neonAccent);
                    btn.setBackground(theme.elevatedPanel);
                }
                @Override public void mouseExited(MouseEvent e) {
                    btn.setForeground(theme.textSecondary);
                    btn.setBackground(theme.panel);
                }
            });
            btn.addActionListener(e -> navigate(url));
            parent.add(btn);
        }

        private JPanel buildInspectorDrawer() {
            Theme theme = ThemeManager.getInstance().getCurrentTheme();
            JPanel drawer = new JPanel(new BorderLayout());
            drawer.setOpaque(true);
            drawer.setBackground(theme.elevatedPanel);
            drawer.setBorder(BorderFactory.createMatteBorder(1, 0, 0, 0, theme.neonAccent));

            JTabbedPane inspectorTabs = new JTabbedPane();
            inspectorTabs.setBackground(theme.elevatedPanel);
            inspectorTabs.setForeground(theme.textPrimary);
            inspectorTabs.setFont(theme.fontSmall);

            // 1. Headers Tab
            headersArea = makeCodeArea(theme);
            inspectorTabs.addTab("HTTP Headers", new JScrollPane(headersArea));

            // 2. Source Tab
            sourceArea = makeCodeArea(theme);
            inspectorTabs.addTab("HTML Page Source", new JScrollPane(sourceArea));

            // 3. Network & Security Audit Tab
            networkAuditArea = makeCodeArea(theme);
            inspectorTabs.addTab("Network & Security Audit", new JScrollPane(networkAuditArea));

            drawer.add(inspectorTabs, BorderLayout.CENTER);
            return drawer;
        }

        private JTextArea makeCodeArea(Theme theme) {
            JTextArea area = new JTextArea();
            area.setEditable(false);
            area.setFont(theme.fontMonospace);
            area.setBackground(theme.background);
            area.setForeground(theme.textPrimary);
            area.setCaretColor(Color.WHITE);
            area.setBorder(BorderFactory.createEmptyBorder(6, 6, 6, 6));
            return area;
        }

        private void handleAddressBarSubmit() {
            String raw = urlField.getText();
            if (raw == null) return;
            String input = raw.trim();
            if (input.isEmpty()) return;

            // 1. Strip accidental leftover "imperion://portal " or "imperion:// "
            if (input.toLowerCase().startsWith("imperion://")) {
                if (input.equalsIgnoreCase("imperion://portal") || input.equalsIgnoreCase("imperion://home") || input.equalsIgnoreCase("imperion://")) {
                    navigate(DEFAULT_HOME_URL);
                    return;
                }
                // User typed search query after imperion://
                String stripped = input.replaceFirst("(?i)^imperion://[^\\s]*\\s*", "").trim();
                if (!stripped.isEmpty()) {
                    input = stripped;
                }
            }

            if (input.isEmpty()) {
                navigate(DEFAULT_HOME_URL);
                return;
            }

            // 2. WhatsApp special keyword shortcut
            if (input.equalsIgnoreCase("whatsap") || input.equalsIgnoreCase("whatsapp")
                    || input.equalsIgnoreCase("whatsap web") || input.equalsIgnoreCase("whatsapp web")) {
                navigate("https://web.whatsapp.com");
                return;
            }

            // 3. YouTube keyword shortcut
            if (input.equalsIgnoreCase("youtube") || input.equalsIgnoreCase("yt")) {
                navigate("https://www.youtube.com");
                return;
            }

            // 4. Exact valid URL scheme check (http://, https://, file://) without spaces
            if (input.matches("(?i)^(https?|file)://\\S+")) {
                navigate(input);
                return;
            }

            // 5. If input contains spaces, it's definitely a search query
            if (input.contains(" ")) {
                executeSearchQuery(input);
                return;
            }

            // 6. Domain name detection (e.g. "google.com", "github.com", "wikipedia.org", "web.whatsapp.com")
            if (input.matches("(?i)^[a-z0-9-]+(\\.[a-z0-9-]+)+(:\\d+)?(/.*)?$")) {
                navigate("https://" + input);
                return;
            }

            // 7. Localhost / IP address detection
            if (input.startsWith("localhost") || input.matches("^\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}(:\\d+)?(/.*)?$")) {
                navigate("http://" + input);
                return;
            }

            // 8. Otherwise, treat as search query on selected search engine
            executeSearchQuery(input);
        }

        private void executeSearchQuery(String query) {
            String engine = (String) searchEngineSelector.getSelectedItem();
            String encoded = URLEncoder.encode(query, StandardCharsets.UTF_8);
            String searchUrl;
            switch (engine != null ? engine : "Google") {
                case "DuckDuckGo":
                    searchUrl = "https://html.duckduckgo.com/html/?q=" + encoded;
                    break;
                case "Wikipedia":
                    searchUrl = "https://en.wikipedia.org/wiki/Special:Search?search=" + encoded;
                    break;
                case "GitHub":
                    searchUrl = "https://github.com/search?q=" + encoded;
                    break;
                case "Bing":
                    searchUrl = "https://www.bing.com/search?q=" + encoded;
                    break;
                default:
                    searchUrl = "https://www.google.com/search?q=" + encoded;
                    break;
            }
            navigate(searchUrl);
        }

        public void navigate(String targetUrl) {
            if (targetUrl == null || targetUrl.isEmpty()) return;

            targetUrl = targetUrl.trim();

            if (!currentUrl.isEmpty() && !currentUrl.equals(targetUrl)) {
                backHistory.push(currentUrl);
                forwardHistory.clear();
            }

            currentUrl = targetUrl;
            urlField.setText(targetUrl);
            updateNavButtons();

            if (DEFAULT_HOME_URL.equalsIgnoreCase(targetUrl) || "about:blank".equalsIgnoreCase(targetUrl)) {
                renderCyberPortal();
                return;
            }

            // Check if this is WhatsApp Web or another modern complex web app
            if (isKnownModernWebApp(targetUrl)) {
                renderWebAppGateway(targetUrl);
                return;
            }

            // Execute asynchronous HTTP fetch
            fetchAndRenderUrl(targetUrl);
        }

        private boolean isKnownModernWebApp(String url) {
            String lower = url.toLowerCase();
            return lower.contains("web.whatsapp.com") || lower.contains("whatsapp.com")
                    || lower.contains("youtube.com") || lower.contains("netflix.com")
                    || lower.contains("discord.com") || lower.contains("spotify.com")
                    || lower.contains("instagram.com");
        }

        private void renderWebAppGateway(String targetUrl) {
            String appName = "Modern Web Application";
            String accentColor = "#22D3EE";
            String iconSymbol = "🌐";
            String note = "This web app requires live JavaScript, WebSockets, and interactive browser features.";

            if (targetUrl.toLowerCase().contains("whatsapp")) {
                appName = "WhatsApp Web";
                accentColor = "#25D366";
                iconSymbol = "💬";
                note = "WhatsApp Web requires end-to-end encryption, QR code camera linking, WebSockets, and WebAssembly.";
            } else if (targetUrl.toLowerCase().contains("youtube")) {
                appName = "YouTube";
                accentColor = "#EF4444";
                iconSymbol = "▶";
                note = "YouTube requires hardware video decoding, HTML5 Media Source Extensions, and live streaming APIs.";
            } else if (targetUrl.toLowerCase().contains("discord")) {
                appName = "Discord Web";
                accentColor = "#5865F2";
                iconSymbol = "🎧";
                note = "Discord requires WebRTC audio channels, WebSockets, and real-time push gateways.";
            }

            if (titleListener != null) titleListener.accept(appName);
            securityBadge.setText("[APP GATEWAY]");
            securityBadge.setForeground(ThemeManager.getInstance().getCurrentTheme().neonAccent);
            statusLabel.setText(appName + " Gateway Active");

            String html = "<html><body style='background-color:#080B14;color:#F1F5F9;font-family:Segoe UI, sans-serif;padding:32px;text-align:center;'>" +
                    "<div style='max-width:620px;margin:20px auto;background-color:#101827;border:2px solid " + accentColor + ";border-radius:12px;padding:32px;box-shadow:0 0 25px rgba(34,211,238,0.15);'>" +
                    "<div style='font-size:44px;margin-bottom:10px;'>" + iconSymbol + "</div>" +
                    "<h1 style='color:" + accentColor + ";font-size:26px;letter-spacing:1px;margin:0 0 12px 0;'>" + appName.toUpperCase() + "</h1>" +
                    "<p style='color:#94A3B8;font-size:14px;line-height:1.6;margin:0 0 24px 0;'>" + note + "</p>" +
                    "<div style='margin-bottom:24px;'>" +
                    "<a href='action://launch-host' style='background-color:" + accentColor + ";color:#080B14;font-size:15px;font-weight:bold;padding:12px 28px;border-radius:6px;text-decoration:none;display:inline-block;'>" +
                    "🚀 LAUNCH " + appName.toUpperCase() + " IN CHROME / EDGE" +
                    "</a>" +
                    "</div>" +
                    "<hr style='border:0;border-top:1px solid #1E293B;margin:20px 0;'/>" +
                    "<table width='100%' cellpadding='4' style='font-size:12px;color:#64748B;text-align:left;'>" +
                    "<tr><td><b>Target URL:</b></td><td><code>" + targetUrl + "</code></td></tr>" +
                    "<tr><td><b>Integration Mode:</b></td><td>Hybrid Cyber Bridge (Host Chromium)</td></tr>" +
                    "<tr><td><b>Quick Navigation:</b></td><td><a href='https://www.google.com/search?q=" + URLEncoder.encode(appName, StandardCharsets.UTF_8) + "'>Search " + appName + " on Google</a> | <a href='imperion://portal'>Return to Home Portal</a></td></tr>" +
                    "</table>" +
                    "</div>" +
                    "</body></html>";

            webView.setText(html);
            webView.setCaretPosition(0);

            // Immediately launch in host browser for instant user access!
            launchInRealBrowser(targetUrl);
        }

        private void fetchAndRenderUrl(String urlStr) {
            progressBar.setVisible(true);
            progressBar.setIndeterminate(true);
            statusLabel.setText("Connecting to " + urlStr + "...");

            boolean isHttps = urlStr.toLowerCase().startsWith("https://");
            securityBadge.setText(isHttps ? "[HTTPS SECURED]" : "[HTTP INSECURE]");
            securityBadge.setForeground(isHttps ? ThemeManager.getInstance().getCurrentTheme().success : ThemeManager.getInstance().getCurrentTheme().warning);

            long startTime = System.currentTimeMillis();

            new SwingWorker<HttpResponse<String>, Void>() {
                private String resolvedIp = "Unknown";
                private String rawBody = "";
                private Exception error = null;

                @Override
                protected HttpResponse<String> doInBackground() {
                    try {
                        URI uri;
                        try {
                            uri = URI.create(urlStr);
                        } catch (Exception ex) {
                            // Try URL parser encoding
                            java.net.URL u = new java.net.URL(urlStr);
                            uri = new URI(u.getProtocol(), u.getUserInfo(), u.getHost(), u.getPort(), u.getPath(), u.getQuery(), u.getRef());
                        }

                        try {
                            if (uri.getHost() != null) {
                                resolvedIp = InetAddress.getByName(uri.getHost()).getHostAddress();
                            }
                        } catch (Exception ignored) {}

                        HttpClient client = HttpClient.newBuilder()
                                .version(HttpClient.Version.HTTP_2)
                                .followRedirects(HttpClient.Redirect.NORMAL)
                                .connectTimeout(Duration.ofSeconds(10))
                                .build();

                        HttpRequest req = HttpRequest.newBuilder()
                                .uri(uri)
                                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36 ImperionOS/2026.10")
                                .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/webp,*/*;q=0.8")
                                .header("Accept-Language", "en-US,en;q=0.9")
                                .timeout(Duration.ofSeconds(12))
                                .GET()
                                .build();

                        HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString());
                        rawBody = resp.body();
                        return resp;
                    } catch (Exception e) {
                        error = e;
                        return null;
                    }
                }

                @Override
                protected void done() {
                    progressBar.setVisible(false);
                    long duration = System.currentTimeMillis() - startTime;

                    if (error != null || rawBody == null) {
                        renderErrorPage(urlStr, error != null ? error.getMessage() : "No response body received.");
                        statusLabel.setText("Failed to load in " + duration + " ms");
                        return;
                    }

                    try {
                        HttpResponse<String> resp = get();
                        int statusCode = resp != null ? resp.statusCode() : 200;

                        // Check if server rejected or returned error status
                        if (statusCode >= 400) {
                            if (isKnownModernWebApp(urlStr)) {
                                renderWebAppGateway(urlStr);
                                return;
                            }
                            renderErrorPage(urlStr, "Server responded with HTTP " + statusCode);
                            statusLabel.setText("HTTP " + statusCode + " | Failed in " + duration + " ms");
                            return;
                        }

                        statusLabel.setText("HTTP " + statusCode + " | Loaded in " + duration + " ms | " + String.format("%.1f KB", rawBody.length() / 1024.0));

                        // Populate Inspector data
                        updateInspectorData(urlStr, resp, resolvedIp, rawBody, duration);

                        // Extract page title if present
                        String pageTitle = extractTitle(rawBody);
                        if (titleListener != null) titleListener.accept(pageTitle != null ? pageTitle : urlStr);

                        // Render web page
                        webView.setText(cleanHtmlForViewer(rawBody, urlStr));
                        webView.setCaretPosition(0);

                    } catch (Exception ex) {
                        renderErrorPage(urlStr, ex.getMessage());
                    }
                }
            }.execute();
        }

        private void updateInspectorData(String urlStr, HttpResponse<String> resp, String ip, String rawHtml, long durationMs) {
            // Headers
            StringBuilder hb = new StringBuilder();
            hb.append("=== REQUEST & RESPONSE HEADERS ===\n");
            hb.append("URL: ").append(urlStr).append("\n");
            if (resp != null) {
                hb.append("Status: ").append(resp.statusCode()).append("\n");
                hb.append("Protocol: ").append(resp.version()).append("\n\n");
                resp.headers().map().forEach((k, v) -> {
                    hb.append(k).append(": ").append(String.join(", ", v)).append("\n");
                });
            }
            headersArea.setText(hb.toString());
            headersArea.setCaretPosition(0);

            // Source
            sourceArea.setText(rawHtml);
            sourceArea.setCaretPosition(0);

            // Network Audit
            StringBuilder nb = new StringBuilder();
            nb.append("=== NETWORK & CYBER SECURITY AUDIT ===\n");
            nb.append("Target Host:        ").append(safeGetHost(urlStr)).append("\n");
            nb.append("Resolved IP:        ").append(ip).append("\n");
            nb.append("Encryption:         ").append(urlStr.startsWith("https") ? "TLS Encrypted (HTTPS)" : "Unencrypted Plaintext (HTTP)").append("\n");
            nb.append("Latency:            ").append(durationMs).append(" ms\n");
            nb.append("Payload Size:       ").append(rawHtml.length()).append(" bytes\n");
            nb.append("Host Browser Sync:  Ready (Click 'Open in Chrome/Edge' to view full JS/WebGL)\n");
            networkAuditArea.setText(nb.toString());
            networkAuditArea.setCaretPosition(0);
        }

        private String safeGetHost(String urlStr) {
            try {
                URI u = URI.create(urlStr);
                return u.getHost() != null ? u.getHost() : urlStr;
            } catch (Exception e) {
                return urlStr;
            }
        }

        private String cleanHtmlForViewer(String rawHtml, String baseUrl) {
            // Strip scripts and styles that choke Swing HTMLEditorKit
            String cleaned = rawHtml.replaceAll("(?s)<script.*?</script>", "")
                    .replaceAll("(?s)<style.*?</style>", "")
                    .replaceAll("(?s)<svg.*?</svg>", "[SVG Image]");

            // Inject host browser launch banner at the top of real web pages
            String banner = "<div style='background-color:#101827;border:1px solid #22D3EE;padding:10px;margin-bottom:14px;border-radius:6px;'>" +
                    "<span style='color:#22D3EE;font-weight:bold;'>[IMPERION NETRUNNER READER VIEW]</span> — " +
                    "<span style='color:#94A3B8;'>Viewing cleaned HTML for <b>" + baseUrl + "</b>. " +
                    "To access live JavaScript/CSS3/WebGL, click <b>Open in Chrome/Edge</b> above.</span>" +
                    "</div>";

            if (cleaned.contains("<body")) {
                cleaned = cleaned.replaceFirst("(<body[^>]*>)", "$1" + banner);
            } else {
                cleaned = banner + cleaned;
            }
            return cleaned;
        }

        private void renderCyberPortal() {
            if (titleListener != null) titleListener.accept("Imperion Portal");
            securityBadge.setText("[LOCAL SECURE]");
            securityBadge.setForeground(ThemeManager.getInstance().getCurrentTheme().neonAccent);
            statusLabel.setText("Imperion Cyber Portal Ready");

            String portalHtml = "<html><body style='background-color:#080B14;color:#F1F5F9;font-family:Segoe UI, sans-serif;padding:24px;'>" +
                    "<div style='text-align:center;padding:20px 0;'>" +
                    "<h1 style='color:#22D3EE;font-size:32px;letter-spacing:2px;margin:0;'>IMPERION OS — NETRUNNER WEB EXPLORER</h1>" +
                    "<p style='color:#94A3B8;font-size:14px;'>Hybrid Cyberpunk Web Gateway & Host Browser Bridge</p>" +
                    "</div>" +
                    "<hr style='border:0;border-top:1px solid #1E293B;margin:16px 0;'/>" +
                    "<h2>QUICK PORTAL ACCESS</h2>" +
                    "<table width='100%' cellpadding='8' style='font-size:14px;'>" +
                    "<tr>" +
                    "<td width='50%' style='background-color:#101827;border:1px solid #1E293B;border-radius:6px;'>" +
                    "<b><a href='https://www.google.com'>Google Search</a></b><br/><span style='color:#94A3B8;font-size:12px;'>Global web index & knowledge engine</span></td>" +
                    "<td width='50%' style='background-color:#101827;border:1px solid #10B981;border-radius:6px;'>" +
                    "<b><a href='https://web.whatsapp.com' style='color:#34D399;'>WhatsApp Web</a></b><br/><span style='color:#94A3B8;font-size:12px;'>Fast messaging, chats & QR connection</span></td>" +
                    "</tr>" +
                    "<tr>" +
                    "<td style='background-color:#101827;border:1px solid #EF4444;border-radius:6px;'>" +
                    "<b><a href='https://www.youtube.com' style='color:#F87171;'>YouTube</a></b><br/><span style='color:#94A3B8;font-size:12px;'>Videos, tech streams & tutorials</span></td>" +
                    "<td style='background-color:#101827;border:1px solid #1E293B;border-radius:6px;'>" +
                    "<b><a href='https://github.com'>GitHub Repositories</a></b><br/><span style='color:#94A3B8;font-size:12px;'>Open source codebase & version control</span></td>" +
                    "</tr>" +
                    "<tr>" +
                    "<td style='background-color:#101827;border:1px solid #1E293B;border-radius:6px;'>" +
                    "<b><a href='https://en.wikipedia.org'>Wikipedia Encyclopedia</a></b><br/><span style='color:#94A3B8;font-size:12px;'>Free collective encyclopedia</span></td>" +
                    "<td style='background-color:#101827;border:1px solid #1E293B;border-radius:6px;'>" +
                    "<b><a href='https://duckduckgo.com'>DuckDuckGo</a></b><br/><span style='color:#94A3B8;font-size:12px;'>Privacy-first trackerless search</span></td>" +
                    "</tr>" +
                    "</table>" +
                    "<br/>" +
                    "<div style='background-color:#172235;border:1px solid #3B82F6;padding:14px;border-radius:8px;'>" +
                    "<b style='color:#38BDF8;'>FEATURES & USAGE:</b><br/>" +
                    "• <b>Full URL Navigation:</b> Enter any <code>https://...</code> URL in the address bar above.<br/>" +
                    "• <b>Smart Search:</b> Type any search terms (e.g., <code>whatsap</code>, <code>cyberpunk neon desktop</code>) to search instantly.<br/>" +
                    "• <b>Web Apps (WhatsApp / YouTube):</b> Click on WhatsApp or YouTube to instantly bridge into your host browser.<br/>" +
                    "• <b>Host Browser Launch:</b> Click <b>'Open in Chrome/Edge'</b> anytime to pop the current site out into your host Chromium browser for interactive JavaScript, videos, and full CSS.<br/>" +
                    "• <b>Cyber Inspector:</b> Click <b>'Inspector'</b> to audit raw HTTP request/response headers, view clean HTML source code, and resolve host DNS & latency." +
                    "</div>" +
                    "</body></html>";

            webView.setText(portalHtml);
            webView.setCaretPosition(0);
        }

        private void renderErrorPage(String urlStr, String errorMsg) {
            if (titleListener != null) titleListener.accept("Error Loading");
            securityBadge.setText("[LOAD FAILED]");
            securityBadge.setForeground(ThemeManager.getInstance().getCurrentTheme().error);

            String suggestionQuery = urlStr.replaceFirst("(?i)^https?://", "")
                    .replaceFirst("(?i)^imperion://[^\\s]*\\s*", "")
                    .trim();
            if (suggestionQuery.isEmpty()) suggestionQuery = urlStr;
            String searchEncoded = URLEncoder.encode(suggestionQuery, StandardCharsets.UTF_8);

            String html = "<html><body style='background-color:#080B14;color:#F1F5F9;font-family:Segoe UI, sans-serif;padding:24px;'>" +
                    "<div style='max-width:680px;margin:0 auto;background-color:#101827;border:1px solid #EF4444;border-radius:10px;padding:24px;'>" +
                    "<h2 style='color:#EF4444;margin-top:0;'>⚠️ Web Connection Notice</h2>" +
                    "<p>Could not connect to URL: <code>" + urlStr + "</code></p>" +
                    "<p style='color:#94A3B8;font-size:13px;'>Diagnostic: " + (errorMsg != null ? errorMsg : "Request timed out or host unreachable") + "</p>" +
                    "<hr style='border:0;border-top:1px solid #1E293B;margin:16px 0;'/>" +
                    "<div style='margin-bottom:16px;'>" +
                    "<a href='action://launch-host' style='background-color:#22D3EE;color:#080B14;font-size:13px;font-weight:bold;padding:8px 16px;border-radius:4px;text-decoration:none;display:inline-block;margin-right:8px;'>" +
                    "🌐 Open in Chrome / Edge" +
                    "</a>" +
                    "<a href='https://www.google.com/search?q=" + searchEncoded + "' style='background-color:#1E293B;color:#22D3EE;font-size:13px;padding:8px 16px;border-radius:4px;text-decoration:none;display:inline-block;border:1px solid #22D3EE;'>" +
                    "🔍 Search Google for \"" + suggestionQuery + "\"" +
                    "</a>" +
                    "</div>" +
                    "<p style='color:#64748B;font-size:12px;'>• Ensure your PC has active internet access.<br/>" +
                    "• If this site requires JavaScript or WebSockets, click 'Open in Chrome/Edge' above.<br/>" +
                    "• Or <a href='imperion://portal' style='color:#38BDF8;'>return to Imperion Cyber Portal</a>.</p>" +
                    "</div>" +
                    "</body></html>";

            webView.setText(html);
            webView.setCaretPosition(0);
        }

        private String extractTitle(String html) {
            int start = html.toLowerCase().indexOf("<title>");
            int end = html.toLowerCase().indexOf("</title>");
            if (start != -1 && end != -1 && end > start + 7) {
                return html.substring(start + 7, end).trim();
            }
            return null;
        }

        private void goBack() {
            if (!backHistory.isEmpty()) {
                forwardHistory.push(currentUrl);
                String prev = backHistory.pop();
                currentUrl = prev;
                urlField.setText(prev);
                updateNavButtons();
                fetchAndRenderUrl(prev);
            }
        }

        private void goForward() {
            if (!forwardHistory.isEmpty()) {
                backHistory.push(currentUrl);
                String next = forwardHistory.pop();
                currentUrl = next;
                urlField.setText(next);
                updateNavButtons();
                fetchAndRenderUrl(next);
            }
        }

        private void reload() {
            if (!currentUrl.isEmpty()) {
                navigate(currentUrl);
            }
        }

        private void updateNavButtons() {
            btnBack.setEnabled(!backHistory.isEmpty());
            btnForward.setEnabled(!forwardHistory.isEmpty());
        }

        private void toggleInspector() {
            inspectorVisible = !inspectorVisible;
            splitPane.getBottomComponent().setVisible(inspectorVisible);
            if (inspectorVisible) {
                splitPane.setDividerLocation(0.65);
            }
            splitPane.revalidate();
            splitPane.repaint();
        }

        private void launchInRealBrowser() {
            launchInRealBrowser(currentUrl);
        }

        private void launchInRealBrowser(String targetUrl) {
            String url = targetUrl;
            String fieldText = urlField != null ? urlField.getText().trim() : "";

            if (url == null || url.isEmpty() || url.toLowerCase().startsWith("imperion://")) {
                if (!fieldText.isEmpty() && !fieldText.toLowerCase().startsWith("imperion://")) {
                    if (fieldText.matches("(?i)^https?://.*")) {
                        url = fieldText;
                    } else if (fieldText.contains(".") && !fieldText.contains(" ")) {
                        url = "https://" + fieldText;
                    } else {
                        url = "https://www.google.com/search?q=" + URLEncoder.encode(fieldText, StandardCharsets.UTF_8);
                    }
                } else {
                    url = "https://www.google.com";
                }
            }

            final String finalUrl = url;

            new Thread(() -> {
                try {
                    if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                        Desktop.getDesktop().browse(URI.create(finalUrl));
                        SwingUtilities.invokeLater(() -> {
                            if (getOS() != null) {
                                getOS().getNotificationManager().postNotification(
                                        "Launched Host Browser", "Opened URL in Windows browser: " + finalUrl, javaos.models.NotificationMessage.Level.INFO);
                            }
                        });
                    } else {
                        // Fallback: Windows cmd start
                        new ProcessBuilder("cmd.exe", "/c", "start", "", finalUrl).start();
                    }
                } catch (Exception ex) {
                    SwingUtilities.invokeLater(() ->
                            JOptionPane.showMessageDialog(this, "Failed to launch host browser:\n" + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE));
                }
            }, "HostBrowserLaunch").start();
        }
    }
}
