package messages;

import java.io.Serializable;
import java.util.function.Function;

public class RegisterCBMsg implements Serializable{
	/**
	 * 
	 */
	private static final long serialVersionUID = -5623763471691051048L;
	public enum CallBackType {
		MEMBER_STATUS,
		LEADER_IDS,
		MERGE_REQ,
		ALL
	};
	
	private int groupId;
	private int nodeId;
	private Function<memStatus[], Boolean> statusRecvFunc;
	private Function<Integer[], Boolean> leaderRecvFunc;
	private Function<MergeGroupMsg, Boolean> mergeReqRecvFunc;
	private Function<SplitGroupMsg, Boolean> splitReqRecvFunc;
	private Function<Integer, Boolean> remReqRecvFunc;
	private Function<Integer, Boolean> addReqRecvFunc;
	private CallBackType CBType;
	
	public RegisterCBMsg(int groupId, int nodeId, CallBackType CBType) {
		this.groupId = groupId;
		this.nodeId = nodeId;
		this.CBType = CBType;
	}
	
	public int getGroupId() {
		return groupId;
	}
	
	public int setNodeId(int nodeId) {
		this.nodeId = nodeId;
		return this.nodeId;
	}
	
	public int getNodeId() {
		return nodeId;
	}
	
	public CallBackType getCBType() {
		return CBType;
	}
	
	public void setstatusRecvFunc(Function<memStatus[], Boolean> statusRecvFunc) {
		this.statusRecvFunc = statusRecvFunc;
	}
	
	public Function<memStatus[], Boolean> getstatusRecvFunc() {
		return statusRecvFunc;
	}
	
	public void setleaderRecvFunc(Function<Integer[], Boolean> leaderRecvFunc) {
		this.leaderRecvFunc = leaderRecvFunc;
	}
	
	public Function<Integer[], Boolean> getleaderRecvFunc() {
		return this.leaderRecvFunc;
	}

	public void setMergeReqRecvFunc(Function<MergeGroupMsg, Boolean> mergeReqReceiver) {
		// TODO Auto-generated method stub
		this.mergeReqRecvFunc = mergeReqReceiver;
	}
	
	public Function<MergeGroupMsg, Boolean> getMergeReqRecvFunc() {
		return this.mergeReqRecvFunc;
	}
	
	public void setSplitReqRecvFunc(Function<SplitGroupMsg, Boolean> splitReqReceiver) {
		// TODO Auto-generated method stub
		this.splitReqRecvFunc = splitReqReceiver;
	}
	
	public Function<SplitGroupMsg, Boolean> getSplitReqRecvFunc() {
		return this.splitReqRecvFunc;
	}
	
	public void setRemReqRecvFunc(Function<Integer, Boolean> remReqReceiver) {
		// TODO Auto-generated method stub
		this.remReqRecvFunc = remReqReceiver;
	}
	
	public Function<Integer, Boolean> getRemReqRecvFunc() {
		return this.remReqRecvFunc;
	}

	public void setAddReqRecvFunc(Function<Integer, Boolean> addReqReceiver) {
		// TODO Auto-generated method stub
		this.addReqRecvFunc = addReqReceiver;
	}
	
	public Function<Integer, Boolean> getAddReqRecvFunc() {
		return this.addReqRecvFunc;
	}
}
