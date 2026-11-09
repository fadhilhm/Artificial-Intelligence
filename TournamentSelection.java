import java.util.Random;

/**
 * Kelas ini mengimplementasikan teknik Tournament Selection (Seleksi Turnamen).
 * Cara kerjanya adalah mengambil sejumlah kecil individu secara acak dari populasi 
 * (sebanyak ukuran turnamen / k), lalu membandingkan nilai fitness mereka. 
 * Individu dengan nilai fitness tertinggi di kelompok kecil tersebut akan keluar sebagai pemenang.
 */
public class TournamentSelection implements Selection{
    private  int tournamentSize;

    public TournamentSelection(int tournamentSize){
        this.tournamentSize = tournamentSize;
    }

    /**
     * Melakukan proses seleksi dengan mengadu sejumlah kandidat acak sebanyak 'tournamentSize'.
     * 
     * @param population Objek populasi tempat kandidat induk akan diambil
     * @param rand       Objek Random untuk memilih indeks kandidat secara acak dari populasi
     * @return           Salinan (clone) dari individu pemenang turnamen yang siap dijadikan parent
     */
    @Override
    public Individual select(Population population, Random rnd){
        int populationSize = population.getPopulationSize();

        // jika ukuran populasi lebih kecil, maka k menggunakan ukuran populasi
        int actualTournamentSize = Math.min(this.tournamentSize, populationSize);

        // individu terbaik
        Individual best = null;

        // melakukan perulangan sebanyak ukuran turnamen (k) untuk mengambil kandidat secara acak
        for(int i = 0; i < actualTournamentSize; i++){
            // Pilih indeks individu secara acak dari seluruh total populasi
            int randomIndex = rnd.nextInt(populationSize);
            Individual competitor = population.getIndividual(randomIndex);
            
            // bandingkan fitness individu terbaik dengan individu lain
            if (best == null || competitor.getFitness() > best.getFitness()){
                best=competitor;
            }
        }

        return best;
    }
}