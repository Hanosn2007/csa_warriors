import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Desktop;
import java.awt.Font;
import java.awt.GridLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import java.util.ArrayList;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComboBox;
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

public class InternalMatchPanel extends JPanel {
    private static final int MAX_MATCHES = 500;
    private static final int SAFETY_ROUND_LIMIT = 2000;

    private final ArenaFrame owner;
    private final JComboBox<String> redBox;
    private final JComboBox<String> blueBox;
    private final JComboBox<String> greenBox;
    private final JComboBox<String> yellowBox;
    private final JTextField matchCountField;
    private final JButton loadButton;
    private final JButton runButton;
    private final JButton refreshButton;
    private final JButton openSrcButton;
    private final JProgressBar progressBar;
    private final JLabel statusLabel;
    private final JTable summaryTable;
    private final DefaultTableModel tableModel;
    private final JTextArea detailArea;
    private final BatchResultChart chart;

    public InternalMatchPanel(ArenaFrame owner) {
        super(new BorderLayout(8, 8));
        this.owner = owner;
        setBackground(new Color(246, 248, 250));
        setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));

        redBox = new JComboBox<String>();
        blueBox = new JComboBox<String>();
        greenBox = new JComboBox<String>();
        yellowBox = new JComboBox<String>();
        matchCountField = new JTextField("20", 6);
        loadButton = new JButton("载入到仿真");
        runButton = new JButton("后台连测");
        refreshButton = new JButton("刷新策略列表");
        openSrcButton = new JButton("打开策略文件夹");
        progressBar = new JProgressBar();
        statusLabel = new JLabel("选择四队策略后，可以载入仿真或后台连测。");
        detailArea = createTextArea();
        chart = new BatchResultChart();

        tableModel = new DefaultTableModel(
                new Object[] {
                        "队伍",
                        "策略类",
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
        refreshStrategies();
    }

    private void buildLayout() {
        JPanel topPanel = new JPanel(new GridLayout(4, 2, 6, 6));
        topPanel.setBackground(new Color(246, 248, 250));
        topPanel.add(new JLabel("红队策略"));
        topPanel.add(redBox);
        topPanel.add(new JLabel("蓝队策略"));
        topPanel.add(blueBox);
        topPanel.add(new JLabel("绿队策略"));
        topPanel.add(greenBox);
        topPanel.add(new JLabel("黄队策略"));
        topPanel.add(yellowBox);

        JPanel actionPanel = new JPanel();
        actionPanel.setBackground(new Color(246, 248, 250));
        actionPanel.add(openSrcButton);
        actionPanel.add(refreshButton);
        actionPanel.add(loadButton);
        actionPanel.add(new JLabel("连测轮数 N"));
        actionPanel.add(matchCountField);
        actionPanel.add(runButton);

        JPanel northPanel = new JPanel(new BorderLayout(0, 8));
        northPanel.setBackground(new Color(246, 248, 250));
        northPanel.add(topPanel, BorderLayout.CENTER);
        northPanel.add(actionPanel, BorderLayout.SOUTH);

        JPanel centerPanel = new JPanel(new GridLayout(2, 1, 0, 8));
        centerPanel.setBackground(new Color(246, 248, 250));
        centerPanel.add(new JScrollPane(summaryTable));
        centerPanel.add(new JScrollPane(chart));

        JPanel bottomPanel = new JPanel(new BorderLayout(0, 6));
        bottomPanel.setBackground(new Color(246, 248, 250));
        bottomPanel.add(statusLabel, BorderLayout.NORTH);
        bottomPanel.add(new JScrollPane(detailArea), BorderLayout.CENTER);
        bottomPanel.add(progressBar, BorderLayout.SOUTH);

        add(northPanel, BorderLayout.NORTH);
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
        area.setText("队内 PK 使用当前已编译的 Warrior 类。修改策略后，需要先在“项目文件”中编译并重启界面。\n"
                + "添加其他队伍策略：把对方的 public 策略类 .java 文件放进 src。名称由队伍自定，但必须唯一，且文件名、public 类名和构造方法名一致，类必须 extends Warrior。\n"
                + "表格可以点击列标题排序，用来比较进攻、资源、续航和失误。\n");
        return area;
    }

    private void bindActions() {
        refreshButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent event) {
                refreshStrategies();
            }
        });

        openSrcButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent event) {
                openStrategyFolder();
            }
        });

        loadButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent event) {
                loadSelectedMatch();
            }
        });

        runButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent event) {
                startBatchRun();
            }
        });
    }

    private void refreshStrategies() {
        ArrayList<String> names = MatchEngineFactory.findAvailableWarriorClasses();
        fillBox(redBox, names);
        fillBox(blueBox, names);
        fillBox(greenBox, names);
        fillBox(yellowBox, names);
        selectIfPresent(redBox, "TeamAlphaWarrior");
        selectIfPresent(blueBox, "AggressiveWarrior");
        selectIfPresent(greenBox, "CarefulWarrior");
        selectIfPresent(yellowBox, "SmartWarrior");
        if (names.size() == 0) {
            detailArea.setText("没有找到可用策略类。请先编译项目。");
        } else {
            detailArea.setText("已找到 "
                    + names.size()
                    + " 个可用策略类。选择四队策略后可以载入仿真或后台连测。\n\n"
                    + "添加其他队伍策略的步骤：\n"
                    + "1. 把对方策略 .java 文件复制到 src 文件夹。\n"
                    + "2. 名称由队伍自定，但同一个 src 中不能重复。\n"
                    + "3. 文件名、public 类名和构造方法名必须一致。\n"
                    + "   例如 AuroraBlade.java / public class AuroraBlade / public AuroraBlade()。\n"
                    + "4. 确认这个类 extends Warrior。\n"
                    + "5. 在“项目文件”中点击“编译并重启界面”。\n"
                    + "6. 回到“队内 PK”，点击“刷新策略列表”。\n");
        }
    }

    private void fillBox(JComboBox<String> box, ArrayList<String> names) {
        box.removeAllItems();
        for (int i = 0; i < names.size(); i++) {
            box.addItem(names.get(i));
        }
    }

    private void selectIfPresent(JComboBox<String> box, String value) {
        for (int i = 0; i < box.getItemCount(); i++) {
            if (value.equals(box.getItemAt(i))) {
                box.setSelectedIndex(i);
                return;
            }
        }
    }

    private MatchConfig selectedConfig() {
        if (redBox.getSelectedItem() == null
                || blueBox.getSelectedItem() == null
                || greenBox.getSelectedItem() == null
                || yellowBox.getSelectedItem() == null) {
            return null;
        }
        return new MatchConfig(
                redBox.getSelectedItem().toString(),
                blueBox.getSelectedItem().toString(),
                greenBox.getSelectedItem().toString(),
                yellowBox.getSelectedItem().toString());
    }

    private void loadSelectedMatch() {
        MatchConfig config = selectedConfig();
        if (config == null) {
            JOptionPane.showMessageDialog(this, "请先选择四个队伍的策略。");
            return;
        }

        try {
            GameEngine engine = MatchEngineFactory.createEngine(config);
            owner.loadEngine(engine);
            detailArea.setText("已载入队内 PK 配置到左侧仿真地图。\n"
                    + "Red=" + config.getRedWarriorClass() + "\n"
                    + "Blue=" + config.getBlueWarriorClass() + "\n"
                    + "Green=" + config.getGreenWarriorClass() + "\n"
                    + "Yellow=" + config.getYellowWarriorClass() + "\n");
        } catch (RuntimeException exception) {
            JOptionPane.showMessageDialog(this, exception.getMessage());
        }
    }

    private void startBatchRun() {
        final MatchConfig config = selectedConfig();
        if (config == null) {
            JOptionPane.showMessageDialog(this, "请先选择四个队伍的策略。");
            return;
        }

        final int matches = parseMatchCount();
        if (matches <= 0) {
            return;
        }

        setRunButtonsEnabled(false);
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
                    final BatchSimulationSummary summary = runMatches(config, matches);
                    SwingUtilities.invokeLater(new Runnable() {
                        @Override
                        public void run() {
                            showSummary(config, summary);
                            setRunButtonsEnabled(true);
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

    private void setRunButtonsEnabled(boolean enabled) {
        runButton.setEnabled(enabled);
        loadButton.setEnabled(enabled);
        refreshButton.setEnabled(enabled);
        openSrcButton.setEnabled(enabled);
    }

    private void showRunFailure(Throwable throwable) {
        setRunButtonsEnabled(true);
        progressBar.setValue(0);
        statusLabel.setText("队内 PK 连测失败。");
        detailArea.setText("后台连测遇到错误，已停止。\n\n"
                + throwable.getClass().getSimpleName()
                + ": "
                + throwable.getMessage());
    }

    private void openStrategyFolder() {
        try {
            File srcDir = new File(System.getProperty("user.dir"), "src");
            Desktop.getDesktop().open(srcDir);
        } catch (Exception exception) {
            JOptionPane.showMessageDialog(this, "无法打开 src 文件夹，请手动打开项目中的 src 目录。");
        }
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

    private BatchSimulationSummary runMatches(MatchConfig config, int matches) {
        ArrayList<BatchTeamStats> stats = createStats(config);
        String details = "";
        int noWinnerMatches = 0;
        int unfinishedMatches = 0;

        for (int i = 1; i <= matches; i++) {
            GameEngine engine = MatchEngineFactory.createEngine(config);
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

    private ArrayList<BatchTeamStats> createStats(MatchConfig config) {
        GameEngine engine = MatchEngineFactory.createEngine(config);
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

    private void showSummary(MatchConfig config, BatchSimulationSummary summary) {
        tableModel.setRowCount(0);
        ArrayList<BatchTeamStats> stats = summary.getTeamStats();
        for (int i = 0; i < stats.size(); i++) {
            BatchTeamStats stat = stats.get(i);
            double winRate = stat.getWins() * 100.0 / summary.getCompletedMatches();
            tableModel.addRow(new Object[] {
                    stat.getTeamName(),
                    strategyForTeam(config, stat.getTeamName()),
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

        String header = "配置: Red="
                + config.getRedWarriorClass()
                + ", Blue="
                + config.getBlueWarriorClass()
                + ", Green="
                + config.getGreenWarriorClass()
                + ", Yellow="
                + config.getYellowWarriorClass()
                + "\n完成 "
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
        statusLabel.setText("队内 PK 连测完成。");
    }

    private String strategyForTeam(MatchConfig config, String teamName) {
        if ("Red".equals(teamName)) {
            return config.getRedWarriorClass();
        }
        if ("Blue".equals(teamName)) {
            return config.getBlueWarriorClass();
        }
        if ("Green".equals(teamName)) {
            return config.getGreenWarriorClass();
        }
        if ("Yellow".equals(teamName)) {
            return config.getYellowWarriorClass();
        }
        return "";
    }
}
