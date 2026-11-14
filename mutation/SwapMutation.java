package mutation;

import java.util.Arrays;
import java.util.Random;

import model.Individual;

public class SwapMutation implements Mutation {

    @Override
    public void mutate(Individual individual, double mutationRate,
                       int totalEmptyCells, Random rand) {
        if (individual.getType() == Individual.ChromosomeType.ARRAY_FS) {
            mutateArrayFS(individual, mutationRate, totalEmptyCells, rand);
        } else {
            mutateBitString(individual, mutationRate, totalEmptyCells, rand);
        }
    }

    /**
     * pilih dua indeks gen, ganti keduanya dengan ID sel kosong baru yang unik.
     */
    private void mutateArrayFS(Individual individual, double mutationRate,
                               int totalEmptyCells, Random rand) {
        if (rand.nextDouble() >= mutationRate) return;

        int[] chromosome = individual.getChromosome();
        int p = chromosome.length;
        if (p < 2) return;

        boolean[] isUsed = new boolean[totalEmptyCells + 1];
        for (int id : chromosome) isUsed[id] = true;

        int i = rand.nextInt(p);
        int j;
        do { j = rand.nextInt(p); }
        while (j == i);

        isUsed[chromosome[i]] = false;
        int newI;
        do { newI = rand.nextInt(totalEmptyCells) + 1; } while (isUsed[newI]);
        isUsed[newI] = true;
        chromosome[i] = newI;

        isUsed[chromosome[j]] = false;
        int newJ;
        do { newJ = rand.nextInt(totalEmptyCells) + 1; } while (isUsed[newJ]);
        isUsed[newJ] = true;
        chromosome[j] = newJ;

        Arrays.sort(chromosome);         
        individual.setChromosome(chromosome);
        individual.setCost(0.0);         
        individual.setFitness(0.0);
    }

    private void mutateBitString(Individual individual, double mutationRate,
                                 int totalEmptyCells, Random rand) {
        if (rand.nextDouble() >= mutationRate) return;

        int[] chromosome = individual.getChromosome();
        int len = chromosome.length;
        if (len < 2) return;

        // pilih satu indeks dengan bit 1 (FS)
        int onesCount = 0;
        for (int b : chromosome) if (b == 1) onesCount++;
        if (onesCount == 0 || onesCount == len) return; // tidak ada yang bisa ditukar

        int i;
        do { i = rand.nextInt(len); } 
            while (chromosome[i] != 1);

        int j;
        do { j = rand.nextInt(len); } 
            while (chromosome[j] != 0);

        chromosome[i] = 0;
        chromosome[j] = 1;

        individual.setChromosome(chromosome);
        individual.setCost(0.0);
        individual.setFitness(0.0);
    }
}