package mutation;

import java.util.Random;

import model.*;

public class SwapMutation implements Mutation {

    @Override
    public void mutate(Individual individual, double mutationRate, int totalEmptyCells, Random rand) {
        if (rand.nextDouble() >= mutationRate) return;

        int[] chromosome = individual.getChromosome();
        int p = chromosome.length;
        if (p < 2) return;

        boolean[] isUsed = new boolean[totalEmptyCells + 1];
        for (int id : chromosome) isUsed[id] = true;

        // pilih dua indeks gen berbeda
        int i = rand.nextInt(p);
        int j;
        do { j = rand.nextInt(p); } 
        while (j == i);

        // ganti gen i
        isUsed[chromosome[i]] = false;
        int newI;
        do { newI = rand.nextInt(totalEmptyCells) + 1; } while (isUsed[newI]);
        isUsed[newI] = true;
        chromosome[i] = newI;

        // ganti gen j
        isUsed[chromosome[j]] = false;
        int newJ;
        do { newJ = rand.nextInt(totalEmptyCells) + 1; } while (isUsed[newJ]);
        isUsed[newJ] = true;
        chromosome[j] = newJ;

        individual.setChromosome(chromosome);
    }
}