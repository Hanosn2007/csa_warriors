import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GridLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSlider;
import javax.swing.JSplitPane;
import javax.swing.JTabbedPane;
import javax.swing.JTextArea;
import javax.swing.Timer;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import javax.swing.plaf.basic.BasicButtonUI;

public class ArenaFrame extends JFrame implements UnitSelectionListener {
    private GameEngine engine;
    private final ArenaPanel arenaPanel;
    private final JTextArea logArea;
    private final JTextArea intentArea;
    private final JTextArea statusArea;
    private final JTextArea scoreArea;
    private final JTextArea selectedArea;
    private final JLabel roundLabel;
    private final JLabel resultLabel;
    private final JButton nextTurnButton;
    private final JButton nextRoundButton;
    private final JButton autoButton;
    private final JButton resetButton;
    private final JSlider speedSlider;
    private final Timer autoTimer;
    private int selectedUnitId;

    public ArenaFrame(GameEngine engine) {
        super("Java Warrior Arena - Visual Debugger");
        this.engine = engine;
        this.engine.setPrintLogs(false);
        this.selectedUnitId = -1;

        arenaPanel = new ArenaPanel(engine, this);
        logArea = createTextArea();
        intentArea = createTextArea();
        statusArea = createTextArea();
        scoreArea = createTextArea();
        selectedArea = createTextArea();
        roundLabel = new JLabel();
        resultLabel = new JLabel();
        nextTurnButton = new JButton("下一步");
        nextRoundButton = new JButton("下一回合");
        autoButton = new JButton("自动播放");
        resetButton = new JButton("重置");
        speedSlider = new JSlider(100, 1200, 500);
        autoTimer = new Timer(speedSlider.getValue(), new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent event) {
                stepTurn();
            }
        });

        buildLayout();
        bindActions();
        refresh();

        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setMinimumSize(new Dimension(1050, 720));
        setLocationRelativeTo(null);
    }

    @Override
    public void unitSelected(int unitId) {
        selectedUnitId = unitId;
        arenaPanel.setSelectedUnitId(unitId);
        refreshSelectedUnit();
    }

    private void buildLayout() {
        JPanel topPanel = new JPanel(new BorderLayout(12, 0));
        topPanel.setBackground(new Color(246, 248, 250));
        topPanel.setBorder(BorderFactory.createEmptyBorder(10, 12, 10, 12));
        JPanel buttonPanel = new JPanel();
        buttonPanel.setBackground(new Color(246, 248, 250));
        styleButton(nextTurnButton, new Color(20, 83, 166));
        styleButton(nextRoundButton, new Color(22, 112, 79));
        styleButton(autoButton, new Color(94, 63, 176));
        styleButton(resetButton, new Color(67, 76, 89));
        buttonPanel.add(nextTurnButton);
        buttonPanel.add(nextRoundButton);
        buttonPanel.add(autoButton);
        buttonPanel.add(resetButton);
        topPanel.add(buttonPanel, BorderLayout.WEST);

        JPanel infoPanel = new JPanel(new GridLayout(2, 1));
        infoPanel.setBackground(new Color(246, 248, 250));
        roundLabel.setFont(new Font("SansSerif", Font.BOLD, 15));
        resultLabel.setForeground(new Color(60, 74, 92));
        infoPanel.add(roundLabel);
        infoPanel.add(resultLabel);
        topPanel.add(infoPanel, BorderLayout.CENTER);

        JPanel speedPanel = new JPanel(new BorderLayout());
        speedPanel.setBackground(new Color(246, 248, 250));
        speedPanel.add(new JLabel("速度"), BorderLayout.WEST);
        speedPanel.add(speedSlider, BorderLayout.CENTER);
        topPanel.add(speedPanel, BorderLayout.EAST);

        JPanel debugPanel = new JPanel(new GridLayout(2, 1, 0, 8));
        debugPanel.setBackground(new Color(246, 248, 250));
        debugPanel.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        debugPanel.add(section("策略意图", intentArea));
        debugPanel.add(section("战斗日志", logArea));

        JPanel scorePanel = new JPanel(new GridLayout(3, 1, 0, 8));
        scorePanel.setBackground(new Color(246, 248, 250));
        scorePanel.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        scorePanel.add(section("评分报告", scoreArea));
        scorePanel.add(section("单位状态", statusArea));
        scorePanel.add(section("选中单位", selectedArea));

        JTabbedPane sideTabs = new JTabbedPane();
        sideTabs.addTab("调试", debugPanel);
        sideTabs.addTab("状态评分", scorePanel);
        sideTabs.addTab("队内 PK", new InternalMatchPanel(this));
        sideTabs.addTab("批量连测", new BatchSimulationPanel());
        sideTabs.addTab("项目文件", new ProjectToolsPanel(this));

        JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, arenaPanel, sideTabs);
        splitPane.setResizeWeight(0.66);
        splitPane.setBorder(BorderFactory.createEmptyBorder(0, 12, 12, 12));

        add(topPanel, BorderLayout.NORTH);
        add(splitPane, BorderLayout.CENTER);
    }

    private JPanel section(String title, JTextArea area) {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBackground(new Color(246, 248, 250));
        JLabel label = new JLabel(title);
        label.setFont(new Font("SansSerif", Font.BOLD, 13));
        label.setForeground(new Color(45, 54, 66));
        label.setBorder(BorderFactory.createEmptyBorder(0, 0, 4, 0));
        panel.add(label, BorderLayout.NORTH);
        panel.add(new JScrollPane(area), BorderLayout.CENTER);
        return panel;
    }

    private JTextArea createTextArea() {
        JTextArea area = new JTextArea();
        area.setEditable(false);
        area.setLineWrap(true);
        area.setWrapStyleWord(true);
        area.setFont(new Font("Monospaced", Font.PLAIN, 13));
        area.setBackground(new Color(253, 253, 251));
        area.setForeground(new Color(35, 42, 52));
        area.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        return area;
    }

    private void styleButton(JButton button, Color color) {
        button.setUI(new BasicButtonUI());
        button.setFocusPainted(false);
        button.setBackground(color);
        button.setForeground(Color.WHITE);
        button.setOpaque(true);
        button.setContentAreaFilled(true);
        button.setBorderPainted(false);
        button.setCursor(new Cursor(Cursor.HAND_CURSOR));
        button.setFont(new Font("SansSerif", Font.BOLD, 12));
        button.setBorder(BorderFactory.createEmptyBorder(9, 16, 9, 16));
    }

    private void bindActions() {
        nextTurnButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent event) {
                stepTurn();
            }
        });

        nextRoundButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent event) {
                engine.stepRound();
                refresh();
                arenaPanel.playAnimation(engine.getLastAnimation());
            }
        });

        autoButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent event) {
                toggleAutoPlay();
            }
        });

        resetButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent event) {
                resetGame();
            }
        });

        speedSlider.addChangeListener(new ChangeListener() {
            @Override
            public void stateChanged(ChangeEvent event) {
                autoTimer.setDelay(speedSlider.getValue());
            }
        });
    }

    private void stepTurn() {
        engine.stepTurn();
        refresh();
        arenaPanel.playAnimation(engine.getLastAnimation());
    }

    private void toggleAutoPlay() {
        if (autoTimer.isRunning()) {
            autoTimer.stop();
            autoButton.setText("自动播放");
        } else {
            autoTimer.start();
            autoButton.setText("暂停");
        }
    }

    private void resetGame() {
        autoTimer.stop();
        autoButton.setText("自动播放");
        loadEngine(ArenaFactory.createDefaultEngine());
    }

    public void loadEngine(GameEngine newEngine) {
        autoTimer.stop();
        autoButton.setText("自动播放");
        engine = newEngine;
        engine.setPrintLogs(false);
        selectedUnitId = -1;
        arenaPanel.setEngine(engine);
        refresh();
        arenaPanel.playAnimation(null);
    }

    private void refresh() {
        if (engine.isGameOver()) {
            autoTimer.stop();
            autoButton.setText("自动播放");
        }

        roundLabel.setText("当前回合: " + engine.getCurrentRound() + " / " + engine.getRoundLimitText());
        resultLabel.setText(engine.isGameOver()
                ? engine.getResultText()
                : "下一步会显示战士意图、行动动画和日志变化。 " + engine.getNoStateChangeWarningText());

        nextTurnButton.setEnabled(!engine.isGameOver());
        nextRoundButton.setEnabled(!engine.isGameOver());
        autoButton.setEnabled(!engine.isGameOver());

        refreshIntent();
        refreshLog();
        refreshScore();
        refreshStatus();
        refreshSelectedUnit();
        arenaPanel.repaint();
    }

    private void refreshIntent() {
        String text = engine.getLastIntentText()
                + "\n\n地图提示:\n"
                + "+ 绿色十字: 治疗点，每次恢复 8 HP\n"
                + "HP 道具: 立即恢复生命\n"
                + "红色三角: 增加攻击力\n"
                + "蓝色镜片: 增加攻击范围\n"
                + "深绿道具: 大量恢复生命\n"
                + "深红核心: 大幅增加攻击力\n"
                + "紫色核心: 恢复生命并增加攻击和范围\n\n"
                + "红队策略: 能补刀就补刀，否则优先发育抢道具，危险近身时撤退。";
        intentArea.setText(text);
        intentArea.setCaretPosition(0);
    }

    private void refreshLog() {
        ArrayList<String> logs = engine.getEventLog();
        String text = "";
        if (logs.size() == 0) {
            text = "比赛尚未开始行动。";
        } else {
            for (int i = 0; i < logs.size(); i++) {
                text = text + logs.get(i) + "\n";
            }
        }
        logArea.setText(text);
        logArea.setCaretPosition(logArea.getDocument().getLength());
    }

    private void refreshScore() {
        scoreArea.setText(engine.getScoreReport());
        scoreArea.setCaretPosition(0);
    }

    private void refreshStatus() {
        String text = "";
        ArrayList<Team> teams = engine.getTeams();
        for (int i = 0; i < teams.size(); i++) {
            Team team = teams.get(i);
            text = text + team.getName() + "\n";
            ArrayList<Unit> units = team.getUnits();
            for (int j = 0; j < units.size(); j++) {
                Unit unit = units.get(j);
                text = text
                        + "  #"
                        + unit.getId()
                        + " "
                        + unit.getName()
                        + " HP "
                        + unit.getHealth()
                        + "/"
                        + unit.getMaxHealth()
                        + " ATK "
                        + unit.getAttackPower()
                        + " RNG "
                        + unit.getRange()
                        + " "
                        + statusText(unit)
                        + "\n";
            }
            text = text + "\n";
        }
        statusArea.setText(text);
        statusArea.setCaretPosition(0);
    }

    private void refreshSelectedUnit() {
        Unit unit = findUnitById(selectedUnitId);
        if (unit == null) {
            selectedArea.setText("点击地图上的战士查看详情。");
            return;
        }

        String text = ""
                + "ID: " + unit.getId() + "\n"
                + "名称: " + unit.getName() + "\n"
                + "队伍: " + unit.getTeamName() + "\n"
                + "生命: " + unit.getHealth() + "/" + unit.getMaxHealth() + "\n"
                + "攻击力: " + unit.getAttackPower() + "\n"
                + "攻击范围: " + unit.getRange() + "\n"
                + "攻击加成: +" + unit.getAttackBonus() + "\n"
                + "范围加成: +" + unit.getRangeBonus() + "\n"
                + "位置: " + unit.getPosition() + "\n"
                + "状态: " + statusText(unit) + "\n\n"
                + "调试提示:\n"
                + "- 看它是否能靠近敌人\n"
                + "- 看低血量时是否撤退或治疗\n"
                + "- 看攻击目标是否符合策略";
        selectedArea.setText(text);
        selectedArea.setCaretPosition(0);
    }

    private Unit findUnitById(int unitId) {
        if (unitId < 0) {
            return null;
        }
        ArrayList<Team> teams = engine.getTeams();
        for (int i = 0; i < teams.size(); i++) {
            ArrayList<Unit> units = teams.get(i).getUnits();
            for (int j = 0; j < units.size(); j++) {
                Unit unit = units.get(j);
                if (unit.getId() == unitId) {
                    return unit;
                }
            }
        }
        return null;
    }

    private String statusText(Unit unit) {
        if (!unit.isAlive()) {
            return "defeated";
        }
        if (unit.isDefending()) {
            return "defending";
        }
        return "alive";
    }
}
