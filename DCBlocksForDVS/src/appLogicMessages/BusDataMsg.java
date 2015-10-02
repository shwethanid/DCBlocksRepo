package appLogicMessages;

import java.io.Serializable;

import appLogicData.Current;
import appLogicData.Voltage;

public class BusDataMsg implements Serializable{
	
	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;
		private  Voltage volt;
		private  Current curr;
		private  int busNum;
		private  String busName;
		private  int busType;
		private  double shuntCap;
		private  double shuntReserv;

		public BusDataMsg() {
			volt = new Voltage();
			curr = new Current();
		}

		public Voltage getVolt() {
			return volt;
		}

		public void setVolt(Voltage volt) {
			//this.volt = new Voltage(volt);
			this.volt = volt;
		}

		public Current getCurr() {
			return curr;
		}

		public void setCurr(Current curr) {
			this.curr = new Current(curr);
		}

		public String getBusName() {
			return busName;
		}

		public void setBusName(String busName) {
			this.busName = busName;
		}

		public int getBusNum() {
			return busNum;
		}

		public void setBusNum(int busNum) {
			this.busNum = busNum;
		}

		public int getBusType() {
			return this.busType;
		}

		public void setBusType(int busType) {
			this.busType = busType;
		}

		public double getShuntCap() {
			return shuntCap;
		}

		public void setShuntCap(double shuntCap) {
			this.shuntCap = shuntCap;
		}

		public double getShuntReserv() {
			return shuntReserv;
		}

		public void setShuntReserv(double shuntReserv) {
			this.shuntReserv = shuntReserv;
		}
	}

