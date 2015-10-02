package appLogicMessages;

import java.io.Serializable;

import akka.actor.ActorRef;

public class LeaderInfoMsg implements Serializable{
	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;
	private int leaderId;
	private int groupId;
	private ActorRef leaderActor;
	
	public LeaderInfoMsg(LeaderInfoMsg info) {
		setLeaderId(info.leaderId);
		setGroupId(info.groupId);
		setLeaderActor(info.leaderActor);
	}
	
	public LeaderInfoMsg(int nid, int gid, ActorRef self) {
		setLeaderId(nid);
		setGroupId(gid);
		setLeaderActor(self);
	}
	public int getLeaderId() {
		return leaderId;
	}
	public void setLeaderId(int leaderId) {
		this.leaderId = leaderId;
	}
	public int getGroupId() {
		return groupId;
	}
	public void setGroupId(int groupId) {
		this.groupId = groupId;
	}
	public ActorRef getLeaderActor() {
		return leaderActor;
	}
	public void setLeaderActor(ActorRef leaderActor) {
		this.leaderActor = leaderActor;
	}
	
}
