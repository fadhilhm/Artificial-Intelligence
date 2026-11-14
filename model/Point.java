package model;

/**
 * Kelas ini merepresentasikan koordinat posisi (baris dan kolom) pada grid peta.
 */
public class Point {
    public int row; // Indeks baris pada grid
    public int col; // Indeks kolom pada grid

    /**
     * Konstruktor untuk membuat objek Point baru.
     * @param row Indeks baris
     * @param col Indeks kolom
     */
    public Point(int row, int col) {
        this.row = row;
        this.col = col;
    }
}