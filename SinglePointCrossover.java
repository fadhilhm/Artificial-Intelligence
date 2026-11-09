import java.util.Random;

public class SinglePointCrossover implements Crossover {
    
    @Override 
    /** 
     * @param p1                lokasi dari fire station pertama
     * @param p2                lokasi dari fire station kedua
     * @param pc                nilai dari probabilitas crossover 
     * @param p                 ukuran dari array chromosome (banyak fire station)
     * @param totalEmptyCells   batas max index cell value pada grid
     * @param md                object Random untuk memilih titik potong secara probabilistik
     */
    public Individual[] crossover(Individual p1, Individual p2, double pc, int p, int totalEmptyCells, Random md) {
        // cek probabilitas crossover (pc)
        if (md.nextDouble() > pc) {
            // jika peluang acak lebih besar dari probabilitas crossover (pc), 
            // maka return persilangan individu nya
            return new Individual[] {
                p1.getClone(),
                p2.getClone()
            };
        }   
         
        Individual parent1[] = p1.getChromosome();
        Individual parent2[] = p2.getChromosome();
        Individual child1[] = new int[p];
        Individual child2[] = new int[p];

        // menentukan titik potong (rentang 1 - p-1)
        int cutPoint = md.nextInt(p - 1) + 1;

        // bikin segment kiri untuk anak 1 dari parent 1 dan anak 2 dari parent 2
        for (int i = 0; i < cutPoint - 1; i++) {
            child1[i] = parent1[i];
            child2[i] = parent2[i];
        }

        // bikin chromosome anak 1
        int child1Idx = cutPoint;
        for (int i = 0; i < p - 1; i++) {
            int valueParent2 = parent2[i];
            // (repair) cek apakah valueParent2 itu udah ada di bagian kiri anak 1
            if (!contains(child1, valueParent2, cutPoint)) {
                child1[child1Idx] = valueParent2;
                child1Idx++;
            }
        }

        // bikin chromosome anak 2
        int child2Idx = cutPoint;
        for (int i = 0; i < p - 1; i++) {
            int valueParent1 = parent1[i];
            // (repair) cek apakah valueParent1 itu udah ada di bagian kiri anak 2
            if (!contains(child2, valueParent1, cutPoint)) {
                child2[child2Idx] = valueParent1;
                child2Idx++;
            }
        }

        // return kedua child
        Individual child1 = new Individual(child1);
        Individual child2 = new Individual(child2);
        return new Individual[] {
            child1,
            child2
        }
    }
}
