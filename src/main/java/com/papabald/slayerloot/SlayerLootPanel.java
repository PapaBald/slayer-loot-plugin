package com.papabald.slayerloot;

import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Graphics2D;
import java.awt.GridLayout;
import java.awt.Insets;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.Path2D;
import java.awt.image.BufferedImage;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JToggleButton;
import javax.swing.Scrollable;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.PluginPanel;
import net.runelite.client.util.AsyncBufferedImage;
import net.runelite.client.util.QuantityFormatter;

class SlayerLootPanel extends PluginPanel
{
    private static final int ICON_BTN_SIZE = 22;
    /** Cached raster icons — Runescape fonts do not draw carets reliably. */
    private static final ImageIcon ICON_CARET_UP = rasterCaretUp(ColorScheme.LIGHT_GRAY_COLOR);
    private static final ImageIcon ICON_CARET_DOWN = rasterCaretDown(ColorScheme.LIGHT_GRAY_COLOR);
    private static final ImageIcon ICON_HIDE_MINUS = rasterMinus(ColorScheme.LIGHT_GRAY_COLOR);
    private static final ImageIcon ICON_CLOSE = rasterClose(ColorScheme.PROGRESS_ERROR_COLOR);
    private static final ImageIcon ICON_EXCLUDE_OFF = rasterXOutline(ColorScheme.MEDIUM_GRAY_COLOR);
    private static final ImageIcon ICON_EXCLUDE_ON = rasterXFilled(ColorScheme.PROGRESS_ERROR_COLOR);
    /** Eye is shown when hidden tasks are visible; eye-with-slash is shown when they are filtered out. */
    private static final ImageIcon ICON_EYE = rasterEye(ColorScheme.LIGHT_GRAY_COLOR);
    private static final ImageIcon ICON_EYE_OFF = rasterEyeOff(ColorScheme.LIGHT_GRAY_COLOR);

    private final SlayerLootPlugin plugin;
    private final JPanel content = new ContentPanel();
    private JScrollPane scrollPane;
    private final JToggleButton showHiddenTasks = new JToggleButton(ICON_EYE_OFF);
    private volatile PanelState state = PanelState.EMPTY;

    /** Vertical-only scrolling content that forces children to fit the viewport width. */
    private static final class ContentPanel extends JPanel implements Scrollable
    {
        @Override
        public Dimension getPreferredScrollableViewportSize()
        {
            return getPreferredSize();
        }

        @Override
        public int getScrollableUnitIncrement(java.awt.Rectangle visibleRect, int orientation, int direction)
        {
            return 16;
        }

        @Override
        public int getScrollableBlockIncrement(java.awt.Rectangle visibleRect, int orientation, int direction)
        {
            return Math.max(16, visibleRect.height - 16);
        }

        @Override
        public boolean getScrollableTracksViewportWidth()
        {
            return true;
        }

        @Override
        public boolean getScrollableTracksViewportHeight()
        {
            return false;
        }
    }
    private final Map<String, Boolean> collapsedTasks = new HashMap<>();

    /** Toggles collapse/expand for all tracked tasks (shown when toolbar is built). */
    private JButton masterCollapseToggleBtn;

    private static final NumberFormat NUMBER_FORMAT = NumberFormat.getIntegerInstance();

    SlayerLootPanel(SlayerLootPlugin plugin)
    {
        super(false);
        this.plugin = plugin;
        setLayout(new BorderLayout());
        setBackground(ColorScheme.DARK_GRAY_COLOR);

        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
        content.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        content.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));

        JPanel controls = buildControlsPanel();
        scrollPane = new JScrollPane(content);
        scrollPane.setBorder(BorderFactory.createEmptyBorder());
        scrollPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        scrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
        scrollPane.getVerticalScrollBar().setUnitIncrement(16);
        add(controls, BorderLayout.NORTH);
        add(scrollPane, BorderLayout.CENTER);
    }

    private JButton createToolbarIconBtn(ImageIcon icon, String tooltip, Runnable action)
    {
        JButton b = iconOnlyButton(icon, tooltip);
        b.addActionListener(e -> action.run());
        return b;
    }

    /**
     * Small toolbar button drawn from a raster icon (Loot Tracker uses pixel icons; ours are vector-drawn bitmaps).
     */
    private static JButton iconOnlyButton(ImageIcon icon, String tooltip)
    {
        JButton b = new JButton(icon);
        b.setToolTipText(tooltip);
        b.setText(null);
        b.setMargin(new Insets(0, 0, 0, 0));
        b.setFocusPainted(false);
        b.setContentAreaFilled(false);
        b.setBorderPainted(false);
        b.setOpaque(false);
        Dimension d = new Dimension(ICON_BTN_SIZE, ICON_BTN_SIZE);
        b.setPreferredSize(d);
        b.setMinimumSize(d);
        b.setMaximumSize(d);
        return b;
    }

    private JPanel buildControlsPanel()
    {
        JPanel controls = new JPanel();
        controls.setLayout(new BoxLayout(controls, BoxLayout.Y_AXIS));
        controls.setBackground(ColorScheme.DARK_GRAY_COLOR);
        controls.setBorder(BorderFactory.createEmptyBorder(6, 8, 6, 8));

        // Two-column equal-width grid so each reset button takes 50% of the toolbar width.
        JPanel resets = new JPanel(new GridLayout(1, 2, 6, 0));
        resets.setOpaque(false);
        resets.setAlignmentX(Component.LEFT_ALIGNMENT);
        resets.setMaximumSize(new Dimension(Integer.MAX_VALUE, 28));

        JButton resetCurrent = new JButton("Reset Current");
        resetCurrent.addActionListener(e ->
        {
            if (plugin.getCurrentTaskKey() == null)
            {
                return;
            }

            int choice = JOptionPane.showConfirmDialog(
                this,
                "Reset current task loot data?",
                "Slayer Loot",
                JOptionPane.YES_NO_OPTION
            );
            if (choice == JOptionPane.YES_OPTION)
            {
                plugin.resetCurrentTask();
            }
        });

        JButton resetAll = new JButton("Reset All");
        resetAll.addActionListener(e ->
        {
            int choice = JOptionPane.showConfirmDialog(
                this,
                "Reset all tracked Slayer loot tasks?",
                "Slayer Loot",
                JOptionPane.YES_NO_OPTION
            );
            if (choice == JOptionPane.YES_OPTION)
            {
                plugin.resetAllTasks();
            }
        });

        resets.add(resetCurrent);
        resets.add(resetAll);

        masterCollapseToggleBtn = createToolbarIconBtn(
            ICON_CARET_UP,
            "Collapse or expand every task loot section",
            () ->
            {
                boolean collapse = collapsedTasks.values().stream().anyMatch(c -> !c);
                collapsedTasks.replaceAll((k, v) -> collapse);
                render();
            }
        );

        JPanel collapseRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 2));
        collapseRow.setOpaque(false);
        collapseRow.setAlignmentX(Component.LEFT_ALIGNMENT);
        collapseRow.add(masterCollapseToggleBtn);
        JLabel collapseHint = new JLabel("Collapse / expand task sections");
        collapseHint.setForeground(ColorScheme.MEDIUM_GRAY_COLOR);
        collapseRow.add(collapseHint);

        showHiddenTasks.setIcon(ICON_EYE_OFF);
        showHiddenTasks.setSelectedIcon(ICON_EYE);
        showHiddenTasks.setText(null);
        showHiddenTasks.setMargin(new Insets(0, 0, 0, 0));
        showHiddenTasks.setOpaque(false);
        showHiddenTasks.setContentAreaFilled(false);
        showHiddenTasks.setBorderPainted(false);
        showHiddenTasks.setFocusPainted(false);
        Dimension eyeSize = new Dimension(ICON_BTN_SIZE, ICON_BTN_SIZE);
        showHiddenTasks.setPreferredSize(eyeSize);
        showHiddenTasks.setMinimumSize(eyeSize);
        showHiddenTasks.setMaximumSize(eyeSize);
        refreshShowHiddenTasksTooltip();
        showHiddenTasks.addActionListener(e ->
        {
            refreshShowHiddenTasksTooltip();
            render();
        });

        JPanel togglesRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 2));
        togglesRow.setOpaque(false);
        togglesRow.setAlignmentX(Component.LEFT_ALIGNMENT);
        togglesRow.add(showHiddenTasks);
        JLabel hiddenHint = new JLabel("Show hidden tasks");
        hiddenHint.setForeground(ColorScheme.MEDIUM_GRAY_COLOR);
        togglesRow.add(hiddenHint);

        controls.add(resets);
        controls.add(Box.createRigidArea(new Dimension(0, 2)));
        controls.add(collapseRow);
        controls.add(Box.createRigidArea(new Dimension(0, 2)));
        controls.add(togglesRow);
        return controls;
    }

    private void refreshShowHiddenTasksTooltip()
    {
        showHiddenTasks.setToolTipText(showHiddenTasks.isSelected()
            ? "Currently showing hidden tasks — click to hide them"
            : "Currently hiding hidden tasks — click to show them");
    }

    /** If any panel is expanded (\u2260 collapsed), next click collapses everything; otherwise expands everything. */
    private void refreshMasterCollapseButton()
    {
        if (collapsedTasks.isEmpty())
        {
            masterCollapseToggleBtn.setIcon(ICON_CARET_UP);
            masterCollapseToggleBtn.setToolTipText("No tasks to collapse");
            masterCollapseToggleBtn.setEnabled(false);
            return;
        }

        masterCollapseToggleBtn.setEnabled(true);
        boolean allCollapsed = collapsedTasks.values().stream().allMatch(Boolean::booleanValue);
        if (allCollapsed)
        {
            masterCollapseToggleBtn.setIcon(ICON_CARET_DOWN);
            masterCollapseToggleBtn.setToolTipText("Expand all task loot sections");
        }
        else
        {
            masterCollapseToggleBtn.setIcon(ICON_CARET_UP);
            masterCollapseToggleBtn.setToolTipText("Collapse all task loot sections");
        }
    }

    void setState(PanelState state)
    {
        this.state = state;
        SwingUtilities.invokeLater(this::render);
    }

    private void render()
    {
        Set<String> validKeys = new HashSet<>();
        for (TaskLootRecord r : state.records)
        {
            validKeys.add(r.getTaskKey());
        }
        collapsedTasks.entrySet().removeIf(e -> !validKeys.contains(e.getKey()));

        int scroll = scrollPane.getVerticalScrollBar().getValue();

        content.removeAll();
        addActiveTaskSummary();
        List<TaskLootRecord> records = new ArrayList<>(state.records);
        records.sort(Comparator.comparing(TaskLootRecord::getStartedAt).reversed());

        if (records.isEmpty())
        {
            JLabel empty = new JLabel("No Slayer loot data yet.");
            empty.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
            empty.setAlignmentX(Component.LEFT_ALIGNMENT);
            content.add(empty);
        }
        else
        {
            int visibleCount = 0;
            for (TaskLootRecord record : records)
            {
                if (addTaskCard(record))
                {
                    visibleCount++;
                }
            }

            if (visibleCount == 0)
            {
                JLabel empty = new JLabel("No visible tasks. Enable 'Show hidden tasks'.");
                empty.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
                empty.setAlignmentX(Component.LEFT_ALIGNMENT);
                content.add(empty);
            }
        }

        refreshMasterCollapseButton();

        content.revalidate();
        content.repaint();

        // Deferred: the scrollbar's max isn't recomputed until after layout runs.
        SwingUtilities.invokeLater(() -> scrollPane.getVerticalScrollBar().setValue(scroll));
    }

    private void addActiveTaskSummary()
    {
        JPanel activeTask = new JPanel();
        activeTask.setLayout(new BoxLayout(activeTask, BoxLayout.Y_AXIS));
        activeTask.setBackground(ColorScheme.DARK_GRAY_COLOR);
        activeTask.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(ColorScheme.MEDIUM_GRAY_COLOR),
            BorderFactory.createEmptyBorder(6, 8, 6, 8)
        ));
        activeTask.setAlignmentX(Component.LEFT_ALIGNMENT);

        String taskName = state.hasActiveTask ? state.currentTaskName : "No active task";
        JLabel name = new JLabel("Current task: " + taskName);
        name.setFont(FontManager.getRunescapeBoldFont());
        name.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
        name.setAlignmentX(Component.LEFT_ALIGNMENT);
        activeTask.add(name);

        if (state.hasActiveTask)
        {
            int completed = state.completed();
            int remaining = state.remaining;
            int original = state.original;
            JLabel counts = new JLabel("Progress: " + NUMBER_FORMAT.format(completed) + "/" + NUMBER_FORMAT.format(original)
                + " (" + NUMBER_FORMAT.format(remaining) + " left)");
            counts.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
            counts.setAlignmentX(Component.LEFT_ALIGNMENT);
            activeTask.add(counts);
        }

        // Stretch full panel width so the box visually aligns with task cards below.
        activeTask.setMaximumSize(new Dimension(Integer.MAX_VALUE, activeTask.getPreferredSize().height));

        content.add(activeTask);
        content.add(Box.createRigidArea(new Dimension(0, 8)));
    }

    private boolean addTaskCard(TaskLootRecord record)
    {
        collapsedTasks.putIfAbsent(record.getTaskKey(), false);
        if (record.isHidden() && !showHiddenTasks.isSelected())
        {
            return false;
        }

        boolean collapsed = collapsedTasks.getOrDefault(record.getTaskKey(), false);

        JLabel title = new JLabel(record.getTaskName() + " \u00D7 " + NUMBER_FORMAT.format(record.getKills()));
        title.setFont(FontManager.getRunescapeBoldFont());
        title.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
        title.setToolTipText("Kills: " + NUMBER_FORMAT.format(record.getKills()));

        JButton collapseBtn = iconOnlyButton(collapsed ? ICON_CARET_DOWN : ICON_CARET_UP, collapsed ? "Expand drops" : "Collapse drops");
        collapseBtn.addActionListener(e ->
        {
            collapsedTasks.put(record.getTaskKey(), !collapsedTasks.getOrDefault(record.getTaskKey(), false));
            render();
        });

        JButton hideBtn = iconOnlyButton(ICON_HIDE_MINUS, record.isHidden() ? "Show task in list" : "Hide task from list");
        hideBtn.addActionListener(e ->
        {
            boolean hide = !record.isHidden();
            int choice = JOptionPane.showConfirmDialog(
                this,
                hide ? "Hide this task from the main list?" : "Unhide this task?",
                "Slayer Loot",
                JOptionPane.YES_NO_OPTION
            );
            if (choice == JOptionPane.YES_OPTION)
            {
                plugin.setTaskHidden(record.getTaskKey(), hide);
            }
        });

        JButton deleteBtn = iconOnlyButton(ICON_CLOSE, "Delete task");
        deleteBtn.addActionListener(e ->
        {
            int choice = JOptionPane.showConfirmDialog(
                this,
                "Delete this tracked task and all its loot history?",
                "Slayer Loot",
                JOptionPane.YES_NO_OPTION
            );
            if (choice == JOptionPane.YES_OPTION)
            {
                plugin.deleteTask(record.getTaskKey());
            }
        });

        JPanel iconActions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 2, 0));
        iconActions.setOpaque(false);
        iconActions.add(collapseBtn);
        iconActions.add(hideBtn);
        iconActions.add(deleteBtn);

        JPanel headerRow = new JPanel();
        headerRow.setLayout(new BoxLayout(headerRow, BoxLayout.X_AXIS));
        headerRow.setOpaque(false);
        headerRow.setAlignmentX(Component.LEFT_ALIGNMENT);
        headerRow.add(title);
        headerRow.add(Box.createHorizontalGlue());
        headerRow.add(iconActions);
        headerRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, ICON_BTN_SIZE + 2));

        JPanel taskCard = new JPanel();
        taskCard.setLayout(new BoxLayout(taskCard, BoxLayout.Y_AXIS));
        taskCard.setBackground(ColorScheme.DARK_GRAY_COLOR);
        taskCard.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(ColorScheme.MEDIUM_GRAY_COLOR),
            BorderFactory.createEmptyBorder(8, 8, 8, 8)
        ));
        taskCard.setAlignmentX(Component.LEFT_ALIGNMENT);

        taskCard.add(headerRow);
        taskCard.add(Box.createRigidArea(new Dimension(0, 2)));

        JLabel valueLeft = new JLabel("Total");
        valueLeft.setForeground(ColorScheme.MEDIUM_GRAY_COLOR);
        JLabel valueRight = new JLabel(QuantityFormatter.quantityToStackSize(record.getGrossProfit()) + " gp");
        valueRight.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
        valueRight.setToolTipText("Gross: " + QuantityFormatter.quantityToStackSize(record.getGrossProfit()) + " gp"
            + "  |  Actual: " + QuantityFormatter.quantityToStackSize(record.getActualProfit()) + " gp");

        JPanel valueRow = new JPanel();
        valueRow.setLayout(new BoxLayout(valueRow, BoxLayout.X_AXIS));
        valueRow.setOpaque(false);
        valueRow.setAlignmentX(Component.LEFT_ALIGNMENT);
        valueRow.add(valueLeft);
        valueRow.add(Box.createHorizontalGlue());
        valueRow.add(valueRight);
        valueRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, valueRight.getPreferredSize().height));
        taskCard.add(valueRow);

        JLabel actualLeft = new JLabel("Actual");
        actualLeft.setForeground(ColorScheme.MEDIUM_GRAY_COLOR);
        JLabel actualRight = new JLabel(QuantityFormatter.quantityToStackSize(record.getActualProfit()) + " gp");
        actualRight.setForeground(ColorScheme.MEDIUM_GRAY_COLOR);
        JPanel actualRow = new JPanel();
        actualRow.setLayout(new BoxLayout(actualRow, BoxLayout.X_AXIS));
        actualRow.setOpaque(false);
        actualRow.setAlignmentX(Component.LEFT_ALIGNMENT);
        actualRow.add(actualLeft);
        actualRow.add(Box.createHorizontalGlue());
        actualRow.add(actualRight);
        actualRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, actualRight.getPreferredSize().height));
        taskCard.add(actualRow);
        taskCard.add(Box.createRigidArea(new Dimension(0, 6)));

        if (collapsed)
        {
            clampCardHeight(taskCard);
            content.add(taskCard);
            content.add(Box.createRigidArea(new Dimension(0, 8)));
            return true;
        }

        List<TaskLootItem> items = new ArrayList<>(record.getItems());
        items.sort(Comparator.comparingLong(TaskLootItem::getTotalGeValue).reversed());
        if (items.isEmpty())
        {
            JLabel noItems = new JLabel("No drops recorded yet.");
            noItems.setForeground(ColorScheme.MEDIUM_GRAY_COLOR);
            noItems.setAlignmentX(Component.LEFT_ALIGNMENT);
            taskCard.add(noItems);
            clampCardHeight(taskCard);
            content.add(taskCard);
            content.add(Box.createRigidArea(new Dimension(0, 8)));
            return true;
        }

        // Fixed 5-column grid mirrors the official Loot Tracker
        // (LootTrackerBox#ITEMS_PER_ROW). GridLayout stretches to whatever
        // width the side panel actually gives us, so we don't need any
        // viewport-width math.
        final int columns = 5;
        final int rowSize = ((items.size() % columns) == 0 ? 0 : 1) + items.size() / columns;

        JPanel itemsPanel = new JPanel(new GridLayout(rowSize, columns, 1, 1));
        itemsPanel.setBackground(ColorScheme.DARK_GRAY_COLOR);
        itemsPanel.setAlignmentX(Component.LEFT_ALIGNMENT);

        for (int i = 0; i < rowSize * columns; i++)
        {
            if (i < items.size())
            {
                itemsPanel.add(buildItemTile(record.getTaskKey(), items.get(i)));
            }
            else
            {
                // Pad short final row with blanks so existing cells aren't stretched horizontally.
                JPanel filler = new JPanel();
                filler.setBackground(ColorScheme.DARKER_GRAY_COLOR);
                itemsPanel.add(filler);
            }
        }

        // Cap height so BoxLayout doesn't grow the grid vertically beyond what it needs.
        itemsPanel.setMaximumSize(new Dimension(Integer.MAX_VALUE, itemsPanel.getPreferredSize().height));
        taskCard.add(itemsPanel);

        JLabel dropsHint = new JLabel("Click an item to exclude in actual");
        dropsHint.setForeground(ColorScheme.MEDIUM_GRAY_COLOR);
        dropsHint.setAlignmentX(Component.LEFT_ALIGNMENT);
        taskCard.add(Box.createRigidArea(new Dimension(0, 4)));
        taskCard.add(dropsHint);

        clampCardHeight(taskCard);
        content.add(taskCard);
        content.add(Box.createRigidArea(new Dimension(0, 8)));
        return true;
    }

    private static void clampCardHeight(JPanel taskCard)
    {
        taskCard.setMaximumSize(new Dimension(Integer.MAX_VALUE, taskCard.getPreferredSize().height));
    }

    private JPanel buildItemTile(String taskKey, TaskLootItem item)
    {
        // Slot mirrors Loot Tracker's slotContainer: a small darker-gray cell
        // that lets GridLayout dictate the cell width so 5 tiles always fit.
        JPanel tile = new JPanel();
        tile.setBackground(ColorScheme.DARKER_GRAY_COLOR);

        JLabel iconLabel = new JLabel();
        iconLabel.setHorizontalAlignment(SwingConstants.CENTER);
        iconLabel.setVerticalAlignment(SwingConstants.CENTER);
        iconLabel.setOpaque(false);

        AsyncBufferedImage img = plugin.getItemIcon(item.getItemId(), item.getQuantity());
        Runnable applyIcon = () ->
        {
            BufferedImage rendered = renderExcluded(img, item.isExcluded());
            iconLabel.setIcon(new ImageIcon(rendered));
        };
        applyIcon.run();
        img.onLoaded(() -> SwingUtilities.invokeLater(applyIcon));

        String valueText = QuantityFormatter.quantityToStackSize(item.getTotalGeValue()) + " gp";
        String tip = "<html><b>" + escapeHtml(item.getItemName()) + "</b><br>"
            + "Quantity: " + NUMBER_FORMAT.format(item.getQuantity()) + "<br>"
            + "Total: " + valueText
            + (item.isExcluded() ? "<br><i>Excluded from Actual</i>" : "")
            + "<br><br><i>Click to "
            + (item.isExcluded() ? "include in Actual" : "exclude from Actual")
            + "</i></html>";
        iconLabel.setToolTipText(tip);
        iconLabel.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

        iconLabel.addMouseListener(new MouseAdapter()
        {
            @Override
            public void mouseClicked(MouseEvent e)
            {
                // Left-click toggles inclusion. Pass the inverse of the current
                // excluded state so plugin.setItemIncluded flips it.
                if (SwingUtilities.isLeftMouseButton(e))
                {
                    boolean nowExcluded = !item.isExcluded();

                    // Flip the snapshot copy and repaint this one tile immediately.
                    // The authoritative push will arrive shortly and agree with us.
                    item.setExcluded(nowExcluded);
                    applyIcon.run();

                    plugin.setItemIncluded(taskKey, item.getItemId(), !nowExcluded);
                }
            }
        });

        tile.add(iconLabel);
        return tile;
    }

    /**
     * Renders the item icon at its native size and overlays a red X when the
     * item is excluded from the Actual profit total.
     */
    private static BufferedImage renderExcluded(BufferedImage source, boolean excluded)
    {
        if (source == null || source.getWidth() <= 0 || source.getHeight() <= 0)
        {
            return new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
        }

        int w = source.getWidth();
        int h = source.getHeight();
        BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = out.createGraphics();
        applyIconQuality(g);
        if (excluded)
        {
            g.setComposite(java.awt.AlphaComposite.getInstance(java.awt.AlphaComposite.SRC_OVER, 0.45f));
            g.drawImage(source, 0, 0, null);
            g.setComposite(java.awt.AlphaComposite.SrcOver);
            g.setColor(ColorScheme.PROGRESS_ERROR_COLOR);
            g.setStroke(new BasicStroke(2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            int pad = Math.max(3, Math.min(w, h) / 6);
            g.drawLine(pad, pad, w - pad, h - pad);
            g.drawLine(w - pad, pad, pad, h - pad);
        }
        else
        {
            g.drawImage(source, 0, 0, null);
        }
        g.dispose();
        return out;
    }

    private static String escapeHtml(String s)
    {
        if (s == null)
        {
            return "";
        }
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    private static ImageIcon rasterCaretUp(Color fg)
    {
        final int s = 14;
        BufferedImage bi = new BufferedImage(s, s, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = bi.createGraphics();
        applyIconQuality(g);
        g.setColor(fg);
        float mid = s / 2f;
        float top = s * 0.3f;
        float bot = s * 0.72f;
        Path2D.Float p = new Path2D.Float();
        p.moveTo(mid, top);
        p.lineTo(mid - s * 0.3f, bot);
        p.lineTo(mid + s * 0.3f, bot);
        p.closePath();
        g.fill(p);
        g.dispose();
        return new ImageIcon(bi);
    }

    private static ImageIcon rasterCaretDown(Color fg)
    {
        final int s = 14;
        BufferedImage bi = new BufferedImage(s, s, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = bi.createGraphics();
        applyIconQuality(g);
        g.setColor(fg);
        float mid = s / 2f;
        float top = s * 0.28f;
        float bot = s * 0.7f;
        Path2D.Float p = new Path2D.Float();
        p.moveTo(mid - s * 0.3f, top);
        p.lineTo(mid + s * 0.3f, top);
        p.lineTo(mid, bot);
        p.closePath();
        g.fill(p);
        g.dispose();
        return new ImageIcon(bi);
    }

    private static ImageIcon rasterMinus(Color fg)
    {
        final int s = 14;
        BufferedImage bi = new BufferedImage(s, s, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = bi.createGraphics();
        applyIconQuality(g);
        g.setColor(fg);
        float w = s * 0.55f;
        float x = (s - w) / 2f;
        float y = (s - 2f) / 2f;
        g.fillRoundRect(Math.round(x), Math.round(y), Math.round(w), 2, 2, 2);
        g.dispose();
        return new ImageIcon(bi);
    }

    private static ImageIcon rasterClose(Color fg)
    {
        final int s = 14;
        BufferedImage bi = new BufferedImage(s, s, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = bi.createGraphics();
        applyIconQuality(g);
        g.setColor(fg);
        g.setStroke(new BasicStroke(2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        int pad = 3;
        g.drawLine(pad, pad, s - pad, s - pad);
        g.drawLine(s - pad, pad, pad, s - pad);
        g.dispose();
        return new ImageIcon(bi);
    }

    private static ImageIcon rasterXOutline(Color fg)
    {
        final int s = 14;
        BufferedImage bi = new BufferedImage(s, s, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = bi.createGraphics();
        applyIconQuality(g);
        g.setColor(fg);
        g.setStroke(new BasicStroke(2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        int pad = 3;
        g.drawLine(pad, pad, s - pad, s - pad);
        g.drawLine(s - pad, pad, pad, s - pad);
        g.dispose();
        return new ImageIcon(bi);
    }

    private static ImageIcon rasterXFilled(Color fg)
    {
        final int s = 14;
        BufferedImage bi = new BufferedImage(s, s, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = bi.createGraphics();
        applyIconQuality(g);
        g.setColor(fg);
        g.setStroke(new BasicStroke(2.5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        int pad = 3;
        g.drawLine(pad, pad, s - pad, s - pad);
        g.drawLine(s - pad, pad, pad, s - pad);
        g.dispose();
        return new ImageIcon(bi);
    }

    private static ImageIcon rasterEye(Color fg)
    {
        final int s = 14;
        BufferedImage bi = new BufferedImage(s, s, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = bi.createGraphics();
        applyIconQuality(g);
        g.setColor(fg);
        g.setStroke(new BasicStroke(1.4f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        Path2D.Float lid = new Path2D.Float();
        lid.moveTo(1.5f, s / 2f);
        lid.curveTo(s * 0.3f, 2f, s * 0.7f, 2f, s - 1.5f, s / 2f);
        lid.curveTo(s * 0.7f, s - 2f, s * 0.3f, s - 2f, 1.5f, s / 2f);
        lid.closePath();
        g.draw(lid);
        float r = 2.2f;
        g.fill(new java.awt.geom.Ellipse2D.Float(s / 2f - r, s / 2f - r, r * 2, r * 2));
        g.dispose();
        return new ImageIcon(bi);
    }

    private static ImageIcon rasterEyeOff(Color fg)
    {
        final int s = 14;
        BufferedImage bi = new BufferedImage(s, s, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = bi.createGraphics();
        applyIconQuality(g);
        g.setColor(fg);
        g.setStroke(new BasicStroke(1.4f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        Path2D.Float lid = new Path2D.Float();
        lid.moveTo(1.5f, s / 2f);
        lid.curveTo(s * 0.3f, 2f, s * 0.7f, 2f, s - 1.5f, s / 2f);
        lid.curveTo(s * 0.7f, s - 2f, s * 0.3f, s - 2f, 1.5f, s / 2f);
        lid.closePath();
        g.draw(lid);
        float r = 2.2f;
        g.fill(new java.awt.geom.Ellipse2D.Float(s / 2f - r, s / 2f - r, r * 2, r * 2));
        g.setStroke(new BasicStroke(1.6f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.drawLine(2, 2, s - 2, s - 2);
        g.dispose();
        return new ImageIcon(bi);
    }

    private static void applyIconQuality(Graphics2D g)
    {
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
    }
}
