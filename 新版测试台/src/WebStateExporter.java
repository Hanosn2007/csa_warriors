import java.util.ArrayList;

public class WebStateExporter {
    public static String engineState(GameEngine engine) {
        StringBuilder json = new StringBuilder();
        json.append("{");
        json.append("\"round\":").append(engine.getCurrentRound()).append(",");
        json.append("\"roundLimit\":\"").append(escape(engine.getRoundLimitText())).append("\",");
        json.append("\"gameOver\":").append(engine.isGameOver()).append(",");
        json.append("\"result\":\"").append(escape(engine.isGameOver() ? engine.getResultText() : engine.getNoStateChangeWarningText())).append("\",");
        json.append("\"winner\":").append(stringOrNull(engine.getWinnerName())).append(",");
        json.append("\"lastActingUnitId\":").append(engine.getLastActingUnitId()).append(",");
        json.append("\"lastIntent\":\"").append(escape(engine.getLastIntentText())).append("\",");
        json.append("\"lastDecisionTrace\":\"").append(escape(engine.getLastDecisionTraceText())).append("\",");
        json.append("\"map\":").append(map(engine.getMap())).append(",");
        json.append("\"teams\":").append(teams(engine.getTeams())).append(",");
        json.append("\"powerUps\":").append(powerUps(engine.getPowerUps())).append(",");
        json.append("\"scores\":").append(scores(engine.getTeamScores())).append(",");
        json.append("\"logs\":").append(strings(engine.getEventLog())).append(",");
        json.append("\"animation\":").append(animation(engine.getLastAnimation()));
        json.append("}");
        return json.toString();
    }

    public static String strategies(ArrayList<String> names) {
        return "{\"strategies\":" + strings(names) + "}";
    }

    public static String batchSummary(ArrayList<BatchTeamStats> stats, int matches, int noWinnerMatches, int unfinishedMatches) {
        StringBuilder json = new StringBuilder();
        json.append("{");
        json.append("\"matches\":").append(matches).append(",");
        json.append("\"noWinnerMatches\":").append(noWinnerMatches).append(",");
        json.append("\"unfinishedMatches\":").append(unfinishedMatches).append(",");
        json.append("\"teams\":[");
        for (int i = 0; i < stats.size(); i++) {
            BatchTeamStats stat = stats.get(i);
            if (i > 0) {
                json.append(",");
            }
            json.append("{");
            json.append("\"teamName\":\"").append(escape(stat.getTeamName())).append("\",");
            json.append("\"wins\":").append(stat.getWins()).append(",");
            json.append("\"winRate\":").append(number(stat.getWins() * 100.0 / Math.max(1, matches))).append(",");
            json.append("\"averageScore\":").append(number(stat.getAverageScore(matches))).append(",");
            json.append("\"bestScore\":").append(stat.getBestScore()).append(",");
            json.append("\"worstScore\":").append(stat.getWorstScore()).append(",");
            json.append("\"totalScore\":").append(stat.getTotalScore()).append(",");
            json.append("\"damageDealt\":").append(stat.getDamageDealt()).append(",");
            json.append("\"defeats\":").append(stat.getDefeats()).append(",");
            json.append("\"powerUps\":").append(stat.getPowerUps()).append(",");
            json.append("\"strongPowerUps\":").append(stat.getStrongPowerUps()).append(",");
            json.append("\"healingDone\":").append(stat.getHealingDone()).append(",");
            json.append("\"penaltyScore\":").append(stat.getPenaltyScore()).append(",");
            json.append("\"invalidActions\":").append(stat.getInvalidActions()).append(",");
            json.append("\"noProgressPenalties\":").append(stat.getNoProgressPenalties()).append(",");
            json.append("\"averageEngagedEnemies\":").append(number(stat.getAverageEngagedEnemies(matches)));
            json.append("}");
        }
        json.append("]}");
        return json.toString();
    }

    public static String projectFiles(ArrayList<WebDebugServer.ProjectFile> files) {
        StringBuilder json = new StringBuilder();
        json.append("{\"files\":[");
        for (int i = 0; i < files.size(); i++) {
            WebDebugServer.ProjectFile file = files.get(i);
            if (i > 0) {
                json.append(",");
            }
            json.append("{");
            json.append("\"group\":\"").append(escape(file.group)).append("\",");
            json.append("\"path\":\"").append(escape(file.path)).append("\",");
            json.append("\"description\":\"").append(escape(file.description)).append("\"");
            json.append("}");
        }
        json.append("]}");
        return json.toString();
    }

    private static String map(GameMap map) {
        StringBuilder json = new StringBuilder();
        json.append("{\"rows\":").append(map.getRows()).append(",\"cols\":").append(map.getCols()).append(",\"tiles\":[");
        for (int row = 0; row < map.getRows(); row++) {
            if (row > 0) {
                json.append(",");
            }
            json.append("[");
            for (int col = 0; col < map.getCols(); col++) {
                if (col > 0) {
                    json.append(",");
                }
                json.append("\"").append(map.getTile(new Position(row, col))).append("\"");
            }
            json.append("]");
        }
        json.append("]}");
        return json.toString();
    }

    private static String teams(ArrayList<Team> teams) {
        StringBuilder json = new StringBuilder();
        json.append("[");
        for (int i = 0; i < teams.size(); i++) {
            Team team = teams.get(i);
            if (i > 0) {
                json.append(",");
            }
            json.append("{\"name\":\"").append(escape(team.getName())).append("\",");
            json.append("\"symbol\":\"").append(escape(String.valueOf(team.getSymbol()))).append("\",");
            json.append("\"units\":[");
            ArrayList<Unit> units = team.getUnits();
            for (int j = 0; j < units.size(); j++) {
                Unit unit = units.get(j);
                if (j > 0) {
                    json.append(",");
                }
                json.append("{");
                json.append("\"id\":").append(unit.getId()).append(",");
                json.append("\"name\":\"").append(escape(unit.getName())).append("\",");
                json.append("\"teamName\":\"").append(escape(unit.getTeamName())).append("\",");
                json.append("\"row\":").append(unit.getRow()).append(",");
                json.append("\"col\":").append(unit.getCol()).append(",");
                json.append("\"health\":").append(unit.getHealth()).append(",");
                json.append("\"maxHealth\":").append(unit.getMaxHealth()).append(",");
                json.append("\"attackPower\":").append(unit.getAttackPower()).append(",");
                json.append("\"range\":").append(unit.getRange()).append(",");
                json.append("\"attackBonus\":").append(unit.getAttackBonus()).append(",");
                json.append("\"rangeBonus\":").append(unit.getRangeBonus()).append(",");
                json.append("\"alive\":").append(unit.isAlive()).append(",");
                json.append("\"defending\":").append(unit.isDefending());
                json.append("}");
            }
            json.append("]}");
        }
        json.append("]");
        return json.toString();
    }

    private static String powerUps(ArrayList<PowerUp> powerUps) {
        StringBuilder json = new StringBuilder();
        json.append("[");
        for (int i = 0; i < powerUps.size(); i++) {
            PowerUp powerUp = powerUps.get(i);
            if (i > 0) {
                json.append(",");
            }
            json.append("{");
            json.append("\"type\":\"").append(powerUp.getType()).append("\",");
            json.append("\"name\":\"").append(escape(powerUp.getDisplayName())).append("\",");
            json.append("\"shortName\":\"").append(escape(powerUp.getShortName())).append("\",");
            json.append("\"row\":").append(powerUp.getPosition().getRow()).append(",");
            json.append("\"col\":").append(powerUp.getPosition().getCol());
            json.append("}");
        }
        json.append("]");
        return json.toString();
    }

    private static String scores(ArrayList<TeamScore> scores) {
        StringBuilder json = new StringBuilder();
        json.append("[");
        for (int i = 0; i < scores.size(); i++) {
            TeamScore score = scores.get(i);
            if (i > 0) {
                json.append(",");
            }
            json.append("{");
            json.append("\"teamName\":\"").append(escape(score.getTeamName())).append("\",");
            json.append("\"totalScore\":").append(score.getTotalScore()).append(",");
            json.append("\"damageDealt\":").append(score.getDamageDealt()).append(",");
            json.append("\"defeats\":").append(score.getDefeats()).append(",");
            json.append("\"powerUps\":").append(score.getPowerUps()).append(",");
            json.append("\"strongPowerUps\":").append(score.getStrongPowerUps()).append(",");
            json.append("\"healingDone\":").append(score.getHealingDone()).append(",");
            json.append("\"survivalScore\":").append(score.getSurvivalScore()).append(",");
            json.append("\"penaltyScore\":").append(score.getPenaltyScore()).append(",");
            json.append("\"invalidActions\":").append(score.getInvalidActions()).append(",");
            json.append("\"noProgressPenalties\":").append(score.getNoProgressPenalties()).append(",");
            json.append("\"engagedEnemyCount\":").append(score.getEngagedEnemyCount());
            json.append("}");
        }
        json.append("]");
        return json.toString();
    }

    private static String animation(TurnAnimation animation) {
        if (animation == null) {
            return "null";
        }
        return "{\"type\":\""
                + escape(animation.getType())
                + "\",\"from\":"
                + position(animation.getFrom())
                + ",\"to\":"
                + position(animation.getTo())
                + "}";
    }

    private static String position(Position position) {
        return "{\"row\":" + position.getRow() + ",\"col\":" + position.getCol() + "}";
    }

    private static String strings(ArrayList<String> values) {
        StringBuilder json = new StringBuilder();
        json.append("[");
        for (int i = 0; i < values.size(); i++) {
            if (i > 0) {
                json.append(",");
            }
            json.append("\"").append(escape(values.get(i))).append("\"");
        }
        json.append("]");
        return json.toString();
    }

    private static String stringOrNull(String value) {
        if (value == null) {
            return "null";
        }
        return "\"" + escape(value) + "\"";
    }

    private static String number(double value) {
        return String.format(java.util.Locale.US, "%.2f", value);
    }

    public static String escape(String value) {
        if (value == null) {
            return "";
        }
        StringBuilder escaped = new StringBuilder();
        for (int i = 0; i < value.length(); i++) {
            char ch = value.charAt(i);
            if (ch == '\\' || ch == '"') {
                escaped.append('\\').append(ch);
            } else if (ch == '\n') {
                escaped.append("\\n");
            } else if (ch == '\r') {
                escaped.append("\\r");
            } else if (ch == '\t') {
                escaped.append("\\t");
            } else {
                escaped.append(ch);
            }
        }
        return escaped.toString();
    }
}
