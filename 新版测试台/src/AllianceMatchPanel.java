import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Desktop;
import java.awt.Font;
import java.awt.GridLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.Properties;
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

public class AllianceMatchPanel extends JPanel {
    private static final int MAX_MATCHES = 500;
    private static final int SAFETY_ROUND_LIMIT = 2000;
    private static final String SETTINGS_FILE_NAME = ".arena-alliance-match.properties";

    private final ArenaFrame owner;
    private final JComboBox<String> challengerOneABox;
    private final JComboBox<String> challengerOneBBox;
    private final JComboBox<String> challengerTwoABox;
    private final JComboBox<String> challengerTwoBBox;
    private final JComboBox<String> baselineOneABox;
    private final JComboBox<String> baselineOneBBox;
    private final JComboBox<String> baselineTwoABox;
    private final JComboBox<String> baselineTwoBBox;
    private final JTextField matchCountField;
    private final JButton openSrcButton;
    private final JButton refreshButton;
    private final JButton restoreDefaultButton;
    private final JButton loadButton;
    private final JButton runButton;
    private final JButton detailButton;
    private final JProgressBar progressBar;
    private final JLabel statusLabel;
    private final JTable summaryTable;
    private final DefaultTableModel tableModel;
    private final JTextArea detailArea;
    private final BatchResultChart chart;
    private BatchSimulationSummary lastSummary;
    private MatchConfig lastConfig;
    private boolean updatingSelections;

    public AllianceMatchPanel(ArenaFrame owner) {
        super(new BorderLayout(8, 8));
        this.owner = owner;
        setBackground(new Color(246, 248, 250));
        setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));

        challengerOneABox = new JComboBox<String>();
        challengerOneBBox = new JComboBox<String>();
        challengerTwoABox = new JComboBox<String>();
        challengerTwoBBox = new JComboBox<String>();
        baselineOneABox = new JComboBox<String>();
        baselineOneBBox = new JComboBox<String>();
        baselineTwoABox = new JComboBox<String>();
        baselineTwoBBox = new JComboBox<String>();
        matchCountField = new JTextField("20", 6);
        openSrcButton = new JButton("打开策略文件夹");
        refreshButton = new JButton("刷新策略列表");
        restoreDefaultButton = new JButton("恢复默认");
        loadButton = new JButton("载入到仿真");
        runButton = new JButton("后台连测");
        detailButton = new JButton("查看得分细节");
        detailButton.setEnabled(false);
        progressBar = new JProgressBar();
        statusLabel = new JLabel("选择 4v4 联队配置后，可以载入仿真或后台连测。");
        detailArea = createTextArea();
        chart = new BatchResultChart();
        lastSummary = null;
        lastConfig = null;
        updatingSelections = false;

        tableModel = new DefaultTableModel(
                new Object[] {
                        "阵营",
                        "策略组合",
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
        JPanel topPanel = new JPanel(new GridLayout(5, 3, 6, 6));
        topPanel.setBackground(new Color(246, 248, 250));
        topPanel.add(new JLabel("联队位置"));
        topPanel.add(new JLabel("战士 A 策略"));
        topPanel.add(new JLabel("战士 B 策略"));
        topPanel.add(new JLabel("挑战方 1"));
        topPanel.add(challengerOneABox);
        topPanel.add(challengerOneBBox);
        topPanel.add(new JLabel("挑战方 2"));
        topPanel.add(challengerTwoABox);
        topPanel.add(challengerTwoBBox);
        topPanel.add(new JLabel("基准方 1"));
        topPanel.add(baselineOneABox);
        topPanel.add(baselineOneBBox);
        topPanel.add(new JLabel("基准方 2"));
        topPanel.add(baselineTwoABox);
        topPanel.add(baselineTwoBBox);

        JPanel actionPanel = new JPanel();
        actionPanel.setBackground(new Color(246, 248, 250));
        actionPanel.add(openSrcButton);
        actionPanel.add(refreshButton);
        actionPanel.add(restoreDefaultButton);
        actionPanel.add(loadButton);
        actionPanel.add(new JLabel("连测轮数 N"));
        actionPanel.add(matchCountField);
        actionPanel.add(runButton);
        actionPanel.add(detailButton);

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
        area.setText("4v4 联队模式会创建两个真正的阵营：挑战方 4 名战士，对抗基准方 4 名战士。\n"
                + "挑战方 1 和挑战方 2 虽然来自两个小队，但在规则中属于同一阵营，不会互相攻击，并且可以通过 getLivingTeammates(self) 看到全部 3 个队友。\n"
                + "基准方同理。这个模式适合两支队伍联合设计配合策略，再对抗固定基准策略。\n");
        return area;
    }

    private void bindActions() {
        openSrcButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent event) {
                openStrategyFolder();
            }
        });

        refreshButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent event) {
                refreshStrategies();
            }
        });

        restoreDefaultButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent event) {
                restoreDefaultSelections();
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

        detailButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent event) {
                openScoreDetails();
            }
        });

        bindSelectionMemory();
    }

    private void refreshStrategies() {
        ArrayList<String> names = MatchEngineFactory.findAvailableWarriorClasses();
        updatingSelections = true;
        fillBox(challengerOneABox, names);
        fillBox(challengerOneBBox, names);
        fillBox(challengerTwoABox, names);
        fillBox(challengerTwoBBox, names);
        fillBox(baselineOneABox, names);
        fillBox(baselineOneBBox, names);
        fillBox(baselineTwoABox, names);
        fillBox(baselineTwoBBox, names);

        MatchConfig rememberedConfig = loadRememberedConfig();
        boolean restoredRemembered = rememberedConfig != null && applyConfigIfAvailable(rememberedConfig);
        if (!restoredRemembered) {
            applyConfigIfAvailable(defaultConfig(names));
        }
        updatingSelections = false;
        saveSelectedConfig();

        if (names.size() == 0) {
            detailArea.setText("没有找到可用策略类。请先编译项目。");
        } else {
            String restoreText = restoredRemembered ? "已恢复上次选择的 4v4 联队配置。\n\n" : "已加载默认 4v4 联队配置。\n\n";
            detailArea.setText(restoreText
                    + "挑战方 1/2 会被放在地图左侧上下两个起点；基准方 1/2 会被放在地图右侧上下两个起点。\n"
                    + "同一阵营内 4 个 warrior 互为队友，不会互相攻击。\n\n"
                    + "添加策略文件后，先编译并重启界面，再回到这里点击“刷新策略列表”。\n");
        }
    }

    private void fillBox(JComboBox<String> box, ArrayList<String> names) {
        box.removeAllItems();
        for (int i = 0; i < names.size(); i++) {
            box.addItem(names.get(i));
        }
    }

    private boolean hasItem(JComboBox<String> box, String value) {
        for (int i = 0; i < box.getItemCount(); i++) {
            if (value.equals(box.getItemAt(i))) {
                return true;
            }
        }
        return false;
    }

    private String preferredClass(ArrayList<String> names, String... candidates) {
        for (int i = 0; i < candidates.length; i++) {
            if (names.contains(candidates[i])) {
                return candidates[i];
            }
        }
        if (names.size() > 0) {
            return names.get(0);
        }
        return candidates.length == 0 ? "TeamAlphaWarrior" : candidates[0];
    }

    private MatchConfig defaultConfig(ArrayList<String> names) {
        String challenger = preferredClass(names, "TeamAlphaWarrior");
        String baseline = preferredClass(names, "AllianceEncirclingWarrior", "TeamAlphaWarrior");
        return new MatchConfig(
                challenger,
                challenger,
                challenger,
                challenger,
                baseline,
                baseline,
                baseline,
                baseline);
    }

    private boolean applyConfigIfAvailable(MatchConfig config) {
        if (config == null
                || !hasItem(challengerOneABox, config.getRedWarriorAClass())
                || !hasItem(challengerOneBBox, config.getRedWarriorBClass())
                || !hasItem(challengerTwoABox, config.getBlueWarriorAClass())
                || !hasItem(challengerTwoBBox, config.getBlueWarriorBClass())
                || !hasItem(baselineOneABox, config.getGreenWarriorAClass())
                || !hasItem(baselineOneBBox, config.getGreenWarriorBClass())
                || !hasItem(baselineTwoABox, config.getYellowWarriorAClass())
                || !hasItem(baselineTwoBBox, config.getYellowWarriorBClass())) {
            return false;
        }

        challengerOneABox.setSelectedItem(config.getRedWarriorAClass());
        challengerOneBBox.setSelectedItem(config.getRedWarriorBClass());
        challengerTwoABox.setSelectedItem(config.getBlueWarriorAClass());
        challengerTwoBBox.setSelectedItem(config.getBlueWarriorBClass());
        baselineOneABox.setSelectedItem(config.getGreenWarriorAClass());
        baselineOneBBox.setSelectedItem(config.getGreenWarriorBClass());
        baselineTwoABox.setSelectedItem(config.getYellowWarriorAClass());
        baselineTwoBBox.setSelectedItem(config.getYellowWarriorBClass());
        return true;
    }

    private void bindSelectionMemory() {
        ActionListener listener = new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent event) {
                if (!updatingSelections) {
                    saveSelectedConfig();
                }
            }
        };
        challengerOneABox.addActionListener(listener);
        challengerOneBBox.addActionListener(listener);
        challengerTwoABox.addActionListener(listener);
        challengerTwoBBox.addActionListener(listener);
        baselineOneABox.addActionListener(listener);
        baselineOneBBox.addActionListener(listener);
        baselineTwoABox.addActionListener(listener);
        baselineTwoBBox.addActionListener(listener);
    }

    private File settingsFile() {
        return new File(System.getProperty("user.dir"), SETTINGS_FILE_NAME);
    }

    private MatchConfig loadRememberedConfig() {
        File file = settingsFile();
        if (!file.isFile()) {
            return null;
        }

        Properties properties = new Properties();
        try (FileInputStream input = new FileInputStream(file)) {
            properties.load(input);
        } catch (Exception exception) {
            return null;
        }

        String challengerOneA = properties.getProperty("challengerOneA");
        String challengerOneB = properties.getProperty("challengerOneB");
        String challengerTwoA = properties.getProperty("challengerTwoA");
        String challengerTwoB = properties.getProperty("challengerTwoB");
        String baselineOneA = properties.getProperty("baselineOneA");
        String baselineOneB = properties.getProperty("baselineOneB");
        String baselineTwoA = properties.getProperty("baselineTwoA");
        String baselineTwoB = properties.getProperty("baselineTwoB");
        if (challengerOneA == null
                || challengerOneB == null
                || challengerTwoA == null
                || challengerTwoB == null
                || baselineOneA == null
                || baselineOneB == null
                || baselineTwoA == null
                || baselineTwoB == null) {
            return null;
        }

        return new MatchConfig(
                challengerOneA,
                challengerOneB,
                challengerTwoA,
                challengerTwoB,
                baselineOneA,
                baselineOneB,
                baselineTwoA,
                baselineTwoB);
    }

    private void saveSelectedConfig() {
        MatchConfig config = selectedConfig();
        if (config == null) {
            return;
        }

        Properties properties = new Properties();
        properties.setProperty("challengerOneA", config.getRedWarriorAClass());
        properties.setProperty("challengerOneB", config.getRedWarriorBClass());
        properties.setProperty("challengerTwoA", config.getBlueWarriorAClass());
        properties.setProperty("challengerTwoB", config.getBlueWarriorBClass());
        properties.setProperty("baselineOneA", config.getGreenWarriorAClass());
        properties.setProperty("baselineOneB", config.getGreenWarriorBClass());
        properties.setProperty("baselineTwoA", config.getYellowWarriorAClass());
        properties.setProperty("baselineTwoB", config.getYellowWarriorBClass());

        try (FileOutputStream output = new FileOutputStream(settingsFile())) {
            properties.store(output, "Java Warrior Arena 4v4 alliance selections");
        } catch (Exception exception) {
            statusLabel.setText("当前选择暂时无法保存，但仍可继续本次测试。");
        }
    }

    private void restoreDefaultSelections() {
        ArrayList<String> names = MatchEngineFactory.findAvailableWarriorClasses();
        updatingSelections = true;
        boolean restored = applyConfigIfAvailable(defaultConfig(names));
        updatingSelections = false;
        if (!restored) {
            JOptionPane.showMessageDialog(this, "默认策略类不完整，请先编译项目或刷新策略列表。");
            return;
        }
        saveSelectedConfig();
        statusLabel.setText("已恢复默认 4v4 联队配置。");
        detailArea.setText("已恢复默认配置：\n" + formatConfig(selectedConfig()));
    }

    private MatchConfig selectedConfig() {
        if (challengerOneABox.getSelectedItem() == null
                || challengerOneBBox.getSelectedItem() == null
                || challengerTwoABox.getSelectedItem() == null
                || challengerTwoBBox.getSelectedItem() == null
                || baselineOneABox.getSelectedItem() == null
                || baselineOneBBox.getSelectedItem() == null
                || baselineTwoABox.getSelectedItem() == null
                || baselineTwoBBox.getSelectedItem() == null) {
            return null;
        }
        return new MatchConfig(
                challengerOneABox.getSelectedItem().toString(),
                challengerOneBBox.getSelectedItem().toString(),
                challengerTwoABox.getSelectedItem().toString(),
                challengerTwoBBox.getSelectedItem().toString(),
                baselineOneABox.getSelectedItem().toString(),
                baselineOneBBox.getSelectedItem().toString(),
                baselineTwoABox.getSelectedItem().toString(),
                baselineTwoBBox.getSelectedItem().toString());
    }

    private void loadSelectedMatch() {
        MatchConfig config = selectedConfig();
        if (config == null) {
            JOptionPane.showMessageDialog(this, "请先选择 4v4 联队策略。");
            return;
        }

        try {
            GameEngine engine = MatchEngineFactory.createAllianceEngine(config);
            owner.loadEngine(engine);
            saveSelectedConfig();
            detailArea.setText("已载入 4v4 联队配置到左侧仿真地图。\n"
                    + formatConfig(config));
        } catch (RuntimeException exception) {
            JOptionPane.showMessageDialog(this, exception.getMessage());
        }
    }

    private void startBatchRun() {
        final MatchConfig config = selectedConfig();
        if (config == null) {
            JOptionPane.showMessageDialog(this, "请先选择 4v4 联队策略。");
            return;
        }

        final int matches = parseMatchCount();
        if (matches <= 0) {
            return;
        }
        saveSelectedConfig();

        setRunButtonsEnabled(false);
        detailButton.setEnabled(false);
        lastSummary = null;
        lastConfig = null;
        detailButton.setText("查看得分细节");
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
        restoreDefaultButton.setEnabled(enabled);
    }

    private void showRunFailure(Throwable throwable) {
        setRunButtonsEnabled(true);
        detailButton.setEnabled(false);
        progressBar.setValue(0);
        statusLabel.setText("4v4 联队连测失败。");
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
        ArrayList<BatchMatchScoreDetail> matchDetails = new ArrayList<BatchMatchScoreDetail>();
        ArrayList<ReplayMatch> replayMatches = new ArrayList<ReplayMatch>();
        String details = "";
        int noWinnerMatches = 0;
        int unfinishedMatches = 0;

        for (int i = 1; i <= matches; i++) {
            GameEngine engine = MatchEngineFactory.createAllianceEngine(config);
            engine.setPrintLogs(false);
            engine.setStoreLogs(false);
            ReplayMatch replay = new ReplayMatch(i, engine);

            while (!engine.isGameOver() && engine.getCurrentRound() <= SAFETY_ROUND_LIMIT) {
                boolean stepped = engine.stepTurn();
                if (!stepped) {
                    break;
                }
                replay.addSnapshot(engine);
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
            replay.finish(winner, unfinished, engine.getCurrentRound());
            replayMatches.add(replay);
            addMatchDetails(matchDetails, i, engine.getCurrentRound(), winner, unfinished, scores);
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

        return new BatchSimulationSummary(
                stats,
                matchDetails,
                replayMatches,
                details,
                matches,
                matches,
                noWinnerMatches,
                unfinishedMatches);
    }

    private ArrayList<BatchTeamStats> createStats(MatchConfig config) {
        GameEngine engine = MatchEngineFactory.createAllianceEngine(config);
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

    private void addMatchDetails(
            ArrayList<BatchMatchScoreDetail> matchDetails,
            int matchNumber,
            int roundCount,
            String winner,
            boolean unfinished,
            ArrayList<TeamScore> scores) {
        for (int i = 0; i < scores.size(); i++) {
            matchDetails.add(new BatchMatchScoreDetail(
                    matchNumber,
                    roundCount,
                    winner,
                    unfinished,
                    scores.get(i)));
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
        lastSummary = summary;
        lastConfig = config;
        detailButton.setText("查看得分细节");
        detailButton.setEnabled(summary.getMatchDetails().size() > 0);
        tableModel.setRowCount(0);
        ArrayList<BatchTeamStats> stats = summary.getTeamStats();
        for (int i = 0; i < stats.size(); i++) {
            BatchTeamStats stat = stats.get(i);
            double winRate = stat.getWins() * 100.0 / summary.getCompletedMatches();
            tableModel.addRow(new Object[] {
                    stat.getTeamName(),
                    strategyForSide(config, stat.getTeamName()),
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

        String header = "配置:\n"
                + formatConfig(config)
                + "完成 "
                + summary.getCompletedMatches()
                + " / "
                + summary.getRequestedMatches()
                + " 局；无胜者 "
                + summary.getNoWinnerMatches()
                + " 局；超过保护上限未结束 "
                + summary.getUnfinishedMatches()
                + " 局。\n\n";
        detailArea.setText(header + summary.getDetailText());
        detailArea.setCaretPosition(0);
        chart.setSummary(summary);
        progressBar.setValue(summary.getCompletedMatches());
        statusLabel.setText("4v4 联队连测完成。");
    }

    private void openScoreDetails() {
        if (lastSummary == null || lastConfig == null) {
            return;
        }
        ScoreDetailDialog dialog = new ScoreDetailDialog(
                SwingUtilities.getWindowAncestor(this),
                lastSummary,
                "4v4 联队得分细节");
        dialog.setVisible(true);
    }

    private String strategyForSide(MatchConfig config, String teamName) {
        if ("Challengers".equals(teamName)) {
            return config.getRedWarriorClass() + " / " + config.getBlueWarriorClass();
        }
        if ("Baseline".equals(teamName)) {
            return config.getGreenWarriorClass() + " / " + config.getYellowWarriorClass();
        }
        return "";
    }

    private String formatConfig(MatchConfig config) {
        return "挑战方 1 A=" + config.getRedWarriorAClass() + ", B=" + config.getRedWarriorBClass() + "\n"
                + "挑战方 2 A=" + config.getBlueWarriorAClass() + ", B=" + config.getBlueWarriorBClass() + "\n"
                + "基准方 1 A=" + config.getGreenWarriorAClass() + ", B=" + config.getGreenWarriorBClass() + "\n"
                + "基准方 2 A=" + config.getYellowWarriorAClass() + ", B=" + config.getYellowWarriorBClass() + "\n";
    }
}
