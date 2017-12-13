package com.pingao;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.Getter;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * Created by pingao on 2017/12/9.
 */
public class Board {
    public static final int N_ROW = 10;
    public static final int N_COL = 10;
    private static final char EMPTY_CHAR = '-';
    private static final int AVAILABLE_DISTANCE = 2;
    private static final Random RANDOM = new Random();
    private static final List<Pos> ALL_POS = buildPos();
    private static final List<List<Pos>> BANDS = buildBands();
    private static final long[][] HASH_TABLE = initHash();

    private final GameStatus status;
    private final char[][] grid;
    private final Player player1;
    private final Player player2;
    private long hash;

    public Board(Player player1, Player player2) {
        this.grid = initGrid();
        this.player1 = player1;
        this.player2 = player2;
        this.status = new GameStatus(Status.ONGOING, null, Collections.emptySet());
        this.hash = RANDOM.nextLong();
    }

    public Board(Board other) {
        this.player1 = other.player1;
        this.player2 = other.player2;
        this.grid = cloneArray(other.grid);
        this.status = new GameStatus(other.status.status, other.status.winner, other.status.winningSet);
        this.hash = other.hash;
    }

    private static char[][] cloneArray(char[][] src) {
        int length = src.length;
        char[][] target = new char[length][src[0].length];
        for (int i = 0; i < length; i++) {
            System.arraycopy(src[i], 0, target[i], 0, src[i].length);
        }
        return target;
    }

    private static long[][] initHash() {
        long[][] hash = new long[N_ROW * N_COL][2];
        for (int i = 0; i < N_ROW * N_COL; i++) {
            for (int j = 0; j < 2; j++) {
                hash[i][j] = RANDOM.nextLong();
            }
        }
        return hash;
    }

    private static List<Pos> buildPos() {
        List<Pos> poses = new ArrayList<>();
        for (int i = 0; i < N_ROW; i++) {
            for (int j = 0; j < N_COL; j++) {
                poses.add(new Pos(i, j));
            }
        }
        return poses;
    }

    private static List<List<Pos>> buildBands() {
        Map<Integer, List<Pos>> basket = new HashMap<>();
        int offset = 2 * (N_ROW + N_COL);
        for (int i = 0; i < N_ROW; i++) {
            for (int j = 0; j < N_COL; j++) {
                // row
                load(basket, i, new Pos(i, j));

                // col
                load(basket, j + offset, new Pos(i, j));

                // diagonal
                load(basket, i + j + 2 * offset, new Pos(i, j));
                load(basket, i - j + 3 * offset, new Pos(i, j));
            }
        }
        return basket.values().stream().filter(l -> l.size() > 1).collect(Collectors.toList());
    }

    private static void load(Map<Integer, List<Pos>> basket, int key, Pos pos) {
        List<Pos> band = basket.get(key);
        if (band == null) {
            basket.put(key, new ArrayList<>(Collections.singletonList(pos)));
        } else {
            band.add(pos);
        }
    }

    private char[][] initGrid() {
        char[][] grid = new char[N_ROW][N_COL];
        for (int i = 0; i < N_ROW; i++) {
            for (int j = 0; j < N_COL; j++) {
                grid[i][j] = EMPTY_CHAR;
            }
        }
        return grid;
    }

    public boolean mark(Pos pos, Player player) {
        if ((pos.row < 0 || pos.row > N_ROW - 1) || (pos.col < 0 || pos.col > N_COL - 1)) {
            System.out.println("Row must between 1 and " + N_ROW + ", Col must between 1 and " + N_COL);
            return false;
        }
        if (grid[pos.row][pos.col] != EMPTY_CHAR) {
            System.out.println(pos + "=" + grid[pos.row][pos.col] + " is not empty");
            return false;
        }
        grid[pos.row][pos.col] = player.marker;
        hash ^= HASH_TABLE[pos.index][player == player1 ? 0 : 1];
        check();
        return true;
    }

    private void check() {
        Map<Player, Set<Set<Pos>>> groups = scan();
        Set<Set<Pos>> groupsOfP1 = groups.get(player1);
        Set<Set<Pos>> groupsOfP2 = groups.get(player2);
        if (groupsOfP1.stream().anyMatch(g -> g.size() >= 5)) {
            status.status = Status.P1_WIN;
            status.winner = player1;
            status.winningSet = groupsOfP1.stream().filter(g -> g.size() >= 5).findFirst().orElse(Collections.emptySet());
        } else if (groupsOfP2.stream().anyMatch(g -> g.size() >= 5)) {
            status.status = Status.P2_WIN;
            status.winner = player2;
            status.winningSet = groupsOfP2.stream().filter(g -> g.size() >= 5).findFirst().orElse(Collections.emptySet());
        } else if (isDraw(grid)) {
            status.status = Status.DRAW;
        }
    }

    private Map<Player, Set<Set<Pos>>> scan() {
        Set<Set<Pos>> groupsOfP1 = new HashSet<>();
        Set<Set<Pos>> groupsOfP2 = new HashSet<>();
        for (List<Pos> band : BANDS) {
            Set<Pos> group1 = new HashSet<>();
            Set<Pos> group2 = new HashSet<>();
            for (int i = 0; i < band.size(); i++) {
                Pos pos = band.get(i);
                if (grid[pos.row][pos.col] == player1.marker) {
                    group1.add(pos);
                    // last one trigger
                    if (i == band.size() - 1) {
                        groupsOfP1.add(group1);
                    }
                } else {
                    if (!group1.isEmpty()) {
                        groupsOfP1.add(group1);
                        group1 = new HashSet<>();
                    }
                }
                if (grid[pos.row][pos.col] == player2.marker) {
                    group2.add(pos);
                    // last one trigger
                    if (i == band.size() - 1) {
                        groupsOfP2.add(group2);
                    }
                } else {
                    if (!group2.isEmpty()) {
                        groupsOfP2.add(group2);
                        group2 = new HashSet<>();
                    }
                }
            }
        }
        Map<Player, Set<Set<Pos>>> groups = new HashMap<>();
        groups.put(player1, groupsOfP1);
        groups.put(player2, groupsOfP2);
        return groups;
    }

    private boolean isDraw(char[][] board) {
        return ALL_POS.stream().noneMatch(p -> board[p.row][p.col] == EMPTY_CHAR);
    }

    public long hash() {
        return hash;
    }

    public GameStatus status() {
        return status;
    }

    public Set<Pos> getAvailablePos() {
        return ALL_POS.stream().filter(this::isPosValid).collect(Collectors.toSet());
    }

    private boolean isPosValid(Pos pos) {
        if (grid[pos.row][pos.col] != EMPTY_CHAR) {
            return false;
        }
        int rowL = pos.row - AVAILABLE_DISTANCE < 0 ? 0 : pos.row - AVAILABLE_DISTANCE;
        int colL = pos.col - AVAILABLE_DISTANCE < 0 ? 0 : pos.col - AVAILABLE_DISTANCE;
        int rowH = pos.row + AVAILABLE_DISTANCE > N_ROW ? N_ROW : pos.row + AVAILABLE_DISTANCE;
        int colH = pos.col + AVAILABLE_DISTANCE > N_COL ? N_COL : pos.col + AVAILABLE_DISTANCE;
        return IntStream.range(rowL, rowH).boxed().flatMap(i -> IntStream.range(colL, colH).mapToObj(j -> grid[i][j] != EMPTY_CHAR)).anyMatch(b -> b);
    }

    public int evaluate(Player player, int depth) {
        if (status.isWinning()) {
            return (player == status.winner) ? (Integer.MAX_VALUE - 1 - depth) : (Integer.MIN_VALUE + 1 + depth);
        } else if (status.isDraw()) {
            return 0;
        } else {
            Map<Player, Set<Set<Pos>>> groups = scan();
            Set<Set<Board.Pos>> groupsOfPlayer = groups.get(player);
            Set<Set<Board.Pos>> groupsOfOpponent = groups.get(opponent(player));
            return groupsOfPlayer.stream().mapToInt(g -> score(g, false)).sum() - groupsOfOpponent.stream().mapToInt(g -> score(g, true)).sum();
        }
    }

    private int score(Set<Pos> group, boolean isO) {
        int res = 0;
        if (group.size() == 1) {
            res = 1;
        } else if (group.size() == 2) {
            int open = getOpenCount(group);
            if (open == 2) {
                res = 2;
            } else if (open == 1) {
                res = 1;
            } else {
                res = 1;
            }
        } else if (group.size() == 3) {
            int open = getOpenCount(group);
            if (open == 2) {
                res = 2000;
            } else if (open == 1) {
                res = isO ? 200 : 20;
            } else {
                res = isO ? 20 : 2;
            }
        } else if (group.size() == 4) {
            int open = getOpenCount(group);
            if (open == 2) {
                res = isO ? 20000 : 10000;
            } else if (open == 1) {
                res = isO ? 10000 : 5000;
            } else {
                res = isO ? 2000 : 200;
            }
        } else if (group.size() == 5) {
            res = 1000000;
        }
        return res;
    }

    private int getOpenCount(Set<Pos> group) {
        List<Pos> poses = new ArrayList<>(group);
        poses.sort(Comparator.comparing(Pos::getIndex));
        Pos min = poses.get(0);
        Pos max = poses.get(poses.size() - 1);
        if (min.row == max.row) {
            return (min.col > 0 && grid[min.row][min.col - 1] == EMPTY_CHAR ? 1 : 0) + (max.col < N_COL - 1 && grid[min.row][max.col + 1] == EMPTY_CHAR ? 1 : 0);
        } else if (min.col == max.col) {
            return (min.row > 0 && grid[min.row - 1][min.col] == EMPTY_CHAR ? 1 : 0) + (max.row < N_ROW - 1 && grid[max.row + 1][min.col] == EMPTY_CHAR ? 1 : 0);
        } else {
            if (min.col < max.col) {
                return (min.row > 0 && min.col > 0 && grid[min.row - 1][min.col - 1] == EMPTY_CHAR ? 1 : 0) + (max.row < N_ROW - 1 && max.col < N_COL - 1 && grid[max.row + 1][max.col + 1] == EMPTY_CHAR ? 1 : 0);
            } else {
                return (min.row > 0 && min.col < N_COL - 1 && grid[min.row - 1][min.col + 1] == EMPTY_CHAR ? 1 : 0) + (max.row < N_ROW - 1 && max.col > 0 && grid[max.row + 1][max.col - 1] == EMPTY_CHAR ? 1 : 0);
            }
        }
    }

    public Player opponent(Player player) {
        return player == player1 ? player2 : player1;
    }

    public void start() {
        print();
        while (!status.isGameOver()) {
            player1.next(this);
            print();
            if (status.isGameOver()) {
                break;
            }
            player2.next(this);
            print();
        }
    }

    public void print() {
        System.out.println();
        System.out.println();
        System.out.println("#" + (player1.step() + player2.step()));
        System.out.println((player1.step() == player2.step() ? "*" : " ") + buildPlayerInfo(player1));
        System.out.println((player1.step() == player2.step() ? " " : "*") + buildPlayerInfo(player2));
        System.out.println();
        System.out.print("    ");
        for (int i = 0; i < N_COL; i++) {
            System.out.print((i + 1) + "   ");
        }
        System.out.println();
        for (int i = 0; i < N_ROW; i++) {
            System.out.print((i + 1) + (i == 9 ? "  " : "   "));
            for (int j = 0; j < N_COL; j++) {
                System.out.print(grid[i][j] + "   ");
            }
            System.out.println();
            System.out.println();
        }
        System.out.println();
        if (status.isGameOver()) {
            if (status.isWinning()) {
                System.out.println(status.winner + " is the WINNER(" + status.winningSet + "), congratulations!");
            } else {
                System.out.println("You both are so good, but game is draw!");
            }
            System.out.println("Summary:");
            double timesOfP1 = player1.times() * 1.0 / 1E9;
            double timesOfP2 = player2.times() * 1.0 / 1E9;
            System.out.printf("%s   Step: %d   Total Time: %3.1fs   Avg Time: %.1fs\n", "*" + player1, player1.step(), timesOfP1, timesOfP1 / player1.step());
            System.out.printf("%s   Step: %d   Total Time: %3.1fs   Avg Time: %.1fs\n", " " + player2, player2.step(), timesOfP2, timesOfP2 / player2.step());
        }
    }

    private String buildPlayerInfo(Player player) {
        return player + "  Step: " + player.step() + "  Last Pos: " + player.getLastPos();
    }

    public enum Status {P1_WIN, P2_WIN, DRAW, ONGOING}

    @Data
    public static class Pos {
        private final int row;
        private final int col;
        private final int index;

        Pos(int row, int col) {
            this.row = row;
            this.col = col;
            this.index = row * N_ROW + col;
        }

        @Override
        public String toString() {
            return "(" + (row + 1) + ", " + (col + 1) + ")";
        }
    }

    @Getter
    @AllArgsConstructor
    public static class GameStatus {
        private Status status;
        private Player winner;
        private Set<Pos> winningSet;

        public boolean isGameOver() {
            return status != Status.ONGOING;
        }

        public boolean isDraw() {
            return status == Status.DRAW;
        }

        public boolean isWinning() {
            return status == Status.P1_WIN || status == Status.P2_WIN;
        }
    }
}
