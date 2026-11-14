package mutation;

import java.util.Arrays;
import java.util.Random;

import model.Individual;

/**
 * Implementasi mutasi dengan metode Random Replacement.
 * Metode ini dipilih karena hanya mengubah urutan gen (swap) tidak memberikan
 * variasi baru.
 * Sebaliknya, mutasi ini akan menukar posisi Fire Station lama dengan posisi
 * sel kosong baru yang belum ada di dalam kromosom.
 */
public class RandomReplacementMutation implements Mutation {

    /**
     * Mengeksekusi proses mutasi pada kromosom individu berdasarkan mutation rate.
     * 
     * @param individual      Individu yang kromosomnya akan dimutasi.
     * @param mutationRate    Probabilitas sebuah gen (titik Fire Station) diganti
     *                        (0.0 - 1.0).
     * @param totalEmptyCells Batas jumlah sel kosong (titik maksimal adalah
     *                        totalEmptyCells).
     * @param rand            Objek Random untuk membangkitkan angka probabilitas
     *                        dan ID sel baru.
     */
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
     * per gen, dengan probabilitas, ganti dengan
     * ID sel kosong baru yang belum dipakai.
     */
    private void mutateArrayFS(Individual individual, double mutationRate,
                               int totalEmptyCells, Random rand) {
        int[] chromosome = individual.getChromosome();
        boolean isMutated = false;

        for (int i = 0; i < chromosome.length; i++) {
            if (rand.nextDouble() < mutationRate) {
                int newCellID;
                boolean isDuplicate;
                do {
                    isDuplicate = false;
                    newCellID = rand.nextInt(totalEmptyCells) + 1;
                    for (int gene : chromosome) {
                        if (gene == newCellID) {
                            isDuplicate = true;
                            break;
                        }
                    }
                } while (isDuplicate);

                chromosome[i] = newCellID;
                isMutated = true;
            }
        }

        if (isMutated) {
            Arrays.sort(chromosome);
            individual.setChromosome(chromosome);
            individual.setCost(0.0);
            individual.setFitness(0.0);
        }
    }

    /**
     * Logika untuk BIT_STRING:
     * per gen, dengan probabilitas, pindahkan bit tersebut ke posisi lain
     * dengan nilai berlawanan (1 -> 0 dan 0 -> 1).
     * Hasilnya selalu menjaga jumlah 1 = p.
     */
    private void mutateBitString(Individual individual, double mutationRate,
                                 int totalEmptyCells, Random rand) {
        int[] chromosome = individual.getChromosome();
        int len = chromosome.length;
        if (len < 2) return;

        boolean isMutated = false;

        for (int i = 0; i < len; i++) {
            if (rand.nextDouble() < mutationRate) {
                // cari indeks j dengan nilai berlawanan
                int targetValue = 1 - chromosome[i];
                int j;
                boolean found = false;
                for (int attempt = 0; attempt < len * 2; attempt++) {
                    j = rand.nextInt(len);
                    if (chromosome[j] == targetValue) {
                        // tukar
                        chromosome[j] = chromosome[i];
                        chromosome[i] = targetValue;
                        isMutated = true;
                        found = true;
                        break;
                    }
                }
                if (!found) break; // tidak ada nilai berlawanan (mis. semua 1 atau semua 0)
            }
        }

        if (isMutated) {
            individual.setChromosome(chromosome);
            individual.setCost(0.0);
            individual.setFitness(0.0);
        }
    }
}