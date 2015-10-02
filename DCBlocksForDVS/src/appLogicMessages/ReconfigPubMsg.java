package appLogicMessages;

import java.io.Serializable;
import java.util.ArrayList;
/**
 * 
 */
public class ReconfigPubMsg implements Serializable{

	private static final long serialVersionUID = 1L;
	private final int oldGroupId;
	private final int newGroupId;
	private final ArrayList<Integer> memberList;
	
	public enum reconfig {
		MERGE,
		SPLIT
	};
	
	public enum action {
		START,
		COMPLETE,
		ERROR
	};
	
	private final action actionType;
	private final reconfig reconfigType;
	
	public ReconfigPubMsg(int oldGroupId, int newGroupId, ArrayList<Integer> memberList, reconfig reconfigType, action actionType) {
		this.oldGroupId = oldGroupId;
		this.newGroupId = newGroupId;
		this.memberList = new ArrayList<Integer>(memberList);
		this.actionType = actionType;
		this.reconfigType = reconfigType;
	}

	public action getActionType() {
		return actionType;
	}


	public int getOldGroupId() {
		return oldGroupId;
	}

	public int getNewGroupId() {
		return newGroupId;
	}

	public ArrayList<Integer> getMemberList() {
		return memberList;
	}

	public reconfig getReconfigType() {
		return reconfigType;
	}

}
