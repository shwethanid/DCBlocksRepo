package util;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Function;

import messages.OperationInfo;
import messages.OperationInfo.opnType;
import messages.MergeGroupMsg;
import messages.RegisterCBMsg;
import messages.SplitGroupMsg;
import messages.StringMsg;
import messages.memStatus;
import messages.RegisterCBMsg.CallBackType;
import scala.concurrent.Await;
import scala.concurrent.Future;
import scala.concurrent.duration.Duration;
import util.GroupState.groupState;
import akka.actor.ActorRef;
import akka.pattern.Patterns;
import akka.util.Timeout;
import constants.Misc;

public class GroupCmdDispatcher {
	private ArrayList<Integer> memberList = new ArrayList<Integer>();
	private ArrayList<Integer> failedMemberList = new ArrayList<Integer>();
	private int gid = 0;
	private HashMap<Integer, Boolean> memberStatusMap = new HashMap<Integer, Boolean>();
	private Boolean onStartUp = true;
	private ArrayList<Function<String, Boolean>> readyCBList = new ArrayList<Function<String, Boolean>>();
	public ArrayList<Function<String, Boolean>> priLeaderFailCBList = new ArrayList<Function<String, Boolean>>();
	public ArrayList<Function<String, Boolean>> secLeaderFailCBList = new ArrayList<Function<String, Boolean>>();
	private Function<MergeGroupMsg, Boolean> mergeReqReceiver = null;
	private Function<SplitGroupMsg, Boolean> splitReqReceiver = null;
	private Function<Integer, Boolean> removeReqReceiver = null;
	private Function<Integer, Boolean> addReqReceiver = null;
	private int priLeaderId = 0;
	private int secLeaderId = 0;
	private ActorRef grpMgrRef = null;
	private GroupState gpState = new GroupState();
	private SplitGroupAction splitAction = null;
	private MyNode nodeInfo = null;
	private MergeGroupAction mergeAction = null;
	private Seed sd = null;
	final Lock lock = new ReentrantLock();
	private Boolean isDone = false;
	private int initialLimit = 0;
	
	public GroupCmdDispatcher() {

	}

	public int getGid() {
		return gid;
	}

	public GroupCmdDispatcher(MyNode nodeInfo, ActorRef grpMgrRef,
			Boolean onStartUp, Seed sd, int gid, int initialLimit) {
		this.nodeInfo = nodeInfo;
		this.gid = gid;
		this.grpMgrRef = grpMgrRef;
		this.onStartUp = onStartUp;
		this.setSd(sd);
		this.setInitialLimit(initialLimit);
	}

	/**
	 * Register with groupMgr actor for any member status change, for leader Ids
	 */
	public void registerForCallBack(Function<Integer, Boolean> addCB,
			Function<Integer, Boolean> remCB,
			Function<MergeGroupMsg, Boolean> mergeCB,
			Function<SplitGroupMsg, Boolean> splitCB) {
		
		if (grpMgrRef != null) {
			RegisterCBMsg regMsg = new RegisterCBMsg(gid, nodeInfo.getNodeid(),
					CallBackType.ALL);// Callback
			// type
			setMergeRecvFunc(mergeCB);
			setAddRecvFunc(addCB);
			setRemRecvFunc(remCB);
			setSplitRecvFunc(splitCB);
			regMsg.setstatusRecvFunc(statusReceiver);
			regMsg.setleaderRecvFunc(leaderIdReceiver);
			regMsg.setMergeReqRecvFunc(getMergeRecvFunc());
			regMsg.setRemReqRecvFunc(getRemRecvFunc());
			regMsg.setAddReqRecvFunc(getAddRecvFunc());
			regMsg.setSplitReqRecvFunc(getSplitRecvFunc());

			try {
				Timeout timeout = new Timeout(Duration.create(1, "seconds"));
				Future<Object> reply = Patterns.ask(grpMgrRef, regMsg, timeout);// in
																				// milliseconds

				Boolean isSuccess = (Boolean) Await.result(reply,
						timeout.duration());
			} catch (Exception e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
	}

	public Boolean addMember(int nodeId) {
		Boolean isSuccess = false;

		if ((grpMgrRef != null) && !memberList.contains(nodeId)) {
			OperationInfo opMsg = new OperationInfo(opnType.ADD_SINGLE_MEMBER);
			//opMsg.type = OperationInfo.opnType.ADD_SINGLE_MEMBER;
			opMsg.setGid(getGid());
			opMsg.getIdList().add(nodeId);
			try {
				Timeout timeout = new Timeout(Duration.create(1, "seconds"));
				Future<Object> reply = Patterns.ask(grpMgrRef, opMsg, timeout);// in
																				// milliseconds

				isSuccess = (Boolean) Await.result(reply, timeout.duration());
			} catch (Exception e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}

		return isSuccess;
	}

	public Boolean removeMember(int nodeId) {
		Boolean isSuccess = false;

		// if ((grpMgrRef != null) && memberList.contains(nodeId)) {
		if ((grpMgrRef != null) && memberList.contains(nodeId)) {
			OperationInfo opMsg = new OperationInfo(opnType.REMOVE_SINGLE_MEMBER);
			//opMsg.type = OperationInfo.opnType.REMOVE_SINGLE_MEMBER;
			opMsg.setGid(getGid());
			opMsg.getIdList().add(nodeId);
			try {
				Timeout timeout = new Timeout(Duration.create(1, "seconds"));
				Future<Object> reply = Patterns.ask(grpMgrRef, opMsg, timeout);// in
																				// milliseconds

				isSuccess = (Boolean) Await.result(reply, timeout.duration());
			} catch (Exception e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}

		return isSuccess;
	}

	public Boolean addMemberList(ArrayList<Integer> nodeIdList) {
		Boolean isSuccess = false;

		if ((grpMgrRef != null)) {
			OperationInfo opMsg = new OperationInfo(opnType.ADD_MEMBER_LIST);
			//opMsg.type = OperationInfo.opnType.ADD_MEMBER_LIST;
			opMsg.setGid(getGid());
			opMsg.getIdList().addAll(nodeIdList);
			try {
				Timeout timeout = new Timeout(Duration.create(1, "seconds"));
				Future<Object> reply = Patterns.ask(grpMgrRef, opMsg, timeout);// in
																				// milliseconds

				isSuccess = (Boolean) Await.result(reply, timeout.duration());
			} catch (Exception e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}

		return isSuccess;
	}

	public Boolean removeMemberList(ArrayList<Integer> nodeIdList) {
		Boolean isSuccess = false;

		if (grpMgrRef != null) {
			OperationInfo opMsg = new OperationInfo(opnType.REMOVE_MEMBER_LIST);
			//opMsg.type = OperationInfo.opnType.REMOVE_MEMBER_LIST;
			opMsg.setGid(getGid());
			opMsg.getIdList().addAll(nodeIdList);
			try {
				Timeout timeout = new Timeout(Duration.create(1, "seconds"));
				Future<Object> reply = Patterns.ask(grpMgrRef, opMsg, timeout);// in
																				// milliseconds

				isSuccess = (Boolean) Await.result(reply, timeout.duration());
			} catch (Exception e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}

		return isSuccess;
	}

	public ArrayList<Integer> getMemberList() {
		Collections.sort(this.memberList);
		return memberList;
	}

	public ArrayList<Integer> getFailedMemberList() {
		return failedMemberList;
	}

	public Boolean getMemberHealthStatus(int nodeId) {
		Boolean status = false;

		if (memberStatusMap.containsKey(nodeId)) {
			status = memberStatusMap.get(nodeId);
		}
		return status;
	}

	public HashMap<Integer, Boolean> getHealthStatusList() {
		return memberStatusMap;
	}

	Function<memStatus[], Boolean> statusReceiver = (statsList) -> {
		memStatus[] statusArr = statsList;
		if (statusArr != null)
			System.out.println("statusReceiver: statsList Size: "
					+ statusArr.length);

		processMemberStatusChange(statusArr);
		return true;
	};

	void processMemberStatusChange(memStatus[] statusArr) {
		lock.lock();
		memberList.clear();
		failedMemberList.clear();
		if (statusArr != null) {
			/*System.out
					.println("[groupCmdDispatcher class, processMemberStatusChange()]...statusArr length: "
							+ statusArr.length);*/
			for (int i = 0; i < statusArr.length; i++) {
				memStatus stats = statusArr[i];
				memberStatusMap.put(stats.getId(), stats.getIsFailed());
				memberList.add(stats.getId());

				if (stats.getIsFailed()) {
					/*System.out
					.println("[groupCmdDispatcher class, processMemberStatusChange()]...node faulty: "
							+ stats.getId());*/
					failedMemberList.add(stats.getId());
					if (stats.getId() == priLeaderId) {
						informPriLeaderFail();
					} else if (stats.getId() == secLeaderId) {
						informSecLeaderFail();
					}
				}
			}

			System.out
			.println("[groupCmdDispatcher class, processMemberStatusChange()]"
					+ "*********Group Id: " + nodeInfo.getGid()+ " Node Id: " + +  nodeInfo.getNodeid()+" MemberList: "
					+ memberList + " Size: " + memberList.size() + " Group limit is " + getInitialLimit() + "*********");
			System.out
					.println("[groupCmdDispatcher class, processMemberStatusChange(), group Id: " + nodeInfo.getGid() + " Node Id: " +  nodeInfo.getNodeid()+"]...MemberList size for group Id: "
							+ gid + " is " + memberList.size() + "Group limit is " + getInitialLimit());

			switch (gpState.getCurrentState()) {
			case STARTING:
				if (onStartUp) {
					//if (memberList.size() >= Misc.MIN_NODES_PER_GROUP) {
					if (memberList.size() >= initialLimit) {
						System.out.println("/********GROUP " + getGid() + " INIT SIZE: " + 
								+ memberList.size() + "**********/");
						System.out.println("GROUP " + getGid() + " INIT SIZE: " + 
								+ memberList.size());
						onStartUp = false;
						setGpState(groupState.RUNNING);
						informGroupReady();
					}
				}
				break;

			case MERGING:
				if (mergeAction != null) {
					System.out.println("MERGING..member stats: " + gid
							+ nodeInfo.getGid() + memberList.size());
					if (memberList.size() == mergeAction.getNewGroupSize()) {
						setGpState(groupState.RUNNING);
					}
					if ((gid == mergeAction.getNewGroup())
							&& (memberList.size() == mergeAction
									.getNewGroupSize())) {
						mergeAction.getActionComplete().apply(mergeAction);
						setGpState(groupState.RUNNING);
					}
				} else {
					System.out.println("Merge info is null");
				}
				break;

			case SPLITTING:
				if (splitAction != null) {
					processSplittingState();

				} else {
					System.out.println("Split info is null");
				}

				break;

			case RUNNING:
			case INACTIVE:
			default:
				break;
			}

		}
		lock.unlock();
	}

	public Boolean processMergeRequest(Integer mergeGrpId, int newGroupSize) {
		Boolean isSuccess = false;

		if (grpMgrRef != null) {
			OperationInfo opMsg = new OperationInfo(opnType.MERGE_GROUPS);
			//opMsg.type = OperationInfo.opnType.MERGE_GROUPS;
			MergeGroupMsg mergeinfo = new MergeGroupMsg(gid, mergeGrpId, newGroupSize, nodeInfo.getNodeid());
			opMsg.setMergeInfo(mergeinfo);

			System.out.println("Sending merge request to GroupMgr: ");
			opMsg.getMergeInfo().printParameters();

			try {
				Timeout timeout = new Timeout(Duration.create(30, "seconds"));
				Future<Object> reply = Patterns.ask(grpMgrRef, opMsg, timeout);// in
																				// milliseconds

				isSuccess = (Boolean) Await.result(reply, timeout.duration());
			} catch (Exception e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}

		return isSuccess;
	}

	public Boolean processSplitRequest(Integer oldGroupId, Integer newGroupId,
			ArrayList<Integer> memberList, Integer oldGroupNewSize) {
		Boolean isSuccess = false;

		if (grpMgrRef != null) {
			OperationInfo opMsg = new OperationInfo(opnType.SPLIT_GROUPS);
			//opMsg.type = OperationInfo.opnType.SPLIT_GROUPS;
			SplitGroupMsg splitinfo = new SplitGroupMsg(oldGroupId, newGroupId, memberList, oldGroupNewSize, memberList.size(), nodeInfo.getNodeid());
			opMsg.setSplitInfo(splitinfo);
			System.out.println("Sending split request to GroupMgr: ");
			opMsg.getSplitInfo().printParameters();

			try {
				Timeout timeout = new Timeout(Duration.create(60, "seconds"));
				Future<Object> reply = Patterns.ask(grpMgrRef, opMsg, timeout);// in
																				// milliseconds

				isSuccess = (Boolean) Await.result(reply, timeout.duration());
			} catch (Exception e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}

		return isSuccess;
	}

	public Boolean sendReRegisterRequest(int newGrpId) {
		Boolean isSuccess = false;
		System.out.println("*********Sending rergister request*********");
		if (grpMgrRef != null) {
			OperationInfo opMsg = new OperationInfo(opnType.RE_REGISTER);
			opMsg.setGid(newGrpId);
			//opMsg.type = OperationInfo.opnType.RE_REGISTER;

			try {
				Timeout timeout = new Timeout(Duration.create(60, "seconds"));
				Future<Object> reply = Patterns.ask(grpMgrRef, opMsg, timeout);// in
																				// milliseconds

				// isSuccess = (Boolean) Await.result(reply,
				// timeout.duration());
			} catch (Exception e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}

		return isSuccess;
	}

	private void informGroupReady() {
		for (int i = 0; i < readyCBList.size(); i++) {
			readyCBList.get(i).apply(StringMsg.GROUP_READY);
		}
	}

	private void informPriLeaderFail() {
		for (int i = 0; i < priLeaderFailCBList.size(); i++) {
			priLeaderFailCBList.get(i).apply(StringMsg.PRIMARY_LEADER_FAILURE);
		}
	}

	private void informSecLeaderFail() {
		for (int i = 0; i < secLeaderFailCBList.size(); i++) {
			secLeaderFailCBList.get(i)
					.apply(StringMsg.SECONDARY_LEADER_FAILURE);
		}
	}

	public void setPriLeaderId(int priLeaderId) {
		this.priLeaderId = priLeaderId;
		System.out.println("/********PRIMARY LEADER FOR GROUP: " + gid + " IS: "
				+ this.priLeaderId + "**********/");
	}

	public int getPriLeaderId() {
		return this.priLeaderId;
	}

	public void setSecLeaderId(int secLeaderId) {
		this.secLeaderId = secLeaderId;
		System.out.println("/********SECONDARY LEADER FOR GROUP: " + gid + " IS: "
				+ this.secLeaderId + "**********/");
	}

	public int getSecLeaderId() {
		return this.secLeaderId;
	}

	Function<Integer[], Boolean> leaderIdReceiver = (leaderIds) -> {
		System.out.println("Got leader Ids for Group: " + gid + " Pri: "
				+ leaderIds[0] + " Sec: " + leaderIds[1]);
		setPriLeaderId(leaderIds[0]);
		setSecLeaderId(leaderIds[1]);
		return true;
	};

	public void setMergeRecvFunc(Function<MergeGroupMsg, Boolean> mergeRecvFunc) {
		this.mergeReqReceiver = mergeRecvFunc;
	}

	public Function<MergeGroupMsg, Boolean> getMergeRecvFunc() {
		return mergeReqReceiver;
	}

	public void setSplitRecvFunc(Function<SplitGroupMsg, Boolean> splitCB) {
		this.splitReqReceiver = splitCB;
	}

	public Function<SplitGroupMsg, Boolean> getSplitRecvFunc() {
		return splitReqReceiver;
	}

	public void setRemRecvFunc(Function<Integer, Boolean> remRecvFunc) {
		this.removeReqReceiver = remRecvFunc;
	}

	public Function<Integer, Boolean> getRemRecvFunc() {
		return removeReqReceiver;
	}

	public void setAddRecvFunc(Function<Integer, Boolean> addRecvFunc) {
		this.addReqReceiver = addRecvFunc;
	}

	public Function<Integer, Boolean> getAddRecvFunc() {
		return addReqReceiver;
	}

	public groupState getGpState() {
		return this.gpState.getCurrentState();
	}

	public void setGpState(groupState gpState) {
		this.gpState.setCurrentState(gpState);
		System.out.println("/********NEW GROUP STATE FOR GROUP: " + gid + " "
				+ this.gpState.getCurrentState().toString() + "**********/");
	}

	public SplitGroupAction getsplitAction() {
		return splitAction;
	}

	public void setsplitAction(SplitGroupAction splitAction) {
		this.splitAction = splitAction;
	}

	public void setMergeAction(MergeGroupAction mergeAction) {
		System.out.println("setting merge info");
		this.mergeAction = (MergeGroupAction) mergeAction;
	}

	public void setSplitAction(SplitGroupAction splitAction) {
		System.out.println("setting split info");
		this.splitAction = (SplitGroupAction) splitAction;
	}

	public ArrayList<Function<String, Boolean>> getPriLeaderFailCBList() {
		return priLeaderFailCBList;
	}

	public void setPriLeaderFailCBList(
			ArrayList<Function<String, Boolean>> priLeaderFailCBList) {
		this.priLeaderFailCBList = priLeaderFailCBList;
	}

	public ArrayList<Function<String, Boolean>> getSecLeaderFailCBList() {
		return secLeaderFailCBList;
	}

	public void setSecLeaderFailCBList(
			ArrayList<Function<String, Boolean>> secLeaderFailCBList) {
		this.secLeaderFailCBList = secLeaderFailCBList;
	}

	public ArrayList<Function<String, Boolean>> getReadyCBList() {
		return readyCBList;
	}

	public void setReadyCBList(ArrayList<Function<String, Boolean>> readyCBList) {
		this.readyCBList = readyCBList;
	}

	public Seed getSd() {
		return sd;
	}

	public void setSd(Seed sd) {
		this.sd = sd;
	}

	private void processSplittingState() {
		/*
		 * System.out.println("SPLITTING..: old: " + splitAction.getOldGroup() +
		 * "new: " + splitAction.getNewGroup() + " Gid member stats: " + gid +
		 * "New group size: " + memberList.size() + "Old group new size "+
		 * splitAction.getoldGroupNewSize());
		 * System.out.println("SPLITTING..member stats: " + gid + gid +
		 * memberList.size() );
		 */
		if (splitAction.getOldGroup() == getGid()) {
			System.out.println("SPLITTING..merber size: " + memberList.size() + "old group size: "+splitAction.getOldGroupNewSize());
		}
		if ((splitAction.getOldGroup() == getGid())
				&& (memberList.size() == splitAction.getOldGroupNewSize())) {

			setGpState(groupState.RUNNING);
			splitAction.getActionComplete()
			.apply(splitAction.getOldGroup());
			if (splitAction.getOldGroup() == nodeInfo.getGid()) {
				
				//if (!isDone) {
				sendReRegisterRequest(splitAction.getNewGroup());
				
				//	isDone = true;
				//}
			}
		}
		if ((splitAction.getNewGroup() == getGid())
				&& (memberList.size() == splitAction.getNewGroupSize())) {
			
			if (memberList.contains(nodeInfo.getNodeid())
					|| (splitAction.getOldGroup() == nodeInfo.getGid())) {
				splitAction.getActionComplete()
						.apply(splitAction.getNewGroup());
				
			}
		
			setGpState(groupState.RUNNING);
		}
	}

	public int getInitialLimit() {
		return initialLimit;
	}

	public void setInitialLimit(int initialLimit) {
		this.initialLimit = initialLimit;
	}
}
