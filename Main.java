import java.util.*;

import model.*;
import model.Individual.ChromosomeType;
import mutation.*;
import selection.*;
import crossover.*;

import java.io.*;

public class Main {

    // map file pilihan utk tahap 1
    static final String[] MAP_FILES = {
            "TestCases/n20_acak_01.txt",
            "TestCases/n20_acak_02.txt",
            "TestCases/n20_klaster_01.txt",
            "TestCases/n20_labirin_01.txt",

            "TestCases/n40_acak_01.txt",
            "TestCases/n40_acak_02.txt",
            "TestCases/n40_klaster_01.txt",
            "TestCases/n40_labirin_01.txt",

            "TestCases/n80_acak_01.txt",
            "TestCases/n80_acak_02.txt",
            "TestCases/n80_klaster_01.txt",
            "TestCases/n80_labirin_01.txt",
    };

    // tidak dibaca dari config, ubah disini
    static int MAX_STAGNANT = 200;
    static Crossover CROSSOVER_TYPE = new SinglePointCrossover();
    static Mutation MUTATION_TYPE = new RandomReplacementMutation();
    static Selection SELECTION_TYPE = new TournamentSelection(4);
    static ChromosomeType CHROMOSOME_TYPE = ChromosomeType.ARRAY_FS;

    // seed
    static final int SEED = 24056;
    static final int NUM_OF_SEEDS = 30;

    // untuk cek berapa kali evaluasi dilakukan
    static int evaluationCount;

    // config genetic algo (dibaca dari file)
    static class Config {
        int populationSize;
        double pc, pm, elitism;
    }

    // baca config dari file, format: populationSize pc pm elitism
    static Config readConfig(String path) throws Exception {
        Scanner sc = new Scanner(new File(path));
        Config c = new Config();
        c.populationSize = sc.nextInt();
        c.pc = sc.nextDouble();
        c.pm = sc.nextDouble();
        c.elitism = sc.nextDouble();
        sc.close();
        return c;
    }

    public static void main(String[] args) throws Exception {

        // 1. CEK MODE TUNING (Harus paling atas!)
        if (args.length >= 3 && args[0].equalsIgnoreCase("tune")) {
            System.out.println("Menjalankan Mode Tuning...");
            String mapFile = args[1];
            String outputFile = args[2];

            // Atur operator statis yang ingin digunakan selama tuning (Fiksasi)
            CROSSOVER_TYPE = new TwoPointCrossover();
            MUTATION_TYPE = new TargetedMutation();
            SELECTION_TYPE = new TournamentSelection(4);
            CHROMOSOME_TYPE = ChromosomeType.ARRAY_FS; // atau BIT_STRING

            Preprocessed prep = loadAndPreprocess(mapFile);
            // HyperparameterTuner.runGridSearch(prep, outputFile);

            return; // PENTING: Hentikan eksekusi di sini agar tidak membaca config.txt di bawah
        }

        // 2. JIKA BUKAN TUNING, CEK ARGUMEN DEFAULT
        if (args.length < 2) {
            System.err.println("Usage:");
            System.err.println("  java Main config.txt experiment output.csv");
            System.err.println("  java Main config.txt TestCases/xxx.txt");
            System.err.println("  java Main tune TestCases/xxx.txt tuning_results.csv"); // Tambahan info CLI
            return;
        }

        // 3. BACA CONFIG & JALANKAN MODE LAMA
        String configFile = args[0];
        Config cfg = readConfig(configFile); // Sebelumnya error di sini karena 'args[0]' adalah "tune"

        if (args.length >= 3 && args[1].equalsIgnoreCase("experiment")) {
            runExperiment(cfg, args[2]);
        } else {
            runSingle(cfg, args[1]);
        }
    }

    // run sekali untuk percobaan
    static void runSingle(Config cfg, String mapFile) throws Exception {
        Preprocessed prep = loadAndPreprocess(mapFile);

        int sizeNumber = prep.n;
        int budget = budgetFromSize(sizeNumber);
        int maxGen = budget / cfg.populationSize;
        long seed = System.currentTimeMillis();

        Individual best = runGA(prep, cfg.populationSize,
                cfg.pc, cfg.pm, cfg.elitism, maxGen, seed);

        System.out.printf("%d %.5f%n", prep.p, best.getCost());
        for (int cellId : best.getSelectedIds()) {
            Point pt = prep.cellCoords[cellId];
            System.out.printf("%d %d%n", pt.col + 1, pt.row + 1);
        }
    }

    // loop dari array map files, terus isi ke dalam csv
    static void runExperiment(Config cfg, String outputCsv) throws Exception {
        int[] seeds = new int[NUM_OF_SEEDS];
        Random r = new Random(SEED);

        for (int i = 0; i < NUM_OF_SEEDS; i++) {
            seeds[i] = r.nextInt(65537);
        }

        try (PrintWriter out = new PrintWriter(outputCsv)) {
            out.println("map,pop_size,pc,pm,elitism,cost,evaluation");

            for (String path : MAP_FILES) {
                Preprocessed prep = loadAndPreprocess(path);
                int sizeNumber = prep.n;
                int budget = budgetFromSize(sizeNumber);

                // maxGen dihitung dari budget / populationSize
                int maxGen = budget / cfg.populationSize;

                for (int seed : seeds) {
                    Individual best = runGA(prep, cfg.populationSize,
                            cfg.pc, cfg.pm, cfg.elitism, maxGen, seed);

                    out.printf("%s,%d,%.2f,%.2f,%.2f,%.6f,%d%n",
                            new File(path).getName(),
                            cfg.populationSize, cfg.pc, cfg.pm, cfg.elitism,
                            best.getCost(), evaluationCount);
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

        int m = sc.nextInt(),
                n = sc.nextInt(),
                p = sc.nextInt();
        sc.nextLine();

        char[][] grid = new char[m][n];
        for (int r = 0; r < m; r++) {
            String line = sc.nextLine().trim();
            for (int c = 0; c < n; c++)
                grid[r][c] = line.charAt(c);
        }
        sc.close();

        return preprocess(grid, p);
    }

    // mengubah petak / sell (x,y) menjadi ID dan
    // melakukan flood fill dari house ke firestation untuk menyimpan jaraknya
    static Preprocessed preprocess(char[][] grid, int p) {
        int m = grid.length, n = grid[0].length;
        Preprocessed prep = new Preprocessed();
        prep.m = m;
        prep.n = n;
        prep.p = p;
        prep.penalty = m * n;

        int totalEmpty = 0;
        for (int r = 0; r < m; r++)
            for (int c = 0; c < n; c++)
                if (grid[r][c] == '.')
                    totalEmpty++;
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
                if (grid[r][c] == 'H')
                    houses.add(new Point(r, c));
        prep.numHouses = houses.size();

        // Matriks jarak precomputed: [numHouses][totalEmptyCells + 1]
        // Indeks 0 dummy, indeks 1..totalEmptyCells adalah ID sel kosong.
        prep.distance = new int[houses.size()][totalEmpty + 1];
        for (int h = 0; h < houses.size(); h++) {
            Point hp = houses.get(h);
            prep.distance[h] = BFS.houseFloodFill(grid, hp.row, hp.col, cellID, totalEmpty);
        }

        return prep;
    }

    // Jalankan GA sebanyak maxGen generasi
    static Individual runGA(Preprocessed prep, int populationSize,
            double pc, double pm, double elitism,
            int maxGen, long seed) {

        Population population = new Population(populationSize);
        population.initializeRandomPopulation(prep.totalEmpty, prep.p, new Random(seed), CHROMOSOME_TYPE);

        // khusus untuk TargetedMutation, bisa dibiarkan saja
        TargetedMutation.setContext(prep.distance, prep.numHouses);

        GeneticAlgorithm ga = new GeneticAlgorithm(
                prep.distance, prep.numHouses, prep.totalEmpty, prep.p, prep.penalty,
                pc, pm, elitism, seed,
                SELECTION_TYPE,
                CROSSOVER_TYPE,
                MUTATION_TYPE,
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
            if (currentBest > bestFitness + 1e-9) {
                bestFitness = currentBest;
                stagnantCounter = 0;
            } else {
                stagnantCounter++;
                if (stagnantCounter >= MAX_STAGNANT) {
                    break; // berhenti karena stagnan
                }
            }
        }

        evaluationCount = ga.getEvaluationCount();

        return population.getBestIndividual();
    }

    // Evaluation budget berdasarkan ukuran grid.
    static int budgetFromSize(int size) {
        switch (size) {
            case 10:
                return 10000;
            case 20:
                return 50000;
            case 40:
                return 200000;
            case 60:
                return 350000; // <-- Tambahkan baris ini agar tidak memakai budget n=20 (50.000)
            case 80:
                return 500000;
            default:
                return 50000;
        }
    }
}