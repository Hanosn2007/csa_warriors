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
import javax.swing.JCheckBox;
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
    private static final String SETTINGS_FILE_NAME = ".arena-internal-match.properties";

    private final ArenaFrame owner;
    private final JComboBox<String> redABox;
    private final JComboBox<String> redBBox;
    private final JComboBox<String> blueABox;
    private final JComboBox<String> blueBBox;
    private final JComboBox<String> greenABox;
    private final JComboBox<String> greenBBox;
    private final JComboBox<String> yellowABox;
    private final JComboBox<String> yellowBBox;
    private final JTextField matchCountField;
    private final JTextField seedField;
    private final JCheckBox fixedSeedCheckBox;
    private final JCheckBox rotateBatchCheckBox;
    private final JButton loadButton;
    private final JButton runButton;
    private final JButton detailButton;
    private final JButton refreshButton;
    private final JButton openSrcButton;
    private final JButton restoreDefaultButton;
    private final JButton rotateButton;
    private final JProgressBar progressBar;
    private final JLabel statusLabel;
    private final JTable summaryTable;
    private final DefaultTableModel tableModel;
    private final JTextArea detailArea;
    private final BatchResultChart chart;
    private BatchSimulationSummary lastSummary;
    private MatchConfig lastConfig;
    private boolean updatingSelections;

    public InternalMatchPanel(ArenaFrame owner) {
        super(new BorderLayout(8, 8));
        this.owner = owner;
        setBackground(new Color(246, 248, 250));
        setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));

        redABox = new JComboBox<String>();
        redBBox = new JComboBox<String>();
        blueABox = new JComboBox<String>();
        blueBBox = new JComboBox<String>();
        greenABox = new JComboBox<String>();
        greenBBox = new JComboBox<String>();
        yellowABox = new JComboBox<String>();
        yellowBBox = new JComboBox<String>();
        matchCountField = new JTextField("20", 6);
        seedField = new JTextField("20260528", 8);
        fixedSeedCheckBox = new JCheckBox("固定随机种子");
        fixedSeedCheckBox.setBackground(new Color(246, 248, 250));
        fixedSeedCheckBox.setSelected(false);
        seedField.setEnabled(false);
        rotateBatchCheckBox = new JCheckBox("轮换四个起点");
        rotateBatchCheckBox.setSelected(true);
        rotateBatchCheckBox.setBackground(new Color(246, 248, 250));
        loadButton = new JButton("载入到仿真");
        runButton = new JButton("后台连测");
        detailButton = new JButton("查看得分细节");
        detailButton.setEnabled(false);
        refreshButton = new JButton("刷新策略列表");
        openSrcButton = new JButton("打开策略文件夹");
        restoreDefaultButton = new JButton("恢复默认");
        rotateButton = new JButton("轮换位置");
        progressBar = new JProgressBar();
        statusLabel = new JLabel("选择四队策略后，可以载入仿真或后台连测。");
        detailArea = createTextArea();
        chart = new BatchResultChart();
        lastSummary = null;
        lastConfig = null;
        updatingSelections = false;

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
        JPanel topPanel = new JPanel(new GridLayout(5, 3, 6, 6));
        topPanel.setBackground(new Color(246, 248, 250));
        topPanel.add(new JLabel("队伍"));
        topPanel.add(new JLabel("战士 A 策略"));
        topPanel.add(new JLabel("战士 B 策略"));
        topPanel.add(new JLabel("红队"));
        topPanel.add(redABox);
        topPanel.add(redBBox);
        topPanel.add(new JLabel("蓝队"));
        topPanel.add(blueABox);
        topPanel.add(blueBBox);
        topPanel.add(new JLabel("绿队"));
        topPanel.add(greenABox);
        topPanel.add(greenBBox);
        topPanel.add(new JLabel("黄队"));
        topPanel.add(yellowABox);
        topPanel.add(yellowBBox);

        JPanel actionPanel = new JPanel();
        actionPanel.setBackground(new Color(246, 248, 250));
        actionPanel.add(openSrcButton);
        actionPanel.add(refreshButton);
        actionPanel.add(restoreDefaultButton);
        actionPanel.add(rotateButton);
        actionPanel.add(loadButton);
        actionPanel.add(new JLabel("连测轮数 N"));
        actionPanel.add(matchCountField);
        actionPanel.add(fixedSeedCheckBox);
        actionPanel.add(seedField);
        actionPanel.add(rotateBatchCheckBox);
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
        area.setText("队内 PK 使用当前已编译的 Warrior 类。每队 A/B 两名战士可以选择同一个策略，也可以选择两个不同策略来配合。\n"
                + "修改策略后，需要先在“项目文件”中编译并重启界面。\n"
                + "添加其他队伍策略：把对方的 public 策略类 .java 文件放进 src。名称由队伍自定，但必须唯一，且文件名、public 类名和构造方法名一致，类必须 extends Warrior。\n"
                + "表格可以点击列标题排序，用来比较进攻、资源、续航和失误。连测完成后可以在得分细节中回放某一局。\n");
        return area;
    }

    private void bindActions() {
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

        rotateButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent event) {
                rotateSelectionsClockwise();
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

        fixedSeedCheckBox.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent event) {
                seedField.setEnabled(fixedSeedCheckBox.isSelected() && runButton.isEnabled());
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
        fillBox(redABox, names);
        fillBox(redBBox, names);
        fillBox(blueABox, names);
        fillBox(blueBBox, names);
        fillBox(greenABox, names);
        fillBox(greenBBox, names);
        fillBox(yellowABox, names);
        fillBox(yellowBBox, names);

        MatchConfig rememberedConfig = loadRememberedConfig();
        boolean restoredRemembered = rememberedConfig != null && applyConfigIfAvailable(rememberedConfig);
        if (!restoredRemembered) {
            applyConfigIfAvailable(defaultConfig());
        }
        updatingSelections = false;
        saveSelectedConfig();

        if (names.size() == 0) {
            detailArea.setText("没有找到可用策略类。请先编译项目。");
        } else {
            String restoreText = restoredRemembered ? "已恢复上次选择的队内 PK 配置。\n\n" : "已加载默认队内 PK 配置。\n\n";
            detailArea.setText(restoreText
                    + "已找到 "
                    + names.size()
                    + " 个可用策略类。每队 A/B 两名战士可以分别选择策略；选同一个类就是双人同策略，选不同类就是分工配合。\n\n"
                    + "添加其他队伍策略的步骤：\n"
                    + "1. 把对方策略 .java 文件复制到 src 文件夹。\n"
                    + "2. 名称由队伍自定，但同一个 src 中不能重复。\n"
                    + "3. 文件名、public 类名和构造方法名必须一致。\n"
                    + "   例如 AuroraBlade.java / public class AuroraBlade / public AuroraBlade()。\n"
                    + "4. 确认这个类 extends Warrior。\n"
                    + "5. 在“项目文件”中点击“编译并重启界面”。\n"
                    + "6. 回到“队内 PK”，点击“刷新策略列表”。\n\n"
                    + "“轮换位置”会把当前四队配置按地图位置顺时针移动一格，用来比较不同起点带来的影响。\n");
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

    private boolean hasItem(JComboBox<String> box, String value) {
        for (int i = 0; i < box.getItemCount(); i++) {
            if (value.equals(box.getItemAt(i))) {
                return true;
            }
        }
        return false;
    }

    private MatchConfig defaultConfig() {
        return new MatchConfig(
                "TeamAlphaWarrior",
                "TeamAlphaWarrior",
                "TeamAlphaWarrior",
                "TeamAlphaWarrior",
                "AllianceEncirclingWarrior",
                "AllianceEncirclingWarrior",
                "AllianceEncirclingWarrior",
                "AllianceEncirclingWarrior");
    }

    private boolean applyConfigIfAvailable(MatchConfig config) {
        if (config == null
                || !hasItem(redABox, config.getRedWarriorAClass())
                || !hasItem(redBBox, config.getRedWarriorBClass())
                || !hasItem(blueABox, config.getBlueWarriorAClass())
                || !hasItem(blueBBox, config.getBlueWarriorBClass())
                || !hasItem(greenABox, config.getGreenWarriorAClass())
                || !hasItem(greenBBox, config.getGreenWarriorBClass())
                || !hasItem(yellowABox, config.getYellowWarriorAClass())
                || !hasItem(yellowBBox, config.getYellowWarriorBClass())) {
            return false;
        }

        redABox.setSelectedItem(config.getRedWarriorAClass());
        redBBox.setSelectedItem(config.getRedWarriorBClass());
        blueABox.setSelectedItem(config.getBlueWarriorAClass());
        blueBBox.setSelectedItem(config.getBlueWarriorBClass());
        greenABox.setSelectedItem(config.getGreenWarriorAClass());
        greenBBox.setSelectedItem(config.getGreenWarriorBClass());
        yellowABox.setSelectedItem(config.getYellowWarriorAClass());
        yellowBBox.setSelectedItem(config.getYellowWarriorBClass());
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
        redABox.addActionListener(listener);
        redBBox.addActionListener(listener);
        blueABox.addActionListener(listener);
        blueBBox.addActionListener(listener);
        greenABox.addActionListener(listener);
        greenBBox.addActionListener(listener);
        yellowABox.addActionListener(listener);
        yellowBBox.addActionListener(listener);
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

        String redA = properties.getProperty("redA");
        String redB = properties.getProperty("redB");
        String blueA = properties.getProperty("blueA");
        String blueB = properties.getProperty("blueB");
        String greenA = properties.getProperty("greenA");
        String greenB = properties.getProperty("greenB");
        String yellowA = properties.getProperty("yellowA");
        String yellowB = properties.getProperty("yellowB");
        if (redA == null
                || redB == null
                || blueA == null
                || blueB == null
                || greenA == null
                || greenB == null
                || yellowA == null
                || yellowB == null) {
            return null;
        }

        return new MatchConfig(redA, redB, blueA, blueB, greenA, greenB, yellowA, yellowB);
    }

    private void saveSelectedConfig() {
        MatchConfig config = selectedConfig();
        if (config == null) {
            return;
        }

        Properties properties = new Properties();
        properties.setProperty("redA", config.getRedWarriorAClass());
        properties.setProperty("redB", config.getRedWarriorBClass());
        properties.setProperty("blueA", config.getBlueWarriorAClass());
        properties.setProperty("blueB", config.getBlueWarriorBClass());
        properties.setProperty("greenA", config.getGreenWarriorAClass());
        properties.setProperty("greenB", config.getGreenWarriorBClass());
        properties.setProperty("yellowA", config.getYellowWarriorAClass());
        properties.setProperty("yellowB", config.getYellowWarriorBClass());

        try (FileOutputStream output = new FileOutputStream(settingsFile())) {
            properties.store(output, "Java Warrior Arena internal match selections");
        } catch (Exception exception) {
            statusLabel.setText("当前选择暂时无法保存，但仍可继续本次测试。");
        }
    }

    private void restoreDefaultSelections() {
        updatingSelections = true;
        boolean restored = applyConfigIfAvailable(defaultConfig());
        updatingSelections = false;
        if (!restored) {
            JOptionPane.showMessageDialog(this, "默认策略类不完整，请先编译项目或刷新策略列表。");
            return;
        }
        saveSelectedConfig();
        statusLabel.setText("已恢复默认队内 PK 配置。");
        detailArea.setText("已恢复默认配置：\n" + formatConfig(selectedConfig()));
    }

    private void rotateSelectionsClockwise() {
        MatchConfig config = selectedConfig();
        if (config == null) {
            JOptionPane.showMessageDialog(this, "请先选择四个队伍的策略。");
            return;
        }

        MatchConfig rotated = new MatchConfig(
                config.getGreenWarriorAClass(),
                config.getGreenWarriorBClass(),
                config.getRedWarriorAClass(),
                config.getRedWarriorBClass(),
                config.getYellowWarriorAClass(),
                config.getYellowWarriorBClass(),
                config.getBlueWarriorAClass(),
                config.getBlueWarriorBClass());

        updatingSelections = true;
        applyConfigIfAvailable(rotated);
        updatingSelections = false;
        saveSelectedConfig();
        try {
            owner.loadEngine(MatchEngineFactory.createEngine(rotated));
            statusLabel.setText("已按地图顺时针轮换四队起点，并载入仿真。");
            detailArea.setText("已轮换并载入左侧仿真：红位使用原绿队，蓝位使用原红队，黄位使用原蓝队，绿位使用原黄队。\n"
                    + formatConfig(selectedConfig()));
        } catch (RuntimeException exception) {
            statusLabel.setText("已轮换选择，但载入仿真失败。");
            JOptionPane.showMessageDialog(this, exception.getMessage());
        }
    }

    private MatchConfig selectedConfig() {
        if (redABox.getSelectedItem() == null
                || redBBox.getSelectedItem() == null
                || blueABox.getSelectedItem() == null
                || blueBBox.getSelectedItem() == null
                || greenABox.getSelectedItem() == null
                || greenBBox.getSelectedItem() == null
                || yellowABox.getSelectedItem() == null
                || yellowBBox.getSelectedItem() == null) {
            return null;
        }
        return new MatchConfig(
                redABox.getSelectedItem().toString(),
                redBBox.getSelectedItem().toString(),
                blueABox.getSelectedItem().toString(),
                blueBBox.getSelectedItem().toString(),
                greenABox.getSelectedItem().toString(),
                greenBBox.getSelectedItem().toString(),
                yellowABox.getSelectedItem().toString(),
                yellowBBox.getSelectedItem().toString());
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
            saveSelectedConfig();
            detailArea.setText("已载入队内 PK 配置到左侧仿真地图。\n"
                    + formatConfig(config));
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
        final Long seedBase = parseSeedBase();
        if (fixedSeedCheckBox.isSelected() && seedBase == null) {
            return;
        }
        final boolean rotateStarts = rotateBatchCheckBox.isSelected();
        final int totalRuns = rotateStarts ? matches * 4 : matches;
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
        progressBar.setMaximum(totalRuns);
        progressBar.setValue(0);
        statusLabel.setText("正在后台连测 0 / " + totalRuns + " 局...");

        Thread thread = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    final BatchSimulationSummary summary = runMatches(config, matches, seedBase, rotateStarts);
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
        rotateButton.setEnabled(enabled);
        fixedSeedCheckBox.setEnabled(enabled);
        seedField.setEnabled(enabled && fixedSeedCheckBox.isSelected());
        rotateBatchCheckBox.setEnabled(enabled);
    }

    private void showRunFailure(Throwable throwable) {
        setRunButtonsEnabled(true);
        detailButton.setEnabled(false);
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

    private Long parseSeedBase() {
        if (!fixedSeedCheckBox.isSelected()) {
            return null;
        }
        try {
            return Long.valueOf(Long.parseLong(seedField.getText().trim()));
        } catch (NumberFormatException exception) {
            JOptionPane.showMessageDialog(this, "随机种子必须是整数，例如 20260528。");
            return null;
        }
    }

    private BatchSimulationSummary runMatches(MatchConfig config, int matches, Long seedBase, boolean rotateStarts) {
        ArrayList<BatchTeamStats> stats = createStats(config, rotateStarts);
        ArrayList<BatchMatchScoreDetail> matchDetails = new ArrayList<BatchMatchScoreDetail>();
        ArrayList<ReplayMatch> replayMatches = new ArrayList<ReplayMatch>();
        String details = "";
        int noWinnerMatches = 0;
        int unfinishedMatches = 0;
        int totalRuns = rotateStarts ? matches * 4 : matches;
        int completedRuns = 0;

        for (int rotation = 0; rotation < (rotateStarts ? 4 : 1); rotation++) {
            MatchConfig runConfig = rotateStarts ? rotatedConfigClockwise(config, rotation) : config;
            for (int i = 1; i <= matches; i++) {
            completedRuns++;
            GameEngine engine;
            if (seedBase == null) {
                engine = MatchEngineFactory.createEngine(runConfig);
            } else {
                long seed = seedBase.longValue() + rotation * 100000L + i;
                engine = MatchEngineFactory.createEngine(runConfig, seed);
            }
            engine.setPrintLogs(false);
            engine.setStoreLogs(false);
            ReplayMatch replay = new ReplayMatch(completedRuns, engine);

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
            String displayWinner = rotateStarts ? originalTeamNameForCurrentColor(rotation, winner) : winner;
            replay.finish(displayWinner, unfinished, engine.getCurrentRound());
            replayMatches.add(replay);
            addMatchDetails(matchDetails, completedRuns, engine.getCurrentRound(), displayWinner, unfinished, scores, rotation, rotateStarts);
            details = details + formatMatchDetail(completedRuns, displayWinner, unfinished, scores, rotation, rotateStarts) + "\n";
            addScores(stats, scores, rotation, rotateStarts);
            if (displayWinner != null) {
                BatchTeamStats winningStats = findStats(stats, displayWinner);
                if (winningStats != null) {
                    winningStats.addWin();
                }
            }

            final int completed = completedRuns;
            SwingUtilities.invokeLater(new Runnable() {
                @Override
                public void run() {
                    progressBar.setValue(completed);
                    statusLabel.setText("正在后台连测 " + completed + " / " + totalRuns + " 局...");
                }
            });
            }
        }

        return new BatchSimulationSummary(
                stats,
                matchDetails,
                replayMatches,
                details,
                totalRuns,
                totalRuns,
                noWinnerMatches,
                unfinishedMatches);
    }

    private ArrayList<BatchTeamStats> createStats(MatchConfig config, boolean rotateStarts) {
        if (rotateStarts) {
            ArrayList<BatchTeamStats> rotatedStats = new ArrayList<BatchTeamStats>();
            rotatedStats.add(new BatchTeamStats("Original Red"));
            rotatedStats.add(new BatchTeamStats("Original Blue"));
            rotatedStats.add(new BatchTeamStats("Original Green"));
            rotatedStats.add(new BatchTeamStats("Original Yellow"));
            return rotatedStats;
        }

        GameEngine engine = MatchEngineFactory.createEngine(config);
        ArrayList<BatchTeamStats> stats = new ArrayList<BatchTeamStats>();
        ArrayList<Team> teams = engine.getTeams();
        for (int i = 0; i < teams.size(); i++) {
            stats.add(new BatchTeamStats(teams.get(i).getName()));
        }
        return stats;
    }

    private void addScores(ArrayList<BatchTeamStats> stats, ArrayList<TeamScore> scores, int rotation, boolean rotateStarts) {
        for (int i = 0; i < scores.size(); i++) {
            TeamScore score = scores.get(i);
            String teamName = rotateStarts
                    ? originalTeamNameForCurrentColor(rotation, score.getTeamName())
                    : score.getTeamName();
            BatchTeamStats stat = findStats(stats, teamName);
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
            ArrayList<TeamScore> scores,
            int rotation,
            boolean rotateStarts) {
        for (int i = 0; i < scores.size(); i++) {
            String teamName = rotateStarts
                    ? originalTeamNameForCurrentColor(rotation, scores.get(i).getTeamName())
                    : scores.get(i).getTeamName();
            matchDetails.add(new BatchMatchScoreDetail(
                    matchNumber,
                    roundCount,
                    teamName,
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

    private MatchConfig rotatedConfigClockwise(MatchConfig config, int rotation) {
        int normalized = ((rotation % 4) + 4) % 4;
        if (normalized == 0) {
            return config;
        }
        if (normalized == 1) {
            return new MatchConfig(
                    config.getGreenWarriorAClass(),
                    config.getGreenWarriorBClass(),
                    config.getRedWarriorAClass(),
                    config.getRedWarriorBClass(),
                    config.getYellowWarriorAClass(),
                    config.getYellowWarriorBClass(),
                    config.getBlueWarriorAClass(),
                    config.getBlueWarriorBClass());
        }
        if (normalized == 2) {
            return new MatchConfig(
                    config.getYellowWarriorAClass(),
                    config.getYellowWarriorBClass(),
                    config.getGreenWarriorAClass(),
                    config.getGreenWarriorBClass(),
                    config.getBlueWarriorAClass(),
                    config.getBlueWarriorBClass(),
                    config.getRedWarriorAClass(),
                    config.getRedWarriorBClass());
        }
        return new MatchConfig(
                config.getBlueWarriorAClass(),
                config.getBlueWarriorBClass(),
                config.getYellowWarriorAClass(),
                config.getYellowWarriorBClass(),
                config.getRedWarriorAClass(),
                config.getRedWarriorBClass(),
                config.getGreenWarriorAClass(),
                config.getGreenWarriorBClass());
    }

    private String originalTeamNameForCurrentColor(int rotation, String currentColor) {
        if (currentColor == null) {
            return null;
        }
        int normalized = ((rotation % 4) + 4) % 4;
        if (normalized == 0) {
            return currentColor;
        }
        if (normalized == 1) {
            if ("Red".equals(currentColor)) {
                return "Original Green";
            }
            if ("Blue".equals(currentColor)) {
                return "Original Red";
            }
            if ("Green".equals(currentColor)) {
                return "Original Yellow";
            }
            if ("Yellow".equals(currentColor)) {
                return "Original Blue";
            }
        }
        if (normalized == 2) {
            if ("Red".equals(currentColor)) {
                return "Original Yellow";
            }
            if ("Blue".equals(currentColor)) {
                return "Original Green";
            }
            if ("Green".equals(currentColor)) {
                return "Original Blue";
            }
            if ("Yellow".equals(currentColor)) {
                return "Original Red";
            }
        }
        if ("Red".equals(currentColor)) {
            return "Original Blue";
        }
        if ("Blue".equals(currentColor)) {
            return "Original Yellow";
        }
        if ("Green".equals(currentColor)) {
            return "Original Red";
        }
        if ("Yellow".equals(currentColor)) {
            return "Original Green";
        }
        return currentColor;
    }

    private String formatMatchDetail(
            int matchNumber,
            String winner,
            boolean unfinished,
            ArrayList<TeamScore> scores,
            int rotation,
            boolean rotateStarts) {
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
            String teamName = rotateStarts
                    ? originalTeamNameForCurrentColor(rotation, score.getTeamName())
                    : score.getTeamName();
            line = line
                    + teamName
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
                + " 局。\n点击表头可以按任意指标排序。伤害、道具、回血等列为本次连测累计值。点击“查看得分细节”可回放最近一次连测缓存中的某一局。\n\n";
        detailArea.setText(header + summary.getDetailText());
        detailArea.setCaretPosition(0);
        chart.setSummary(summary);
        progressBar.setValue(summary.getCompletedMatches());
        statusLabel.setText("队内 PK 连测完成。");
    }

    private void openScoreDetails() {
        if (lastSummary == null || lastConfig == null) {
            return;
        }
        ScoreDetailDialog dialog = new ScoreDetailDialog(
                SwingUtilities.getWindowAncestor(this),
                lastSummary,
                "队内 PK 得分细节");
        dialog.setVisible(true);
    }

    private String strategyForTeam(MatchConfig config, String teamName) {
        if ("Red".equals(teamName) || "Original Red".equals(teamName)) {
            return config.getRedWarriorClass();
        }
        if ("Blue".equals(teamName) || "Original Blue".equals(teamName)) {
            return config.getBlueWarriorClass();
        }
        if ("Green".equals(teamName) || "Original Green".equals(teamName)) {
            return config.getGreenWarriorClass();
        }
        if ("Yellow".equals(teamName) || "Original Yellow".equals(teamName)) {
            return config.getYellowWarriorClass();
        }
        return "";
    }

    private String formatConfig(MatchConfig config) {
        return "Red A=" + config.getRedWarriorAClass() + ", Red B=" + config.getRedWarriorBClass() + "\n"
                + "Blue A=" + config.getBlueWarriorAClass() + ", Blue B=" + config.getBlueWarriorBClass() + "\n"
                + "Green A=" + config.getGreenWarriorAClass() + ", Green B=" + config.getGreenWarriorBClass() + "\n"
                + "Yellow A=" + config.getYellowWarriorAClass() + ", Yellow B=" + config.getYellowWarriorBClass() + "\n";
    }
}
