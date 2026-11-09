import java.util.Random;

/**
 * Interface ini mendefinisikan kontrak utama untuk berbagai strategi 
 * persilangan (crossover) yang dapat digunakan dalam Algoritma Genetik.
 * Setiap kelas turunan wajib mengimplementasikan metode crossover ini.
 */
public interface Crossover {
    /**
     * Melakukan persilangan antara dua individu induk (parent) untuk menghasilkan 
     * dua individu keturunan (offspring/children).
     * 
     * @param parent1         Individu induk pertama (Parent 1)
     * @param parent2         Individu induk kedua (Parent 2)
     * @param crossoverProb   Probabilitas crossover (Crossover Rate, misal 0.8 atau 80%)
     * @param p               Ukuran panjang kromosom (jumlah Fire Station yang ditempatkan)
     * @param totalEmptyCells Batas maksimum nilai ID sel kosong yang valid pada peta grid
     * @param rand            Objek Random untuk pengundian peluang acak secara probabilistik
     * @return                Array berisi dua objek Individual baru hasil keturunan
     */
    Individual[] crossover(Individual parent1, Individual parent2, double crossoverProb, int p, int totalEmptyCells, Random rand);
}