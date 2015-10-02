package appLogicData;

import java.io.Serializable;

public class Current implements Serializable{	
	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;
	private Complex Ia = null;
	private Complex Ib = null;
	private Complex Ic = null;

	public Current() { 
		Ia = new Complex();
		Ib = new Complex();
		Ic = new Complex();
	};
	
	public Current(double ia_re, double ia_img, double ib_re, double ib_img, double ic_re, double ic_img)
	{
		
		Ia = new Complex(ia_re, ia_img);
		Ib = new Complex(ib_re, ib_img);
		Ic = new Complex(ic_re, ic_img);
	}
	
	public Current(Current toCopy)
	{
		Ia = new Complex(toCopy.Ia);
		Ib = new Complex(toCopy.Ib);
		Ic = new Complex(toCopy.Ic);
	}

	public Complex getIa() {
		return Ia;
	}

	public void setIa(Complex ia) {
		Ia = ia;
	}

	public Complex getIb() {
		return Ib;
	}

	public void setIb(Complex ib) {
		Ib = ib;
	}

	public Complex getIc() {
		return Ic;
	}

	public void setIc(Complex ic) {
		Ic = ic;
	}

	
	public void printCurrent() {
		System.out.println("Current values: A: " + getIa().getRe() + " +j " + getIa().getImg());
		System.out.println("Current values: B: " + getIb().getRe() + " +j " + getIb().getImg());
		System.out.println("Current values: C: " + getIc().getRe() + " +j " + getIc().getImg());
	}
}
