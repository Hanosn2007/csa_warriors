import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;

public class BatchSimulationSummary {
    private final ArrayList<BatchTeamStats> teamStats;
    private final ArrayList<BatchMatchScoreDetail> matchDetails;
    private final ArrayList<ReplayMatch> replayMatches;
    private final String detailText;
    private final int requestedMatches;
    private final int completedMatches;
    private final int noWinnerMatches;
    private final int unfinishedMatches;

    public BatchSimulationSummary(
            ArrayList<BatchTeamStats> teamStats,
            ArrayList<BatchMatchScoreDetail> matchDetails,
            ArrayList<ReplayMatch> replayMatches,
            String detailText,
            int requestedMatches,
            int completedMatches,
            int noWinnerMatches,
            int unfinishedMatches) {
        this.teamStats = teamStats;
        this.matchDetails = matchDetails;
        this.replayMatches = replayMatches;
        this.detailText = detailText;
        this.requestedMatches = requestedMatches;
        this.completedMatches = completedMatches;
        this.noWinnerMatches = noWinnerMatches;
        this.unfinishedMatches = unfinishedMatches;
    }

    public ArrayList<BatchTeamStats> getTeamStats() {
        return teamStats;
    }

    public ArrayList<BatchMatchScoreDetail> getMatchDetails() {
        return new ArrayList<BatchMatchScoreDetail>(matchDetails);
    }

    public ArrayList<ReplayMatch> getReplayMatches() {
        return new ArrayList<ReplayMatch>(replayMatches);
    }

    public ReplayMatch findReplayMatch(int matchNumber) {
        for (int i = 0; i < replayMatches.size(); i++) {
            ReplayMatch match = replayMatches.get(i);
            if (match.getMatchNumber() == matchNumber) {
                return match;
            }
        }
        return null;
    }

    public String getDetailText() {
        return detailText;
    }

    public int getRequestedMatches() {
        return requestedMatches;
    }

    public int getCompletedMatches() {
        return completedMatches;
    }

    public int getNoWinnerMatches() {
        return noWinnerMatches;
    }

    public int getUnfinishedMatches() {
        return unfinishedMatches;
    }

    public String buildSortedScoreDetailText() {
        ArrayList<BatchMatchScoreDetail> sorted = getMatchDetails();
        Collections.sort(sorted, new Comparator<BatchMatchScoreDetail>() {
            @Override
            public int compare(BatchMatchScoreDetail first, BatchMatchScoreDetail second) {
                if (first.getTotalScore() != second.getTotalScore()) {
                    return second.getTotalScore() - first.getTotalScore();
                }
                if (first.getMatchNumber() != second.getMatchNumber()) {
                    return first.getMatchNumber() - second.getMatchNumber();
                }
                return first.getTeamName().compareTo(second.getTeamName());
            }
        });

        String text = "得分细节（按单局单队总分从高到低排序）\n"
                + "总分 = 伤害分 + 击败分 + 道具分 + 治疗分 + 生存分 + 存活分 + 胜利分 + 扣分。\n"
                + "扣分包括无效行动和连续无进展扣分；治疗量、伤害量是 HP 数值，不等同于得分。\n\n";

        for (int i = 0; i < sorted.size(); i++) {
            BatchMatchScoreDetail detail = sorted.get(i);
            text = text
                    + String.format("%03d", i + 1)
                    + ". 第 "
                    + detail.getMatchNumber()
                    + " 局"
                    + " | 回合 "
                    + detail.getRoundCount()
                    + " | "
                    + detail.getTeamName()
                    + " | "
                    + detail.getResultText()
                    + " | 总分 "
                    + detail.getTotalScore()
                    + "\n"
                    + "    伤害分 "
                    + detail.getDamageScore()
                    + "（造成 "
                    + detail.getDamageDealt()
                    + " HP），击败分 "
                    + detail.getDefeatScore()
                    + "（"
                    + detail.getDefeats()
                    + " 次），道具分 "
                    + detail.getPowerUpScore()
                    + "（"
                    + detail.getPowerUps()
                    + " 个，强力 "
                    + detail.getStrongPowerUps()
                    + " 个）\n"
                    + "    治疗分 "
                    + detail.getHealingScore()
                    + "（恢复 "
                    + detail.getHealingDone()
                    + " HP），生存分 "
                    + detail.getSurvivalScore()
                    + "，存活分 "
                    + detail.getFinalAliveScore()
                    + "，胜利分 "
                    + detail.getWinnerScore()
                    + "\n"
                    + "    扣分 "
                    + detail.getPenaltyScore()
                    + "（无效行动 "
                    + detail.getInvalidActions()
                    + " 次，无进展 "
                    + detail.getNoProgressPenalties()
                    + " 次），交战敌人 "
                    + detail.getEngagedEnemyCount()
                    + " 个\n\n";
        }

        return text;
    }
}
