import java.util.Random;

/**
 * Kelas ini bertindak sebagai mesin utama pengatur jalannya siklus evolusi 
 * Algoritma Genetik (GA) untuk masalah penempatan Fire Station. 
 * 
 * Kelas ini mengatur:
 * 1. Evaluasi nilai kualitas (Fitness) dan biaya (Cost) populasi menggunakan Root Mean Square (RMS).
 * 2. Langkah evolusi (Evolution Step) baik secara Generational maupun Steady-State.
 * 3. Penerapan strategi modular (Selection, Crossover, dan Mutation) serta Elitisme.
 */
public class GeneticAlgorithm {
    
    private int[][] distanceMatrix;     // Matriks lookup jarak terpendek dari setiap rumah ke setiap sel kosong
    private int numHouses;              // Total jumlah rumah yang ada di peta grid
    private int totalEmptyCells;        // Total jumlah sel kosong yang tersedia di peta
    private int p;                      // Jumlah Fire Station (panjang kromosom) yang akan ditempatkan
    private int penaltyValue;           // Nilai penalti jika rumah tidak terjangkau (aturan: m * n)
    
    private double crossoverRate;       // Probabilitas crossover (Crossover Rate, misal 0.8)
    private double mutationRate;        // Probabilitas mutasi (Mutation Rate, misal 0.05)
    private double elitismRate;         // Persentase individu juara yang diamankan langsung ke generasi berikutnya (misal 0.20 / 20%)
    private Random rand;                // Objek Random untuk proses pengundian acak

    private Selection selectionStrategy;      // Strategi seleksi induk (contoh: TournamentSelection)
    private Crossover crossoverStrategy;      // Strategi persilangan (contoh: SinglePointCrossover)
    private Mutation mutationStrategy;        // Strategi mutasi (contoh: MoveMutation)
    private EvolutionModel evolutionModel;    // Model evolusi yang digunakan (GENERATIONAL atau STEADY_STATE)

    /**
     * Konstruktor untuk menginisialisasi parameter dan strategi Algoritma Genetik.
     * 
     * @param distanceMatrix    Matriks jarak pre-computed hasil BFS dari setiap rumah ke sel kosong
     * @param numHouses         Jumlah total rumah di peta
     * @param totalEmptyCells   Jumlah total sel kosong di peta
     * @param p                 Jumlah Fire Station dalam satu kromosom
     * @param penaltyValue      Nilai penalti untuk rumah yang buntu/tidak terjangkau
     * @param crossoverRate     Probabilitas terjadinya crossover (0.0 s.d 1.0)
     * @param mutationRate      Probabilitas terjadinya mutasi (0.0 s.d 1.0)
     * @param elitismRate       Persentase elitisme untuk mengamankan individu terbaik (0.0 s.d 1.0)
     * @param seed              Angka seed acak agar hasil eksperimen bisa direplikasi
     * @param selectionStrategy Objek strategi seleksi induk yang digunakan
     * @param crossoverStrategy Objek strategi crossover yang digunakan
     * @param mutationStrategy  Objek strategi mutasi yang digunakan
     * @param evolutionModel    Model evolusi yang dipilih (GENERATIONAL / STEADY_STATE)
     */
    public GeneticAlgorithm(int[][] distanceMatrix, int numHouses, int totalEmptyCells, int p, int penaltyValue,
                            double crossoverRate, double mutationRate, double elitismRate, long seed,
                            Selection selectionStrategy, Crossover crossoverStrategy, Mutation mutationStrategy,
                            EvolutionModel evolutionModel) {
        this.distanceMatrix = distanceMatrix;
        this.numHouses = numHouses;
        this.totalEmptyCells = totalEmptyCells;
        this.p = p;
        this.penaltyValue = penaltyValue;
        this.crossoverRate = crossoverRate;
        this.mutationRate = mutationRate;
        this.elitismRate = elitismRate;
        this.rand = new Random(seed);
        
        this.selectionStrategy = selectionStrategy;
        this.crossoverStrategy = crossoverStrategy;
        this.mutationStrategy = mutationStrategy;
        this.evolutionModel = evolutionModel;
    }

    /**
     * Mengevaluasi seluruh individu di dalam populasi menggunakan fungsi Root Mean Square (RMS).
     * Menghitung rata-rata kuadrat jarak rumah ke Fire Station terdekat, lalu diakar kuadratkan.
     * 
     * @param population Objek populasi yang berisi kumpulan individu yang akan dievaluasi
     */
    public void evaluatePopulation(Population population) {
        // Looping untuk setiap individu di dalam populasi
        for (Individual ind : population.getIndividuals()) {
            int[] chromosome = ind.getChromosome();
            double sumOfSquares = 0.0;

            // Hitung jarak dari setiap rumah ke Fire Station terdekat di dalam kromosom
            for (int h = 0; h < numHouses; h++) {
                int minDistanceFromHouse = Integer.MAX_VALUE;
                
                for (int fsId : chromosome) {
                    int dist = distanceMatrix[h][fsId];
                    if (dist != -1 && dist < minDistanceFromHouse) {
                        minDistanceFromHouse = dist;
                    }
                }

                // Jika rumah tidak terjangkau (buntu), berikan nilai penalti
                if (minDistanceFromHouse == Integer.MAX_VALUE) {
                    minDistanceFromHouse = penaltyValue;
                }

                // Kuadratkan jarak rumah sebelum dijumlahkan (dengan RMS)
                sumOfSquares += Math.pow(minDistanceFromHouse, 2);
            }

            // Hitung Cost akhir = Akar kuadrat dari rata-rata jumlah kuadrat jarak
            double cost = Math.sqrt(sumOfSquares / numHouses);
            ind.setCost(cost);
            
            // Ubah Cost menjadi Fitness (Nilai fitness berbanding terbalik dengan cost, semakin kecil cost semakin tinggi fitness)
            ind.setFitness(1.0 / cost);
        }
    }

    /**
     * Menjalankan satu langkah/siklus evolusi berdasarkan model yang dipilih.
     * 
     * @param population Objek populasi yang akan dievolusikan ke generasi berikutnya
     */
    public void evolveStep(Population population) {
        if (this.evolutionModel == EvolutionModel.GENERATIONAL) {
            evolveGenerational(population);
        } else {
            evolveSteadyState(population);
        }
    }

    /**
     * Model Evolusi Generational dengan Elitisme:
     * Seluruh populasi diganti dengan generasi baru, namun sebagian kecil individu terbaik 
     * (berdasarkan elitismRate) dijamin lolos langsung tanpa perubahan.
     * 
     * @param population Objek populasi saat ini yang akan diperbarui
     */
    private void evolveGenerational(Population population) {
        int populationSize = population.getPopulationSize();
        Population nextGen = new Population(populationSize);

        // 1. Tahap Elitisme: Urutkan populasi berdasarkan fitness tertinggi
        population.sortByFitness();
        int eliteCount = (int) (populationSize * elitismRate);
        
        // Salin individu-individu juara langsung ke generasi berikutnya
        for (int i = 0; i < eliteCount; i++) {
            nextGen.setIndividual(i, population.getIndividual(i).getClone());
        }

        // 2. Tahap Regenerasi Sisa Populasi (Seleksi -> Crossover -> Mutasi)
        int idx = eliteCount;
        while (idx < populationSize) {
            // Pilih dua parent menggunakan strategi seleksi
            Individual p1 = selectionStrategy.select(population, rand);
            Individual p2 = selectionStrategy.select(population, rand);

            // Lakukan persilangan (crossover) untuk melahirkan 2 anak
            Individual[] children = crossoverStrategy.crossover(p1, p2, crossoverRate, p, totalEmptyCells, rand);

            // Terapkan mutasi pada anak pertama
            mutationStrategy.mutate(children[0], mutationRate, totalEmptyCells, rand);
            
            // Terapkan mutasi pada anak kedua (jika kuota populasi masih mencukupi)
            if (idx + 1 < populationSize) {
                mutationStrategy.mutate(children[1], mutationRate, totalEmptyCells, rand);
            }

            // Masukkan anak ke dalam populasi generasi baru
            nextGen.setIndividual(idx++, children[0]);
            if (idx < populationSize) {
                nextGen.setIndividual(idx++, children[1]);
            }
        }

        // Evaluasi nilai cost dan fitness untuk generasi baru
        evaluatePopulation(nextGen);

        // Timpa populasi lama dengan populasi generasi baru
        for (int i = 0; i < populationSize; i++) {
            population.setIndividual(i, nextGen.getIndividual(i));
        }
    }

    /**
     * Model Evolusi Steady-State:
     * Hanya menghasilkan satu atau sedikit anak baru dalam satu siklus, lalu anak tersebut 
     * langsung dibandingkan untuk menggantikan individu terburuk di dalam populasi.
     * 
     * @param population Objek populasi saat ini
     */
    private void evolveSteadyState(Population population) {
        // Pilih 2 parent
        Individual p1 = selectionStrategy.select(population, rand);
        Individual p2 = selectionStrategy.select(population, rand);

        // Lakukan crossover dan mutasi untuk menghasilkan 1 anak
        Individual[] children = crossoverStrategy.crossover(p1, p2, crossoverRate, p, totalEmptyCells, rand);
        mutationStrategy.mutate(children[0], mutationRate, totalEmptyCells, rand);

        // Evaluasi fitness anak tersebut
        evaluatePopulationForSingle(children[0]);

        // Urutkan populasi untuk mencari posisi individu dengan fitness terburuk
        population.sortByFitness();
        int worstIdx = population.getPopulationSize() - 1;

        // Jika fitness anak lebih baik dari individu terburuk di populasi, gantikan posisinya
        if (children[0].getFitness() > population.getIndividual(worstIdx).getFitness()) {
            population.setIndividual(worstIdx, children[0]);
        }
    }

    /**
     * Helper privat untuk mengevaluasi satu individu tunggal secara mandiri.
     * 
     * @param ind Objek Individual tunggal yang ingin dihitung cost & fitness-nya
     */
    private void evaluatePopulationForSingle(Individual ind) {
        Population singlePop = new Population(1);
        singlePop.setIndividual(0, ind);
        evaluatePopulation(singlePop);
    }
}