import java.util.Scanner;

public class Main {
    public static void main(String[] args) {
        Scanner sc = new Scanner(System.in);
        int m, n, p;

        m = sc.nextInt();
        n = sc.nextInt();
        p = sc.nextInt();

        char[][] petaGrid = new char[m][n];

        for (int i = 0; i < m; i++) {
            String line = sc.next();
            
            for (int j = 0; j < n; j++) {
                petaGrid[i][j] = line.charAt(j);
            }
        }

        sc.close();
    }   
}