package appLogicMessages;

import java.io.Serializable;
import java.util.ArrayList;

public class LineDataMsg implements Serializable{
	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;
	private ArrayList<Integer> fromBus = new ArrayList<Integer>();
	private ArrayList<Integer> toBus = new ArrayList<Integer>();
	private ArrayList<Double> R = new ArrayList<Double>();
	private ArrayList<Double> X = new ArrayList<Double>();
	private ArrayList<Double> C = new ArrayList<Double>();
	private ArrayList<Double> Tap = new ArrayList<Double>();

	public ArrayList<Integer> getFromBus() {
		return fromBus;
	}

	public void setFromBus(int fromBus) {
		this.fromBus.add(fromBus);
	}

	public ArrayList<Integer> getToBus() {
		return toBus;
	}

	public void setToBus(int toBus) {
		this.toBus.add(toBus);
	}

	public ArrayList<Double> getC() {
		return this.C;
	}

	public void setC(double c) {
		this.C.add(c);
	}

	public ArrayList<Double> getTap() {
		return Tap;
	}

	public void setTap(double tap) {
		this.Tap.add(tap);
	}

	public ArrayList<Double> getR() {
		return this.R;
	}
	

	public void setR(double r) {
		this.R.add(r);
	}

	public ArrayList<Double> getX() {
		return this.X;
	}

	public void setX(double x) {
		this.X.add(x);
	}

	public int getLineCount() {
		return getToBus().size();
	}
	
	public void printValues() {
		System.out.println("Line data");
		System.out.println("From bus: " + getFromBus()
				+ "To Bus: " + getToBus()
				+ " R: " + getR()
				+ " X: " + getX()
				+ " C: " + getC()
				+ " Tap: " + getTap());
	}
}