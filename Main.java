import java.util.*;

public class Main {
    final static int TOURNAMENT_SIZE = 3;
    final static double MUTATION_RATE = 0.005; // pastikan kurang dari 1%
    final static double ELITISM_RATE = 0.1; 
    final static double CROSSOVER_RATE = 1; // ada hanya agar "modular"
    final static long SEED = System.currentTimeMillis(); // gunakan nilai long agar deterministik atau System.currentTimeMillis() agar random
    final static int MAX_GENERATION = 500; // berapa kali Genetic Algorithm berjalan
    final static int POPULATION_SIZE = 100; 

    public static void main(String[] args) {
        Scanner scanner = new Scanner(System.in);
        
        // 1. Membaca m, n, p file input.txt
        int m = scanner.nextInt();
        int n = scanner.nextInt();
        int p = scanner.nextInt();
    
        if (scanner.hasNextLine()) {
            scanner.nextLine();
        }

        // 2. Membaca baris-baris peta grid secara dinamis sebanyak m baris
        char[][] grid = new char[m][n];
        int rowsRead = 0;

        while (rowsRead < m && scanner.hasNextLine()) {
            String line = scanner.nextLine().trim();
            if (line.isEmpty()) continue; // Lewati baris kosong jika ada

            for (int c = 0; c < Math.min(n, line.length()); c++) {
                grid[rowsRead][c] = line.charAt(c);
            }
            rowsRead++;
        }

        scanner.close();

        int penaltyValue = m * n;

        // 3. Preprocessing: Petakan ID sel kosong dan buat Lookup Table Koordinat
        int totalEmptyCells = 0;
        for (int r = 0; r < m; r++) {
            for (int c = 0; c < n; c++) {
                if (grid[r][c] == '.') totalEmptyCells++;
            }
        }

        Point[] cellCoordinates = new Point[totalEmptyCells + 1];
        int[][] cellID = new int[m][n];
        int idCounter = 1;

        for (int r = 0; r < m; r++) {
            for (int c = 0; c < n; c++) {
                if (grid[r][c] == '.') {
                    cellID[r][c] = idCounter;
                    cellCoordinates[idCounter] = new Point(r, c);
                    idCounter++;
                }
            }
        }

        // 4. Kumpulkan rumah & jalankan BFS Preprocessing sekali di awal
        List<Point> houses = new ArrayList<>();
        for (int r = 0; r < m; r++) {
            for (int c = 0; c < n; c++) {
                if (grid[r][c] == 'H') houses.add(new Point(r, c));
            }
        }

        int numHouses = houses.size();
        int[][] distanceMatrix = new int[numHouses][totalEmptyCells + 1];

        for (int h = 0; h < numHouses; h++) {
            Point house = houses.get(h);
            distanceMatrix[h] = BFS.houseFloodFill(grid, house.row, house.col, cellID, totalEmptyCells);
        }

        // 5. Inisialisasi Populasi Awal
        int populationSize = POPULATION_SIZE;
        Population population = new Population(populationSize);
        population.initializeRandomPopulation(totalEmptyCells, p, new Random(SEED));

        // 6. Inisialisasi Mesin GA dengan Strategy Pattern
        GeneticAlgorithm ga = new GeneticAlgorithm(
            distanceMatrix, numHouses, totalEmptyCells, p, penaltyValue,
            CROSSOVER_RATE,   // crossover rate
            MUTATION_RATE,  // mutation rate
            ELITISM_RATE,  // elitism rate
            SEED,    // seed acak
            new TournamentSelection(TOURNAMENT_SIZE),      // Strategi Seleksi (k=3)
            new SinglePointCrossover(),                     // Strategi Crossover + Repair
            new RandomReplacementMutation(),                // Strategi Mutasi
            EvolutionModel.GENERATIONAL                     // Model Generasi
        );

        // Evaluasi populasi awal
        // Membersihkan sisa newline di baris pertama
        ga.evaluatePopulation(population);

        // 7. Jalankan Looping Generasi (Misal: 100 generasi)
        int maxGenerations = MAX_GENERATION;
        for (int gen = 1; gen <= maxGenerations; gen++) {
            ga.evolveStep(population);
        }

        // 8. Ambil dan Terjemahkan (Decode) Hasil Terbaik ke Koordinat Peta Asli
        population.sortByFitness();
        Individual best = population.getBestIndividual();

        System.out.println("=========================================");
        System.out.println("   HASIL AKHIR ALGORITMA GENETIK (GA)    ");
        System.out.println("=========================================");
        System.out.println("Cost Terbaik (RMS Distance) : " + best.getCost());
        System.out.println("Fitness Terbaik             : " + best.getFitness());
        System.out.println("\nTitik Penempatan Fire Station Optimal:");
        
        int counter = 1;
        for (int cellId : best.getChromosome()) {
            Point pt = cellCoordinates[cellId];
            System.out.println("  " + counter++ + ". ID Sel: " + cellId + " -> Koordinat (Baris: " + pt.row + ", Kolom: " + pt.col + ")");
        }
        System.out.println("=========================================");
    }
}