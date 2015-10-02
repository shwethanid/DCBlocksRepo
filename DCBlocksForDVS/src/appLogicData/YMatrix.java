package appLogicData;

import java.io.Serializable;
import java.util.ArrayList;

/**
 * This class implements Y matrix power data
 */
public class YMatrix implements Serializable {
	private static final long serialVersionUID = 1L;
	public int MAX_LINES = 0;
	public static final int COLS = 7;
	private static final int LINE_COL = 0;
	private static final int FROM_COL = 1;
	private static final int TO_COL = 2;
	private static final int R_COL = 3;
	private static final int X_COL = 4;
	private static final int C_COL = 5;
	private static final int TAP_COL = 6;
	private double ymatrix[][];
	private int busNum = 0;

	public YMatrix() {
		/*
		 * ymatrix = new double[MAX_LINES][]; for(int i=0; i< MAX_LINES; i++) {
		 * ymatrix[i] = new double[COLS]; }
		 */
	}

	public YMatrix(YMatrix ref) {
		setBusNum(ref.getBusNum());
		if (ref.getLen() > 0) {
			setYMatrix(ref.getYMatrix());
		}
	}

	public void setYMatrix(double[][] y) {
		setLen(y.length);
		if (y.length > 0) {
			ymatrix = new double[y.length][COLS];
			// System.out.println(y.length);
			for (int i = 0; i < y.length; i++) {
				for (int j = 0; j < COLS; j++) {
					ymatrix[i][j] = y[i][j];
				}
			}
		}
	}

	public void setYMatrix(ArrayList<double[]> y) {
		setLen(y.size());
		System.out.println("Y size: " + y.size());
		if (y.size() > 0) {
			ymatrix = new double[y.size()][COLS];
			double[] mm = new double[COLS];

			for (int i = 0; i < y.size(); i++) {
				System.arraycopy(y.get(i), 0, ymatrix[i], 0, ymatrix[i].length);
			}
		}
	}

	public void printYMatrix() {
		// System.out.println("Y Matrix: ");
		for (int i = 0; i < ymatrix.length; i++) {
			for (int j = 0; j < COLS; j++) {
				System.out.println(ymatrix[i][j] + " ");
			}
			System.out.println("\n");
		}
	}

	public double[][] getYMatrix() {
		return ymatrix;
	}

	public int getBusNum() {
		return busNum;
	}

	public void setBusNum(int busNum) {
		this.busNum = busNum;
	}

	public double[] getColumn(int col) {
		double y[] = new double[ymatrix.length];
		for (int i = 0; i < ymatrix.length; i++) {
			y[i] = ymatrix[i][col];
		}
		return y;
	}

	public double[] getLine() {
		return getColumn(LINE_COL);
	}

	public double[] getFb() {
		return getColumn(FROM_COL);
	}

	public double[] getTb() {
		return getColumn(TO_COL);
	}

	public double[] getR() {
		return getColumn(R_COL);
	}

	public double[] getX() {
		return getColumn(X_COL);
	}

	public double[] getC() {
		return getColumn(C_COL);
	}

	public double[] getTap() {
		return getColumn(TAP_COL);
	}

	public int getLen() {
		return MAX_LINES;
	}

	public void setLen(int len) {
		MAX_LINES = len;
	}
}
