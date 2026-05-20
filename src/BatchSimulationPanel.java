import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GridLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.table.DefaultTableModel;

public class BatchSimulationPanel extends JPanel {
    private static final int MAX_MATCHES = 500;
    private static final int SAFETY_ROUND_LIMIT = 2000;

    private final JTextField matchCountField;
    private final JButton runButton;
    private final JProgressBar progressBar;
    private final JLabel statusLabel;
    private final JTable summaryTable;
    private final DefaultTableModel tableModel;
    private final JTextArea detailArea;
    private final BatchResultChart chart;

    public BatchSimulationPanel() {
        super(new BorderLayout(8, 8));
        setBackground(new Color(246, 248, 250));
        setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));

        matchCountField = new JTextField("20", 6);
        runButton = new JButton("开始连测");
        progressBar = new JProgressBar();
        statusLabel = new JLabel("输入轮数后开始后台连测。");
        detailArea = createTextArea();
        chart = new BatchResultChart();
        chart.setPreferredSize(new Dimension(420, 330));

        tableModel = new DefaultTableModel(
                new Object[] {
                        "队伍",
                        "胜场",
                        "胜率",
                        "平均分",
                        "最高分",
                        "最低分",
                        "总分",
                        "总伤害",
                        "击败",
                        "道具",
                        "强力道具",
                        "回血量",
                        "交战敌人/局",
                        "扣分",
                        "无效",
                        "无进展"
                },
                0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };
        summaryTable = new JTable(tableModel);
        summaryTable.setAutoCreateRowSorter(true);
        summaryTable.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
        summaryTable.setFont(new Font("SansSerif", Font.PLAIN, 12));
        summaryTable.getTableHeader().setFont(new Font("SansSerif", Font.BOLD, 12));

        buildLayout();
        bindActions();
    }

    private void buildLayout() {
        JPanel topPanel = new JPanel(new BorderLayout(8, 0));
        topPanel.setBackground(new Color(246, 248, 250));

        JPanel inputPanel = new JPanel();
        inputPanel.setBackground(new Color(246, 248, 250));
        inputPanel.add(new JLabel("连测轮数 N"));
        inputPanel.add(matchCountField);
        inputPanel.add(runButton);
        topPanel.add(inputPanel, BorderLayout.WEST);
        topPanel.add(statusLabel, BorderLayout.CENTER);

        JPanel centerPanel = new JPanel(new GridLayout(2, 1, 0, 8));
        centerPanel.setBackground(new Color(246, 248, 250));
        centerPanel.add(new JScrollPane(summaryTable));
        centerPanel.add(new JScrollPane(chart));

        JPanel bottomPanel = new JPanel(new BorderLayout(0, 6));
        bottomPanel.setBackground(new Color(246, 248, 250));
        bottomPanel.add(new JScrollPane(detailArea), BorderLayout.CENTER);
        bottomPanel.add(progressBar, BorderLayout.SOUTH);

        add(topPanel, BorderLayout.NORTH);
        add(centerPanel, BorderLayout.CENTER);
        add(bottomPanel, BorderLayout.SOUTH);
    }

    private JTextArea createTextArea() {
        JTextArea area = new JTextArea(7, 20);
        area.setEditable(false);
        area.setLineWrap(true);
        area.setWrapStyleWord(true);
        area.setFont(new Font("Monospaced", Font.PLAIN, 12));
        area.setBackground(new Color(253, 253, 251));
        area.setForeground(new Color(35, 42, 52));
        area.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        area.setText("连测不会展示过程，会在后台直接运行当前默认配置中的队伍和策略。\n"
                + "表格可以点击列标题排序，用来观察伤害、道具、回血、扣分等不同维度。\n");
        return area;
    }

    private void bindActions() {
        runButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent event) {
                startBatchRun();
            }
        });
    }

    private void startBatchRun() {
        final int matches = parseMatchCount();
        if (matches <= 0) {
            return;
        }

        runButton.setEnabled(false);
        tableModel.setRowCount(0);
        detailArea.setText("");
        chart.setSummary(null);
        progressBar.setMinimum(0);
        progressBar.setMaximum(matches);
        progressBar.setValue(0);
        statusLabel.setText("正在后台连测 0 / " + matches + " 局...");

        Thread thread = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    final BatchSimulationSummary summary = runMatches(matches);
                    SwingUtilities.invokeLater(new Runnable() {
                        @Override
                        public void run() {
                            showSummary(summary);
                            runButton.setEnabled(true);
                        }
                    });
                } catch (final Throwable throwable) {
                    SwingUtilities.invokeLater(new Runnable() {
                        @Override
                        public void run() {
                            showRunFailure(throwable);
                        }
                    });
                }
            }
        });
        thread.start();
    }

    private void showRunFailure(Throwable throwable) {
        runButton.setEnabled(true);
        progressBar.setValue(0);
        statusLabel.setText("连测失败。");
        detailArea.setText("后台连测遇到错误，已停止。\n\n"
                + throwable.getClass().getSimpleName()
                + ": "
                + throwable.getMessage());
    }

    private int parseMatchCount() {
        try {
            int value = Integer.parseInt(matchCountField.getText().trim());
            if (value < 1) {
                JOptionPane.showMessageDialog(this, "连测轮数必须至少为 1。");
                return -1;
            }
            if (value > MAX_MATCHES) {
                JOptionPane.showMessageDialog(this, "单次最多连测 " + MAX_MATCHES + " 轮。");
                return -1;
            }
            return value;
        } catch (NumberFormatException exception) {
            JOptionPane.showMessageDialog(this, "请输入整数轮数，例如 20。");
            return -1;
        }
    }

    private BatchSimulationSummary runMatches(int matches) {
        ArrayList<BatchTeamStats> stats = createStats();
        String details = "";
        int noWinnerMatches = 0;
        int unfinishedMatches = 0;

        for (int i = 1; i <= matches; i++) {
            GameEngine engine = ArenaFactory.createDefaultEngine();
            engine.setPrintLogs(false);
            engine.setStoreLogs(false);

            while (!engine.isGameOver() && engine.getCurrentRound() <= SAFETY_ROUND_LIMIT) {
                engine.stepRound();
            }

            boolean unfinished = !engine.isGameOver();
            if (unfinished) {
                unfinishedMatches++;
            }

            String winner = engine.getWinnerName();
            if (winner == null && !unfinished) {
                noWinnerMatches++;
            }

            ArrayList<TeamScore> scores = engine.getTeamScores();
            details = details + formatMatchDetail(i, winner, unfinished, scores) + "\n";
            addScores(stats, scores);
            if (winner != null) {
                BatchTeamStats winningStats = findStats(stats, winner);
                if (winningStats != null) {
                    winningStats.addWin();
                }
            }

            final int completed = i;
            SwingUtilities.invokeLater(new Runnable() {
                @Override
                public void run() {
                    progressBar.setValue(completed);
                    statusLabel.setText("正在后台连测 " + completed + " / " + matches + " 局...");
                }
            });
        }

        return new BatchSimulationSummary(stats, details, matches, matches, noWinnerMatches, unfinishedMatches);
    }

    private ArrayList<BatchTeamStats> createStats() {
        GameEngine engine = ArenaFactory.createDefaultEngine();
        ArrayList<BatchTeamStats> stats = new ArrayList<BatchTeamStats>();
        ArrayList<Team> teams = engine.getTeams();
        for (int i = 0; i < teams.size(); i++) {
            stats.add(new BatchTeamStats(teams.get(i).getName()));
        }
        return stats;
    }

    private void addScores(ArrayList<BatchTeamStats> stats, ArrayList<TeamScore> scores) {
        for (int i = 0; i < scores.size(); i++) {
            TeamScore score = scores.get(i);
            BatchTeamStats stat = findStats(stats, score.getTeamName());
            if (stat != null) {
                stat.addTeamScore(score);
            }
        }
    }

    private BatchTeamStats findStats(ArrayList<BatchTeamStats> stats, String teamName) {
        for (int i = 0; i < stats.size(); i++) {
            if (stats.get(i).getTeamName().equals(teamName)) {
                return stats.get(i);
            }
        }
        return null;
    }

    private String formatMatchDetail(int matchNumber, String winner, boolean unfinished, ArrayList<TeamScore> scores) {
        String line = "第 " + matchNumber + " 局: ";
        if (unfinished) {
            line = line + "超过 " + SAFETY_ROUND_LIMIT + " 回合未结束";
        } else if (winner == null) {
            line = line + "无胜者";
        } else {
            line = line + winner + " 胜";
        }
        line = line + " | ";
        for (int i = 0; i < scores.size(); i++) {
            TeamScore score = scores.get(i);
            line = line
                    + score.getTeamName()
                    + "="
                    + score.getTotalScore()
                    + "(伤害 "
                    + score.getDamageDealt()
                    + ", 击败 "
                    + score.getDefeats()
                    + ", 道具 "
                    + score.getPowerUps()
                    + ", 回血 "
                    + score.getHealingDone()
                    + ")";
            if (i < scores.size() - 1) {
                line = line + ", ";
            }
        }
        return line;
    }

    private void showSummary(BatchSimulationSummary summary) {
        tableModel.setRowCount(0);
        ArrayList<BatchTeamStats> stats = summary.getTeamStats();
        for (int i = 0; i < stats.size(); i++) {
            BatchTeamStats stat = stats.get(i);
            double winRate = stat.getWins() * 100.0 / summary.getCompletedMatches();
            tableModel.addRow(new Object[] {
                    stat.getTeamName(),
                    stat.getWins(),
                    String.format("%.1f%%", winRate),
                    String.format("%.1f", stat.getAverageScore(summary.getCompletedMatches())),
                    stat.getBestScore(),
                    stat.getWorstScore(),
                    stat.getTotalScore(),
                    stat.getDamageDealt(),
                    stat.getDefeats(),
                    stat.getPowerUps(),
                    stat.getStrongPowerUps(),
                    stat.getHealingDone(),
                    String.format("%.1f", stat.getAverageEngagedEnemies(summary.getCompletedMatches())),
                    stat.getPenaltyScore(),
                    stat.getInvalidActions(),
                    stat.getNoProgressPenalties()
            });
        }

        String header = "完成 "
                + summary.getCompletedMatches()
                + " / "
                + summary.getRequestedMatches()
                + " 局；无胜者 "
                + summary.getNoWinnerMatches()
                + " 局；超过保护上限未结束 "
                + summary.getUnfinishedMatches()
                + " 局。\n点击表头可以按任意指标排序。伤害、道具、回血等列为本次连测累计值。\n\n";
        detailArea.setText(header + summary.getDetailText());
        detailArea.setCaretPosition(0);
        chart.setSummary(summary);
        progressBar.setValue(summary.getCompletedMatches());
        statusLabel.setText("连测完成。");
    }
}
