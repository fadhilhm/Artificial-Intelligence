import java.util.Random;

public class TournamentSelection implements Selection{
    private  int tournamentSize;

    public TournamentSelection(int tournamentSize){
        this.tournamentSize = tournamentSize;
    }

    @Override
    public Individual Select(Population population, Random rnd){
        int populationSize = Population.getPopulationSize();

        int ActualTournamentSize = Math.min(this.tournamentSize, populationSize);

        Individual best = null;

        for(int i = 0; i < ActualTournamentSize; i++){
            int randomIndex = rnd.nextInt(populationSize);
            Individual competitor = Population.getIndividual(randomIndex);
            
            if(best == null || competitor.getFitness() > best.getFitness()){
                best=competitor;
            }
        }

        return best;
    }
}