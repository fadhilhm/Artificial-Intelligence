import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;

/**
 * Implementasi mutasi Model 2 dengan metode Neighbor Shift (Eksploitasi Lokal).
 * Metode ini menggeser posisi Fire Station ke salah satu sel kosong tetangga
 * terdekat (atas, bawah, kiri, atau kanan) untuk penyempurnaan solusi (fine-tuning).
 */
public class NeighborShiftMutation implements Mutation {

    private final Point[] cellCoordinates;
    private final int[][] cellID;

    // Arah pergerakan 4 penjuru (atas, bawah, kiri, kanan) sesuai aturan grid BFS
    private static final int[] D_ROW = {-1, 1, 0, 0};
    private static final int[] D_COL = {0, 0, -1, 1};

    /**
     * Konstruktor untuk menerima lookup table koordinat dan matriks ID sel dari Main.
     *
     * @param cellCoordinates Array pemetaan dari cellID (1-based) ke koordinat (row, col)
     * @param cellID          Matriks peta yang menyimpan ID sel kosong (> 0 jika kosong)
     */
    public NeighborShiftMutation(Point[] cellCoordinates, int[][] cellID) {
        this.cellCoordinates = cellCoordinates;
        this.cellID = cellID;
    }

    @Override
    public void mutate(Individual individual, double mutationRate, int totalEmptyCells, Random rand) {
        int[] chromosome = individual.getChromosome();

        // Guard: jika jumlah sel kosong <= jumlah fire station, tidak ada ruang untuk berpindah
        if (totalEmptyCells <= chromosome.length) {
            return;
        }

        int numRows = cellID.length;
        int numCols = cellID[0].length;
        boolean isMutated = false;

        for (int i = 0; i < chromosome.length; i++) {
            // Cek probabilitas mutasi untuk gen ke-i
            if (rand.nextDouble() < mutationRate) {
                int currentCellId = chromosome[i];
                Point currentPoint = cellCoordinates[currentCellId];

                List<Integer> validNeighborIds = new ArrayList<>();

                // Periksa 4 sel tetangga di sekitar posisi Fire Station saat ini
                for (int dir = 0; dir < 4; dir++) {
                    int nextRow = currentPoint.row + D_ROW[dir];
                    int nextCol = currentPoint.col + D_COL[dir];

                    // Pastikan koordinat masih di dalam batas peta grid
                    if (nextRow >= 0 && nextRow < numRows && nextCol >= 0 && nextCol < numCols) {
                        int neighborId = cellID[nextRow][nextCol];

                        // neighborId > 0 menandakan sel tersebut adalah '.' (bukan 'H' atau 'T')
                        if (neighborId > 0 && !isDuplicate(chromosome, neighborId)) {
                            validNeighborIds.add(neighborId);
                        }
                    }
                }

                // Jika terdapat sel tetangga kosong yang belum ditempati, pilih salah satu secara acak
                if (!validNeighborIds.isEmpty()) {
                    int chosenNeighborId = validNeighborIds.get(rand.nextInt(validNeighborIds.size()));
                    chromosome[i] = chosenNeighborId;
                    isMutated = true;
                }
            }
        }

        // Jika terjadi perubahan, urutkan kromosom dan reset cost/fitness untuk dievaluasi ulang
        if (isMutated) {
            Arrays.sort(chromosome);

            individual.setChromosome(chromosome);
            individual.setCost(0.0);
            individual.setFitness(0.0);
        }
    }

    /**
     * Memastikan ID sel tujuan belum digunakan oleh Fire Station lain dalam kromosom.
     */
    private boolean isDuplicate(int[] chromosome, int targetCellId) {
        for (int gene : chromosome) {
            if (gene == targetCellId) {
                return true;
            }
        }
        return false;
    }
}