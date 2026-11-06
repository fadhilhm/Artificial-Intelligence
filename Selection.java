import java.util.Random;

public interface Selection{

    Individual Select(Population population, Random rnd);
}