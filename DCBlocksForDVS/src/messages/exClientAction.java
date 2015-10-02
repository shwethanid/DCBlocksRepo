package messages;

import java.io.Serializable;

public class exClientAction implements Serializable {

	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;

	public enum action {
		REMOVE, ADD
	}

	private final int groupId;
	private final action actionType;

	public exClientAction(int groupId, action actiontype) {
		this.groupId = groupId;
		this.actionType = actiontype;
	}

	public int getGroupId() {
		return groupId;
	}

	public action getActionType() {
		return actionType;
	}
	
	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result
				+ ((actionType == null) ? 0 : actionType.hashCode());
		result = prime * result + groupId;
		return result;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		exClientAction other = (exClientAction) obj;
		if (actionType != other.actionType)
			return false;
		if (groupId != other.groupId)
			return false;
		return true;
	}

}
