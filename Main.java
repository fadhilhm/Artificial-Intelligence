import java.util.*;
import java.io.*;

public class Main {

    // map file pilihan utk tahap 1
    static final String[] MAP_FILES = {
            // "TestCases/n20_acak_01.txt",
            // "TestCases/n20_acak_02.txt",
            // "TestCases/n20_klaster_01.txt",
            // "TestCases/n20_labirin_01.txt",

            "TestCases/n40_acak_01.txt",
            "TestCases/n40_acak_02.txt",
            "TestCases/n40_klaster_01.txt",
            "TestCases/n40_labirin_01.txt",

            // "TestCases/n80_acak_01.txt",
            // "TestCases/n80_acak_02.txt",
            // "TestCases/n80_klaster_01.txt",
            // "TestCases/n80_labirin_01.txt",
    };

    // config genetic algo
    static final int POP_SIZE       = 100;
    static final int TOURN_SIZE     = 3;
    static final double PC          = 0.8;
    static final double PM          = 0.05;
    static final double ELITISM     = 0.20;

    // seed
    static final int NPM = 24056;
    static final int NUM_SEEDS = 30;

    // kondisi berhenti lain
    static final int MAX_STAGNANT = 200;

    // Menyimpan jumlah evaluasi dari runGA terakhir untuk CSV
    static int lastEvalCount;

    public static void main(String[] args) throws Exception {
        // java Main config.txt TestCases/xxx.txt       -> single run, grid dari file
        // java Main experiment out.csv                 -> eksperimen semua MAP_FILES
        //
        // Format config.txt: popSize pc pm elitism
        if (args[0].equalsIgnoreCase("experiment")) {
            runExperiment(args[1]);
        } else {
            runSingle(args[0], args[1]);
        }
    }

    // run sekali untuk percobaan
    static void runSingle(String configFile, String mapFile) throws Exception {
        Scanner cfg = new Scanner(new File(configFile));
        int popSize = cfg.nextInt();
        double pc = cfg.nextDouble();
        double pm = cfg.nextDouble();
        double elitism = cfg.nextDouble();
        cfg.close();

        int sizeNumber = sizeNumberFromFilename(mapFile);
        int budget = budgetForSize(sizeNumber);
        int maxGen = budget / popSize;

        Preprocessed prep = loadAndPreprocess(mapFile);

        long seed = System.currentTimeMillis();

        Individual best = runGA(prep, popSize, TOURN_SIZE, pc, pm, elitism, maxGen, seed);

        System.out.printf("%d %.5f%n", prep.p, best.getCost());
        for (int cellId : best.getChromosome()) {
            Point pt = prep.cellCoords[cellId];
            System.out.printf("%d %d%n", pt.col + 1, pt.row + 1);
        }
    }

    // loop dari map files, terus isi ke dalam csv
    static void runExperiment(String outputCsv) throws Exception {
        int[] seeds = new int[NUM_SEEDS];
        Random r = new Random(NPM);
        for (int i = 0; i < NUM_SEEDS; i++) {
            seeds[i] = r.nextInt(65537);
        }

        try (PrintWriter out = new PrintWriter(outputCsv)) {
            out.println("map,pop_size,pc,pm,elitism,cost,eval_count");

            for (String path : MAP_FILES) {
                Preprocessed prep = loadAndPreprocess(path);
                int sizeNumber = sizeNumberFromFilename(path);
                int budget = budgetForSize(sizeNumber);

                // maxGen dihitung dari budget / popSize
                int maxGen = budget / POP_SIZE;

                for (int seed : seeds) {
                    Individual best = runGA(prep, POP_SIZE, TOURN_SIZE,
                            PC, PM, ELITISM, maxGen, seed);

                    out.printf("%s,%d,%.2f,%.2f,%.2f,%.6f,%d%n",
                            new File(path).getName(),
                            POP_SIZE, PC, PM, ELITISM,
                            best.getCost(), lastEvalCount);
                }
            }
        }
        System.out.println("Selesai: " + outputCsv);
    }

    // helper
    static class Preprocessed {
        int m, n, p, penalty, numHouses, totalEmpty;
        Point[] cellCoords;
        int[][] distance;
    }

    // baca path untuk membuat map-nya dan banyak firestation
    static Preprocessed loadAndPreprocess(String path) throws Exception {
        Scanner sc = new Scanner(new File(path));
        int m = sc.nextInt(), n = sc.nextInt(), p = sc.nextInt();
        sc.nextLine();
        char[][] grid = new char[m][n];
        for (int r = 0; r < m; r++) {
            String line = sc.nextLine().trim();
            for (int c = 0; c < n; c++) grid[r][c] = line.charAt(c);
        }
        sc.close();
        return preprocess(grid, p);
    }

    // mengubah petak / sell (x,y) menjadi ID dan
    // melakukan flood fill dari house ke firestation untuk menyimpan jaraknya
    static Preprocessed preprocess(char[][] grid, int p) {
        int m = grid.length, n = grid[0].length;
        Preprocessed prep = new Preprocessed();
        prep.m = m; prep.n = n; prep.p = p; prep.penalty = m * n;

        int totalEmpty = 0;
        for (int r = 0; r < m; r++)
            for (int c = 0; c < n; c++)
                if (grid[r][c] == '.') totalEmpty++;
        prep.totalEmpty = totalEmpty;

        prep.cellCoords = new Point[totalEmpty + 1];
        int[][] cellID = new int[m][n];
        int id = 1;
        for (int r = 0; r < m; r++)
            for (int c = 0; c < n; c++)
                if (grid[r][c] == '.') {
                    cellID[r][c] = id;
                    prep.cellCoords[id] = new Point(r, c);
                    id++;
                }

        List<Point> houses = new ArrayList<>();
        for (int r = 0; r < m; r++)
            for (int c = 0; c < n; c++)
                if (grid[r][c] == 'H') houses.add(new Point(r, c));
        prep.numHouses = houses.size();

        // Matriks jarak precomputed: [numHouses][totalEmptyCells + 1]
        // Indeks 0 dummy, indeks 1..totalEmptyCells adalah ID sel kosong.
        prep.distance = new int[houses.size()][totalEmpty + 1];
        for (int h = 0; h < houses.size(); h++) {
            Point hp = houses.get(h);
            prep.distance[h] = BFS.houseFloodFill(
                    grid, hp.row, hp.col, cellID, totalEmpty);
        }
        return prep;
    }

    /**
     * Jalankan GA tepat maxGen generasi.
     */
    static Individual runGA(Preprocessed prep, int popSize, int tournSize,
                            double pc, double pm, double elitism,
                            int maxGen, long seed) {

        Population population = new Population(popSize);
        population.initializeRandomPopulation(prep.totalEmpty, prep.p, new Random(seed));

        GeneticAlgorithm ga = new GeneticAlgorithm(
                prep.distance, prep.numHouses, prep.totalEmpty, prep.p, prep.penalty,
                pc, pm, elitism, seed,
                new TournamentSelection(tournSize),
                new SinglePointCrossover(),
                new RandomReplacementMutation(),
                EvolutionModel.GENERATIONAL);

        // Evaluasi populasi awal
        ga.evaluatePopulation(population);
        population.sortByFitness();

        // Inisialisasi pelacak stagnasi dari populasi awal
        double bestFitness = population.getBestIndividual().getFitness();
        int stagnantCounter = 0;

        // Kondisi berhenti hingga sampai maxGen atau berhenti lebih awal jika stagnan
        for (int gen = 0; gen < maxGen; gen++) {
            ga.evolveStep(population);
            population.sortByFitness();

            double currentBest = population.getBestIndividual().getFitness();
            if (currentBest > bestFitness + 1e-12) {
                bestFitness = currentBest;
                stagnantCounter = 0;
            } else {
                stagnantCounter++;
                if (stagnantCounter >= MAX_STAGNANT) {
                    break; // berhenti karena stagnan
                }
            }
        }

        lastEvalCount = ga.getEvaluationCount();        
        return population.getBestIndividual();
    }

    /** Ambil angka ukuran dari nama file: "n40_labirin_04.txt" -> 40. */
    static int sizeNumberFromFilename(String path) {
        String name = new File(path).getName();
        int i = 1;
        while (i < name.length() && Character.isDigit(name.charAt(i))) i++;
        return Integer.parseInt(name.substring(1, i));
    }

    /** Evaluation budget berdasarkan ukuran grid. */
    static int budgetForSize(int size) {
        switch (size) {
            case 10: return 10000;
            case 20: return 50000;
            case 40: return 200000;
            case 80: return 500000;
            default: return 50000;
        }
    }
}