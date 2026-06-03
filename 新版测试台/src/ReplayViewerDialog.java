import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Window;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSlider;
import javax.swing.JSplitPane;
import javax.swing.JTextArea;
import javax.swing.Timer;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;

public class ReplayViewerDialog extends JDialog {
    private final ArrayList<ReplayMatch> matches;
    private final JComboBox<String> matchBox;
    private final ReplayPanel replayPanel;
    private final JTextArea infoArea;
    private final JSlider stepSlider;
    private final JButton prevButton;
    private final JButton nextButton;
    private final JButton playButton;
    private final Timer timer;
    private ReplayMatch currentMatch;
    private int currentIndex;
    private boolean sliderChanging;

    public ReplayViewerDialog(Window owner, ArrayList<ReplayMatch> matches, int selectedMatchNumber) {
        super(owner, "对局全局回放");
        this.matches = matches;
        this.matchBox = new JComboBox<String>();
        this.replayPanel = new ReplayPanel();
        this.infoArea = new JTextArea();
        this.stepSlider = new JSlider();
        this.prevButton = new JButton("上一步");
        this.nextButton = new JButton("下一步");
        this.playButton = new JButton("播放");
        this.currentMatch = null;
        this.currentIndex = 0;
        this.sliderChanging = false;
        this.timer = new Timer(360, new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent event) {
                playNextFrame();
            }
        });

        setModal(false);
        setSize(1100, 760);
        setLocationRelativeTo(owner);
        buildLayout();
        bindActions();
        fillMatches(selectedMatchNumber);
    }

    private void buildLayout() {
        JPanel root = new JPanel(new BorderLayout(10, 10));
        root.setBackground(new Color(246, 248, 250));
        root.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));
        setContentPane(root);

        JPanel topPanel = new JPanel(new BorderLayout(10, 0));
        topPanel.setBackground(new Color(246, 248, 250));
        JLabel matchLabel = new JLabel("选择对局");
        matchLabel.setFont(new Font("SansSerif", Font.BOLD, 13));
        topPanel.add(matchLabel, BorderLayout.WEST);
        topPanel.add(matchBox, BorderLayout.CENTER);
        root.add(topPanel, BorderLayout.NORTH);

        replayPanel.setPreferredSize(new Dimension(690, 640));
        infoArea.setEditable(false);
        infoArea.setLineWrap(true);
        infoArea.setWrapStyleWord(true);
        infoArea.setFont(new Font("Monospaced", Font.PLAIN, 12));
        infoArea.setBackground(new Color(253, 253, 251));
        infoArea.setForeground(new Color(35, 42, 52));
        infoArea.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));

        JSplitPane splitPane = new JSplitPane(
                JSplitPane.HORIZONTAL_SPLIT,
                replayPanel,
                new JScrollPane(infoArea));
        splitPane.setResizeWeight(0.68);
        splitPane.setBorder(BorderFactory.createEmptyBorder());
        root.add(splitPane, BorderLayout.CENTER);

        JPanel controlPanel = new JPanel(new BorderLayout(8, 0));
        controlPanel.setBackground(new Color(246, 248, 250));
        JPanel buttonPanel = new JPanel();
        buttonPanel.setBackground(new Color(246, 248, 250));
        buttonPanel.add(prevButton);
        buttonPanel.add(playButton);
        buttonPanel.add(nextButton);
        controlPanel.add(buttonPanel, BorderLayout.WEST);
        controlPanel.add(stepSlider, BorderLayout.CENTER);
        root.add(controlPanel, BorderLayout.SOUTH);
    }

    private void bindActions() {
        matchBox.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent event) {
                selectMatchFromBox();
            }
        });

        prevButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent event) {
                setFrame(currentIndex - 1);
            }
        });

        nextButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent event) {
                setFrame(currentIndex + 1);
            }
        });

        playButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent event) {
                togglePlay();
            }
        });

        stepSlider.addChangeListener(new ChangeListener() {
            @Override
            public void stateChanged(ChangeEvent event) {
                if (!sliderChanging && currentMatch != null) {
                    setFrame(stepSlider.getValue());
                }
            }
        });
    }

    private void fillMatches(int selectedMatchNumber) {
        int selectedIndex = 0;
        for (int i = 0; i < matches.size(); i++) {
            ReplayMatch match = matches.get(i);
            matchBox.addItem("第 " + match.getMatchNumber() + " 局 - " + match.getResultText());
            if (match.getMatchNumber() == selectedMatchNumber) {
                selectedIndex = i;
            }
        }
        if (matchBox.getItemCount() > 0) {
            matchBox.setSelectedIndex(selectedIndex);
            setCurrentMatch(matches.get(selectedIndex));
        }
    }

    private void selectMatchFromBox() {
        int index = matchBox.getSelectedIndex();
        if (index >= 0 && index < matches.size()) {
            stopPlaying();
            setCurrentMatch(matches.get(index));
        }
    }

    private void setCurrentMatch(ReplayMatch match) {
        currentMatch = match;
        currentIndex = 0;
        int maxIndex = Math.max(0, match.getSnapshotCount() - 1);
        sliderChanging = true;
        stepSlider.setMinimum(0);
        stepSlider.setMaximum(maxIndex);
        stepSlider.setValue(0);
        sliderChanging = false;
        setFrame(0);
    }

    private void setFrame(int index) {
        if (currentMatch == null) {
            return;
        }
        currentIndex = Math.max(0, Math.min(index, currentMatch.getSnapshotCount() - 1));
        ReplaySnapshot snapshot = currentMatch.getSnapshot(currentIndex);
        replayPanel.setSnapshot(currentMatch, snapshot);
        updateInfo(snapshot);

        sliderChanging = true;
        stepSlider.setValue(currentIndex);
        sliderChanging = false;
    }

    private void updateInfo(ReplaySnapshot snapshot) {
        if (currentMatch == null || snapshot == null) {
            infoArea.setText("");
            return;
        }

        String text = "第 "
                + currentMatch.getMatchNumber()
                + " 局  "
                + currentMatch.getResultText()
                + "\n"
                + "帧 "
                + (currentIndex + 1)
                + " / "
                + currentMatch.getSnapshotCount()
                + "，回合 "
                + snapshot.getRound()
                + "\n\n"
                + "本帧意图\n"
                + snapshot.getIntentText()
                + "\n\n"
                + "场上单位\n"
                + buildUnitText(snapshot)
                + "\n代码追踪\n"
                + snapshot.getTraceText();
        infoArea.setText(text);
        infoArea.setCaretPosition(0);
    }

    private String buildUnitText(ReplaySnapshot snapshot) {
        String text = "";
        ArrayList<ReplayUnitState> units = snapshot.getUnits();
        for (int i = 0; i < units.size(); i++) {
            ReplayUnitState unit = units.get(i);
            text = text
                    + "- "
                    + unit.getTeamName()
                    + "#"
                    + unit.getId()
                    + " "
                    + unit.getName()
                    + ": HP "
                    + unit.getHealth()
                    + "/"
                    + unit.getMaxHealth()
                    + ", ATK "
                    + unit.getAttackPower()
                    + ", RNG "
                    + unit.getRange()
                    + ", POS ("
                    + unit.getRow()
                    + ","
                    + unit.getCol()
                    + ")";
            if (!unit.isAlive()) {
                text = text + ", defeated";
            } else if (unit.isDefending()) {
                text = text + ", defending";
            }
            text = text + "\n";
        }
        return text;
    }

    private void togglePlay() {
        if (timer.isRunning()) {
            stopPlaying();
        } else {
            if (currentMatch != null && currentIndex >= currentMatch.getSnapshotCount() - 1) {
                setFrame(0);
            }
            timer.start();
            playButton.setText("暂停");
        }
    }

    private void playNextFrame() {
        if (currentMatch == null) {
            stopPlaying();
            return;
        }
        if (currentIndex >= currentMatch.getSnapshotCount() - 1) {
            stopPlaying();
            return;
        }
        setFrame(currentIndex + 1);
    }

    private void stopPlaying() {
        timer.stop();
        playButton.setText("播放");
    }
}
