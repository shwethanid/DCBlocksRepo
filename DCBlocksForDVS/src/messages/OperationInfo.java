package messages;

import java.io.Serializable;
import java.util.ArrayList;

import akka.actor.ActorRef;

public class OperationInfo implements Serializable{
	/**
	 * 
	 */
	public enum opnType {
		ADD_SINGLE_MEMBER,
		ADD_MEMBER_LIST,
		REMOVE_SINGLE_MEMBER,
		REMOVE_MEMBER_LIST,
		GET_GROUP_HEALTH_STATUS,
		MERGE_GROUPS,
		SPLIT_GROUPS,
		START_EXTERNAL_GROUP_CLIENTS,
		ADD_STANDALONE_NODE,
		REMOVE_STANDALONE_NODE,
		REGISTER_EXTERNAL_GROUPS,
		UNREGISTER_EXTERNAL_GROUPS,
		PUBLISH_EXTERNAL_GROUPS,
		RE_REGISTER,
		STOP_CLUSTER_CLIENT,
		START_CLUSTER_CLIENT,
		NONE
	};
	
	private static final long serialVersionUID = 1L;
	private opnType type;
	public ArrayList<Integer> idList = new ArrayList<Integer>();
	private int gid;
	private String topic;
	private ActorRef subRef;
	private Object msgToPub = null;
	private SplitGroupMsg splitInfo = null;
	private MergeGroupMsg mergeInfo = null;
	
	public OperationInfo(opnType type) {
		this.type = type;
	}
	public void setOperationType(opnType type) {
		this.type = type;
	}
	public opnType getOperationType() {
		return type;
	}
	
	public void setGid(int gid) {
		this.gid = gid;
	}
	public int getGid() {
		return this.gid;
	}
	
	public void setTopic(String topic) {
		this.topic = topic;
	}
	public String getTopic() {
		return this.topic;
	}
	
	public void setSubRef(ActorRef sub) {
		this.subRef = sub;
	}
	public ActorRef getSubRef() {
		return this.subRef;
	}
	
	public void setMsgToPub(Object msgToPub) {
		this.msgToPub = msgToPub;
	}
	public Object getMsgToPub() {
		return this.msgToPub;
	}

	public void setSplitInfo(SplitGroupMsg split) {
		this.splitInfo = new SplitGroupMsg(split);
	}
	
	public SplitGroupMsg getSplitInfo() {
		return this.splitInfo;
	}
	
	public void setMergeInfo(MergeGroupMsg merge) {
		this.mergeInfo = new MergeGroupMsg(merge);
	}
	
	public MergeGroupMsg getMergeInfo() {
		return this.mergeInfo;
	}
	
	
	public void setIdList(ArrayList<Integer> idList) {
		this.idList = new ArrayList<Integer> (idList);
	}
	public ArrayList<Integer> getIdList() {
		return this.idList;
	}
	
	public void addId(int id) {
		idList.add(id);
	}
}
