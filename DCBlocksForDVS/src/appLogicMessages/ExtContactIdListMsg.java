package appLogicMessages;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;

/**
 * 
 */
public class ExtContactIdListMsg implements Serializable {

	private static final long serialVersionUID = 1L;
	private HashMap<Integer, ArrayList<Integer>> idList;// Key: igroup id, id
														// list

	public ExtContactIdListMsg() {
		setIdList(new HashMap<Integer, ArrayList<Integer>>());
	}

	public ExtContactIdListMsg(HashMap<Integer, ArrayList<Integer>> idMap) {
		setIdList(new HashMap<Integer, ArrayList<Integer>>(idMap));
	}

	public HashMap<Integer, ArrayList<Integer>> getIdList() {
		return idList;
	}

	public void setIdList(HashMap<Integer, ArrayList<Integer>> idList) {
		this.idList = idList;
	}
}
