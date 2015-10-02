package appLogicMessages;

import java.io.Serializable;
import java.util.ArrayList;

public class BusListMsg implements Serializable{
	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;
	private ArrayList<BusDataMsg> busList = new ArrayList<BusDataMsg>();
	
	public ArrayList<BusDataMsg> getBusList() {
		return busList;
	}
	public void setBusList(ArrayList<BusDataMsg> busList) {
		this.busList = busList;
	}
	
}
