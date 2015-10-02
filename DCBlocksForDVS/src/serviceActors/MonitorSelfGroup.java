package serviceActors;

import static java.util.concurrent.TimeUnit.SECONDS;

import java.util.ArrayList;

import messages.LeaderIdMsg;
import messages.MergeGroupMsg;
import messages.OperationInfo;
import messages.SplitGroupMsg;
import messages.StringMsg;
import messages.memStatus;
import scala.concurrent.duration.Duration;
import util.*;
import constants.ActorNames;
import constants.Topics;
import akka.actor.*;
import akka.cluster.*;
import akka.cluster.ClusterEvent.*;

public class MonitorSelfGroup extends MonitorGroup {

	private ArrayList<memStatus> memberStatusList = new ArrayList<memStatus>();
	private Cancellable cStatsSch = null;
	private ArrayList<IndependentNodes> standaloneNodeList = new ArrayList<IndependentNodes>();
	private MyNode nodeInfo = null;
	private Boolean isSelfRemoved = false;
	private Boolean isMemberLeaveGrp = false;
	private ActorRef remRef = null;
	private ActorRef mergeSub = null;
	private ActorRef mergePub = null;
	private ActorRef splitSub = null;
	private ActorRef splitPub = null;

	public MonitorSelfGroup(ActorSystem system, int myNodeId,
			ArrayList<Integer> groupIdList, ArrayList<Address> seedAddresslist,
			ArrayList<String> seedAddrStringList, ArrayList<HostNode> hostlist,
			ActorRef mgrRef) {
		super(system, myNodeId, groupIdList, seedAddresslist,
				seedAddrStringList, hostlist, mgrRef);
		cluster = Cluster.get(getContext().system());
		setIsSelf(true);
	}

	public MonitorSelfGroup(ActorSystem system, ArrayList<Integer> groupIdList,
			ArrayList<Address> seedAddresslist,
			ArrayList<String> seedAddrStringList, ArrayList<HostNode> hostlist,
			MyNode nodeInfo, ActorRef mgrRef) {

		super(system, nodeInfo.getNodeid(), groupIdList, seedAddresslist,
				seedAddrStringList, hostlist, mgrRef);
		/*
		 * System.out.println(
		 * "[MonitorSelfGroup class, MonitorSelfGroup()]Creating monitorSelfGroup for "
		 * ); for (int gid: groupIdList) { System.out.println("Gid: " + gid); }
		 */
		cluster = Cluster.get(getContext().system());
		// subscribeToSelfGroupRequest(Topics.MERGE_LOCAL);
		this.nodeInfo = new MyNode(nodeInfo);
		setIsSelf(true);
	}

	// subscribe to cluster changes
	@Override
	public void preStart() {

		// #subscribe
		cluster.subscribe(getSelf(), ClusterEvent.initialStateAsEvents(),
				MemberUp.class, UnreachableMember.class, MemberRemoved.class,
				CurrentClusterState.class);
		// #subscribe
		processRegisterRequest(Topics.REQUEST_GROUP_STATUS, getSelf());
		processRegisterRequest(Topics.LEADER_ID, getSelf());
		// This will schedule to send member status message after timeout
		/*
		 * cStatsSch = getContext() .system() .scheduler()
		 * .scheduleOnce(Duration.create(30, SECONDS), getSelf(),
		 * Topics.REQUEST_GROUP_STATUS, getContext().dispatcher(), getSelf());
		 */
		cStatsSch = getContext()
				.system()
				.scheduler()
				.schedule(Duration.create(30, SECONDS),
						Duration.create(120, SECONDS), getSelf(),
						Topics.REQUEST_GROUP_STATUS, getContext().dispatcher(),
						getSelf());

	}

	// subscribe to cluster changes
	@Override
	public void postStop() {
		System.out.println("MonitorSelfGroup stopping");
		// Unregister for member status messages
		processUnregisterRequest(Topics.REQUEST_GROUP_STATUS, getSelf());
		processUnregisterRequest(Topics.LEADER_ID, getSelf());

		// Unregister for member status messages
		cluster.unsubscribe(getSelf());
		if (mergeSub != null) {
			getContext().stop(mergeSub);
			mergeSub = null;
		}
		if (mergePub != null) {
			getContext().stop(mergePub);
			mergePub = null;
		}
		if (splitSub != null) {
			getContext().stop(splitSub);
			splitSub = null;
		}
		if (splitPub != null) {
			getContext().stop(splitPub);
			splitPub = null;
		}
		if (!cStatsSch.isCancelled()) {
			System.out
					.println("*************************cStatsSch is cancelled ***************************");
			cStatsSch.cancel();
		}
	}

	@Override
	public void onReceive(Object message) throws Exception {
		// TODO Auto-generated method stub

		if (message instanceof ActorIdentity) {
			super.onReceive(message);
			ActorRef act = ((ActorIdentity) message).getRef();
			if (act != null) {
				System.out
						.println("Sending group status to all external nodes after receing identity message");
				sendGroupStatusMsgToExtGroups();
				sendGroupStatusToStandaloneActors();
			} else {
				System.out
				.println("[MonitorSelfGroup class, onreceive()] Identity seed is null!!!");
			}
		} else if (message instanceof ReceiveTimeout) {
			super.onReceive(message);
		} else if (message instanceof MemberUp) {
			MemberUp mUp = (MemberUp) message;
			log.info("Member is Up: {}", mUp.member());
			processMemberUpEvent(mUp.member());

		} else if (message instanceof UnreachableMember) {
			UnreachableMember mUnreachable = (UnreachableMember) message;
			log.info("Member detected as unreachable: {}",
					mUnreachable.member());
			processMemberUnReachableEvent(mUnreachable.member());

		} else if (message instanceof MemberRemoved) {
			MemberRemoved mRemoved = (MemberRemoved) message;
			log.info("Member is Removed: {}", mRemoved.member());
			processMemberRemovedEvent(mRemoved.member());
		} else if (message instanceof String) {
			if (message.equals(Topics.REQUEST_GROUP_STATUS)) {
				log.info("Sending group status");

				if (!cStatsSch.isCancelled()) {
					sendGroupStatusMsgToExtGroups();
					sendGroupStatusToStandaloneActors();
				} else {
					System.out
							.println("*************************cStatsSch is restarted ***************************");
					cStatsSch = getContext()
							.system()
							.scheduler()
							.schedule(Duration.create(30, SECONDS),
									Duration.create(60, SECONDS), getSelf(),
									Topics.REQUEST_GROUP_STATUS,
									getContext().dispatcher(), getSelf());
					
				}
			} else if (message.equals(StringMsg.IS_MEMBER_REMOVED)) {
				isMemberLeaveGrp = true;
				remRef = getSender();
			}

		} else if (message instanceof OperationInfo) {
			OperationInfo opInfo = (OperationInfo) message;
			printOpnRequest(opInfo);
			if (opInfo.getOperationType() == OperationInfo.opnType.ADD_STANDALONE_NODE) {
				processAddStandaloneRequest(opInfo);
			} else if (opInfo.getOperationType()  == OperationInfo.opnType.REMOVE_STANDALONE_NODE) {
				processRemoveStandaloneRequest(opInfo);
			} else if (opInfo.getOperationType()  == OperationInfo.opnType.MERGE_GROUPS) {
				processMergeGroupRequest(opInfo);
			} else if (opInfo.getOperationType()  == OperationInfo.opnType.SPLIT_GROUPS) {
				processSplitGroupRequest(opInfo);
			} else if (opInfo.getOperationType()  == OperationInfo.opnType.STOP_CLUSTER_CLIENT) {
				for (int i = 0; i < exGrpList.size(); i++) {
					if (exGrpList.get(i).getGid() == opInfo.getGid()) {
						System.out
								.println("[MonitorSelfGroup class, onreceive()] stopping cluster client"
										+ exGrpList.get(i).getGid());
						exGrpList.get(i).stopClusterClient();
					}
				}
			} else if (opInfo.getOperationType()  == OperationInfo.opnType.START_CLUSTER_CLIENT) {
				for (int i = 0; i < exGrpList.size(); i++) {
					if (exGrpList.get(i).getGid() == opInfo.getGid()) {
						System.out
								.println("[MonitorSelfGroup class, onreceive()] setting cluster client"
										+ exGrpList.get(i).getGid());
						exGrpList.get(i).setClusterClient();
					}
				}
			} else if (message instanceof LeaderIdMsg) {
				LeaderIdMsg msg = (LeaderIdMsg) message;
				if (msg.getGroupId() == nodeInfo.getGid()) {
					sendLeaderIdMsgToClients((LeaderIdMsg) message);
				} else {
					mgrRef.forward(message, getContext());
				}
			} else if (message instanceof MergeGroupMsg) {
				mgrRef.forward(message, getContext());
			} else if (message instanceof SplitGroupMsg) {
				SplitGroupMsg msg = (SplitGroupMsg) message;
				mgrRef.forward(message, getContext());
			}
		} else {
			log.info("unhandled message is status: {}", message);
			unhandled(message);
		}
	}

	void processMemberUpEvent(Member mem) {
		int id = 0;

		id = getNodeId(mem);
		// System.out.println("[MonitorSelfGroup class, processMemberUpEvent()]...Member up From Node Id:"
		// + id);
		memStatus memStat = new memStatus();
		memStat.setMember(mem);
		memStat.setId(id);
		memStat.setGId(nodeInfo.getGid());
		memberStatusList.add(memStat);
		sendGroupStatusToMgr();
		sendGroupStatusMsgToExtGroups();
		sendGroupStatusToStandaloneActors();
	}

	Boolean processMemberUnReachableEvent(Member mem) {
		Boolean isFnd = false;

		for (int i = 0; i < memberStatusList.size() && !isFnd; i++) {

			if (mem.equals(memberStatusList.get(i).getMember())) {

				memberStatusList.get(i).setIsFailed(true);
				/*
				 * System.out.println(
				 * "[MonitorSelfGroup class, processMemberUnReachableEvent()]..Setting Node as failed. node id: "
				 * + memberStatusList.get(i).getId());
				 */
				isFnd = true;
			}
		}
		if (isFnd) {
			sendGroupStatusToMgr();
			isFnd = sendGroupStatusMsgToExtGroups();
			sendGroupStatusToStandaloneActors();
		}
		return isFnd;
	}

	Boolean processMemberRemovedEvent(Member mem) {
		Boolean isFnd = false;

		// System.out.println("aasdsff");
		for (int i = 0; i < memberStatusList.size() && !isFnd; i++) {

			if (mem.equals(memberStatusList.get(i).getMember())) {
				System.out
						.println("[MonitorSelfGroup class, processMemberRemovedEvent()]...Removing member from list"
								+ memberStatusList.get(i).getId());
				if (memberStatusList.get(i).getId().intValue() == nodeInfo
						.getNodeid()) {
					isSelfRemoved = true;
					System.out
							.println("Informing groupMgr that meber is removed");
					if (isMemberLeaveGrp) {
						remRef.tell(isSelfRemoved, getSelf());
						isMemberLeaveGrp = false;
					}
				}
				memberStatusList.remove(i);
				isFnd = true;
			}
		}
		if (isFnd) {
			sendGroupStatusToMgr();
			isFnd = sendGroupStatusMsgToExtGroups();
			sendGroupStatusToStandaloneActors();
		}

		return isFnd;
	}

	private int getNodeId(Member member) {
		String addStr = member.address().toString()
				.substring(member.address().toString().indexOf("@") + 1);
		// System.out.println("Address string: "+addStr);
		String hostinfo[] = addStr.split(":");
		Boolean isFnd = false;
		int id = 0;
		// System.out.println("member hostname: "+hostinfo[0]+" port: "+hostinfo[1]);

		for (int i = 0; i < hostlist.size() && !isFnd; i++) {
			if (hostinfo[0].equals(hostlist.get(i).getHostname())
					&& (Integer.parseInt(hostinfo[1]) == hostlist.get(i)
							.getPort())) {
				// System.out.println("node id: "+node.getNodeid());

				id = hostlist.get(i).getNodeid();
				isFnd = true;
			}
		}
		return id;
	}

	private void processAddStandaloneRequest(OperationInfo opInfo) {
		Boolean isFnd = false;

		for (int i = 0; i < standaloneNodeList.size() && !isFnd; i++) {
			if (standaloneNodeList.get(i).getNodeId() == opInfo.idList.get(0)) {
				isFnd = true;
			}
		}
		if (!isFnd) {
			IndependentNodes a = new IndependentNodes(opInfo.idList.get(0),
					getSender());
			/*
			 * System.out .println(
			 * "[MonitorSelfGroup class, processAddStandaloneRequest()]..Adding stand req: "
			 * + getSender().toString());
			 */
			standaloneNodeList.add(a);
		}
		// System.out.println("[MonitorSelfGroup class, processAddStandaloneRequest()].List contains: "+standaloneNodeList);

	}

	private void processRemoveStandaloneRequest(OperationInfo opInfo) {
		Boolean isFnd = false;

		for (int i = 0; i < standaloneNodeList.size() && !isFnd; i++) {
			if (standaloneNodeList.get(i).nodeId == opInfo.idList.get(0)) {
				/*
				 * System.out .println(
				 * "[MonitorSelfGroup class, processRemoveStandaloneRequest()]..Removing Ref: "
				 * + standaloneNodeList.get(i).getRef().toString());
				 */
				standaloneNodeList.remove(i);
			}
		}
		// System.out.println("[MonitorSelfGroup class, processRemoveStandaloneRequest()].List contains: "+standaloneNodeList);
	}

	private void sendGroupStatusToStandaloneActors() {

		memStatus stats[] = new memStatus[memberStatusList.size()];
		stats = memberStatusList.toArray(stats);

		for (IndependentNodes node : standaloneNodeList) {
			System.out
					.println("[MonitorSelfGroup class, sendGroupStatusToStandaloneActors()]..Sending group status to: "
							+ node.getNodeId());
			node.getRef().tell(stats, getSelf());
		}
	}

	private Boolean sendGroupStatusMsgToExtGroups() {
		Boolean isSuccess = true;
		/*
		 * for (int i = 0; i < exGrpList.size(); i++) {
		 * exGrpList.get(i).publishTimepass(nodeInfo.getGid(),
		 * nodeInfo.getNodeid()); }
		 */

		if (cluster.state() != null) {
			if (cluster.state().getLeader() != null) {
				/*
				 * System.out.println("leader is safe: " +
				 * cluster.state().getLeader() + "my Address: " +
				 * nodeInfo.getAddress());
				 */
				if (cluster.state().getLeader().equals(nodeInfo.getAddress())) {

					/*
					 * System.out .println(
					 * "[MonitorSelfGroup class, sendGroupStatusMsgToExtGroups()]*****IM THE LEADER!!!!! for gid: ******"
					 * + nodeInfo.getGid() );
					 */

					if (!memberStatusList.isEmpty()) {
						/*
						 * System.out .println(
						 * "[MonitorSelfGroup class, sendGroupStatusMsgToExtGroups()]sdf"
						 * + nodeInfo.getGid() );
						 */
						// Publish to other groups
						memStatus stats[] = new memStatus[memberStatusList
								.size()];
						stats = memberStatusList.toArray(stats);

						for (int i = 0; i < exGrpList.size(); i++) {
							/*
							 * System.out .println(
							 * "[MonitorSelfGroup class, sendGroupStatusMsgToExtGroups()]sff"
							 * + nodeInfo.getGid() );
							 */
							exGrpList.get(i).publishTimepass(nodeInfo.getGid(),
									nodeInfo.getNodeid());
							if (!exGrpList.get(i).publishMemberStatus(stats)) {
								isSuccess = false;
							}
						}
					} else {
						/*
						 * System.out .println(
						 * "[MonitorSelfGroup class, sendGroupStatusMsgToExtGroups()]dfjgkfgj"
						 * + nodeInfo.getGid());
						 */
					}
				}
			} else {
				isSuccess = false;
			}
		} else {
			isSuccess = false;
		}
		return isSuccess;
	}

	private Boolean sendLeaderIdMsgToClients(LeaderIdMsg msg) {
		Boolean isSuccess = true;

		if (cluster.state() != null) {
			if (cluster.state().getLeader() != null) {
				/*
				 * System.out.println("leader is safe: " +
				 * cluster.state().getLeader() + "my Address: " +
				 * nodeInfo.getAddress());
				 */
				if (cluster.state().getLeader().equals(nodeInfo.getAddress())) {
					/*
					 * System.out .println(
					 * "[MonitorSelfGroup class, sendLeaderIdMsgToClients()]*****IM THE LEADER!!!!!******"
					 * );
					 */// Publish to other groups

					for (externalGroupClient extClient : exGrpList) {
						extClient.publishAppMsg(Topics.LEADER_ID, msg);
					}
				}
			} else {
				isSuccess = false;
			}
		} else {
			isSuccess = false;
		}
		return isSuccess;
	}

	private void sendGroupStatusToMgr() {
		if (!memberStatusList.isEmpty()) {
			memStatus stats[] = new memStatus[memberStatusList.size()];
			stats = memberStatusList.toArray(stats);

			mgrRef.tell(stats, getSelf());
		}
		/*
		 * System.out.println(
		 * "[MonitorSelfGroup class, sendGroupStatusToMgr()].. Trying to send group status for Gid: "
		 * + nodeInfo.getGid() + "memstats: " + memberStatusList.get(0).getGId()
		 * + " Size: "+memberStatusList.size());
		 */
	}

	private Boolean sendMergeMsgtoExtGroup(MergeGroupMsg msg) {
		Boolean isSuccess = true;

		// Publish to other groups
		for (int i = 0; i < exGrpList.size(); i++) {
			if (!exGrpList.get(i).publishAppMsg(Topics.MERGE_EXT, msg)) {
				isSuccess = false;
			}
		}

		return isSuccess;
	}

	private Boolean sendSplitMsgtoExtGroup(SplitGroupMsg msg) {
		Boolean isSuccess = true;

		// Publish to other groups
		for (int i = 0; i < exGrpList.size(); i++) {
			if (!exGrpList.get(i).publishAppMsg(Topics.MERGE_EXT, msg)) {
				isSuccess = false;
			}
		}
		return isSuccess;
	}

	private void processMergeGroupRequest(OperationInfo opInfo) {
		/*int newGrpId = opInfo.getGid();

		MergeGroupMsg msg = new MergeGroupMsg(opInfo.getOldGrp(), newGrpId,
				opInfo.newGroupSize);*/

		System.out
				.println("[MonitorSelfGroup class, processMergeGroupRequest()]: Request:");
		opInfo.getMergeInfo().printParameters();

		if (mergePub == null) {
			// Publish to own group
			mergePub = getContext().system().actorOf(
					Props.create(Publisher.class, Topics.MERGE_LOCAL),
					ActorNames.MON_SELF_PUBLISHER);
		}
		System.out.println("Publishing merge request to same group");
		mergePub.tell(opInfo.getMergeInfo(), getSelf());
		//getContext().system().stop(mergePub);
		System.out.println("Publishing merge request to diff group");
		// publish to diff group
		sendMergeMsgtoExtGroup(opInfo.getMergeInfo());
	}

	private void processSplitGroupRequest(OperationInfo opInfo) {
		//int newGrpId = opInfo.getGid();

		/*SplitGroupMsg msg = new SplitGroupMsg(opInfo.getOldGrp(), newGrpId,
				opInfo.getNewGrpSz(), opInfo.getOldGrpNewSz());
		msg.setIdList(opInfo.getIdList());*/
		

		if (splitPub == null) {
			// Publish to own group
			splitPub = getContext().system().actorOf(
					Props.create(Publisher.class, Topics.SPLIT_LOCAL),
					ActorNames.MON_SELF_PUBLISHER);
		}
		System.out.println("Publishing split request to same group");
		splitPub.tell(opInfo.getSplitInfo(), getSelf());

		System.out.println("Publishing split request to diff group");
		// publish to diff group
		sendSplitMsgtoExtGroup(opInfo.getSplitInfo());
	}

	private void subscribeToSelfGroupRequest(String topic) {
		mergeSub = getContext().system().actorOf(
				Props.create(Subscriber.class, getSelf(), Topics.MERGE_LOCAL),
				ActorNames.MON_SELF_SUBSCRIBER);
		splitSub = getContext().system().actorOf(
				Props.create(Subscriber.class, getSelf(), Topics.SPLIT_LOCAL),
				ActorNames.MON_SELF_SUBSCRIBER);
	}

	private void printOpnRequest(OperationInfo opn) {
		System.out
				.println("[MonitorSelfGroup class, printOpnRequest()]: Opn request: *Type: "
						+ opn.getOperationType().toString()
						+ "* Group Id: "
						+ opn.getGid()
						+ "* Id List: "
						+ opn.getIdList()
						+ "*"
						+ "Sender: "
						+ getSender());
	}

	private class IndependentNodes {
		private int nodeId;
		private ActorRef actorRef = null;

		public IndependentNodes(int nodeId, ActorRef ref) {
			this.nodeId = nodeId;
			this.actorRef = ref;
		}

		public int getNodeId() {
			return this.nodeId;
		}

		public ActorRef getRef() {
			return this.actorRef;
		}
	}
}
