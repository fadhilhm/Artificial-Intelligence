import java.util.*;

/**
 * Kelas ini berfungsi untuk menjalankan algoritma Breadth-First Search (BFS) 
 * *flood fill* sekali di awal program guna membangun tabel lookup jarak dari setiap rumah ke seluruh sel kosong.
 */
public class BFS {

    // Helper class untuk BFS-nya
    static class Node {
        int row, col, dist;

        Node(int r, int c, int d) {
            this.row = r;
            this.col = c;
            this.dist = d;
        }
    }

    /**
     * Menghitung jarak terpendek dari satu titik rumah ke semua sel kosong di peta.
     * 
     * @param grid            Matriks karakter peta grid ('H' = Rumah, '.' = Sel Kosong, 'T' = Pohon/Tembok)
     * @param startRow        Indeks baris posisi rumah awal
     * @param startCol        Indeks kolom posisi rumah awal
     * @param cellID          Matriks ID unik untuk setiap sel kosong
     * @param totalEmptyCells Jumlah total sel kosong yang ada di peta
     * @return                Array berisi jarak terpendek dari rumah tersebut ke setiap ID sel kosong
     */
    public static int[] houseFloodFill(char[][] grid, int startRow, int startCol, int[][] cellID, int totalEmptyCells) {
        int m = grid.length;
        int n = grid[0].length;
        
        int[] distances = new int[totalEmptyCells + 1];
        Arrays.fill(distances, -1); // -1 menandakan sel tidak terjangkau

        boolean[][] visited = new boolean[m][n];
        Queue<Node> queue = new LinkedList<>();

        queue.add(new Node(startRow, startCol, 0));
        visited[startRow][startCol] = true;

        int[] dRow = {-1, 1, 0, 0};
        int[] dCol = {0, 0, -1, 1};

        while (!queue.isEmpty()) {
            Node curr = queue.poll();

            int id = cellID[curr.row][curr.col];
            if (id > 0) {
                distances[id] = curr.dist;
            }

            for (int i = 0; i < 4; i++) {
                int nextRow = curr.row + dRow[i];
                int nextCol = curr.col + dCol[i];

                if (nextRow >= 0 && nextRow < m && nextCol >= 0 && nextCol < n) {
                    if (!visited[nextRow][nextCol] && grid[nextRow][nextCol] != 'T' && grid[nextRow][nextCol] != 'H') {
                        visited[nextRow][nextCol] = true;
                        queue.add(new Node(nextRow, nextCol, curr.dist + 1));
                    }
                }
            }
        }
        return distances;
    }
}