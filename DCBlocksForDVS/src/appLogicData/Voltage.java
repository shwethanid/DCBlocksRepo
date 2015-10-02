package appLogicData;

import java.io.Serializable;

public class Voltage implements Serializable{
	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;
	private Complex Va = null;
	private Complex Vb = null;
	private Complex Vc = null;
	
	public Voltage() {
		Va = new Complex();
		Vb = new Complex();
		Vc = new Complex();
	}
	
	public Voltage(Voltage toCopy) {
		Va = new Complex(toCopy.Va);
		Vb = new Complex(toCopy.Vb);
		Vc = new Complex(toCopy.Vc);
	}
	
	public Complex getVa() {
		return Va;
	}
	public void setVa(Complex va) {
		Va = va;
	}
	
	public Complex getVb() {
		return Vb;
	}
	public void setVb(Complex vb) {
		Vb = vb;
	}
	
	public Complex getVc() {
		return Vc;
	}
	public void setVc(Complex vc) {
		Vc = vc;
	}

	public void printVoltage() {
		System.out.println("Voltage values: A: " + getVa().getRe() + " +j " + getVa().getImg());
		System.out.println("Voltage values: B: " + getVb().getRe() + " +j " + getVb().getImg());
		System.out.println("Voltage values: C: " + getVc().getRe() + " +j " + getVc().getImg());
	}
}
