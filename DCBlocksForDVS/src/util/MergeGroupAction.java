package util;

import java.util.function.Function;

import messages.MergeGroupMsg;

public class MergeGroupAction extends MergeGroupMsg {
	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;

	Function<MergeGroupMsg, Boolean> mergeActionComplete;

	
	/*public int getOldGroup() {
		return oldGroup;
	}
	public int getNewGroup() {
		return newGroup;
	}*/
	
	public MergeGroupAction(int oldGroup, int newGroup, int newGroupSize, int starterId) {
		super(oldGroup, newGroup, newGroupSize, starterId);
	}
	
	public void setActionComplete(Function<MergeGroupMsg, Boolean> actionTwo) {
		mergeActionComplete = actionTwo;
	}
	public Function<MergeGroupMsg, Boolean> getActionComplete() {
		return mergeActionComplete;
	}
}
