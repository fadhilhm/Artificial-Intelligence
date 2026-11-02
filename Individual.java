import java.util.Random;

public class Individual {
    public int[] chromosome;
    public int fitness;
    public Random myRand;
    public double parentProbability;

    // membuat individu acak
    public Individual(Random myRand, int p, int titikKosong) {
        this.myRand = myRand;
        this.chromosome = new int[p];

        for (int i = 0; i < p; i++) {
            this.chromosome[i] = this.myRand.nextInt(0, titikKosong); 
        }

        this.fitness = 0;
        this.parentProbability = 0;
    }

    
}