import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import java.awt.Desktop;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.lang.reflect.Method;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;

public class WebDebugServer {
    private static final int MAX_BATCH_MATCHES = 500;
    private static final int SAFETY_ROUND_LIMIT = 2000;

    private final HttpServer server;
    private final int port;
    private GameEngine engine;
    private MatchConfig activeConfig;
    private final HashMap<Integer, BatchJob> batchJobs;
    private int nextBatchJobId;

    public WebDebugServer(int port) throws IOException {
        this.port = port;
        this.engine = ArenaFactory.createDefaultEngine();
        this.activeConfig = null;
        this.batchJobs = new HashMap<Integer, BatchJob>();
        this.nextBatchJobId = 1;
        this.engine.setPrintLogs(false);
        this.server = HttpServer.create(new InetSocketAddress(port), 0);
        bindRoutes();
    }

    public void start() {
        server.start();
    }

    private void bindRoutes() {
        server.createContext("/api/state", new JsonHandler() {
            @Override
            protected String handleJson(HttpExchange exchange) {
                return WebStateExporter.engineState(engine);
            }
        });
        server.createContext("/api/step", new JsonHandler() {
            @Override
            protected String handleJson(HttpExchange exchange) {
                engine.stepTurn();
                return WebStateExporter.engineState(engine);
            }
        });
        server.createContext("/api/round", new JsonHandler() {
            @Override
            protected String handleJson(HttpExchange exchange) {
                engine.stepRound();
                return WebStateExporter.engineState(engine);
            }
        });
        server.createContext("/api/reset", new JsonHandler() {
            @Override
            protected String handleJson(HttpExchange exchange) {
                engine = createActiveEngine();
                engine.setPrintLogs(false);
                return WebStateExporter.engineState(engine);
            }
        });
        server.createContext("/api/strategies", new JsonHandler() {
            @Override
            protected String handleJson(HttpExchange exchange) {
                return WebStateExporter.strategies(MatchEngineFactory.findAvailableWarriorClasses());
            }
        });
        server.createContext("/api/load", new JsonHandler() {
            @Override
            protected String handleJson(HttpExchange exchange) throws IOException {
                String body = readBody(exchange);
                MatchConfig config = new MatchConfig(
                        readJsonString(body, "red"),
                        readJsonString(body, "blue"),
                        readJsonString(body, "green"),
                        readJsonString(body, "yellow"));
                activeConfig = config;
                engine = MatchEngineFactory.createEngine(config);
                engine.setPrintLogs(false);
                return WebStateExporter.engineState(engine);
            }
        });
        server.createContext("/api/batch", new JsonHandler() {
            @Override
            protected String handleJson(HttpExchange exchange) throws IOException {
                String body = readBody(exchange);
                int matches = Math.max(1, Math.min(MAX_BATCH_MATCHES, readJsonInt(body, "matches", 20)));
                MatchConfig config = new MatchConfig(
                        readJsonString(body, "red"),
                        readJsonString(body, "blue"),
                        readJsonString(body, "green"),
                        readJsonString(body, "yellow"));
                return runBatch(config, matches);
            }
        });
        server.createContext("/api/batch-default", new JsonHandler() {
            @Override
            protected String handleJson(HttpExchange exchange) throws IOException {
                String body = readBody(exchange);
                int matches = Math.max(1, Math.min(MAX_BATCH_MATCHES, readJsonInt(body, "matches", 20)));
                return runDefaultBatch(matches);
            }
        });
        server.createContext("/api/batch/start", new JsonHandler() {
            @Override
            protected String handleJson(HttpExchange exchange) throws IOException {
                return startBatchJob(readBody(exchange), false);
            }
        });
        server.createContext("/api/batch-default/start", new JsonHandler() {
            @Override
            protected String handleJson(HttpExchange exchange) throws IOException {
                return startBatchJob(readBody(exchange), true);
            }
        });
        server.createContext("/api/batch/status", new JsonHandler() {
            @Override
            protected String handleJson(HttpExchange exchange) throws IOException {
                return batchJobStatus(readBody(exchange));
            }
        });
        server.createContext("/api/project/files", new JsonHandler() {
            @Override
            protected String handleJson(HttpExchange exchange) {
                return projectFiles();
            }
        });
        server.createContext("/api/project/open", new JsonHandler() {
            @Override
            protected String handleJson(HttpExchange exchange) throws IOException {
                String body = readBody(exchange);
                String path = readJsonString(body, "path");
                File file = "root".equals(path) ? new File(".") : new File(path);
                openFile(file);
                return "{\"ok\":true,\"message\":\"opened\"}";
            }
        });
        server.createContext("/api/project/compile", new JsonHandler() {
            @Override
            protected String handleJson(HttpExchange exchange) {
                return compileProject();
            }
        });
        server.createContext("/api/project/compile-restart", new JsonHandler() {
            @Override
            protected String handleJson(HttpExchange exchange) {
                return compileAndRestart();
            }
        });
        server.createContext("/api/debug/path", new JsonHandler() {
            @Override
            protected String handleJson(HttpExchange exchange) throws IOException {
                return debugPath(readBody(exchange));
            }
        });
        server.createContext("/", new StaticHandler());
    }

    private GameEngine createActiveEngine() {
        if (activeConfig == null) {
            return ArenaFactory.createDefaultEngine();
        }
        return MatchEngineFactory.createEngine(activeConfig);
    }

    private String debugPath(String body) {
        int unitId = readJsonInt(body, "unitId", -1);
        int row = readJsonInt(body, "row", -1);
        int col = readJsonInt(body, "col", -1);
        Unit unit = findUnit(unitId);
        if (unit == null || !unit.isAlive()) {
            return debugUnavailable("没有找到可调试的存活单位。");
        }

        try {
            Warrior warrior = unit.getWarrior();
            UnitInfo self = unit.toInfo();
            GameState state = createGameState();
            if (row >= 0 && col >= 0) {
                Method method = warrior.getClass().getMethod("debugPath", GameState.class, UnitInfo.class, Position.class);
                Object value = method.invoke(warrior, state, self, new Position(row, col));
                if (value != null) {
                    return debugPathJson(value, unitId, row, col);
                }
                return debugUnavailable("debugPath 没有返回调试结果。");
            }

            Method method = warrior.getClass().getMethod("debugCostMap", GameState.class, UnitInfo.class);
            Object value = method.invoke(warrior, state, self);
            if (value instanceof int[][]) {
                return debugHeatmapJson((int[][]) value, unitId);
            }
            return debugUnavailable("debugCostMap 返回值不是 int[][]。");
        } catch (NoSuchMethodException exception) {
            return debugUnavailable("当前策略没有提供 A* 调试方法。");
        } catch (Exception exception) {
            return "{\"available\":false,\"message\":\""
                    + WebStateExporter.escape(exception.getMessage())
                    + "\"}";
        }
    }

    private Unit findUnit(int unitId) {
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

    private GameState createGameState() {
        ArrayList<UnitInfo> infos = new ArrayList<UnitInfo>();
        ArrayList<Team> teams = engine.getTeams();
        for (int i = 0; i < teams.size(); i++) {
            ArrayList<Unit> units = teams.get(i).getUnits();
            for (int j = 0; j < units.size(); j++) {
                infos.add(units.get(j).toInfo());
            }
        }

        ArrayList<Position> powerUpPositions = new ArrayList<Position>();
        ArrayList<PowerUp> powerUps = engine.getPowerUps();
        for (int i = 0; i < powerUps.size(); i++) {
            powerUpPositions.add(powerUps.get(i).getPosition());
        }
        return new GameState(engine.getMap().copyTiles(), infos, powerUpPositions);
    }

    private String debugHeatmapJson(int[][] costMap, int unitId) {
        CostRange range = costRange(costMap);
        return "{\"available\":true,"
                + "\"unitId\":" + unitId + ","
                + "\"reachable\":false,"
                + "\"totalCost\":-1,"
                + "\"target\":null,"
                + "\"minCost\":" + range.min + ","
                + "\"maxCost\":" + range.max + ","
                + "\"costMap\":" + intGrid(costMap) + ","
                + "\"path\":[]}";
    }

    private String debugPathJson(Object result, int unitId, int row, int col) throws Exception {
        Method getCostMap = result.getClass().getMethod("getCostMap");
        Method isReachable = result.getClass().getMethod("isReachable");
        Method getTotalCost = result.getClass().getMethod("getTotalCost");
        Method getPath = result.getClass().getMethod("getPath");
        int[][] costMap = (int[][]) getCostMap.invoke(result);
        boolean reachable = ((Boolean) isReachable.invoke(result)).booleanValue();
        int totalCost = ((Number) getTotalCost.invoke(result)).intValue();
        Object path = getPath.invoke(result);
        CostRange range = costRange(costMap);
        return "{\"available\":true,"
                + "\"unitId\":" + unitId + ","
                + "\"reachable\":" + reachable + ","
                + "\"totalCost\":" + totalCost + ","
                + "\"target\":{\"row\":" + row + ",\"col\":" + col + "},"
                + "\"minCost\":" + range.min + ","
                + "\"maxCost\":" + range.max + ","
                + "\"costMap\":" + intGrid(costMap) + ","
                + "\"path\":" + positions(path) + "}";
    }

    private String debugUnavailable(String message) {
        return "{\"available\":false,\"message\":\"" + WebStateExporter.escape(message) + "\"}";
    }

    private static CostRange costRange(int[][] grid) {
        int min = Integer.MAX_VALUE;
        int max = Integer.MIN_VALUE;
        for (int row = 0; row < grid.length; row++) {
            for (int col = 0; col < grid[row].length; col++) {
                int value = grid[row][col];
                if (value < 0) {
                    continue;
                }
                min = Math.min(min, value);
                max = Math.max(max, value);
            }
        }
        if (min == Integer.MAX_VALUE) {
            return new CostRange(0, 0);
        }
        return new CostRange(min, max);
    }

    private static String intGrid(int[][] grid) {
        StringBuilder json = new StringBuilder();
        json.append("[");
        for (int row = 0; row < grid.length; row++) {
            if (row > 0) {
                json.append(",");
            }
            json.append("[");
            for (int col = 0; col < grid[row].length; col++) {
                if (col > 0) {
                    json.append(",");
                }
                json.append(grid[row][col]);
            }
            json.append("]");
        }
        json.append("]");
        return json.toString();
    }

    private static String positions(Object pathValue) {
        if (!(pathValue instanceof ArrayList)) {
            return "[]";
        }
        ArrayList<?> path = (ArrayList<?>) pathValue;
        StringBuilder json = new StringBuilder();
        json.append("[");
        int written = 0;
        for (int i = 0; i < path.size(); i++) {
            Object value = path.get(i);
            if (!(value instanceof Position)) {
                continue;
            }
            if (written > 0) {
                json.append(",");
            }
            Position position = (Position) value;
            json.append("{\"row\":")
                    .append(position.getRow())
                    .append(",\"col\":")
                    .append(position.getCol())
                    .append("}");
            written++;
        }
        json.append("]");
        return json.toString();
    }

    private String runBatch(MatchConfig config, int matches) {
        ArrayList<BatchTeamStats> stats = createStats(config);
        int noWinnerMatches = 0;
        int unfinishedMatches = 0;

        for (int i = 0; i < matches; i++) {
            GameEngine matchEngine = MatchEngineFactory.createEngine(config);
            MatchOutcome outcome = runOneBatchMatch(matchEngine);
            applyBatchOutcome(stats, outcome);
            if (outcome.unfinished) {
                unfinishedMatches++;
            }
            if (outcome.noWinner) {
                noWinnerMatches++;
            }
        }

        return WebStateExporter.batchSummary(stats, matches, noWinnerMatches, unfinishedMatches);
    }

    private String runDefaultBatch(int matches) {
        ArrayList<BatchTeamStats> stats = createDefaultStats();
        int noWinnerMatches = 0;
        int unfinishedMatches = 0;

        for (int i = 0; i < matches; i++) {
            GameEngine matchEngine = ArenaFactory.createDefaultEngine();
            MatchOutcome outcome = runOneBatchMatch(matchEngine);
            applyBatchOutcome(stats, outcome);
            if (outcome.unfinished) {
                unfinishedMatches++;
            }
            if (outcome.noWinner) {
                noWinnerMatches++;
            }
        }

        return WebStateExporter.batchSummary(stats, matches, noWinnerMatches, unfinishedMatches);
    }

    private String startBatchJob(String body, boolean useDefaultConfig) {
        int matches = Math.max(1, Math.min(MAX_BATCH_MATCHES, readJsonInt(body, "matches", 20)));
        MatchConfig config = null;
        ArrayList<BatchTeamStats> stats;
        if (useDefaultConfig) {
            stats = createDefaultStats();
        } else {
            config = new MatchConfig(
                    readJsonString(body, "red"),
                    readJsonString(body, "blue"),
                    readJsonString(body, "green"),
                    readJsonString(body, "yellow"));
            stats = createStats(config);
        }

        final BatchJob job;
        synchronized (this) {
            job = new BatchJob(nextBatchJobId++, matches, useDefaultConfig, config, stats);
            batchJobs.put(job.id, job);
        }

        Thread worker = new Thread(new Runnable() {
            @Override
            public void run() {
                runBatchJob(job);
            }
        }, "web-batch-" + job.id);
        worker.setDaemon(true);
        worker.start();
        return "{\"jobId\":" + job.id + "}";
    }

    private String batchJobStatus(String body) {
        int jobId = readJsonInt(body, "jobId", -1);
        BatchJob job;
        synchronized (this) {
            job = batchJobs.get(jobId);
        }
        if (job == null) {
            return "{\"found\":false,\"message\":\"batch job not found\"}";
        }
        synchronized (job) {
            return batchJobJson(job);
        }
    }

    private void runBatchJob(BatchJob job) {
        try {
            for (int i = 0; i < job.totalMatches; i++) {
                GameEngine matchEngine = job.useDefaultConfig
                        ? ArenaFactory.createDefaultEngine()
                        : MatchEngineFactory.createEngine(job.config);
                MatchOutcome outcome;
                synchronized (job) {
                    if (job.cancelled) {
                        job.done = true;
                        return;
                    }
                }
                outcome = runOneBatchMatch(matchEngine);
                synchronized (job) {
                    applyBatchOutcome(job.stats, outcome);
                    if (outcome.unfinished) {
                        job.unfinishedMatches++;
                    }
                    if (outcome.noWinner) {
                        job.noWinnerMatches++;
                    }
                    job.completedMatches++;
                }
            }
        } catch (Throwable throwable) {
            synchronized (job) {
                job.error = throwable.toString();
            }
        } finally {
            synchronized (job) {
                job.done = true;
            }
        }
    }

    private MatchOutcome runOneBatchMatch(GameEngine matchEngine) {
        matchEngine.setPrintLogs(false);
        matchEngine.setStoreLogs(false);
        while (!matchEngine.isGameOver() && matchEngine.getCurrentRound() <= SAFETY_ROUND_LIMIT) {
            matchEngine.stepRound();
        }

        boolean unfinished = !matchEngine.isGameOver();
        String winner = matchEngine.getWinnerName();
        boolean noWinner = winner == null && matchEngine.isGameOver();
        return new MatchOutcome(winner, matchEngine.getTeamScores(), noWinner, unfinished);
    }

    private void applyBatchOutcome(ArrayList<BatchTeamStats> stats, MatchOutcome outcome) {
        addScores(stats, outcome.scores);
        if (outcome.winnerName != null) {
            BatchTeamStats winningStats = findStats(stats, outcome.winnerName);
            if (winningStats != null) {
                winningStats.addWin();
            }
        }
    }

    private String batchJobJson(BatchJob job) {
        int completed = Math.max(0, job.completedMatches);
        String summary = WebStateExporter.batchSummary(
                job.stats,
                completed,
                job.noWinnerMatches,
                job.unfinishedMatches);
        return "{\"found\":true,"
                + "\"jobId\":" + job.id + ","
                + "\"completed\":" + completed + ","
                + "\"total\":" + job.totalMatches + ","
                + "\"done\":" + job.done + ","
                + "\"error\":\"" + WebStateExporter.escape(job.error) + "\","
                + "\"result\":" + summary
                + "}";
    }

    private ArrayList<BatchTeamStats> createStats(MatchConfig config) {
        GameEngine sample = MatchEngineFactory.createEngine(config);
        ArrayList<BatchTeamStats> stats = new ArrayList<BatchTeamStats>();
        ArrayList<Team> teams = sample.getTeams();
        for (int i = 0; i < teams.size(); i++) {
            stats.add(new BatchTeamStats(teams.get(i).getName()));
        }
        return stats;
    }

    private ArrayList<BatchTeamStats> createDefaultStats() {
        GameEngine sample = ArenaFactory.createDefaultEngine();
        ArrayList<BatchTeamStats> stats = new ArrayList<BatchTeamStats>();
        ArrayList<Team> teams = sample.getTeams();
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

    private String projectFiles() {
        ArrayList<ProjectFile> files = new ArrayList<ProjectFile>();
        addIfExists(files, "使用指南", "Guide.html", "项目规则、运行方式、评分规则和提交内容。");
        addIfExists(files, "项目说明", "README.html", "项目整体说明。");
        addIfExists(files, "评分说明", "ScoringGuide.html", "评分项和扣分规则。");

        File srcDir = new File("src");
        File[] javaFiles = srcDir.listFiles(new FilenameFilter() {
            @Override
            public boolean accept(File dir, String name) {
                return name.endsWith(".java");
            }
        });
        if (javaFiles != null) {
            Arrays.sort(javaFiles);
            for (int i = 0; i < javaFiles.length; i++) {
                File file = javaFiles[i];
                String relativePath = "src/" + file.getName();
                if (isStrategySource(file)) {
                    addIfExists(files, "策略", relativePath, "可运行 Warrior 策略类。");
                } else {
                    addIfExists(files, "源码", relativePath, "项目 Java 源码。");
                }
            }
        }
        return WebStateExporter.projectFiles(files);
    }

    private void addIfExists(ArrayList<ProjectFile> files, String group, String path, String description) {
        File file = new File(path);
        if (file.exists()) {
            files.add(new ProjectFile(group, path, description));
        }
    }

    private boolean isStrategySource(File file) {
        try {
            String content = new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
            return content.contains("extends Warrior") && !file.getName().equals("Warrior.java");
        } catch (Exception exception) {
            return false;
        }
    }

    private void openFile(File file) {
        try {
            if (!Desktop.isDesktopSupported()) {
                throw new IllegalStateException("Desktop.open is not supported.");
            }
            Desktop.getDesktop().open(file);
        } catch (Exception exception) {
            throw new IllegalStateException(exception.getMessage());
        }
    }

    private String compileProject() {
        CommandResult result = compileProjectCommand("编译完成。Web 服务当前进程不会自动热重载新 class；重启 WebDebugMain 后使用新代码。\\n");
        return "{\"exitCode\":"
                + result.exitCode
                + ",\"output\":\""
                + WebStateExporter.escape(result.output)
                + "\"}";
    }

    private String compileAndRestart() {
        CommandResult result = compileProjectCommand("编译完成。Web 调试台正在重启，请稍后刷新页面。\\n");
        if (result.exitCode == 0) {
            restartWebServerSoon();
        }
        return "{\"exitCode\":"
                + result.exitCode
                + ",\"output\":\""
                + WebStateExporter.escape(result.output)
                + "\"}";
    }

    private void restartWebServerSoon() {
        Thread thread = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    Thread.sleep(1000);
                    server.stop(0);
                    ArrayList<String> command = restartCommand();
                    ProcessBuilder builder = new ProcessBuilder(command);
                    builder.directory(new File("."));
                    builder.start();
                } catch (Exception exception) {
                    System.out.println("Web restart failed: " + exception.getMessage());
                }
                System.exit(0);
            }
        });
        thread.setDaemon(false);
        thread.start();
    }

    private ArrayList<String> restartCommand() {
        ArrayList<String> command = new ArrayList<String>();
        command.add(findJavaTool("java"));
        command.add("-cp");
        command.add("out");
        command.add("WebDebugMain");
        command.add(String.valueOf(port));
        return command;
    }

    private CommandResult compileProjectCommand(String successMessage) {
        File srcDir = new File("src");
        File outDir = new File("out");
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
        return runCommand(command, successMessage);
    }

    private CommandResult runCommand(ArrayList<String> command, String successMessage) {
        StringBuilder output = new StringBuilder();
        try {
            ProcessBuilder builder = new ProcessBuilder(command);
            builder.directory(new File("."));
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

    private static String readBody(HttpExchange exchange) throws IOException {
        InputStream input = exchange.getRequestBody();
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[1024];
        int read;
        while ((read = input.read(buffer)) != -1) {
            output.write(buffer, 0, read);
        }
        return new String(output.toByteArray(), StandardCharsets.UTF_8);
    }

    private static String readJsonString(String body, String key) {
        String marker = "\"" + key + "\"";
        int keyIndex = body.indexOf(marker);
        if (keyIndex < 0) {
            return "";
        }
        int colon = body.indexOf(':', keyIndex + marker.length());
        int firstQuote = body.indexOf('"', colon + 1);
        int secondQuote = body.indexOf('"', firstQuote + 1);
        if (colon < 0 || firstQuote < 0 || secondQuote < 0) {
            return "";
        }
        return body.substring(firstQuote + 1, secondQuote);
    }

    private static int readJsonInt(String body, String key, int fallback) {
        String marker = "\"" + key + "\"";
        int keyIndex = body.indexOf(marker);
        if (keyIndex < 0) {
            return fallback;
        }
        int colon = body.indexOf(':', keyIndex + marker.length());
        if (colon < 0) {
            return fallback;
        }
        int start = colon + 1;
        while (start < body.length() && Character.isWhitespace(body.charAt(start))) {
            start++;
        }
        int end = start;
        while (end < body.length() && Character.isDigit(body.charAt(end))) {
            end++;
        }
        if (start == end) {
            return fallback;
        }
        return Integer.parseInt(body.substring(start, end));
    }

    private abstract static class JsonHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            try {
                if ("OPTIONS".equals(exchange.getRequestMethod())) {
                    write(exchange, 204, "application/json", "");
                    return;
                }
                write(exchange, 200, "application/json", handleJson(exchange));
            } catch (Throwable throwable) {
                write(exchange, 500, "application/json", "{\"error\":\"" + WebStateExporter.escape(throwable.toString()) + "\"}");
            }
        }

        protected abstract String handleJson(HttpExchange exchange) throws IOException;
    }

    private static class StaticHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            String path = exchange.getRequestURI().getPath();
            if ("/".equals(path)) {
                path = "/index.html";
            }
            if (path.contains("..")) {
                write(exchange, 403, "text/plain", "Forbidden");
                return;
            }
            File file = new File("web", path.substring(1));
            if (!file.exists() || file.isDirectory()) {
                write(exchange, 404, "text/plain", "Not found");
                return;
            }
            write(exchange, 200, contentType(file.getName()), readFile(file));
        }
    }

    private static byte[] readFile(File file) throws IOException {
        FileInputStream input = new FileInputStream(file);
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[4096];
        int read;
        try {
            while ((read = input.read(buffer)) != -1) {
                output.write(buffer, 0, read);
            }
        } finally {
            input.close();
        }
        return output.toByteArray();
    }

    private static String contentType(String name) {
        if (name.endsWith(".html")) {
            return "text/html; charset=utf-8";
        }
        if (name.endsWith(".css")) {
            return "text/css; charset=utf-8";
        }
        if (name.endsWith(".js")) {
            return "application/javascript; charset=utf-8";
        }
        return "application/octet-stream";
    }

    private static void write(HttpExchange exchange, int status, String type, String body) throws IOException {
        write(exchange, status, type, body.getBytes(StandardCharsets.UTF_8));
    }

    private static void write(HttpExchange exchange, int status, String type, byte[] body) throws IOException {
        exchange.getResponseHeaders().set("Content-Type", type);
        exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
        exchange.getResponseHeaders().set("Access-Control-Allow-Methods", "GET,POST,OPTIONS");
        exchange.getResponseHeaders().set("Access-Control-Allow-Headers", "Content-Type");
        exchange.sendResponseHeaders(status, body.length);
        OutputStream output = exchange.getResponseBody();
        output.write(body);
        output.close();
    }

    public static class ProjectFile {
        public final String group;
        public final String path;
        public final String description;

        public ProjectFile(String group, String path, String description) {
            this.group = group;
            this.path = path;
            this.description = description;
        }
    }

    private static class CommandResult {
        private final int exitCode;
        private final String output;

        public CommandResult(int exitCode, String output) {
            this.exitCode = exitCode;
            this.output = output;
        }
    }

    private static class CostRange {
        private final int min;
        private final int max;

        public CostRange(int min, int max) {
            this.min = min;
            this.max = max;
        }
    }

    private static class BatchJob {
        private final int id;
        private final int totalMatches;
        private final boolean useDefaultConfig;
        private final MatchConfig config;
        private final ArrayList<BatchTeamStats> stats;
        private int completedMatches;
        private int noWinnerMatches;
        private int unfinishedMatches;
        private boolean done;
        private boolean cancelled;
        private String error;

        public BatchJob(int id, int totalMatches, boolean useDefaultConfig, MatchConfig config, ArrayList<BatchTeamStats> stats) {
            this.id = id;
            this.totalMatches = totalMatches;
            this.useDefaultConfig = useDefaultConfig;
            this.config = config;
            this.stats = stats;
            this.completedMatches = 0;
            this.noWinnerMatches = 0;
            this.unfinishedMatches = 0;
            this.done = false;
            this.cancelled = false;
            this.error = "";
        }
    }

    private static class MatchOutcome {
        private final String winnerName;
        private final ArrayList<TeamScore> scores;
        private final boolean noWinner;
        private final boolean unfinished;

        public MatchOutcome(String winnerName, ArrayList<TeamScore> scores, boolean noWinner, boolean unfinished) {
            this.winnerName = winnerName;
            this.scores = scores;
            this.noWinner = noWinner;
            this.unfinished = unfinished;
        }
    }
}
