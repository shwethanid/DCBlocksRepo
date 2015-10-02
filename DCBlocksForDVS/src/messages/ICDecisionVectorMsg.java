package messages;

import java.io.Serializable;
import java.util.HashMap;

public class ICDecisionVectorMsg implements Serializable{

	/**
	 * 
	 */
	private static final long serialVersionUID = -3744986859367926752L;
	
	private HashMap<Integer, Integer> finalList;//Key: node Id, Value: proposed value
	
	public ICDecisionVectorMsg() {
		finalList = new HashMap<Integer, Integer> ();
	}
	
	public ICDecisionVectorMsg(HashMap<Integer, Integer> finalMap) {
		setFinalList(new HashMap<Integer, Integer> (finalMap));
	}
	
	public HashMap<Integer, Integer> getFinalList() {
		return finalList;
	}

	public void setFinalList(HashMap<Integer, Integer> finalList) {
		this.finalList = finalList;
	}
}
