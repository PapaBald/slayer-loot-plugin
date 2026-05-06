package com.papabald.slayerloot;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.SwingUtilities;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.PluginPanel;
import net.runelite.client.util.QuantityFormatter;

class SlayerLootPanel extends PluginPanel
{
    private final SlayerLootPlugin plugin;
    private final JPanel content = new JPanel();
    private final JCheckBox showHiddenTasks = new JCheckBox("Show hidden tasks");
    private static final NumberFormat NUMBER_FORMAT = NumberFormat.getIntegerInstance();

    SlayerLootPanel(SlayerLootPlugin plugin)
    {
        super(false);
        this.plugin = plugin;
        setLayout(new BorderLayout());
        setBackground(ColorScheme.DARK_GRAY_COLOR);

        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
        content.setBackground(ColorScheme.DARKER_GRAY_COLOR);

        JPanel controls = buildControlsPanel();
        JScrollPane scrollPane = new JScrollPane(content);
        scrollPane.setBorder(BorderFactory.createEmptyBorder());
        add(controls, BorderLayout.NORTH);
        add(scrollPane, BorderLayout.CENTER);
    }

    private JPanel buildControlsPanel()
    {
        JPanel controls = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 4));
        controls.setBackground(ColorScheme.DARK_GRAY_COLOR);

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

        showHiddenTasks.setOpaque(false);
        showHiddenTasks.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
        showHiddenTasks.addActionListener(e -> rebuild());

        controls.add(resetCurrent);
        controls.add(resetAll);
        controls.add(showHiddenTasks);
        return controls;
    }

    void rebuild()
    {
        SwingUtilities.invokeLater(() ->
        {
            content.removeAll();
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

            content.revalidate();
            content.repaint();
        });
    }

    private boolean addTaskCard(TaskLootRecord record)
    {
        if (record.isHidden() && !showHiddenTasks.isSelected())
        {
            return false;
        }

        JPanel taskCard = new JPanel();
        taskCard.setLayout(new BoxLayout(taskCard, BoxLayout.Y_AXIS));
        taskCard.setBackground(ColorScheme.DARK_GRAY_COLOR);
        taskCard.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(ColorScheme.MEDIUM_GRAY_COLOR),
            BorderFactory.createEmptyBorder(8, 8, 8, 8)
        ));
        taskCard.setAlignmentX(Component.LEFT_ALIGNMENT);
        taskCard.setMaximumSize(new Dimension(Integer.MAX_VALUE, Integer.MAX_VALUE));

        JLabel title = new JLabel(record.getTaskName());
        title.setFont(FontManager.getRunescapeBoldFont());
        title.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
        taskCard.add(title);

        JLabel summary = new JLabel(
            "Kills: " + NUMBER_FORMAT.format(record.getKills())
        );
        summary.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
        taskCard.add(summary);

        JLabel profit = new JLabel(
            "Per-task Profit -> Gross: " + QuantityFormatter.quantityToStackSize(record.getGrossProfit()) + " gp"
                + " | Actual: " + QuantityFormatter.quantityToStackSize(record.getActualProfit()) + " gp"
        );
        profit.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
        taskCard.add(profit);
        taskCard.add(Box.createRigidArea(new Dimension(0, 6)));

        JButton hideButton = new JButton(record.isHidden() ? "Unhide Task" : "Hide Task");
        hideButton.setAlignmentX(Component.LEFT_ALIGNMENT);
        hideButton.addActionListener(e -> plugin.setTaskHidden(record.getTaskKey(), !record.isHidden()));
        taskCard.add(hideButton);
        taskCard.add(Box.createRigidArea(new Dimension(0, 6)));

        List<TaskLootItem> items = new ArrayList<>(record.getItems());
        items.sort(Comparator.comparingLong(TaskLootItem::getTotalGeValue).reversed());
        for (TaskLootItem item : items)
        {
            JCheckBox checkBox = new JCheckBox(
                item.getItemName()
                    + " x" + NUMBER_FORMAT.format(item.getQuantity())
                    + " (" + QuantityFormatter.quantityToStackSize(item.getTotalGeValue()) + " gp)"
            );
            checkBox.setSelected(!item.isExcluded());
            checkBox.setOpaque(false);
            checkBox.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
            checkBox.setAlignmentX(Component.LEFT_ALIGNMENT);
            checkBox.addActionListener(e -> plugin.setItemIncluded(
                record.getTaskKey(),
                item.getItemId(),
                checkBox.isSelected()
            ));
            taskCard.add(checkBox);
        }

        content.add(taskCard);
        content.add(Box.createRigidArea(new Dimension(0, 8)));
        return true;
    }
}
