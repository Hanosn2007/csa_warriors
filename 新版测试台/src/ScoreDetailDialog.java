import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Window;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTable;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableColumn;

public class ScoreDetailDialog extends JDialog {
    private static final String[] CATEGORY_NAMES = {
            "伤害分",
            "击败分",
            "道具分",
            "治疗分",
            "生存分",
            "存活分",
            "胜利分",
            "扣分"
    };

    private final BatchSimulationSummary summary;
    private final JComboBox<String> teamBox;
    private final CategoryChart categoryChart;
    private final JButton replayButton;
    private final JTable categoryTable;
    private final JTable matchTable;
    private final DefaultTableModel categoryModel;
    private final DefaultTableModel matchModel;
    private final JLabel overviewLabel;

    public ScoreDetailDialog(Window owner, BatchSimulationSummary summary, String title) {
        super(owner, title);
        this.summary = summary;
        setModal(false);
        setSize(980, 680);
        setLocationRelativeTo(owner);

        teamBox = new JComboBox<String>();
        categoryChart = new CategoryChart();
        replayButton = new JButton("回放选中局");
        replayButton.setEnabled(summary.getReplayMatches().size() > 0);
        overviewLabel = new JLabel(" ");
        categoryModel = new DefaultTableModel(new Object[] {"类别", "总分", "每局平均"}, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };
        matchModel = new DefaultTableModel(
                new Object[] {
                        "局",
                        "结果",
                        "回合",
                        "总分",
                        "伤害分",
                        "伤害HP",
                        "击败分",
                        "击败",
                        "道具分",
                        "道具",
                        "强力",
                        "治疗分",
                        "回血HP",
                        "生存分",
                        "存活分",
                        "胜利分",
                        "扣分",
                        "无效",
                        "无进展",
                        "交战敌人"
                },
                0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };
        categoryTable = new JTable(categoryModel);
        matchTable = new JTable(matchModel);

        buildLayout();
        bindActions();
        fillTeams();
        refreshSelectedTeam();
    }

    private void buildLayout() {
        JPanel root = new JPanel(new BorderLayout(10, 10));
        root.setBackground(new Color(246, 248, 250));
        root.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));
        setContentPane(root);

        JPanel topPanel = new JPanel(new BorderLayout(12, 0));
        topPanel.setBackground(new Color(246, 248, 250));
        JLabel label = new JLabel("选择队伍");
        label.setFont(new Font("SansSerif", Font.BOLD, 13));
        topPanel.add(label, BorderLayout.WEST);
        topPanel.add(teamBox, BorderLayout.CENTER);
        topPanel.add(replayButton, BorderLayout.EAST);
        root.add(topPanel, BorderLayout.NORTH);

        categoryChart.setPreferredSize(new Dimension(420, 260));
        categoryTable.setAutoCreateRowSorter(true);
        categoryTable.setFont(new Font("SansSerif", Font.PLAIN, 12));
        categoryTable.getTableHeader().setFont(new Font("SansSerif", Font.BOLD, 12));
        setColumnWidth(categoryTable, 0, 90);
        setColumnWidth(categoryTable, 1, 80);
        setColumnWidth(categoryTable, 2, 90);

        JPanel categoryPanel = new JPanel(new BorderLayout(8, 8));
        categoryPanel.setBackground(new Color(246, 248, 250));
        categoryPanel.add(overviewLabel, BorderLayout.NORTH);
        categoryPanel.add(categoryChart, BorderLayout.CENTER);
        categoryPanel.add(new JScrollPane(categoryTable), BorderLayout.EAST);

        matchTable.setAutoCreateRowSorter(true);
        matchTable.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
        matchTable.setFont(new Font("SansSerif", Font.PLAIN, 12));
        matchTable.getTableHeader().setFont(new Font("SansSerif", Font.BOLD, 12));
        setMatchColumnWidths();

        JSplitPane splitPane = new JSplitPane(
                JSplitPane.VERTICAL_SPLIT,
                categoryPanel,
                new JScrollPane(matchTable));
        splitPane.setResizeWeight(0.42);
        splitPane.setBorder(BorderFactory.createEmptyBorder());
        root.add(splitPane, BorderLayout.CENTER);
    }

    private void bindActions() {
        teamBox.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent event) {
                refreshSelectedTeam();
            }
        });

        replayButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent event) {
                openSelectedReplay();
            }
        });
    }

    private void fillTeams() {
        ArrayList<BatchTeamStats> stats = summary.getTeamStats();
        for (int i = 0; i < stats.size(); i++) {
            teamBox.addItem(stats.get(i).getTeamName());
        }
    }

    private void refreshSelectedTeam() {
        if (teamBox.getSelectedItem() == null) {
            return;
        }
        String teamName = teamBox.getSelectedItem().toString();
        ArrayList<BatchMatchScoreDetail> details = detailsForTeam(teamName);
        CategoryTotals totals = sumCategories(details);
        categoryChart.setValues(teamName, totals.values);
        fillCategoryTable(totals);
        fillMatchTable(details);
        overviewLabel.setText("  "
                + teamName
                + "："
                + details.size()
                + " 局，合计 "
                + totals.total
                + " 分，平均 "
                + formatAverage(totals.total, details.size())
                + " 分/局。下方表格已按单局总分从高到低排序。");
    }

    private ArrayList<BatchMatchScoreDetail> detailsForTeam(String teamName) {
        ArrayList<BatchMatchScoreDetail> all = summary.getMatchDetails();
        ArrayList<BatchMatchScoreDetail> filtered = new ArrayList<BatchMatchScoreDetail>();
        for (int i = 0; i < all.size(); i++) {
            BatchMatchScoreDetail detail = all.get(i);
            if (teamName.equals(detail.getTeamName())) {
                filtered.add(detail);
            }
        }
        Collections.sort(filtered, new Comparator<BatchMatchScoreDetail>() {
            @Override
            public int compare(BatchMatchScoreDetail first, BatchMatchScoreDetail second) {
                if (first.getTotalScore() != second.getTotalScore()) {
                    return second.getTotalScore() - first.getTotalScore();
                }
                return first.getMatchNumber() - second.getMatchNumber();
            }
        });
        return filtered;
    }

    private CategoryTotals sumCategories(ArrayList<BatchMatchScoreDetail> details) {
        int[] values = new int[CATEGORY_NAMES.length];
        for (int i = 0; i < details.size(); i++) {
            BatchMatchScoreDetail detail = details.get(i);
            values[0] = values[0] + detail.getDamageScore();
            values[1] = values[1] + detail.getDefeatScore();
            values[2] = values[2] + detail.getPowerUpScore();
            values[3] = values[3] + detail.getHealingScore();
            values[4] = values[4] + detail.getSurvivalScore();
            values[5] = values[5] + detail.getFinalAliveScore();
            values[6] = values[6] + detail.getWinnerScore();
            values[7] = values[7] + detail.getPenaltyScore();
        }
        return new CategoryTotals(values);
    }

    private void fillCategoryTable(CategoryTotals totals) {
        categoryModel.setRowCount(0);
        int matches = selectedTeamMatchCount();
        for (int i = 0; i < CATEGORY_NAMES.length; i++) {
            categoryModel.addRow(new Object[] {
                    CATEGORY_NAMES[i],
                    totals.values[i],
                    formatAverage(totals.values[i], matches)
            });
        }
    }

    private int selectedTeamMatchCount() {
        if (teamBox.getSelectedItem() == null) {
            return 0;
        }
        return detailsForTeam(teamBox.getSelectedItem().toString()).size();
    }

    private void fillMatchTable(ArrayList<BatchMatchScoreDetail> details) {
        matchModel.setRowCount(0);
        for (int i = 0; i < details.size(); i++) {
            BatchMatchScoreDetail detail = details.get(i);
            matchModel.addRow(new Object[] {
                    detail.getMatchNumber(),
                    detail.getResultText(),
                    detail.getRoundCount(),
                    detail.getTotalScore(),
                    detail.getDamageScore(),
                    detail.getDamageDealt(),
                    detail.getDefeatScore(),
                    detail.getDefeats(),
                    detail.getPowerUpScore(),
                    detail.getPowerUps(),
                    detail.getStrongPowerUps(),
                    detail.getHealingScore(),
                    detail.getHealingDone(),
                    detail.getSurvivalScore(),
                    detail.getFinalAliveScore(),
                    detail.getWinnerScore(),
                    detail.getPenaltyScore(),
                    detail.getInvalidActions(),
                    detail.getNoProgressPenalties(),
                    detail.getEngagedEnemyCount()
            });
        }
        if (matchModel.getRowCount() > 0) {
            matchTable.setRowSelectionInterval(0, 0);
        }
    }

    private void openSelectedReplay() {
        if (summary.getReplayMatches().size() == 0 || matchModel.getRowCount() == 0) {
            return;
        }

        int viewRow = matchTable.getSelectedRow();
        if (viewRow < 0) {
            viewRow = 0;
        }
        int modelRow = matchTable.convertRowIndexToModel(viewRow);
        int matchNumber = ((Integer) matchModel.getValueAt(modelRow, 0)).intValue();
        ReplayViewerDialog dialog = new ReplayViewerDialog(
                this,
                summary.getReplayMatches(),
                matchNumber);
        dialog.setVisible(true);
    }

    private String formatAverage(int total, int count) {
        if (count <= 0) {
            return "0.0";
        }
        return String.format("%.1f", total * 1.0 / count);
    }

    private void setMatchColumnWidths() {
        int[] widths = {
                46, 58, 58, 70, 70, 70, 70, 58, 70, 58,
                58, 70, 70, 70, 70, 70, 70, 58, 70, 80
        };
        for (int i = 0; i < widths.length; i++) {
            setColumnWidth(matchTable, i, widths[i]);
        }
    }

    private void setColumnWidth(JTable table, int columnIndex, int width) {
        TableColumn column = table.getColumnModel().getColumn(columnIndex);
        column.setPreferredWidth(width);
    }

    private static class CategoryTotals {
        private final int[] values;
        private final int total;

        public CategoryTotals(int[] values) {
            this.values = values;
            int sum = 0;
            for (int i = 0; i < values.length; i++) {
                sum = sum + values[i];
            }
            this.total = sum;
        }
    }

    private static class CategoryChart extends JPanel {
        private String teamName;
        private int[] values;

        public CategoryChart() {
            setBackground(new Color(253, 253, 251));
            setBorder(BorderFactory.createLineBorder(new Color(207, 217, 226)));
            teamName = "";
            values = new int[CATEGORY_NAMES.length];
        }

        public void setValues(String teamName, int[] values) {
            this.teamName = teamName;
            this.values = values;
            repaint();
        }

        @Override
        protected void paintComponent(Graphics graphics) {
            super.paintComponent(graphics);
            Graphics2D g = (Graphics2D) graphics;
            g.setFont(new Font("SansSerif", Font.PLAIN, 12));

            int width = getWidth();
            int left = 84;
            int top = 32;
            int rowHeight = 26;
            int barWidth = Math.max(80, width - left - 86);
            int maxAbs = 1;

            for (int i = 0; i < values.length; i++) {
                maxAbs = Math.max(maxAbs, Math.abs(values[i]));
            }

            g.setColor(new Color(35, 42, 52));
            g.setFont(new Font("SansSerif", Font.BOLD, 13));
            g.drawString(teamName + " 得分类别", 14, 20);
            g.setFont(new Font("SansSerif", Font.PLAIN, 12));

            for (int i = 0; i < CATEGORY_NAMES.length; i++) {
                int y = top + i * rowHeight;
                int value = values[i];
                int length = (int) (Math.abs(value) * 1.0 / maxAbs * barWidth);

                g.setColor(new Color(35, 42, 52));
                g.drawString(CATEGORY_NAMES[i], 14, y + 15);

                g.setColor(new Color(231, 236, 242));
                g.fillRoundRect(left, y, barWidth, 16, 6, 6);

                if (value < 0) {
                    g.setColor(new Color(205, 65, 57));
                } else {
                    g.setColor(colorForCategory(i));
                }
                g.fillRoundRect(left, y, length, 16, 6, 6);

                g.setColor(new Color(35, 42, 52));
                g.drawString(String.valueOf(value), left + barWidth + 10, y + 14);
            }
        }

        private Color colorForCategory(int index) {
            if (index == 0) {
                return new Color(205, 65, 57);
            }
            if (index == 1) {
                return new Color(142, 74, 181);
            }
            if (index == 2) {
                return new Color(56, 112, 208);
            }
            if (index == 3) {
                return new Color(51, 152, 94);
            }
            if (index == 4) {
                return new Color(217, 153, 25);
            }
            if (index == 5) {
                return new Color(73, 132, 112);
            }
            if (index == 6) {
                return new Color(34, 94, 168);
            }
            return new Color(205, 65, 57);
        }
    }
}
