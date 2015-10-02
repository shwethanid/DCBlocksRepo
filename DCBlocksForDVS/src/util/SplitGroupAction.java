package util;

import java.util.ArrayList;
import java.util.function.Function;

import messages.SplitGroupMsg;
import util.GroupState.groupState;
import akka.util.Timeout;

public class SplitGroupAction extends SplitGroupMsg{
	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;
	ArrayList<Integer> memberList = new ArrayList<Integer>();
	Function<Integer, Boolean> actionComplete;
	Boolean oldGroupsizeComplete = false;
	Boolean newGroupsizeComplete = false;
	
	public SplitGroupAction(int oldGroup, int newGroup, ArrayList<Integer> memberList, int oldGroupNewSize, int starterId) {
		super(oldGroup, newGroup, memberList, oldGroupNewSize, memberList.size(), starterId);
	}
		
	public void setActionComplete(Function<Integer, Boolean> actionComplete) {
		this.actionComplete = actionComplete;
	}
	public Function<Integer, Boolean> getActionComplete() {
		return actionComplete;
	}
	public void setOldGroupsizeComplete(Boolean oldGroupsizeComplete) {
		this.oldGroupsizeComplete = oldGroupsizeComplete;
	}
	
	public Boolean getOldGroupsizeComplete() {
		return this.oldGroupsizeComplete;
	}
	
	public void setNewGroupsizeComplete(Boolean newGroupsizeComplete) {
		this.newGroupsizeComplete = newGroupsizeComplete;
	}
	
	public Boolean getNewGroupsizeComplete() {
		return this.newGroupsizeComplete;
	}
}
