package mutation;

import java.util.Arrays;
import java.util.Random;

import model.Individual;

/**
 * Implementasi mutasi dengan metode Targeted Mutation (mutasi terarah)
 * khusus untuk masalah penempatan Fire Station.
 *
 * Ide:
 *   1. Cari rumah yang paling jauh dari FS terdekatnya (worst-served house).
 *   2. Pilih satu FS acak untuk dipindahkan.
 *   3. Pindahkan FS itu ke sel kosong yang paling dekat dengan rumah tersebut.
 */
public class TargetedMutation implements Mutation {

    // Matriks jarak hasil preprocessing: [numHouses][totalEmptyCells + 1]
    private static int[][] distanceMatrix;
    private static int numHouses;

    // Jumlah sampel kandidat sel kosong saat mencari posisi baru.
    // Naikkan nilainya untuk pencarian lebih teliti (lebih lambat),
    // turunkan untuk lebih cepat.
    private static final int CANDIDATE_SAMPLES = 30;

    /**
     * @param distanceMatrix matriks jarak rumah -> sel kosong (indeks 0 dummy)
     * @param numHouses      jumlah rumah pada peta
     */
    public TargetedMutation() {
    }

    public static void setContext(int[][] dist, int numHouses) {
        TargetedMutation.distanceMatrix = dist;
        TargetedMutation.numHouses = numHouses;
    }

    /**
     * Mengeksekusi proses mutasi pada kromosom individu berdasarkan mutation rate.
     *
     * @param individual      Individu yang kromosomnya akan dimutasi.
     * @param mutationRate    Probabilitas terjadinya mutasi (0.0 - 1.0).
     * @param totalEmptyCells Batas jumlah sel kosong pada peta.
     * @param rand            Objek Random untuk pengundian dan pemilihan acak.
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
     * Logika untuk ARRAY_FS:
     *   1. Cari rumah dengan jarak terbesar ke FS terdekat.
     *   2. Pilih satu gen (FS) acak, lalu ganti dengan sel kosong terdekat
     *      ke rumah tersebut, yang belum dipakai FS lain.
     *   3. Sort kromosom agar bentuknya kanonik.
     */
    private void mutateArrayFS(Individual individual, double mutationRate,
                               int totalEmptyCells, Random rand) {
        if (rand.nextDouble() >= mutationRate) return;

        int[] chromosome = individual.getChromosome();
        if (chromosome.length == 0) return;

        // 1. Cari worst-served house berdasarkan FS saat ini
        int worstHouse = findWorstServedHouse(chromosome);
        if (worstHouse == -1) return;

        // 2. Tandai ID yang sudah dipakai
        boolean[] used = new boolean[totalEmptyCells + 1];
        for (int fsId : chromosome) {
            if (fsId >= 1 && fsId <= totalEmptyCells) used[fsId] = true;
        }

        // 3. Pilih satu gen (FS) acak untuk dipindahkan
        int idx = rand.nextInt(chromosome.length);

        // 4. Cari sel kosong terdekat ke worstHouse yang belum dipakai
        int bestCell = pickClosestFreeCell(worstHouse, used, totalEmptyCells, rand);
        if (bestCell == -1) return;

        // 5. Terapkan perubahan
        chromosome[idx] = bestCell;
        Arrays.sort(chromosome);

        individual.setChromosome(chromosome);
        individual.setCost(0.0);
        individual.setFitness(0.0);
    }

    /**
     * Logika untuk BIT_STRING:
     *   1. Cari rumah dengan jarak terbesar ke FS terdekat.
     *   2. Pilih satu bit 1 (FS) acak untuk dipindahkan.
     *   3. Matikan bit FS lama, nyalakan bit sel kosong terdekat ke rumah tsb.
     * Jumlah bit 1 tetap = p setelah operasi, jadi tidak perlu repair.
     */
    private void mutateBitString(Individual individual, double mutationRate,
                                 int totalEmptyCells, Random rand) {
        if (rand.nextDouble() >= mutationRate) return;

        int[] chromosome = individual.getChromosome();
        int len = chromosome.length;
        if (len < 2) return;

        // 1. Kumpulkan ID FS yang sedang aktif
        int[] selectedIds = individual.getSelectedIds();
        if (selectedIds.length == 0) return;

        // 2. Cari worst-served house
        int worstHouse = findWorstServedHouse(selectedIds);
        if (worstHouse == -1) return;

        // 3. Tandai ID yang sudah dipakai
        boolean[] used = new boolean[totalEmptyCells + 1];
        for (int fsId : selectedIds) used[fsId] = true;

        // 4. Pilih satu FS acak untuk dipindahkan
        int oldId = selectedIds[rand.nextInt(selectedIds.length)];

        // 5. Cari sel kosong terdekat yang belum dipakai
        int bestCell = pickClosestFreeCell(worstHouse, used, totalEmptyCells, rand);
        if (bestCell == -1) return;

        // 6. Terapkan perubahan: matikan bit lama, nyalakan bit baru
        chromosome[oldId - 1] = 0;
        chromosome[bestCell - 1] = 1;

        individual.setChromosome(chromosome);
        individual.setCost(0.0);
        individual.setFitness(0.0);
    }

    /**
     * Mencari indeks rumah yang paling jauh dari FS terdekat.
     * Rumah yang tidak terjangkau FS mana pun dianggap paling buruk.
     *
     * @param selectedIds ID sel kosong yang sedang dipakai sebagai FS
     * @return indeks rumah terburuk, atau -1 jika numHouses == 0
     */
    private int findWorstServedHouse(int[] selectedIds) {
        if (numHouses == 0 || selectedIds.length == 0) return -1;

        int worstHouse = -1;
        int worstDist = -1;

        for (int h = 0; h < numHouses; h++) {
            int minDist = Integer.MAX_VALUE;
            for (int fsId : selectedIds) {
                int d = distanceMatrix[h][fsId];
                if (d >= 0 && d < minDist) minDist = d;
            }
            // Rumah tak terjangkau dianggap paling buruk
            if (minDist == Integer.MAX_VALUE) minDist = Integer.MAX_VALUE;

            if (minDist > worstDist) {
                worstDist = minDist;
                worstHouse = h;
            }
        }
        return worstHouse;
    }

    /**
     * Memilih sel kosong terdekat ke rumah tertentu dari sejumlah sampel acak.
     * Sampling dipakai agar tetap cepat pada grid besar; naikkan
     *
     * @param house           indeks rumah tujuan
     * @param used            penanda ID sel kosong yang sudah dipakai FS
     * @param totalEmptyCells batas ID sel kosong
     * @param rand            objek Random
     * @return ID sel kosong terbaik, atau -1 jika tidak ada kandidat valid
     */
    private int pickClosestFreeCell(int house, boolean[] used,
                                    int totalEmptyCells, Random rand) {
        int bestCell = -1;
        int bestDist = Integer.MAX_VALUE;

        for (int k = 0; k < CANDIDATE_SAMPLES; k++) {
            int cand = rand.nextInt(totalEmptyCells) + 1;
            if (used[cand]) continue;

            int d = distanceMatrix[house][cand];
            if (d >= 0 && d < bestDist) {
                bestDist = d;
                bestCell = cand;
            }
        }
        return bestCell;
    }
}