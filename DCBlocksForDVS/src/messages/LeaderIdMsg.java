package messages;

import java.io.Serializable;

/**
 * Group Leader Id message
 */
public class LeaderIdMsg implements Serializable {

	private static final long serialVersionUID = 1L;
	private Integer groupId = 0;
	private Integer[] leaderIds = new Integer[2];
	
	public LeaderIdMsg(Integer groupId) {
		this.groupId = groupId;
	}

	public Integer getGroupId() {
		return groupId;
	}
	
	public void setLeaderIds(Integer[] leaders) {
		this.leaderIds = leaders;
	}
	
	public Integer[] getLeaderIds() {
		return this.leaderIds;
	}
}
