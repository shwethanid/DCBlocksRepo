package appLogic;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;

import appLogicData.Complex;

public class DVSProcessor {

	private Complex imagUnit = new Complex(0.0, 1.0);

	private double Increment = 0.0; // percentage increment
	private double BaseMva = 100.0;
	private int maxBus = 30;
	// Grouping happens by DCBlock
	// private int[] grpList = new
	// int[]{1,2,3,4,5,6,7,8,9,10,11,12,13,14,15,16,17,18,19,20,21,22,23,24,25,26,27,28,29,30};
	// private int sizegrpList = nbus;

	// private int nbus = 0; // I need to set this to allocate memory of arrays
	// and matrix
	private int nbranch = 0;

	// bus data
	private Integer[] bus_num;
	private Integer[] type;
	private Complex[] VA;
	private Complex[] VB;
	private Complex[] VC;
	private Complex[] IA;
	private Complex[] IB;
	private Complex[] IC;
	private Double[] Shunt_DVS;
	private Double[] Shunt_reserved_DVS;

	/*
	 * // line data private Integer[] fb; // from bus number of branches private
	 * Integer[] tb; // to bus number of branches private Double[] R; private
	 * Double[] X; private Double[] C; private Double[] Tap;
	 */
	private Complex[] Ylc;
	private Complex[] Z;
	private Complex[] Y;
	double[] VVSI;

	// return value of Fn_DVS_control
	private int[] Degree;
	private double Qextra;
	// shunt is already declared
	private double Vdesired;
	private ArrayList<Integer> Priority = new ArrayList<Integer>();
	private int numGrp;
	private ArrayList<Integer> orgGrpList;
	private ArrayList<Integer> grpList;
	private int nbusOrg = 0;
	private int caCnt = 0;
	/**
	 * Constructor for DVSProcessor
	 * 
	 * @param numGrp
	 *            group number
	 * @param nbus
	 *            number of buses in the group
	 * @param nbranch
	 *            number of branches
	 * @param grpList
	 *            list of buses in the group
	 */
	public DVSProcessor(int numGrp, int nbus, ArrayList<Integer> grpList, int attempt) {
		this.numGrp = numGrp;
		// this.nbus = nbus;
		// this.nbranch = nbranch;
		type = new Integer[nbus];

		this.grpList = new ArrayList<Integer>(grpList);
		this.orgGrpList = new ArrayList<Integer>(grpList);
		//System.out.println("grp list: " + grpList);
		nbusOrg = nbus;
		this.caCnt = attempt;
	}

	// private int Find(int busNumber, int[] grp)
	private int Find(int busNumber, ArrayList<Integer> grp) {
		// System.out.println("Find busNumber: "+ busNumber+ "in grp: " + grp);
		for (int i = 0; i < grp.size(); i++) {
			if (grp.get(i) == busNumber) {
				// return index
				return i;

			}
		}

		// return -1 if not found
		return -1;
	}

	public ArrayList<Integer> find(double[] array) {
		ArrayList<Integer> indexList = new ArrayList<Integer>();

		for (int i = 0; i < array.length; i++) {
			if (array[i] != 0) {
				indexList.add(i);
			}
		}

		return indexList;
	}

	public int min(ArrayList<Double> array) {
		int index = 0;
		double min = array.get(0);
		for (int i = 1; i < array.size(); i++) {
			if (min > array.get(i)) {
				index = i;
				min = array.get(i);
			}
		}

		return index;

	}

	// inverse a square matrix
	// return Complex[][] of inversed matrix
	public Complex[][] InverseMatrix(Complex[][] a, int n) {

		int i, j, k;

		Complex x[][] = Complex.createComplexMatrix(n, n);
		Complex b[][] = Complex.createComplexMatrix(n, n);

		if (n == 0) {
			return x;
		}

		int index[] = new int[n];

		for (i = 0; i < n; ++i)
			b[i][i] = new Complex(1.0, 0);

		// Transform the matrix into an upper triangle
		double c[] = new double[n];

		// Initialize the index
		for (i = 0; i < n; ++i)
			index[i] = i;

		// Find the rescaling factors, one from each row
		for (i = 0; i < n; ++i) {
			double c1 = 0;
			for (j = 0; j < n; ++j) {
				double c0 = a[i][j].abs();
				if (c0 > c1)
					c1 = c0;
			}
			c[i] = c1;
		}

		// Search the pivoting element from each column
		k = 0;
		for (j = 0; j < n - 1; ++j) {
			double pi1 = 0;
			for (i = j; i < n; ++i) {
				double pi0 = a[index[i]][j].abs();
				pi0 /= c[index[i]];
				if (pi0 > pi1) {
					pi1 = pi0;
					k = i;
				}
			}

			// Interchange rows according to the pivoting order
			int itmp = index[j];
			index[j] = index[k];
			index[k] = itmp;
			for (i = j + 1; i < n; ++i) {
				Complex pj = a[index[i]][j].divideBy(a[index[j]][j]);

				// Record pivoting ratios below the diagonal
				a[index[i]][j] = pj;

				// Modify other elements accordingly
				for (int l = j + 1; l < n; ++l) {
					// a[index[i]][l] = a[index[i]][l]- pj*a[index[j]][l];
					a[index[i]][l] = a[index[i]][l].minus(pj
							.multiply(a[index[j]][l]));
				}

			}
		}

		// Update the matrix b[i][j] with the ratios stored
		for (i = 0; i < n - 1; ++i) {
			for (j = i + 1; j < n; ++j) {
				for (k = 0; k < n; ++k) {
					// b[index[j]][k] = b[index[j]][k]-
					// a[index[j]][i]*b[index[i]][k];
					b[index[j]][k] = b[index[j]][k].minus(a[index[j]][i]
							.multiply(b[index[i]][k]));
				}

			}

		}

		// Perform backward substitutions
		for (i = 0; i < n; ++i) {
			x[n - 1][i] = b[index[n - 1]][i].divideBy(a[index[n - 1]][n - 1]);
			for (j = n - 2; j >= 0; --j) {
				x[j][i] = b[index[j]][i];
				for (k = j + 1; k < n; ++k) {
					// x[j][i] = x[j][i] - a[index[j]][k]*x[k][i];
					x[j][i] = x[j][i].minus(a[index[j]][k].multiply(x[k][i]));
				}
				x[j][i] = x[j][i].divideBy(a[index[j]][j]);
			}
		}

		return x;
	}

	// parameters: YYBus, npv, npq, ntie, pv, pq, tie, V, Del, Pcal, Qcal
	// I don't pass Increment because not used

	public double[] Fn_DVS(Complex[][] YBus, int npv, int npq, int ntie,
			Integer[] pv, Integer[] pq, Integer[] tie, Double[] V,
			Double[] Del, Double[] Pcal, Double[] Qcal)// , double Increment)
	// double[] Pcal, double[] Qcal)// , double Increment)
	{
		int i, j, k, l;

		/*
		 * Start Calculating VSI for Distributed Voltage Stability in
		 * Transmission
		 */

		// variables for Calculating VSI
		/*
		 * complex<double> YLL[npq][npq]; complex<double> YLT[npq][ntie];
		 * complex<double> YLG[npq][npv]; complex<double> YTL[ntie][npq];
		 * complex<double> YTT[ntie][ntie]; complex<double> revYTT[ntie][ntie];
		 * complex<double> YTG[ntie][npv]; complex<double> YGL[npq][npv];
		 * complex<double> YGT[npv][ntie]; complex<double> YGG[npv][npv];
		 */

		Complex[][] YLL = Complex.createComplexMatrix(npq, npq);
		Complex[][] YLT = Complex.createComplexMatrix(npq, ntie);
		Complex[][] YLG = Complex.createComplexMatrix(npq, npv);
		Complex[][] YTL = Complex.createComplexMatrix(ntie, npq);
		Complex[][] YTT = Complex.createComplexMatrix(ntie, ntie);
		Complex[][] invYTT = Complex.createComplexMatrix(ntie, ntie);
		Complex[][] YTG = Complex.createComplexMatrix(ntie, npv);
		Complex[][] YGL = Complex.createComplexMatrix(npv, npq);
		Complex[][] YGT = Complex.createComplexMatrix(npv, ntie);
		Complex[][] YGG = Complex.createComplexMatrix(npv, npv);

		for (i = 0; i < npq; i++) {

			for (j = 0; j < npq; j++) {
				// YLL(i, j) = YBus(pq(i), pq(j));
				YLL[i][j] = YBus[pq[i] - 1][pq[j] - 1];

			}

			for (j = 0; j < ntie; j++) {
				YLT[i][j] = YBus[pq[i] - 1][tie[j] - 1];
			}

			for (j = 0; j < npv; j++) {

				YLG[i][j] = YBus[pq[i] - 1][pv[j] - 1];
			}
		}

		for (i = 0; i < ntie; i++) {
			for (j = 0; j < npq; j++) {
				YTL[i][j] = YBus[tie[i] - 1][pq[j] - 1];
			}

			for (j = 0; j < ntie; j++) {
				YTT[i][j] = YBus[tie[i] - 1][tie[j] - 1];
			}

			for (j = 0; j < npv; j++) {
				YTG[i][j] = YBus[tie[i] - 1][pv[j] - 1];
			}
		}

		for (i = 0; i < npv; i++) {
			for (j = 0; j < npq; j++) {
				YGL[i][j] = YBus[pv[i] - 1][pq[j] - 1];
			}

			for (j = 0; j < ntie; j++) {
				YGT[i][j] = YBus[pv[i] - 1][tie[j] - 1];
			}

			for (j = 0; j < npv; j++) {
				YGG[i][j] = YBus[pv[i] - 1][pv[j] - 1];
			}
		}

		/* ZLL = (YLL - YLT*YTT^-1*YTL)^-1; */

		Complex[][] ZLL = Complex.createComplexMatrix(npq, npq);
		Complex[][] partZLL1 = Complex.createComplexMatrix(npq, ntie); // result
																		// = YLT
																		// *
																		// revYTT
		Complex[][] partZLL2 = Complex.createComplexMatrix(npq, npq); // result
																		// * YTL

		// get inverse YTT
		invYTT = InverseMatrix(YTT, ntie);

		// calculate YLT * revYTT * YTL

		for (i = 0; i < npq; i++) {
			for (j = 0; j < ntie; j++) {
				partZLL1[i][j] = new Complex(0.0, 0.0);

				for (k = 0; k < ntie; k++) {
					partZLL1[i][j] = partZLL1[i][j].plus(YLT[i][k]
							.multiply(invYTT[k][j]));
				}
			}
		}

		for (i = 0; i < npq; i++) {
			for (j = 0; j < npq; j++) {
				partZLL2[i][j] = new Complex(0.0, 0.0);

				for (k = 0; k < ntie; k++) {
					partZLL2[i][j] = partZLL2[i][j].plus(partZLL1[i][k]
							.multiply(YTL[k][j]));
				}
			}
		}

		// complete calculating ZLL
		for (i = 0; i < npq; i++) {
			for (j = 0; j < npq; j++) {
				ZLL[i][j] = YLL[i][j].minus(partZLL2[i][j]);

			}

		}

		ZLL = InverseMatrix(ZLL, npq);

		/* ZLT = -ZLL*YLT*YTT^-1; */
		Complex[][] ZLT = Complex.createComplexMatrix(npq, ntie);
		Complex[][] partZLT = Complex.createComplexMatrix(npq, ntie); // ZLL*YLT

		for (i = 0; i < npq; i++) {
			for (j = 0; j < ntie; j++) {
				partZLT[i][j] = new Complex(0.0, 0.0);

				for (k = 0; k < npq; k++) {
					partZLT[i][j] = partZLT[i][j].plus(ZLL[i][k]
							.multiply(YLT[k][j]));
				}
			}
		}

		for (i = 0; i < npq; i++) {
			for (j = 0; j < ntie; j++) {
				ZLT[i][j] = new Complex(0.0, 0.0);

				for (k = 0; k < ntie; k++) {
					ZLT[i][j] = ZLT[i][j].plus(partZLT[i][k]
							.multiply(invYTT[k][j]));
				}

				ZLT[i][j] = ZLT[i][j].multiply(-1);
			}
		}

		/* HLG = ZLL*(YLT*YTT^-1*YTG - YLG); */
		Complex[][] HLG = Complex.createComplexMatrix(npq, npv);
		Complex[][] partHLG1 = Complex.createComplexMatrix(npq, ntie); // YLT*YTT^-1
		Complex[][] partHLG2 = Complex.createComplexMatrix(npq, npv); // partHLG1*YTG
																		// -
																		// YLG;

		for (i = 0; i < npq; i++) {
			for (j = 0; j < ntie; j++) {
				partHLG1[i][j] = new Complex(0.0, 0.0);

				for (k = 0; k < ntie; k++) {
					partHLG1[i][j] = partHLG1[i][j].plus(YLT[i][k]
							.multiply(invYTT[k][j]));
				}

			}
		}

		for (i = 0; i < npq; i++) {
			for (j = 0; j < npv; j++) {
				partHLG2[i][j] = new Complex(0.0, 0.0);

				for (k = 0; k < ntie; k++) {
					partHLG2[i][j] = partHLG2[i][j].plus(partHLG1[i][k]
							.multiply(YTG[k][j]));
				}

				partHLG2[i][j] = partHLG2[i][j].minus(YLG[i][j]);
			}
		}

		for (i = 0; i < npq; i++) {
			for (j = 0; j < npv; j++) {
				HLG[i][j] = new Complex(0.0, 0.0);

				for (k = 0; k < npq; k++) {
					HLG[i][j] = HLG[i][j].plus(ZLL[i][k]
							.multiply(partHLG2[k][j]));
				}

			}
		}

		/*
		 * Vs = V.*exp(1i*Del*pi/180); S = Pcal + 1i*Qcal; VL = V(pq); SL =
		 * S(pq); PL = -real(S(pq)); QL = imag(S(pq));
		 */

		int sizeV = V.length;
		Complex[] Vs = Complex.createComplexVector(sizeV);
		Complex[] S = Complex.createComplexVector(sizeV);

		for (i = 0; i < sizeV; i++) {
			// Vs = V.*exp(1i*Del*pi/180);
			Vs[i] = imagUnit.multiply(Del[i]).multiply((Math.PI / 180)).exp()
					.multiply(V[i]);

			S[i] = new Complex(-Pcal[i], -Qcal[i]);

		}

		// complex<double> VL[npq];
		Complex[] SL = Complex.createComplexVector(npq);
		double[] PL = new double[npq];
		double[] QL = new double[npq];

		j = 0;
		for (i = 0; i < sizeV; i++) {
			if (j < npq && i == (pq[j] - 1)) {
				// SL = S(pq);
				// PL = -real(S(pq));
				// QL = imag(S(pq));
				SL[j] = S[i];
				PL[j] = (+1) * S[i].real();
				QL[j] = S[i].imag();

				// System.out.println(i + " " + SL[j]);

				j++;
			}
		}

		/*
		 * I = YBus*conj(Vs); IL = I(pq); IT = I(tie); VG = Vs(pv);
		 */

		Complex[] I = Complex.createComplexVector(sizeV);
		Complex[] IL = Complex.createComplexVector(npq);
		Complex[] IT = Complex.createComplexVector(ntie);
		Complex[] VG = Complex.createComplexVector(npv);
		// Complex[] VsConj = Complex.createComplexVector(sizeV);

		for (i = 0; i < sizeV; i++) {
			I[i] = S[i].divideBy(Vs[i]).conj();
		}

		j = 0;
		k = 0;
		l = 0;
		for (i = 0; i < sizeV; i++) {
			if (j < npq && i == (pq[j] - 1)) {
				IL[j] = I[i];
				j++;
			}

			if (k < ntie && i == (tie[k] - 1)) {
				IT[k] = I[i];
				k++;
			}

			if (l < npv && i == (pv[l] - 1)) {
				VG[l] = Vs[i];
				l++;
			}
		}

		/*
		 * for j = 1 : npq HLG1 = 0; ZLL2 = 0;
		 * 
		 * ZLL1 = ZLL(j,j)*IL(j); for i = 1 : npq if i ~= j ZLL2 = ZLL2 +
		 * ZLL(j,i)*IL(i); end end
		 * 
		 * for k = 1 : npv HLG1 = HLG1 + HLG(j, k)*VG(k); end
		 * 
		 * vL(j) = ZLL1 + ZLL2 + HLG1; end
		 */

		Complex HLG1;
		Complex ZLL2;
		Complex ZLL1;
		Complex[] vL = Complex.createComplexVector(npq);

		for (j = 0; j < npq; j++) {
			HLG1 = new Complex(0.0, 0.0);
			ZLL2 = new Complex(0.0, 0.0);
			ZLL1 = ZLL[j][j].multiply(IL[j]);

			for (i = 0; i < npq; i++) {
				if (i != j) {
					ZLL2 = ZLL2.plus(ZLL[j][i].multiply(IL[i]));
				}
			}
			for (k = 0; k < npv; k++) {
				HLG1 = HLG1.plus(HLG[j][k].multiply(VG[k]));
			}

			vL[j] = ZLL1.plus(ZLL2).plus(HLG1);
		}

		Complex[] Vequ = Complex.createComplexVector(npq);
		Complex[] Zequ = Complex.createComplexVector(npq);
		/*
		 * for j = 1 : npq HLG1 = 0; ZLL2 = 0;
		 * 
		 * for i = 1 : npq if i ~= j ZLL2 = ZLL2 + ZLL(j,i)*IL(i); end end
		 * 
		 * for k = 1 : npv HLG1 = HLG1 + HLG(j, k)*VG(k); end
		 * 
		 * Vequ(j) = ZLL2 + HLG1;
		 * 
		 * Zequ(j) = ZLL(j,j);
		 * 
		 * end
		 */
		for (j = 0; j < npq; j++) {
			HLG1 = new Complex(0.0, 0.0);
			ZLL2 = new Complex(0.0, 0.0);

			for (i = 0; i < npq; i++) {
				if (i != j) {
					ZLL2 = ZLL2.plus(ZLL[j][i].multiply(IL[i]));
				}
			}
			for (k = 0; k < npv; k++) {
				HLG1 = HLG1.plus(HLG[j][k].multiply(VG[k]));
			}

			Vequ[j] = ZLL2.plus(HLG1);

			Zequ[j] = ZLL[j][j];
		}

		/*
		 * for j = 1 : npq AngleS(j) = atan(QL(j)/PL(j));
		 * 
		 * Smax(j) = Vequ(j)*conj(Vequ(j)./(2*Zequ(j)));
		 * 
		 * Pmax(j) = real(Smax(j)); Qmax(j) = imag(Smax(j));
		 * 
		 * VSI(1,j) = 1 - (Pmax(j)-PL(j))/Pmax(j); VSI(2,j) = 1 -
		 * (Qmax(j)-QL(j))/Qmax(j); VSI(3,j) = 1 -
		 * abs(Smax(j)-SL(j))/abs(Smax(j));
		 * 
		 * end
		 */

		double[] AngleS = new double[npq];
		;
		Complex[] Smax = Complex.createComplexVector(npq);
		double[] Pmax = new double[npq];
		;
		double[] Qmax = new double[npq];
		;
		double[][] VSI = new double[npq][3];

		for (j = 0; j < npq; j++) {
			// Smax(j) = Vequ(j)*conj(Vequ(j)./(2*Zequ(j)));
			Smax[j] = Vequ[j].multiply(Vequ[j].divideBy(Zequ[j].multiply(2.0))
					.conj());

			// Pmax(j) = real(Smax(j));
			// Qmax(j) = imag(Smax(j));
			Pmax[j] = Smax[j].real();
			Qmax[j] = Smax[j].imag();

			VSI[j][0] = 1.0 - (Pmax[j] - PL[j]) / Pmax[j];
			VSI[j][1] = 1.0 - (Qmax[j] - QL[j]) / Qmax[j];
			VSI[j][2] = 1.0 - Smax[j].minus(SL[j]).abs() / Smax[j].abs();

		}

		double[] VVSI = new double[npq];

		for (i = 0; i < npq; i++) {
			VVSI[i] = getMax(VSI[i]);
		}

		/* End Calculating VSI for Distributed Voltage Stability in Transmission */

		return VVSI;
	}

	// return max value in array
	public double getMax(double[] array) {
		double max;

		max = array[0];
		for (int i = 1; i < array.length; i++) {
			if (max < array[i]) {
				max = array[i];
			}
		}

		return max;

	}

	public ArrayList<Integer> getPriority() {
		return Priority;
	}
	
	public double getQextra() {
		return Qextra;
	}
	
	// parameters: Shunt_reserved, Shunt, VVSI, YBus, npq, V, Del, pq, nbus,
	// numDegree, Vd, Qcal
	// return double[] Shunt
	// other return values of Fn_DVS_control in MATLAB declared as data members
	// of this class
	public Double[] Fn_DVS_control(Double[] Shunt_reserved, Double[] Shunt,
			double[] VVSI, Complex[][] YBus, int npq, Double[] V, Double[] Del,
			Integer[] pq, int nbus, int numDegree, double[] Vd, Double[] Qcal) {

		int i, j, k;

		Qextra = 0.0;
		Priority.clear();
		/*
		 * delta = Del*pi/180; G = real(YBus); % Conductance B = imag(YBus); %
		 * Susceptance
		 */
		double[] delta = new double[nbus];
		double[][] G = new double[nbus][nbus];
		double[][] B = new double[nbus][nbus];

		for (i = 0; i < nbus; i++) {
			delta[i] = Del[i] * (Math.PI / 180.0);

			for (j = 0; j < nbus; j++) {
				G[i][j] = YBus[i][j].real();
				B[i][j] = YBus[i][j].imag();
			}
		}

		/*
		 * % find Electrical Distrance Qmax = Shunt_reserved - Shunt; [WeakBus,
		 * WeakIndex] = max(VVSI); WeakBus = pq(WeakIndex); Vdesired =
		 * Vd(WeakBus);
		 */

		double[] Qmax = new double[Shunt_reserved.length];
		for (i = 0; i < Shunt_reserved.length; i++) {
			// Qmax[i] = Shunt_reserved[i] - Shunt[i];
			Qmax[i] = Shunt_reserved[i];
			//System.out.println("Qmax: " + Qmax[i]);
		}

		int WeakIndex;

		double max = VVSI[0];
		WeakIndex = 0;

		for (i = 1; i < VVSI.length; i++) {
			if (max < VVSI[i]) {
				max = VVSI[i];
				WeakIndex = i; // WeakIndex starts its index with 0
			}
		}

		int WeakBus = pq[WeakIndex]; // weak bus number
		Vdesired = Vd[WeakBus - 1];

		/*
		 * % Calcuate Electrical Distance Ybus_Based_Weakbus = YBus(WeakBus, :);
		 * Qmax';
		 * 
		 * 
		 * Possible_Bus = Ybus_Based_Weakbus.*Qmax;
		 */
		Complex[] Ybus_Based_Weakbus = Complex.createComplexVector(Qmax.length);
		for (i = 0; i < nbus; i++) {
			Ybus_Based_Weakbus[i] = YBus[WeakBus - 1][i];
		}

		Complex[] Possible_Bus = Complex.createComplexVector(Qmax.length);

		for (i = 0; i < Possible_Bus.length; i++) {

			Possible_Bus[i] = Ybus_Based_Weakbus[i].multiply(Qmax[i]);
			// System.out.println("Possible_Bus: "+ Possible_Bus[i]);
		}

		// % 1 degree For control

		Degree = new int[V.length];
		double[] Priority_index = new double[V.length];

		int cnt_Degree = 1;
		Degree[cnt_Degree - 1] = WeakBus;

		Possible_Bus[Degree[cnt_Degree - 1] - 1].clear(); // real = 0.0, imag =
															// 0.0

		double[] index = new double[Possible_Bus.length];
		Arrays.fill(index, 1.0);

		for (i = 0; i < index.length; i++) {
			index[i] = index[i] * Qmax[i];

			if (index[i] != 0.0) {
				index[i] = i + 1; // index starts with 0
				// System.out.println("index[i]: "+ index[i]);
			}
		}

		Complex[][] Priority_YBus = YBus;
		for (i = 0; i < nbus; i++) {
			Priority_YBus[i][i].clear();
		}

		/*
		 * Prority_index(cnt_Degree) = index(WeakBus); Point = WeakBus; [r c
		 * Temp_matrix] = (find(imag(Prority_YBus(Point, :)))); index(WeakBus) =
		 * 0;
		 */
		Priority_index[cnt_Degree - 1] = index[WeakBus - 1];
		int Point = WeakBus; // Point starts with 1
		double[] imag_Priority_YBus = new double[Priority_YBus.length];
		for (i = 0; i < Priority_YBus.length; i++) {
			imag_Priority_YBus[i] = Priority_YBus[Point - 1][i].imag();

		}

		ArrayList<Integer> c = find(imag_Priority_YBus); // c starts with 0, r
															// is not used, so
															// not returned
		// System.out.println("c: "+ c);
		ArrayList<Double> Temp_matrix = new ArrayList<Double>();

		for (i = 0; i < c.size(); i++) {
			Temp_matrix.add(imag_Priority_YBus[c.get(i)]);

		}
		index[WeakBus - 1] = 0.0;
		// index[WeakBus - 1] = -1;
		// System.out.println("WeakBus-1: "+ (WeakBus-1));
		// System.out.println("Temp_matrix: "+ Temp_matrix);
		/*
		 * flag = 1; row = []; column = []; Temp_values = [];
		 */

		/*
		 * Temp2 = Temp_matrix; for jj = 1 : length(Temp_matrix) [cc ii] =
		 * min(Temp_matrix); Temp_matrix(ii) = 9999; cnt_Degree = cnt_Degree +
		 * 1; Prority_index(cnt_Degree) = index(c(ii)); index(c(ii)) = 0;
		 * 
		 * end
		 */

		// flag is not used,so not declare here
		ArrayList<Integer> row = new ArrayList<Integer>();
		ArrayList<Integer> column = new ArrayList<Integer>();
		ArrayList<Double> Temp_values = new ArrayList<Double>();

		Double[] Temp2 = Temp_matrix.toArray(new Double[Temp_matrix.size()]);

		/*
		 * for (i = 0; i < index.length; i++) { System.out.println("i: "+ i +
		 * "index: " + index[i]); }
		 */

		for (j = 0; j < Temp_matrix.size(); j++) {

			int ii = min(Temp_matrix);
			Temp_matrix.set(ii, 9999.0);
			cnt_Degree = cnt_Degree + 1;
			Priority_index[cnt_Degree - 1] = index[c.get(ii)];
			// System.out.println("[c.get(ii)]: " + c.get(ii) + "index: " +
			// index[c.get(ii)]);
			index[c.get(ii)] = 0;
			// index[c.get(ii)] = -1;
		}
		/*
		 * for (i = 0; i < index.length; i++) {
		 * System.out.println("indexqqqqqqqqq[i]: "+ index[i]); }
		 */

		/*
		 * for(j=0; j<Priority_index.length; j++) {
		 * System.out.println("Priority_index: "+ Priority_index[j]); }
		 */
		/*
		 * for kk = 1 : length(Temp_matrix) Point = c(kk); [r c Temp_matrix] =
		 * (find(imag(Prority_YBus(Point, :)))); row = [row r]; column= [column
		 * c]; Temp_matrix = Temp_matrix+Temp2(kk); Temp_values = [Temp_values
		 * Temp_matrix]; end
		 */
		ArrayList<Double> matrix = new ArrayList<Double>();

		for (k = 0; k < Temp_matrix.size(); k++) {
			if (k < c.size()) {
				Point = c.get(k); // here Point starts with 0 because c index
									// starts
									// with 0

				for (i = 0; i < Priority_YBus.length; i++) {
					imag_Priority_YBus[i] = Priority_YBus[Point][i].imag();
				}

				c = find(imag_Priority_YBus);
				matrix.clear();

				for (i = 0; i < c.size(); i++) {
					matrix.add(imag_Priority_YBus[c.get(i)] + Temp2[k]);

				}

				column.addAll(c);
				Temp_values.addAll(matrix);
			}

		}
		/*
		 * for(j=0; j<index.length; j++) { System.out.println("indexmmnmsdd: "+
		 * index[j]); }
		 */
		/*
		 * System.out.println("c: "+ c + "column: " + column + "Temp_values: " +
		 * Temp_values); for(j=0; j<Priority_index.length; j++) {
		 * System.out.println("Priority_index: "+ Priority_index[j]); }
		 */

		/*
		 * for jj = 1 : length(index) [cc ii] = min(Temp_values);
		 * Temp_values(ii) = 9999; if index(column(ii)) ~= 0 cnt_Degree =
		 * cnt_Degree + 1; Prority_index(cnt_Degree) = index(column(ii));
		 * index(column(ii)) = 0; end end
		 */

		for (j = 0; j < index.length; j++) {
			int ii = min(Temp_values);
			Temp_values.set(ii, 9999.0);

			if (index[column.get(ii)] != 0.0) // column index starts with 0
			// if (index[column.get(ii)] != -1) // column index starts with 0
			{
				cnt_Degree = cnt_Degree + 1;
				Priority_index[cnt_Degree - 1] = index[column.get(ii)];
				/*System.out
						.println("[column.get(ii)]: " + index[column.get(ii)]);*/
				index[column.get(ii)] = 0.0;
				// index[column.get(ii)] = -1;
			}

		}

		/*
		for (j = 0; j < Priority_index.length; j++) {
		System.out.println("Priority_index: " + Priority_index[j]); }*/
		 
		// Prority = nonzeros(Prority_index);

		for (i = 0; i < Priority_index.length; i++) {
			if (Priority_index[i] != 0.0) {
				// if (Priority_index[i] != -1) {
				Priority.add((int) Priority_index[i] - 1);// array index starts
															// with 0
			}
		}

		/*
		 * index = nonzeros(index); Prority = nonzeros(Prority_index); Prority =
		 * [Prority;index];
		 */
		for (i = 0; i < index.length; i++) {
			if (index[i] != 0.0) {
				// if (index[i] != -1) {
				Priority.add((int) index[i] - 1);// array index starts with 0
			}
		}
		System.out.println("Priority: " + Priority);

		/*
		 * for ii = 1 : length(pq) if Prority(numDegree) == pq(ii)
		 * point_of_control = ii; end end
		 */

		int point_of_control = 0;
		for (i = 0; i < pq.length; i++) {
			if (Priority.get(numDegree - 1) == (pq[i] - 1)) {
				point_of_control = i;
			}
		}
		point_of_control = Priority.get(numDegree - 1);
		/*System.out.println("point_of_control: " + point_of_control
				+ "numDegree: " + numDegree);*/
		/*
		 * %%% Control by degree 1 % J22 Diagonal J22 = zeros(npq,npq); Q22 =
		 * zeros(npq,npq);
		 * 
		 * for ii = 1:npq m = pq(ii); for k = 1:npq n = pq(k); if n == m for n =
		 * 1:nbus Q22(ii,k) = Q22(ii,k) - (V(m))* V(n)*sqrt(G(m,n)^2 +
		 * B(m,n)^2)*sin(atan2(B(m,n),G(m,n))-delta(m)+delta(n)); end J22(ii,k)
		 * = Q22(ii,k) - (V(m)^2)*B(m,m); end end end
		 */
		double[][] J22 = new double[nbus][nbus];
		double[][] Q22 = new double[nbus][nbus];
		int m, n;

		for (i = 0; i < nbus; i++) {
			// m = pq[i];
			m = i + 1;
			for (k = 0; k < nbus; k++) {

				for (j = 0; j < nbus; j++) {
					// Q22(ii,k) = Q22(ii,k) - (V(m))* V(n)*sqrt(G(m,n)^2 +
					// B(m,n)^2)*sin(atan2(B(m,n),G(m,n))-delta(m)+delta(n));
					Q22[i][k] = Q22[i][k]
							- V[m - 1]
							* V[j]
							* Math.sqrt(G[m - 1][j] * G[m - 1][j] + B[m - 1][j]
									* B[m - 1][j])
							* Math.sin(Math.atan2(B[m - 1][j], G[m - 1][j])
									- delta[m - 1] + delta[j]);

				}

				// J22(ii,k) = Q22(ii,k) - (V(m)^2)*B(m,m);
				J22[i][k] = Q22[i][k] - V[m - 1] * V[m - 1] * B[m - 1][m - 1];

			}
		}
		/*
		 * for (i = 0; i < nbus; i++) { for (k = 0; k < nbus; k++) {
		 * System.out.println("J22: "+ J22[i][k]); } }
		 */
		/*
		 * 
		 * % J22 Non-Diagonal for ii = 1:npq m = pq(ii); for k = 1:npq n =
		 * pq(k); if n ~= m J22(ii,k) = - (V(m))* V(n)*sqrt(G(m,n)^2 +
		 * B(m,n)^2)*sin(atan2(B(m,n),G(m,n))-delta(m)+delta(n)); end end end
		 */

		for (i = 0; i < nbus; i++) {
			m = i + 1;
			for (k = 0; k < nbus; k++) {
				// n = pq[k];
				for (n = 1; n <= nbus; n++) {
					// J22(ii,k) = - (V(m))* V(n)*sqrt(G(m,n)^2 +
					// B(m,n)^2)*sin(atan2(B(m,n),G(m,n))-delta(m)+delta(n));
					J22[i][k] = (-1)
							* V[m - 1]
							* V[n - 1]
							* Math.sqrt(G[m - 1][n - 1] * G[m - 1][n - 1]
									+ B[m - 1][n - 1] * B[m - 1][n - 1])
							* Math.sin(Math.atan2(B[m - 1][n - 1],
									G[m - 1][n - 1])
									- delta[m - 1]
									+ delta[n - 1]);
				}

			}
		}

		/*
		 * for (i = 0; i < nbus; i++) { for (k = 0; k < nbus; k++) {
		 * System.out.println("J22: "+ J22[i][k]); } }
		 */
		/*
		 * if Prority(numDegree) == WeakBus Qreq = (Vdesired -
		 * (V(Prority(numDegree))) * J22(point_of_control,point_of_control)) -
		 * Qcal(Prority(numDegree)); if Qreq < 0 Qreq = 0; end if Qreq <=
		 * Qmax(Prority(numDegree)) Shunt(Prority(numDegree)) =
		 * Shunt(Prority(numDegree)) + Qreq; Qextra = 0; else
		 * Shunt(Prority(numDegree)) = Shunt(Prority(numDegree)) +
		 * Qmax(Prority(numDegree)); Qextra = Qreq - Qmax(Prority(numDegree));
		 * end else Qreq = (Vdesired - (V(Prority(numDegree))) *
		 * J22(WeakIndex,point_of_control)) - Qcal(Prority(numDegree)); if Qreq
		 * < 0 Qreq = 0; end if Qreq <= Qmax(Prority(numDegree))
		 * Shunt(Prority(numDegree)) = Shunt(Prority(numDegree)) + Qreq; Qextra
		 * = 0; else Shunt(Prority(numDegree)) = Shunt(Prority(numDegree)) +
		 * Qmax(Prority(numDegree)); Qextra = Qreq - Qmax(Prority(numDegree));
		 * end end
		 */
		
		if (Priority.get(numDegree - 1).intValue() == (WeakBus - 1)) {
			int PriorityIndex = Priority.get(numDegree - 1);
			// Qreq = (Vdesired - (V(Prority(numDegree))) *
			// J22(point_of_control,point_of_control)) -
			// Qcal(Prority(numDegree));
			/*System.out.println("Vdesired: "+Vdesired + " PriorityIndex: " + PriorityIndex+ 
					" V[PriorityIndex]: " +V[PriorityIndex] + " J22[point_of_control]: " + J22[point_of_control]);*/
			Qcal[PriorityIndex] = -Qcal[PriorityIndex];
			double Qreq = ((Vdesired - V[PriorityIndex]) * J22[point_of_control][point_of_control])
					+ Qcal[PriorityIndex];
			if (Qreq < 0) {
				Qreq = 0.0;
			}

			if (Qreq <= Qmax[PriorityIndex]) {
				Shunt[PriorityIndex] += Qmax[PriorityIndex];
				Qextra = 0.0;
			} else {
				// Shunt(Prority(numDegree)) = Shunt(Prority(numDegree)) +
				// Qmax(Prority(numDegree));
				// Qextra = Qreq - Qmax(Prority(numDegree));
				Shunt[PriorityIndex] += Qmax[PriorityIndex];
				Qextra = Qreq - Qmax[PriorityIndex];

			}

		} else {
			int PriorityIndex = Priority.get(numDegree - 1);

			// Qreq = (Vdesired - (V(Prority(numDegree))) *
			// J22(WeakIndex,point_of_control)) - Qcal(Prority(numDegree));
			/*System.out.println("Vdesired: "+Vdesired + " PriorityIndex: " + PriorityIndex+ 
					" V[PriorityIndex]: " +V[PriorityIndex] + " point_of_control: " + point_of_control + " J22[point_of_control]: " + J22[point_of_control] + "WeakBus: " + WeakBus);*/
			double Qreq = ((Vdesired - V[PriorityIndex]) * J22[WeakBus - 1][point_of_control])
					- Qcal[PriorityIndex];

			if (Qreq < 0) {
				Qreq = 0.0;
			}

			/*
			 * if Qreq <= Qmax(Prority(numDegree)) Shunt(Prority(numDegree)) =
			 * Shunt(Prority(numDegree)) + Qreq; Qextra = 0; else
			 * Shunt(Prority(numDegree)) = Shunt(Prority(numDegree)) +
			 * Qmax(Prority(numDegree)); Qextra = Qreq -
			 * Qmax(Prority(numDegree)); end
			 */
			if (Qreq <= Qmax[PriorityIndex]) {
				Shunt[PriorityIndex] += Qreq;
				Qextra = 0.0;

			} else {
				Shunt[PriorityIndex] += Qmax[PriorityIndex];
				Qextra = Qreq - Qmax[PriorityIndex];

			}
		}

		// I don't create Qmax configuration file _ Hyesun
		return Shunt;

	}

	/**
	 * This methid runs the DVS algorithm
	 * 
	 * @param bNo
	 *            bus num
	 * @param bt
	 *            bus type (3:Slack, 2:PV, 0:PQ, 4:Tie Bus)
	 * @param VA
	 *            <bus, Voltage> mapping
	 * @param IA
	 *            <bus, Current> mapping
	 * @param Shunt
	 *            shunt capacitance
	 * @param Shunt_reserved
	 *            shunt cap reserved
	 * @param fb
	 *            from bus
	 * @param tb
	 *            to bus
	 * @param R
	 *            resistance
	 * @param X
	 *            impedance
	 * @param C
	 *            cap
	 * @param Tap
	 *            tap ratio
	 * @return true if success else false
	 */
	public Boolean processDVSData(ArrayList<Integer> bNo,
			ArrayList<Integer> bt, HashMap<Integer, Complex> VA,
			HashMap<Integer, Complex> IA, ArrayList<Double> Shunt,
			ArrayList<Double> Shunt_reserved, ArrayList<Integer> fb_DVS,
			ArrayList<Integer> tb_DVS, ArrayList<Double> R_DVS,
			ArrayList<Double> X_DVS, ArrayList<Double> C_DVS,
			ArrayList<Double> Tap_DVS, int nbranch) {

		// TODO Auto-generated method stub
		//System.out.println("nbranch: " + nbranch);

		Ylc = Complex.createComplexVector(nbranch);
		Z = Complex.createComplexVector(nbranch);
		Y = Complex.createComplexVector(nbranch);
		// Bus data
		this.type = bt.toArray(new Integer[bt.size()]);
		this.nbranch = nbranch;
		ArrayList<Complex> Ylc_DVS = new ArrayList<Complex>();
		ArrayList<Complex> Z_DVS = new ArrayList<Complex>();
		ArrayList<Complex> Y_DVS = new ArrayList<Complex>();

		for (int i = 0; i < fb_DVS.size(); i++) {
			Complex temp = new Complex(1.0, 0);

			// Ylc[i] = new Complex(0.0, C[i]/2);
			Ylc_DVS.add(new Complex(0.0, C_DVS.get(i) / 2));
			Z_DVS.add(new Complex(R_DVS.get(i), X_DVS.get(i)));
			Y_DVS.add(temp.divideBy(Z_DVS.get(i)));
		}
		/*
		 * for (int h = 0; h < fb_DVS.size(); h++) {
		 * System.out.println("Ylc_DVS: " + Ylc_DVS.get(h) + "		Z_DVS: " +
		 * Z_DVS.get(h) + "		Y_DVS: " + Y_DVS.get(h)); }
		 */
		int i, j, k, l;

		/*
		 * % This parameters are created by your DCBlock by collecting PMU data
		 * from % each bus in group. cnt = 0; for ii = 1 : length(X) for j = 1 :
		 * length(grpList) if fb(ii) == grpList(j) cnt = cnt + 1;
		 * 
		 * fb_DVS(cnt) = fb(ii); tb_DVS(cnt) = tb(ii);
		 * 
		 * R_DVS(cnt) = R(ii); X_DVS(cnt) = X(ii); C_DVS(cnt) = C(ii);
		 * Tap_DVS(cnt) = Tap(ii);
		 * 
		 * Ylc_DVS(cnt) = Ylc(ii); Z_DVS(cnt) = Z(ii); Y_DVS(cnt) = Y(ii); end
		 * end end
		 */

		ArrayList<Complex> VA_DVS = new ArrayList<Complex>();
		ArrayList<Complex> IA_DVS = new ArrayList<Complex>();

		for (int fromBus : grpList) {
			VA_DVS.add(VA.get(fromBus));
			IA_DVS.add(IA.get(fromBus));
		}

		// System.out.println("VA_DVS: "+VA_DVS);
		// System.out.println("IA_DVS: "+IA_DVS);
		/*
		 * for ii = 1 : length(VA) for j = 1 : length(grpList) if bus_num(ii) ==
		 * grpList(j) cnt = cnt + 1; VA_DVS(cnt) = VA(ii); end end end
		 * VA_Mag_DVS = sqrt(real(VA_DVS).^2 + imag(VA_DVS).^2); VA_Ang_DVS =
		 * angle(VA_DVS); % in rad
		 */

		// int sizeVA_DVS = VA_DVS.size();
		int sizeVA_DVS = VA_DVS.size();
		ArrayList<Double> VA_Mag_DVS = new ArrayList<Double>();
		ArrayList<Double> VA_Ang_DVS = new ArrayList<Double>();

		ArrayList<Double> IA_Mag_DVS = new ArrayList<Double>();
		ArrayList<Double> IA_Ang_DVS = new ArrayList<Double>();
		ArrayList<Double> Del_DVS = new ArrayList<Double>();
		// VA_Mag_DVS = sqrt(real(VA_DVS).^2 + imag(VA_DVS).^2);
		// VA_Ang_DVS = angle(VA_DVS); % in rad

		for (i = 0; i < sizeVA_DVS; i++) {
			VA_Mag_DVS.add(Math.sqrt(Math.pow(VA_DVS.get(i).real(), 2.0)
					+ Math.pow(VA_DVS.get(i).imag(), 2.0)));

			VA_Ang_DVS.add(VA_DVS.get(i).phase()); // angle
			Del_DVS.add(VA_Ang_DVS.get(i) * (180 / Math.PI));
			IA_Mag_DVS.add(Math.sqrt(Math.pow(IA_DVS.get(i).real(), 2.0)
					+ Math.pow(IA_DVS.get(i).imag(), 2.0)));

			IA_Ang_DVS.add(IA_DVS.get(i).phase()); // angle
		}

		/*
		 * pv = find(type == 2 | type == 3); pq = find(type == 0); tie =
		 * find(type == 4);
		 */

		ArrayList<Integer> pv_DVS = new ArrayList<Integer>();
		ArrayList<Integer> pq_DVS = new ArrayList<Integer>();
		ArrayList<Integer> tie_DVS = new ArrayList<Integer>();

		for (i = 0; i < type.length; i++) {
			if (type[i] == 2 || type[i] == 3) {
				pv_DVS.add(i + 1);

			} else if (type[i] == 0) {
				pq_DVS.add(i + 1);
			} else if (type[i] == 4) {
				tie_DVS.add(i + 1);
			}
		}
		int nbus_DVS = pv_DVS.size() + pq_DVS.size() + tie_DVS.size();
		/*System.out.println("pv_DVS+pq_DVS+tie_DVS: "
				+ (pv_DVS.size() + pq_DVS.size() + tie_DVS.size()));*/
		// int npv = pv.size();
		// int npq = pq.size();
		// int ntie = tie.size();

		/*
		 * for ii = 1 : length(pv) for j = 1 : length(grpList) if pv(ii) ==
		 * grpList(j) cnt = cnt + 1; pv_DVS1(cnt) = pv(ii); pv_DVS(cnt) =
		 * find(grpList == pv_DVS1(cnt)); end end end
		 */

		/*
		 * for j = 1 : length(grpList) Shunt_DVS(j) = Shunt(grpList(j));
		 * Shunt_reserved_DVS(j) = Shunt_reserved(grpList(j)); end
		 */

		/*
		 * npv = length(pv_DVS); npq = length(pq_DVS); ntie = length(tie_DVS);
		 * nbus = npv + npq + ntie;
		 */

		// % Total power is calculated by PMU data.
		/*
		 * Scal_DVS = VA_DVS.*conj(IA_DVS); Pcal_DVS = real(Scal_DVS); Qcal_DVS
		 * = imag(Scal_DVS);
		 */

		// VA
		Complex[] Scal_DVS = Complex.createComplexVector(sizeVA_DVS);
		ArrayList<Double> Pcal_DVS = new ArrayList<Double>();
		ArrayList<Double> Qcal_DVS = new ArrayList<Double>();

		for (i = 0; i < sizeVA_DVS; i++) {
			Scal_DVS[i] = VA_DVS.get(i).multiply(IA_DVS.get(i).conj());
			Pcal_DVS.add(Scal_DVS[i].real());
			Qcal_DVS.add(Scal_DVS[i].imag());
		}
		// System.out.println("Pcal_DVS: "+Pcal_DVS);
		// System.out.println("Qcal_DVS: "+Qcal_DVS);

		ArrayList<Integer> tempGrpList = new ArrayList<Integer>(grpList);
		ArrayList<Integer> tempFbList = new ArrayList<Integer>();
		int fb_DVS_size = fb_DVS.size();
		int cntr = 1;
		for (i = 0; i < fb_DVS_size; i++) {
			Integer fromBus = fb_DVS.get(i);
			Integer toBus = tb_DVS.get(i);
			tempFbList.add(fb_DVS.get(i));
			// if(Find(tb_DVS.get(i), grpList) == -1)
			if (!grpList.contains(tb_DVS.get(i))) {
				// S_bound = VA(fb_DVS(ii))*(conj(IA(fb_DVS(ii)))) -
				// VA(tb_DVS(ii))*(conj(IA(tb_DVS(ii))));
				// System.out.println("frombus: "+ fromBus + "tobus" + toBus);

				Complex S_bound = VA.get(fromBus)
						.multiply(IA.get(fromBus).conj())
						.minus(VA.get(toBus).multiply(IA.get(toBus).conj()));

				int nextElem = maxBus + cntr;
				grpList.add(nextElem);
				cntr++;
				tie_DVS.add(grpList.indexOf(nextElem) + 1);
				VA_Mag_DVS.add(Math.sqrt(Math.pow(VA.get(tb_DVS.get(i)).real(),
						2.0) + Math.pow(VA.get(tb_DVS.get(i)).imag(), 2.0)));

				Del_DVS.add(VA.get(tb_DVS.get(i)).phase() * (180 / Math.PI));
				tempGrpList.add(tb_DVS.get(i));
				// Del_DVS(length(Grp1)) = angle(VA(fb_DVS(ii)))*180/pi;
				tb_DVS.remove(i);
				tb_DVS.add(i, nextElem);
				Shunt.add(0.0);
				Shunt_reserved.add(0.0);
				Pcal_DVS.add(-S_bound.real());
				Qcal_DVS.add(-S_bound.imag());
			} else if (!grpList.contains(fb_DVS.get(i))) {
				// System.out.println("frombus: "+ fromBus + "tobus" + toBus);

				Complex S_bound = VA.get(fromBus)
						.multiply(IA.get(fromBus).conj())
						.minus(VA.get(toBus).multiply(IA.get(toBus).conj()));

				int nextElem = maxBus + cntr;
				grpList.add(nextElem);
				cntr++;
				tie_DVS.add(grpList.indexOf(nextElem) + 1);
				VA_Mag_DVS.add(Math.sqrt(Math.pow(VA.get(fb_DVS.get(i)).real(),
						2.0) + Math.pow(VA.get(fb_DVS.get(i)).imag(), 2.0)));
				Del_DVS.add(VA.get(fb_DVS.get(i)).phase() * (180 / Math.PI));
				tempGrpList.add(fb_DVS.get(i));
				fb_DVS.remove(i);
				fb_DVS.add(i, nextElem);
				Shunt.add(0.0);
				Shunt_reserved.add(0.0);
				Pcal_DVS.add(-S_bound.real());
				Qcal_DVS.add(-S_bound.imag());
			}
		}
		/*System.out.println("fb list: " + tempFbList);
		System.out.println("VA_Mag_DVS: " + VA_Mag_DVS + "size: "
				+ VA_Mag_DVS.size());*/
		// double[] V_DVS = VA_Mag_DVS.clone();
		// Double[] V_DVS = VA_Mag_DVS.toArray(new Double[VA_Mag_DVS.size()]);
		// Double[] Del_DVS = VA_Ang_DVS.toArray(new Double[VA_Ang_DVS.size()]);

		this.Shunt_DVS = new Double[Shunt.size()];
		this.Shunt_reserved_DVS = new Double[Shunt_reserved.size()];
		this.Shunt_DVS = Shunt.toArray(new Double[Shunt.size()]);
		this.Shunt_reserved_DVS = Shunt_reserved
				.toArray(new Double[Shunt_reserved.size()]);
		/*System.out.println("shunt-DVS: " + Shunt_DVS.length
				+ "Shunt_reserved_DVS: " + Shunt_reserved_DVS.length);
		
		 System.out.println( "Shunt: " + Shunt + "Shunt_reserved: " +
		 Shunt_reserved);
		 
		System.out.println("fb_DVS: " + fb_DVS + " size: " + fb_DVS.size());
		System.out.println("tb_DVS: " + tb_DVS + " size: " + tb_DVS.size());
		System.out.println("new grp: " + grpList + " size: " + grpList.size());
		System.out.println("temp grp: " + tempGrpList + " size: "
				+ tempGrpList.size());*/

		// generate YBus
		// I don't call Ygen function anymore since Hyojong coded it here.
		/*
		 * % Off Diagonal YBus = zeros(length(grpList),length(grpList)); % YBus
		 * matrix for a = 1:length(R_DVS); if isempty(find(tb_DVS(a) ==
		 * grpList)) == 0 YBus(find(grpList == fb_DVS(a)),find(grpList ==
		 * tb_DVS(a))) = YBus(find(grpList == fb_DVS(a)),find(grpList ==
		 * tb_DVS(a)))-Y_DVS(a)/Tap_DVS(a); YBus(find(grpList ==
		 * tb_DVS(a)),find(grpList == fb_DVS(a))) = YBus(find(grpList ==
		 * fb_DVS(a)),find(grpList == tb_DVS(a))); end end
		 */

		Complex[][] YBus = Complex.createComplexMatrix(grpList.size(),
				grpList.size());
		int sizeR_DVS = R_DVS.size();

		for (int a = 0; a < sizeR_DVS; a++) {

			if (Find(tb_DVS.get(a), grpList) >= 0) {
				int fromBus = Find(fb_DVS.get(a), grpList);
				int toBus = Find(tb_DVS.get(a), grpList);
				YBus[fromBus][toBus] = YBus[fromBus][toBus].minus(Y_DVS.get(a)
						.divideBy(Tap_DVS.get(a)));
				YBus[toBus][fromBus] = YBus[fromBus][toBus];
				
/*				System.out.println("a: "+ a+ " from bus: " + fromBus +
				" to bus: " + toBus + " fb: " + fb_DVS.get(a) + " tb: " +
				tb_DVS.get(a) + " Y_DVS: " +
				Y_DVS.get(a)+"		YBus: "+YBus[fromBus][toBus]);*/
				 
			} else {
				// System.out.println("a: "+ a+ " tb: " + tb_DVS.get(a));
			}

		}

		/*
		 * % Diagonal for m = 1:length(grpList) for n =1:length(R_DVS) if
		 * fb_DVS(n) == grpList(m) if Tap_DVS(n) ~= 0; YBus(find(grpList(m) ==
		 * grpList),find(grpList(m) == grpList)) = YBus(find(grpList(m) ==
		 * grpList),find(grpList(m) == grpList)) + Y_DVS(n)/(Tap_DVS(n)^2) +
		 * Ylc_DVS(n) + 1*i*Shunt_DVS(m); else YBus(find(grpList(m) ==
		 * grpList),find(grpList(m) == grpList)) = YBus(find(grpList(m) ==
		 * grpList),find(grpList(m) == grpList)) + Y_DVS(n) + Ylc_DVS(n) +
		 * 1*i*Shunt_DVS(m); end end end end
		 */
		int m, n;
		for (m = 0; m < grpList.size(); m++) {
			for (n = 0; n < sizeR_DVS; n++) {
				if (fb_DVS.get(n).intValue() == grpList.get(m).intValue()) {
					Complex shuntImag = new Complex(0.0, Shunt_DVS[m]);

					int index = Find(grpList.get(m), grpList);

					if (Tap_DVS.get(n) != 0.0) {

						YBus[index][index] = YBus[index][index].plus(Y_DVS
								.get(n).divideBy(Math.pow(Tap_DVS.get(n), 2.0))
								.plus(Ylc_DVS.get(n)).plus(shuntImag));
					} else {
						YBus[index][index] = YBus[index][index]
								.plus(Y_DVS.get(n)).plus(Ylc_DVS.get(n))
								.plus(shuntImag);
					}
					//System.out.println("YBus: "+ YBus[index][index] + "index: " + index);
				}
			}
		}

		/*
		 * for m = 1:length(grpList) for n =1:length(R_DVS) if tb_DVS(n) ==
		 * grpList(m) YBus(find(grpList(m) == grpList),find(grpList(m) ==
		 * grpList)) = YBus(find(grpList(m) == grpList),find(grpList(m) ==
		 * grpList)) + Y_DVS(n) + Ylc_DVS(n) + 1*i*Shunt_DVS(m); end end end
		 */
		for (m = 0; m < grpList.size(); m++) {

			for (n = 0; n < sizeR_DVS; n++) {
				if (tb_DVS.get(n).intValue() == grpList.get(m).intValue()) {
					Complex shuntImag = new Complex(0.0, Shunt_DVS[m]);

					int index = Find(grpList.get(m), grpList);

					YBus[index][index] = YBus[index][index].plus(Y_DVS.get(n))
							.plus(Ylc_DVS.get(n)).plus(shuntImag);
					//System.out.println("YBus: "+ YBus[index][index] + "index: " + index);
				}
				
			}
		}

		Complex[][] YBus_DVS = YBus.clone();

		/*
		 * Vdesired = ones(1,length(IC)); [VVSI] = Fn_DVS(YBus_DVS, npv, npq,
		 * ntie, pv_DVS, pq_DVS, tie_DVS, V_DVS, Del_DVS, Pcal_DVS, Qcal_DVS,
		 * Increment); G = real(YBus_DVS); % Conductance B = imag(YBus_DVS); %
		 * Susceptance delta = Del_DVS*pi/180;
		 */

		int npv = pv_DVS.size();
		int npq = pq_DVS.size();
		int ntie = tie_DVS.size();
		// int nbus_DVS = npq + npq + ntie; // nbus is already taken to refer to
		// the number of buses of the whole
		// system. so I used nbus_DVS
		Double[] V_DVS = new Double[VA_Mag_DVS.size()];
		V_DVS = VA_Mag_DVS.toArray(new Double[VA_Mag_DVS.size()]);

		/*System.out.println(" npv: " + npv + " npq: " + npq + " ntie: " + ntie
				+ " nbus_DVS: " + nbus_DVS + " pv_DVS: " + pv_DVS + " pq_DVS: "
				+ pq_DVS + " tie_DVS: " + tie_DVS + "V_DVS size: "
				+ V_DVS.length);
		for (int h = 0; h < V_DVS.length; h++) {
			System.out.println("fb_FVS: " + fb_DVS.get(h) + "		tb_DVS: "
					+ tb_DVS.get(h) + "		V_DVS: " + V_DVS[h] + "		Del_DVS: "
					+ Del_DVS.get(h) + " 	Pcal_DVS: " + Pcal_DVS.get(h)
					+ " 	Qcal_DVS: " + Qcal_DVS.get(h));
		}*/

		/*for (m = 0; m < grpList.size(); m++) {
			System.out.println("bg: ");
			for (n = 0; n < grpList.size(); n++) {
				System.out.println("YBus: " + YBus[m][n]);
			}
		}*/

		double[] Vdesired = new double[nbus_DVS];
		Arrays.fill(Vdesired, 1.1);

		VVSI = Fn_DVS(YBus_DVS, npv, npq, ntie,
				pv_DVS.toArray(new Integer[pv_DVS.size()]),
				pq_DVS.toArray(new Integer[pq_DVS.size()]),
				tie_DVS.toArray(new Integer[tie_DVS.size()]), V_DVS,
				Del_DVS.toArray(new Double[Del_DVS.size()]),
				Pcal_DVS.toArray(new Double[Pcal_DVS.size()]),
				Qcal_DVS.toArray(new Double[Qcal_DVS.size()]));

		for (i = 0; i < VVSI.length; i++) {
			if (VVSI[i] < 0) {
				VVSI[i] = 0;
			}
		}
		
		/*for (i = 0; i < VVSI.length; i++) {
			System.out.println(VVSI[i]);
		}*/
		int Maximum_Control = 0;
		for (i = 0; i < Shunt_reserved_DVS.length; i++) {
			if (Shunt_reserved_DVS[i].doubleValue() != 0.0) {
				Maximum_Control ++;
			}
		}
		System.out.println("Maximum_Control: " + Maximum_Control);
		int numDegree = 0;
		// nbus_DVS = nbusOrg;
		for (int cnt = 0; cnt < Maximum_Control; cnt++) {

			// in matlab, it runs this once because it calls Fn_PowerFlow2 and
			// Fn_DVS
			// , so I only run this once since Hyojong comment I don't need to
			// call both funtions in java
			if (getMax(VVSI) > 0.7) {

				numDegree = numDegree + 1;

				// double[] Shunt_reserved, double[] Shunt, double[] VVSI,
				// Complex[][] YBus,
				// int npq, double[] V, double[] Del, int[] pq, int nbus, int
				// numDegree, double[] Vd, double[] Qcal
				Shunt_DVS = Fn_DVS_control(Shunt_reserved_DVS, Shunt_DVS, VVSI,
						YBus_DVS, npq, V_DVS,
						Del_DVS.toArray(new Double[Del_DVS.size()]),
						pq_DVS.toArray(new Integer[pq_DVS.size()]), nbus_DVS,
						numDegree, Vdesired,
						Qcal_DVS.toArray(new Double[Qcal_DVS.size()]));

				// Shunt = Shunt + Shunt_DVS;
				// System.out.println("Shunt: ");
				/*
				 * for (i = 0; i < Shunt.length; i++) {
				 * System.out.println(Shunt[i]);
				 * 
				 * Shunt[i] += Shunt_DVS[i]; }
				 */

			}
		}

		System.out.println("%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%");
		System.out.println("-----------------------------------------------------------------------------");
		System.out.println("---------------------- DVS monitor and control data--------------------------");
		System.out.println("          GROUP : " + orgGrpList);
	    System.out.println("-----------------------------------------------------------------------------");
	    System.out.println("VVSI: "); // check VVSI
		for (i = 0; i < VVSI.length; i++) {
			System.out.println(VVSI[i]);
		}
		System.out.println("");
		System.out.println("MAX VSI: " + getMax(VVSI) );
		System.out.println("-----------------------------------------------------------------------------");
		if(getMax(VVSI) > 0.7) {
			System.out.println("Control action performed. Attempt #: " + caCnt);
		
		System.out.println("Priority: " + Priority);
		System.out.println("-----------------------------------------------------------------------------");
		System.out.println("Qextra: " + Qextra);
	    System.out.println("------------------------------------------------------------------------------");
		} else {
			System.out.println("No Control action required");
		}
		System.out.println("");
	    System.out.println("%%% End Calculating VSI for Distributed Voltage Stability in Transmission %%%");
	    System.out.println("%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%");
		return true;
	}

}
