package model;

import java.util.Random;

import crossover.Crossover;
import mutation.Mutation;
import selection.Selection;

public class GeneticAlgorithm {

    // Matriks jarak precomputed: [numHouses][totalEmptyCells + 1]
    // Indeks 0 dummy, indeks 1..totalEmptyCells adalah ID sel kosong.
    private int[][] distanceMatrix;
    private int numHouses;
    private int totalEmptyCells;
    private int p;
    private int penaltyValue;

    // Hyperparameter GA
    private double crossoverRate;
    private double mutationRate;
    private double elitismRate;
    private Random rand;

    // Strategi modular
    private Selection selectionStrategy;
    private Crossover crossoverStrategy;
    private Mutation mutationStrategy;
    private EvolutionModel evolutionModel;

    // State runtime (evaluationCount hanya untuk logging, bukan penghenti)
    private int evaluationCount;

    /**
     * Menginisialisasi mesin GA dengan parameter masalah, hyperparameter,
     * dan strategi operator.
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

        this.evaluationCount = 0;
    }

    // EVALUASI

    /**
     * Mengevaluasi satu individu.
     * Cost dihitung memakai Root Mean Square (RMS) dari jarak setiap rumah
     * ke fire station terdekat. RMS dipilih untuk mendorong pemerataan
     * jarak antar rumah.
     * Setiap pemanggilan menambah evaluationCount (khusus logging).
     */
    private void evaluateIndividual(Individual ind) {
        int[] chromosome = ind.getChromosome();
        double sumOfSquares = 0.0;

        for (int h = 0; h < numHouses; h++) {
            int minDistanceFromHouse = Integer.MAX_VALUE;

            for (int fsId : chromosome) {
                int dist = distanceMatrix[h][fsId];
                if (dist != -1 && dist < minDistanceFromHouse) {
                    minDistanceFromHouse = dist;
                }
            }

            // Rumah tidak terjangkau oleh FS mana pun
            if (minDistanceFromHouse == Integer.MAX_VALUE) {
                minDistanceFromHouse = penaltyValue;
            }

            sumOfSquares += (double) minDistanceFromHouse * minDistanceFromHouse;
        }

        // Cost = RMS jarak rumah ke FS terdekat
        double cost = Math.sqrt(sumOfSquares / numHouses);
        ind.setCost(cost);

        // Fitness berbanding terbalik dengan cost; epsilon menghindari Infinity
        ind.setFitness(1.0 / (cost + 1e-9));

        evaluationCount++;
    }

    /**
     * Mengevaluasi seluruh individu di dalam populasi.
     * Biasanya dipakai untuk mengevaluasi populasi awal.
     */
    public void evaluatePopulation(Population population) {
        Individual[] individuals = population.getIndividuals();
        for (int i = 0; i < individuals.length; i++) {
            evaluateIndividual(individuals[i]);
        }
    }

    /**
     * Mengembalikan jumlah evaluasi fitness yang sudah dilakukan.
     * Hanya untuk logging/CSV, bukan untuk menghentikan loop.
     */
    public int getEvaluationCount() {
        return evaluationCount;
    }

    /**
     * Menghitung jumlah individu elite yang dipertahankan
     * pada generasi berikutnya.
     */
    private int computeEliteCount(int populationSize) {
        return (int) (populationSize * elitismRate);
    }

    // EVOLUSI

    /**
     * Menjalankan satu langkah evolusi sesuai model yang dipilih:
     * generational atau steady-state.
     */
    public void evolveStep(Population population) {
        if (evolutionModel == EvolutionModel.GENERATIONAL) {
            evolveGenerational(population);
        } else {
            evolveSteadyState(population);
        }
    }

    /**
     * Evolusi model generational:
     * 1. Individu elite disalin langsung tanpa evaluasi ulang.
     * 2. Sisa populasi diisi anak hasil seleksi, crossover, dan mutasi.
     * 3. Setiap anak baru dievaluasi.
     * 4. Populasi lama digantikan seluruhnya oleh generasi baru.
     */
    private void evolveGenerational(Population population) {
        int populationSize = population.getPopulationSize();
        Population nextGen = new Population(populationSize);

        population.sortByFitness();
        int eliteCount = computeEliteCount(populationSize);

        // Elitism: salin individu terbaik tanpa evaluasi ulang
        for (int i = 0; i < eliteCount; i++) {
            nextGen.setIndividual(i, population.getIndividual(i).getClone());
        }

        int idx = eliteCount;
        while (idx < populationSize) {
            Individual p1 = selectionStrategy.select(population, rand);
            Individual p2 = selectionStrategy.select(population, rand);

            Individual[] children = crossoverStrategy.crossover(p1, p2, crossoverRate, p, totalEmptyCells, rand);

            mutationStrategy.mutate(children[0], mutationRate, totalEmptyCells, rand);
            if (idx + 1 < populationSize) {
                mutationStrategy.mutate(children[1], mutationRate, totalEmptyCells, rand);
            }

            evaluateIndividual(children[0]);
            nextGen.setIndividual(idx++, children[0]);

            if (idx < populationSize) {
                evaluateIndividual(children[1]);
                nextGen.setIndividual(idx++, children[1]);
            }
        }

        for (int i = 0; i < populationSize; i++) {
            population.setIndividual(i, nextGen.getIndividual(i));
        }
    }

    /**
     * Evolusi model steady-state:
     * 1. Dua induk dipilih, lalu crossover dan mutasi menghasilkan satu anak.
     * 2. Anak dievaluasi.
     * 3. Jika anak lebih baik dari individu terburuk dan bukan bagian elite,
     *    anak menggantikan individu terburuk tersebut.
     */
    private void evolveSteadyState(Population population) {
        Individual p1 = selectionStrategy.select(population, rand);
        Individual p2 = selectionStrategy.select(population, rand);

        Individual[] children = crossoverStrategy.crossover(
                p1, p2, crossoverRate, p, totalEmptyCells, rand);

        mutationStrategy.mutate(children[0], mutationRate, totalEmptyCells, rand);

        evaluateIndividual(children[0]);

        population.sortByFitness();
        int populationSize = population.getPopulationSize();
        int worstIdx = populationSize - 1;
        int eliteCount = computeEliteCount(populationSize);

        // individu top e% tidak boleh digantikan
        if (worstIdx < eliteCount) {
            return;
        }

        if (children[0].getFitness() > population.getIndividual(worstIdx).getFitness()) {
            population.setIndividual(worstIdx, children[0]);
        }
    }
}