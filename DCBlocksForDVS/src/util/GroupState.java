package util;

public class GroupState {
	public enum groupState{
		STARTING,
		RUNNING,
		MERGING,
		SPLITTING,
		INACTIVE,
		NONE
	};
	private groupState currentState;
	
	public GroupState() {
		this.currentState = groupState.NONE;
	}
	
	public groupState getCurrentState() {
		return currentState;
	}
	public void setCurrentState(groupState currentState) {
		this.currentState = currentState;
	}
}
