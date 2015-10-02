package appLogicMessages;

import java.io.Serializable;
import java.util.ArrayList;

public class ExtIdMsg implements Serializable{

	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;
	ArrayList<Integer> idList = new ArrayList<Integer>();
	
	public ArrayList<Integer> getIdList() {
		return idList;
	}
	
	public void setIdList(ArrayList<Integer> list) {
		idList = list;
	}
}
