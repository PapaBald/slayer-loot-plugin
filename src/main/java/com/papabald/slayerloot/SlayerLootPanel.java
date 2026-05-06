package com.papabald.slayerloot;

import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Graphics2D;
import java.awt.GridLayout;
import java.awt.Insets;
import java.awt.RenderingHints;
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
import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JToggleButton;
import javax.swing.SwingUtilities;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.PluginPanel;
import net.runelite.client.util.AsyncBufferedImage;
import net.runelite.client.util.QuantityFormatter;

class SlayerLootPanel extends PluginPanel
{
    private static final int ICON_BTN_SIZE = 28;
    /** Cached raster icons — Runescape fonts do not draw carets reliably. */
    private static final ImageIcon ICON_CARET_UP = rasterCaretUp(ColorScheme.LIGHT_GRAY_COLOR);
    private static final ImageIcon ICON_CARET_DOWN = rasterCaretDown(ColorScheme.LIGHT_GRAY_COLOR);
    private static final ImageIcon ICON_HIDE_MINUS = rasterMinus(ColorScheme.LIGHT_GRAY_COLOR);
    private static final ImageIcon ICON_CLOSE = rasterClose(ColorScheme.PROGRESS_ERROR_COLOR);
    private static final ImageIcon ICON_EXCLUDE_OFF = rasterXOutline(ColorScheme.MEDIUM_GRAY_COLOR);
    private static final ImageIcon ICON_EXCLUDE_ON = rasterXFilled(ColorScheme.PROGRESS_ERROR_COLOR);

    private final SlayerLootPlugin plugin;
    private final JPanel content = new JPanel();
    private final JCheckBox showHiddenTasks = new JCheckBox("Show hidden tasks");
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
        JScrollPane scrollPane = new JScrollPane(content);
        scrollPane.setBorder(BorderFactory.createEmptyBorder());
        scrollPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
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
        b.setMargin(new Insets(2, 2, 2, 2));
        b.setFocusPainted(false);
        b.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        b.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(ColorScheme.MEDIUM_GRAY_COLOR, 1, true),
            BorderFactory.createEmptyBorder(4, 4, 4, 4)
        ));
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

        JPanel resets = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 2));
        resets.setOpaque(false);
        resets.setAlignmentX(Component.LEFT_ALIGNMENT);

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
                rebuild();
            }
        );

        JPanel collapseRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 2));
        collapseRow.setOpaque(false);
        collapseRow.setAlignmentX(Component.LEFT_ALIGNMENT);
        collapseRow.add(masterCollapseToggleBtn);
        JLabel collapseHint = new JLabel("Collapse / expand task sections");
        collapseHint.setForeground(ColorScheme.MEDIUM_GRAY_COLOR);
        collapseRow.add(collapseHint);

        JPanel toggles = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        toggles.setOpaque(false);
        toggles.add(showHiddenTasks);
        showHiddenTasks.setOpaque(false);
        showHiddenTasks.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
        showHiddenTasks.addActionListener(e -> rebuild());
        toggles.setAlignmentX(Component.LEFT_ALIGNMENT);

        controls.add(resets);
        controls.add(Box.createRigidArea(new Dimension(0, 2)));
        controls.add(collapseRow);
        controls.add(Box.createRigidArea(new Dimension(0, 2)));
        controls.add(toggles);
        return controls;
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

    void rebuild()
    {
        SwingUtilities.invokeLater(() ->
        {
            Set<String> validKeys = new HashSet<>();
            for (TaskLootRecord r : plugin.getTaskRecords())
            {
                validKeys.add(r.getTaskKey());
            }
            collapsedTasks.entrySet().removeIf(e -> !validKeys.contains(e.getKey()));

            content.removeAll();
            addActiveTaskSummary();
            List<TaskLootRecord> records = new ArrayList<>(plugin.getTaskRecords());
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
        });
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

        String taskName = plugin.hasActiveSlayerTask() ? plugin.getCurrentTaskName() : "No active task";
        JLabel name = new JLabel("Current task: " + taskName);
        name.setFont(FontManager.getRunescapeBoldFont());
        name.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
        activeTask.add(name);

        if (plugin.hasActiveSlayerTask())
        {
            int completed = plugin.getCurrentTaskCompletedCount();
            int remaining = plugin.getCurrentTaskRemaining();
            int original = plugin.getCurrentTaskOriginalAmount();
            JLabel counts = new JLabel("Progress: " + NUMBER_FORMAT.format(completed) + "/" + NUMBER_FORMAT.format(original)
                + " (" + NUMBER_FORMAT.format(remaining) + " left)");
            counts.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
            activeTask.add(counts);
        }

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

        JPanel titleRow = new JPanel(new BorderLayout(0, 0));
        titleRow.setOpaque(false);
        titleRow.setAlignmentX(Component.LEFT_ALIGNMENT);
        JLabel title = new JLabel(record.getTaskName());
        title.setFont(FontManager.getRunescapeBoldFont());
        title.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
        titleRow.add(title, BorderLayout.WEST);

        JPanel iconActions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 4, 0));
        iconActions.setOpaque(false);
        JButton collapseBtn = iconOnlyButton(collapsed ? ICON_CARET_DOWN : ICON_CARET_UP, collapsed ? "Expand drops" : "Collapse drops");
        collapseBtn.addActionListener(e ->
        {
            collapsedTasks.put(record.getTaskKey(), !collapsedTasks.getOrDefault(record.getTaskKey(), false));
            rebuild();
        });
        iconActions.add(collapseBtn);

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
        iconActions.add(hideBtn);

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
        iconActions.add(deleteBtn);

        JPanel toolbarRow = new JPanel(new BorderLayout(0, 0));
        toolbarRow.setOpaque(false);
        toolbarRow.setAlignmentX(Component.LEFT_ALIGNMENT);
        toolbarRow.add(iconActions, BorderLayout.EAST);

        JPanel taskCard = new JPanel();
        taskCard.setLayout(new BoxLayout(taskCard, BoxLayout.Y_AXIS));
        taskCard.setBackground(ColorScheme.DARK_GRAY_COLOR);
        taskCard.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(ColorScheme.MEDIUM_GRAY_COLOR),
            BorderFactory.createEmptyBorder(8, 8, 8, 8)
        ));
        taskCard.setAlignmentX(Component.LEFT_ALIGNMENT);

        taskCard.add(titleRow);
        taskCard.add(Box.createRigidArea(new Dimension(0, 2)));
        taskCard.add(toolbarRow);
        taskCard.add(Box.createRigidArea(new Dimension(0, 4)));

        JLabel summary = new JLabel(
            "Kills: " + NUMBER_FORMAT.format(record.getKills())
        );
        summary.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
        summary.setAlignmentX(Component.LEFT_ALIGNMENT);
        taskCard.add(summary);

        JLabel profit = new JLabel(
            "Profit: Gross " + QuantityFormatter.quantityToStackSize(record.getGrossProfit()) + " gp"
                + " | Actual: " + QuantityFormatter.quantityToStackSize(record.getActualProfit()) + " gp"
        );
        profit.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
        profit.setAlignmentX(Component.LEFT_ALIGNMENT);
        taskCard.add(profit);
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

            JLabel dropsHeader = new JLabel("Drops (toggle \u2715 to exclude from Actual):");
        dropsHeader.setForeground(ColorScheme.MEDIUM_GRAY_COLOR);
        dropsHeader.setAlignmentX(Component.LEFT_ALIGNMENT);
        taskCard.add(dropsHeader);
        taskCard.add(Box.createRigidArea(new Dimension(0, 4)));

        int availableWidth = Math.max(getWidth(), content.getWidth());
        int columns = availableWidth >= 320 ? 3 : (availableWidth >= 220 ? 2 : 1);
        JPanel itemsPanel = new JPanel(new GridLayout(0, columns, 6, 6));
        itemsPanel.setOpaque(false);
        itemsPanel.setAlignmentX(Component.LEFT_ALIGNMENT);
        for (TaskLootItem item : items)
        {
            JPanel itemTile = new JPanel(new BorderLayout(4, 0));
            itemTile.setOpaque(false);
            itemTile.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(ColorScheme.DARKER_GRAY_HOVER_COLOR),
                BorderFactory.createEmptyBorder(3, 3, 3, 3)
            ));

            JLabel iconLabel = new JLabel();
            iconLabel.setPreferredSize(new Dimension(18, 18));
            AsyncBufferedImage itemImage = plugin.getItemIcon(item.getItemId(), item.getQuantity());
            iconLabel.setIcon(new ImageIcon(itemImage));
            itemImage.onLoaded(() -> SwingUtilities.invokeLater(() ->
                iconLabel.setIcon(new ImageIcon(itemImage)))
            );
            JPanel west = new JPanel(new FlowLayout(FlowLayout.LEFT, 2, 0));
            west.setOpaque(false);
            west.add(iconLabel);
            JLabel qtyLabel = new JLabel(NUMBER_FORMAT.format(item.getQuantity()));
            qtyLabel.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
            qtyLabel.setToolTipText(item.getItemName());
            west.add(qtyLabel);
            itemTile.add(west, BorderLayout.WEST);

            String valueText = QuantityFormatter.quantityToStackSize(item.getTotalGeValue()) + " gp";
            JLabel valueLabel = new JLabel(valueText);
            valueLabel.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
            valueLabel.setToolTipText(item.getItemName() + " \u2014 Gross for this stack");

            JToggleButton excludeBtn = new JToggleButton(ICON_EXCLUDE_OFF);
            excludeBtn.setText(null);
            excludeBtn.setToolTipText("Exclude from Actual profit (toggle)");
            excludeBtn.setSelectedIcon(ICON_EXCLUDE_ON);
            excludeBtn.setOpaque(false);
            excludeBtn.setContentAreaFilled(false);
            excludeBtn.setBorderPainted(false);
            excludeBtn.setFocusPainted(false);
            excludeBtn.setSelected(item.isExcluded());
            excludeBtn.addActionListener(e -> plugin.setItemIncluded(
                record.getTaskKey(),
                item.getItemId(),
                !excludeBtn.isSelected()
            ));

            JPanel east = new JPanel(new FlowLayout(FlowLayout.RIGHT, 4, 0));
            east.setOpaque(false);
            east.add(valueLabel);
            east.add(excludeBtn);
            itemTile.add(east, BorderLayout.EAST);

            itemsPanel.add(itemTile);
        }
        taskCard.add(itemsPanel);

        clampCardHeight(taskCard);
        content.add(taskCard);
        content.add(Box.createRigidArea(new Dimension(0, 8)));
        return true;
    }

    private static void clampCardHeight(JPanel taskCard)
    {
        taskCard.setMaximumSize(new Dimension(Integer.MAX_VALUE, taskCard.getPreferredSize().height));
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

    private static void applyIconQuality(Graphics2D g)
    {
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
    }
}
