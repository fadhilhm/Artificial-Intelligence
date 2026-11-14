import java.util.*;

import model.*;
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

            // "TestCases/n40_acak_01.txt",
            // "TestCases/n40_acak_02.txt",
            // "TestCases/n40_klaster_01.txt",
            // "TestCases/n40_labirin_01.txt",

            // "TestCases/n80_acak_01.txt",
            // "TestCases/n80_acak_02.txt",
            // "TestCases/n80_klaster_01.txt",
            // "TestCases/n80_labirin_01.txt",
    };

    // tidak dibaca dari config
    static final int TOURN_SIZE     = 3;
    static final int MAX_STAGNANT   = 200;

    // seed
    static final int NPM = 24056;
    static final int NUM_SEEDS = 30;

    // menghitung berapa kali populasi dievaluasi
    static int lastEvalCount;

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
        c.pc      = sc.nextDouble();
        c.pm      = sc.nextDouble();
        c.elitism = sc.nextDouble();
        sc.close();
        return c;
    }

    public static void main(String[] args) throws Exception {
        // java Main config.txt TestCases/xxx.txt       -> single run, grid dari file
        // java Main config.txt experiment out.csv      -> eksperimen semua MAP_FILES
        //
        // Format config.txt: populationSize pc pm elitism
        if (args.length < 2) {
            System.err.println("Usage:");
            System.err.println("  java Main config.txt experiment output.csv");
            System.err.println("  java Main config.txt TestCases/xxx.txt");
            return;
        }

        String configFile = args[0];
        Config cfg = readConfig(configFile);

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

        Individual best = runGA(prep, cfg.populationSize, TOURN_SIZE,
                cfg.pc, cfg.pm, cfg.elitism, maxGen, seed);

        System.out.printf("%d %.5f%n", prep.p, best.getCost());
        for (int cellId : best.getChromosome()) {
            Point pt = prep.cellCoords[cellId];
            System.out.printf("%d %d%n", pt.col + 1, pt.row + 1);
        }
    }

    // loop dari array map files, terus isi ke dalam csv
    static void runExperiment(Config cfg, String outputCsv) throws Exception {
        int[] seeds = new int[NUM_SEEDS];
        Random r = new Random(NPM);
        for (int i = 0; i < NUM_SEEDS; i++) {
            seeds[i] = r.nextInt(65537);
        }

        try (PrintWriter out = new PrintWriter(outputCsv)) {
            out.println("map,pop_size,pc,pm,elitism,cost,eval_count");

            for (String path : MAP_FILES) {
                Preprocessed prep = loadAndPreprocess(path);
                int sizeNumber = prep.n;
                int budget = budgetFromSize(sizeNumber);

                // maxGen dihitung dari budget / populationSize
                int maxGen = budget / cfg.populationSize;

                for (int seed : seeds) {
                    Individual best = runGA(prep, cfg.populationSize, TOURN_SIZE,
                            cfg.pc, cfg.pm, cfg.elitism, maxGen, seed);

                    out.printf("%s,%d,%.2f,%.2f,%.2f,%.6f,%d%n",
                            new File(path).getName(),
                            cfg.populationSize, cfg.pc, cfg.pm, cfg.elitism,
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
        
        int m = sc.nextInt(), 
            n = sc.nextInt(), 
            p = sc.nextInt();
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
        prep.m = m; 
        prep.n = n; 
        prep.p = p; 
        prep.penalty = m * n;

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
            prep.distance[h] = BFS.houseFloodFill(grid, hp.row, hp.col, cellID, totalEmpty);
        }
        
        return prep;
    }

    // Jalankan GA sebanyak maxGen generasi
    static Individual runGA(Preprocessed prep, int populationSize, int tournSize,
                            double pc, double pm, double elitism,
                            int maxGen, long seed) {

        Population population = new Population(populationSize);
        population.initializeRandomPopulation(prep.totalEmpty, prep.p, new Random(seed));

        GeneticAlgorithm ga = new GeneticAlgorithm(
                prep.distance, prep.numHouses, prep.totalEmpty, prep.p, prep.penalty,
                pc, pm, elitism, seed,
                // new TournamentSelection(tournSize),
                new RouletteWheelSelection(),
                // new SinglePointCrossover(),
                new TwoPointCrossover(),
                // new RandomReplacementMutation(),
                new SwapMutation(),
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

        lastEvalCount = ga.getEvaluationCount();
        return population.getBestIndividual();
    }

    // Evaluation budget berdasarkan ukuran grid.
    static int budgetFromSize(int size) {
        switch (size) {
            case 10: return 10000;
            case 20: return 50000;
            case 40: return 200000;
            case 80: return 500000;
            default: return 50000;
        }
    }
}