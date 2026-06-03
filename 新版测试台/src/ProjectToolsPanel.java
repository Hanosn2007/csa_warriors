import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Desktop;
import java.awt.Font;
import java.awt.GridLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.BufferedReader;
import java.io.File;
import java.io.FilenameFilter;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import javax.swing.BorderFactory;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.ListSelectionModel;
import javax.swing.SwingUtilities;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;

public class ProjectToolsPanel extends JPanel {
    private final ArenaFrame owner;
    private final File projectRoot;
    private final DefaultListModel<ProjectFileItem> fileModel;
    private final JList<ProjectFileItem> fileList;
    private final JTextArea helpArea;
    private final JTextArea outputArea;
    private final JButton openButton;
    private final JButton refreshButton;
    private final JButton openFolderButton;
    private final JButton compileButton;
    private final JButton compileRestartButton;

    public ProjectToolsPanel(ArenaFrame owner) {
        super(new BorderLayout(8, 8));
        this.owner = owner;
        this.projectRoot = new File(System.getProperty("user.dir"));

        fileModel = new DefaultListModel<ProjectFileItem>();
        fileList = new JList<ProjectFileItem>(fileModel);
        fileList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        fileList.setFont(new Font("SansSerif", Font.PLAIN, 13));

        helpArea = createTextArea();
        outputArea = createTextArea();
        outputArea.setFont(new Font("Monospaced", Font.PLAIN, 12));

        openButton = new JButton("打开选中文件");
        refreshButton = new JButton("刷新列表");
        openFolderButton = new JButton("打开项目文件夹");
        compileButton = new JButton("编译代码");
        compileRestartButton = new JButton("编译并重启界面");

        buildLayout();
        bindActions();
        refreshFiles();
        showDefaultHelp();
    }

    private void buildLayout() {
        setBackground(new Color(246, 248, 250));
        setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));

        JPanel buttonPanel = new JPanel(new GridLayout(3, 2, 6, 6));
        buttonPanel.setBackground(new Color(246, 248, 250));
        buttonPanel.add(openButton);
        buttonPanel.add(refreshButton);
        buttonPanel.add(openFolderButton);
        buttonPanel.add(compileButton);
        buttonPanel.add(compileRestartButton);

        JPanel centerPanel = new JPanel(new GridLayout(2, 1, 0, 8));
        centerPanel.setBackground(new Color(246, 248, 250));
        centerPanel.add(new JScrollPane(fileList));
        centerPanel.add(new JScrollPane(helpArea));

        add(buttonPanel, BorderLayout.NORTH);
        add(centerPanel, BorderLayout.CENTER);
        add(new JScrollPane(outputArea), BorderLayout.SOUTH);
    }

    private JTextArea createTextArea() {
        JTextArea area = new JTextArea();
        area.setEditable(false);
        area.setLineWrap(true);
        area.setWrapStyleWord(true);
        area.setBackground(new Color(253, 253, 251));
        area.setForeground(new Color(35, 42, 52));
        area.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        return area;
    }

    private void bindActions() {
        fileList.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent event) {
                if (event.getClickCount() == 2) {
                    openSelectedFile();
                }
            }
        });

        fileList.addListSelectionListener(new ListSelectionListener() {
            @Override
            public void valueChanged(ListSelectionEvent event) {
                if (!event.getValueIsAdjusting()) {
                    showSelectedHelp();
                }
            }
        });

        openButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent event) {
                openSelectedFile();
            }
        });

        refreshButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent event) {
                refreshFiles();
            }
        });

        openFolderButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent event) {
                openFile(projectRoot);
            }
        });

        compileButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent event) {
                compileInBackground(false);
            }
        });

        compileRestartButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent event) {
                compileInBackground(true);
            }
        });
    }

    private void refreshFiles() {
        fileModel.clear();
        addIfExists("你的策略模板", "src/TeamAlphaWarrior.java", "主要修改这个文件中的策略区域。");
        addWarriorExamples();
        addIfExists("使用指南", "Guide.html", "查看项目规则、运行方式、评分规则和提交内容。");
        addIfExists("项目说明", "README.html", "查看项目整体说明。");
        if (fileModel.size() > 0) {
            fileList.setSelectedIndex(0);
        } else {
            showDefaultHelp();
        }
        outputArea.setText("已加载项目文件列表。双击文件即可用系统默认软件打开。\n");
    }

    private void addWarriorExamples() {
        File srcDir = new File(projectRoot, "src");
        File[] javaFiles = srcDir.listFiles(new FilenameFilter() {
            @Override
            public boolean accept(File dir, String name) {
                File file = new File(dir, name);
                return name.endsWith(".java")
                        && !name.equals("Warrior.java")
                        && !name.equals("TeamAlphaWarrior.java")
                        && isStrategySource(file);
            }
        });
        if (javaFiles == null) {
            return;
        }

        Arrays.sort(javaFiles);
        for (int i = 0; i < javaFiles.length; i++) {
            String relativePath = "src/" + javaFiles[i].getName();
            addIfExists("示例/策略", relativePath, "阅读或打开这个策略类，观察它如何返回 Action。");
        }
    }

    private boolean isStrategySource(File file) {
        try {
            String content = new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
            return content.contains("extends Warrior");
        } catch (Exception exception) {
            return false;
        }
    }

    private void addIfExists(String group, String relativePath, String description) {
        File file = new File(projectRoot, relativePath);
        if (file.exists()) {
            fileModel.addElement(new ProjectFileItem(group, relativePath, file, description));
        }
    }

    private void showDefaultHelp() {
        helpArea.setText("项目文件\n\n"
                + "1. 双击文件，用系统默认编辑软件打开。\n"
                + "2. 修改策略后点击“编译代码”。\n"
                + "3. 编译成功后点击“编译并重启界面”，加载新的策略代码。\n\n"
                + "如果系统没有给 .java 文件设置默认编辑软件，可以先安装并打开 VS Code。");
    }

    private void showSelectedHelp() {
        ProjectFileItem item = fileList.getSelectedValue();
        if (item == null) {
            showDefaultHelp();
            return;
        }
        helpArea.setText(item.getGroup()
                + "\n\n"
                + item.getRelativePath()
                + "\n\n"
                + item.getDescription()
                + "\n\n修改保存后需要重新编译。当前正在运行的界面要重启后才会加载新代码。");
    }

    private void openSelectedFile() {
        ProjectFileItem item = fileList.getSelectedValue();
        if (item == null) {
            JOptionPane.showMessageDialog(this, "请先选择一个文件。");
            return;
        }
        openFile(item.getFile());
    }

    private void openFile(File file) {
        try {
            if (!Desktop.isDesktopSupported()) {
                throw new IllegalStateException("当前系统不支持 Desktop.open。");
            }
            Desktop.getDesktop().open(file);
            outputArea.setText("已打开: " + file.getPath() + "\n");
        } catch (Exception exception) {
            outputArea.setText("无法打开文件:\n" + file.getPath() + "\n\n" + exception.getMessage());
            JOptionPane.showMessageDialog(this, "无法打开文件，请在文件管理器中手动打开。");
        }
    }

    private void compileInBackground(final boolean restartAfterSuccess) {
        setButtonsEnabled(false);
        outputArea.setText("正在编译，请稍等...\n");

        Thread thread = new Thread(new Runnable() {
            @Override
            public void run() {
                final CommandResult result = compileProject();
                SwingUtilities.invokeLater(new Runnable() {
                    @Override
                    public void run() {
                        setButtonsEnabled(true);
                        outputArea.setText(result.output);
                        if (result.exitCode == 0 && restartAfterSuccess) {
                            restartUi();
                        }
                    }
                });
            }
        });
        thread.start();
    }

    private void setButtonsEnabled(boolean enabled) {
        openButton.setEnabled(enabled);
        refreshButton.setEnabled(enabled);
        openFolderButton.setEnabled(enabled);
        compileButton.setEnabled(enabled);
        compileRestartButton.setEnabled(enabled);
    }

    private CommandResult compileProject() {
        File srcDir = new File(projectRoot, "src");
        File outDir = new File(projectRoot, "out");
        if (!outDir.exists()) {
            outDir.mkdirs();
        }

        File[] javaFiles = srcDir.listFiles(new FilenameFilter() {
            @Override
            public boolean accept(File dir, String name) {
                return name.endsWith(".java");
            }
        });
        if (javaFiles == null || javaFiles.length == 0) {
            return new CommandResult(1, "没有找到 src 目录下的 Java 文件。\n");
        }
        Arrays.sort(javaFiles);

        ArrayList<String> command = new ArrayList<String>();
        command.add(findJavaTool("javac"));
        command.add("--release");
        command.add("17");
        command.add("-encoding");
        command.add("UTF-8");
        command.add("-d");
        command.add("out");
        for (int i = 0; i < javaFiles.length; i++) {
            command.add(javaFiles[i].getPath());
        }

        return runCommand(command, "编译完成。新的代码会在界面重启后生效。\n");
    }

    private void restartUi() {
        try {
            ArrayList<String> command = new ArrayList<String>();
            command.add(findJavaTool("java"));
            command.add("-cp");
            command.add("out");
            command.add("ArenaUiMain");

            ProcessBuilder builder = new ProcessBuilder(command);
            builder.directory(projectRoot);
            builder.start();
            owner.dispose();
            System.exit(0);
        } catch (Exception exception) {
            outputArea.setText(outputArea.getText()
                    + "\n编译成功，但无法自动重启界面。\n"
                    + exception.getMessage()
                    + "\n");
        }
    }

    private CommandResult runCommand(ArrayList<String> command, String successMessage) {
        StringBuilder output = new StringBuilder();
        try {
            ProcessBuilder builder = new ProcessBuilder(command);
            builder.directory(projectRoot);
            builder.redirectErrorStream(true);
            Process process = builder.start();

            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8));
            String line = reader.readLine();
            while (line != null) {
                output.append(line).append("\n");
                line = reader.readLine();
            }
            int exitCode = process.waitFor();
            if (exitCode == 0) {
                output.append(successMessage);
            } else {
                output.append("编译失败。请根据上面的错误信息修改代码。\n");
            }
            return new CommandResult(exitCode, output.toString());
        } catch (Exception exception) {
            output.append("命令执行失败:\n").append(exception.getMessage()).append("\n");
            return new CommandResult(1, output.toString());
        }
    }

    private String findJavaTool(String toolName) {
        String javaHome = System.getProperty("java.home");
        String executable = toolName;
        if (System.getProperty("os.name").toLowerCase().contains("win")) {
            executable = toolName + ".exe";
        }

        File tool = new File(new File(javaHome, "bin"), executable);
        if (tool.exists()) {
            return tool.getPath();
        }
        return executable;
    }

    private static class CommandResult {
        private final int exitCode;
        private final String output;

        public CommandResult(int exitCode, String output) {
            this.exitCode = exitCode;
            this.output = output;
        }
    }

    private static class ProjectFileItem {
        private final String group;
        private final String relativePath;
        private final File file;
        private final String description;

        public ProjectFileItem(String group, String relativePath, File file, String description) {
            this.group = group;
            this.relativePath = relativePath;
            this.file = file;
            this.description = description;
        }

        public String getGroup() {
            return group;
        }

        public String getRelativePath() {
            return relativePath;
        }

        public File getFile() {
            return file;
        }

        public String getDescription() {
            return description;
        }

        @Override
        public String toString() {
            return group + " - " + relativePath;
        }
    }
}
