package serviceActors;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.function.Function;

import constants.ActorNames;
import constants.Topics;
import scala.collection.Seq;
import scala.concurrent.Await;
import scala.concurrent.Future;
import scala.concurrent.duration.Duration;
import util.*;
import messages.*;
import messages.OperationInfo.opnType;
import messages.RegisterCBMsg.CallBackType;
import messages.exClientAction.action;
import akka.actor.*;
import akka.cluster.Cluster;
import akka.contrib.pattern.ClusterReceptionistExtension;
import akka.event.Logging;
import akka.event.LoggingAdapter;
import akka.pattern.Patterns;
import akka.util.Timeout;
import static java.util.concurrent.TimeUnit.SECONDS;

public class GroupMgr extends UntypedActor {
	private MyNode nodeInfo = null;
	private ArrayList<Seed> seedList = null;
	private ArrayList<HostNode> hostlist = null;
	private ActorSystem mySystem = null;
	private ActorRef monitorSelfGrpRef = null;
	private ActorRef monitorDiffGrpRef = null;
	private HashMap<Integer, memStatus[]> statusMap = new HashMap<Integer, memStatus[]>();
	private LoggingAdapter log = Logging.getLogger(getContext().system(), this);
	private ActorRef mergeSubRef = null;

	private HashMap<Integer, Function<memStatus[], Boolean>> statusCBMap = new HashMap<Integer, Function<memStatus[], Boolean>>();
	private HashMap<Integer, Function<Integer[], Boolean>> leadersCBMap = new HashMap<Integer, Function<Integer[], Boolean>>();
	private HashMap<Integer/* node id */, Function<MergeGroupMsg, Boolean>> mergeCBMap = new HashMap<Integer, Function<MergeGroupMsg, Boolean>>();
	private HashMap<Integer/* node id */, Function<Integer, Boolean>> remCBMap = new HashMap<Integer, Function<Integer, Boolean>>();
	private HashMap<Integer/* node id */, Function<Integer, Boolean>> addCBMap = new HashMap<Integer, Function<Integer, Boolean>>();
	private HashMap<Integer/* node id */, Function<SplitGroupMsg, Boolean>> splitCBMap = new HashMap<Integer, Function<SplitGroupMsg, Boolean>>();

	/**
	 * Constructor for GroupMgr
	 * 
	 * @param system
	 * @param seedList
	 * @param nodeInfo
	 * @param hostlist
	 */
	public GroupMgr(ActorSystem system, ArrayList<Seed> seedList,
			MyNode nodeInfo, ArrayList<HostNode> hostlist) {
		this.mySystem = system;
		this.seedList = new ArrayList<Seed>(seedList);
		this.nodeInfo = new MyNode(nodeInfo);
		this.hostlist = new ArrayList<HostNode>(hostlist);
	}

	// subscribe to cluster changes
	@Override
	public void preStart() {
		mergeSubRef = getContext().system().actorOf(
				Props.create(Subscriber.class, getSelf(), Topics.MERGE_LOCAL),
				ActorNames.MON_SELF_SUBSCRIBER);
	}

	// subscribe to cluster changes
	@Override
	public void postStop() {
		System.out.println("GroupMgr stopping");

		if (monitorSelfGrpRef != null) {
			getContext().stop(monitorSelfGrpRef);
		}
		if (monitorDiffGrpRef != null) {
			getContext().stop(monitorDiffGrpRef);
		}
		if (mergeSubRef != null) {
			getContext().stop(mergeSubRef);
		}
	}

	@Override
	public void onReceive(Object message) throws Exception {
		// TODO Auto-generated method stub
		if (message instanceof OperationInfo) {
			OperationInfo opInfo = (OperationInfo) message;
			log.info("Got: {}", message);
			processOperationRequest(opInfo);
		} else if (message instanceof memStatus[]) {

			memStatus[] memberStats = (memStatus[]) message;
			if (memberStats.length > 0) {
				int gid = memberStats[0].getGId();
				// Any member status change event is stored
				statusMap.put(gid, memberStats);
				// GroupDispatcher is informed (through status receiver
				// function)
				statusCBMap.get(gid).apply(memberStats);
			}
		} else if (message instanceof MergeGroupMsg) {
			System.out.println("GroupMgr received MergeGroupMsg");
			processMergeGroupMsg((MergeGroupMsg) message);
		} else if (message instanceof SplitGroupMsg) {
			System.out.println("GroupMgr received SplitGroupMsg");
			processSplitGroupMsg((SplitGroupMsg) message);

		} else if (message instanceof RegisterCBMsg) {
			processRegisterCBMsg((RegisterCBMsg) message);

			getSender().tell(true, getSelf());
		} else if (message instanceof LeaderIdMsg) {
			LeaderIdMsg msg = (LeaderIdMsg) message;
			int gid = msg.getGroupId();
			leadersCBMap.get(gid).apply(msg.getLeaderIds());
			if (nodeInfo.getGid() == gid) {
				// If Sender is from own group, then publish leader Id
				// to other groups
				monitorSelfGrpRef.forward(msg, getContext());
			}
		} else if (message instanceof MergeGroupMsg) {
			processMergeGroupMsg((MergeGroupMsg) message);
		} else {
			log.info("unhandled message is status: {}", message);
			System.out
					.println("Unhandled message!!: Sender is: " + getSender());
			unhandled(message);
		}
	}

	private Boolean processOperationRequest(OperationInfo opInfo) {
		Boolean status = false;

		printOpnRequest(opInfo);

		switch (opInfo.getOperationType()) {
		case RE_REGISTER:
			System.out.println("/*********Received register msg from app");
			OperationInfo opn = new OperationInfo(opnType.START_CLUSTER_CLIENT);
			opn.setGid(opInfo.getGid());
			//opn.setOperationType(opnType.START_CLUSTER_CLIENT);
			getSender().tell(true, getSelf());
			Cancellable cSSch = getContext()
					.system()
					.scheduler()
					.scheduleOnce(Duration.create(30, SECONDS), monitorDiffGrpRef,
							opn, getContext().dispatcher(),
							getSelf()); 
			Cancellable cDSch = getContext()
					.system()
					.scheduler()
					.scheduleOnce(Duration.create(20, SECONDS), monitorSelfGrpRef,
							opn, getContext().dispatcher(),
							getSelf()); 
			//monitorDiffGrpRef.tell(opInfo, getSelf());
			//monitorSelfGrpRef.tell(opInfo, getSelf());
			break;

		case ADD_SINGLE_MEMBER:
			status = processAddMemberRequest(opInfo);
			getSender().tell(status, getSelf());
			break;

		case ADD_MEMBER_LIST:
			for (int nId : opInfo.getIdList()) {
				OperationInfo op = new OperationInfo(opnType.ADD_SINGLE_MEMBER);
				//op.type = OperationInfo.opnType.ADD_SINGLE_MEMBER;
				op.setGid(opInfo.getGid());
				op.addId(nId);
				status = processAddMemberRequest(op);
			}
			getSender().tell(status, getSelf());
			break;

		case REMOVE_SINGLE_MEMBER:
			status = processRemoveMemberRequest(opInfo);
			getSender().tell(status, getSelf());
			break;

		case REMOVE_MEMBER_LIST:
			for (int nId : opInfo.getIdList()) {
				OperationInfo op = new OperationInfo(opnType.REMOVE_SINGLE_MEMBER);
				//op.type = OperationInfo.opnType.REMOVE_SINGLE_MEMBER;
				op.setGid(opInfo.getGid());
				op.addId(nId);
				status = processRemoveMemberRequest(op);
			}
			getSender().tell(status, getSelf());
			break;

		case MERGE_GROUPS:
			System.out.println("GroupMgr: case MERGE_GROUPS");
			monitorSelfGrpRef.forward(opInfo, getContext());
			// status = processMergeGroupRequest(opInfo);
			status = true;
			getSender().tell(status, getSelf());
			break;

		case SPLIT_GROUPS:
			System.out.println("GroupMgr: case SPLIT_GROUPS");
			status = true;
			getSender().tell(status, getSelf());
			monitorSelfGrpRef.forward(opInfo, getContext());
			// status = processMergeGroupRequest(opInfo);
			
			break;

		case GET_GROUP_HEALTH_STATUS:
			status = processGetHealthRequest(opInfo);
			break;

		case START_EXTERNAL_GROUP_CLIENTS:
			processStartExtClientsRequest();
			break;

		case REGISTER_EXTERNAL_GROUPS:
		case UNREGISTER_EXTERNAL_GROUPS:
		case PUBLISH_EXTERNAL_GROUPS:
			status = processExtGroupRequest(opInfo);
			getSender().tell(status, getSelf());
			break;

		default:
			break;

		}
		return status;
	}

	private Boolean processAddMemberRequest(OperationInfo opInfo) {
		Boolean isSuccess = false;
		int gid = opInfo.getGid();
		int nodeId = opInfo.getIdList().get(0);

		System.out
				.println("[groupMgr class, processAddMemberRequest()]... Add Member Request for For Group Id: "
						+ gid + " and nodeId: " + nodeId);
		if (nodeId == nodeInfo.getNodeid()) {// Local node

			if (monitorSelfGrpRef == null) {
				if (createSelfGroupMonitor(gid)) {
					ArrayList<Address> addr = new ArrayList<Address>();
					addr.add(getSeedAddress(gid));

					Seq addrSeq = scala.collection.JavaConversions
							.asScalaBuffer(addr);
					Cluster.get(mySystem).joinSeedNodes(addrSeq.toList());
					System.out.println("Joing seed address: "
							+ getSeedAddress(gid).toString());
					isSuccess = true;
					System.out
							.println("[groupMgr class, processAddMemberRequest()]****node ID: "
									+ nodeId
									+ "****JOINED GROUP Id:"
									+ gid
									+ "****");
					/*
					 * if (isSuccess) { if
					 * (addCBMap.containsKey(nodeInfo.getNodeid())) { isSuccess
					 * = addCBMap.get(nodeInfo.getNodeid())
					 * .apply(nodeInfo.getGid());
					 * 
					 * } }
					 */
				}

			}
		} else {// RemoteNode
			Boolean isFnd = false;
			for (int i = 0; i < hostlist.size() && !isFnd; i++) {
				if (nodeId == hostlist.get(i).getNodeid()) {
					String addressStr = new String("akka.tcp://"
							+ ActorNames.GROUP_ACTOR_SYSTEM + "@"
							+ hostlist.get(i).getHostname() + ":"
							+ hostlist.get(i).getPort());
					String path = addressStr + "/user/" + ActorNames.GROUP_MGR;

					System.out
							.println("[groupMgr class, processAddMemberRequest()]..Remote member add request: Actor Path: "
									+ path);
					getContext().actorSelection(path).tell(opInfo, getSelf());
					isFnd = true;
					isSuccess = true;
				}
			}
		}
		return isSuccess;
	}

	private Boolean processRemoveMemberRequest(OperationInfo opInfo) {
		Boolean isSuccess = false;
		int gid = opInfo.getGid();
		int nodeId = opInfo.getIdList().get(0);

		System.out
				.println("[groupMgr class, processRemoveMemberRequest()]..Node Id to remove: "
						+ nodeId);
		// Local node
		if (nodeId == nodeInfo.getNodeid()) {
			System.out
					.println("[groupMgr class, processRemoveMemberRequest()]..Leaving cluster: "
							+ nodeInfo.getAddress());
			Cluster.get(mySystem).leave(nodeInfo.getAddress());
			try {
				Timeout timeout = new Timeout(Duration.create(30, "seconds"));
				Future<Object> reply = Patterns.ask(monitorSelfGrpRef,
						StringMsg.IS_MEMBER_REMOVED, timeout);// in milliseconds

				isSuccess = (Boolean) Await.result(reply, timeout.duration());
				if (isSuccess) {
					if (remCBMap.containsKey(nodeInfo.getNodeid())) {
						isSuccess = remCBMap.get(nodeInfo.getNodeid()).apply(
								nodeId);
					}

				}
			} catch (Exception e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}

			nodeInfo.setGid(0);
		} else {// RemoteNode
			Boolean isFnd = false;
			for (int i = 0; i < hostlist.size() && !isFnd; i++) {
				if (nodeId == hostlist.get(i).getNodeid()) {
					String addressStr = new String("akka.tcp://"
							+ "GroupMgtSystem" + "@"
							+ hostlist.get(i).getHostname() + ":"
							+ hostlist.get(i).getPort());
					String path = addressStr + "/user/" + ActorNames.GROUP_MGR;
					System.out
							.println("[groupMgr class, processRemoveMemberRequest()]...Remote member remove request: Actor Path: "
									+ path);
					getContext().actorSelection(path).tell(opInfo, getSelf());
					isFnd = true;
					isSuccess = true;
				}
			}
		}
		return isSuccess;
	}

	private Boolean processGetHealthRequest(OperationInfo opInfo) {
		int gid = opInfo.getGid();
		Boolean isSuccess = false;

		if (statusMap.containsKey(gid)) {
			getSender().tell(statusMap.get(gid), getSelf());
			isSuccess = true;
		}
		return isSuccess;
	}

	/*
	 * private Boolean processMergeGroupRequest(OperationInfo opInfo) { Boolean
	 * isSuccess = false; Boolean isSelf = false;
	 * 
	 * for (Integer nid : opInfo.getIdList()) { if (nid.intValue() ==
	 * nodeInfo.getNodeid()) {// Local node.. // Defer processing to later
	 * isSelf = true; } else {// RemoteNode Boolean isFnd = false; for (int i =
	 * 0; i < hostlist.size() && !isFnd; i++) { if (nid ==
	 * hostlist.get(i).getNodeid()) { String addressStr = new
	 * String("akka.tcp://" + ActorNames.GROUP_ACTOR_SYSTEM + "@" +
	 * hostlist.get(i).getHostname() + ":" + hostlist.get(i).getPort()); String
	 * path = addressStr + "/user/" + ActorNames.GROUP_MGR;
	 * 
	 * System.out .println("[groupMgr class, processAddMemberRequest()]" +
	 * "..Merge group request: Actor Path: " + path); OperationInfo op = new
	 * OperationInfo(); op.setGid(opInfo.getGid()); op.addId(nid);
	 * op.setOperationType(opnType.MERGE_GROUPS);
	 * getContext().actorSelection(path).tell(opInfo, getSelf()); isFnd = true;
	 * isSuccess = true; } } } }
	 * 
	 * if (isSelf) { try { // Inform SelfGroupRef to inform external groups that
	 * this group // is dissolved monitorSelfGrpRef.tell(StringMsg.MERGER,
	 * getSelf()); Thread.sleep(5000); MergeGroupReqInfo mergeInfo = new
	 * MergeGroupReqInfo( nodeInfo.getGid(), opInfo.gid, opInfo.getIdList()
	 * .size()); // Inform groupImpl to shutdown actor system and join to new //
	 * group isSuccess = mergeCBMap.get(nodeInfo.getNodeid()).apply( mergeInfo);
	 * 
	 * } catch (Exception e) { e.printStackTrace(); } }
	 * 
	 * return isSuccess; }
	 */

	private Boolean createSelfGroupMonitor(int gid) {
		Boolean isSuccess = false;
		ArrayList<Integer> grplist = new ArrayList<Integer>();
		ArrayList<String> seedAddrStringlist = new ArrayList<String>();
		ArrayList<Address> seedAddrlist = new ArrayList<Address>();

		for (Seed seed : seedList) {
			if (gid != seed.getGid()) {
				grplist.add(seed.getGid());
				seedAddrStringlist.add(seed.getAddressStr());
				seedAddrlist.add(seed.getAddress());
			}
		}
		try {

			nodeInfo.setGid(gid);
			// String actorStr = "MonitorSelfGroup";
			if (monitorDiffGrpRef != null) {
				exClientAction clientActMsg = new exClientAction(gid,
						action.REMOVE);
				monitorDiffGrpRef.tell(clientActMsg, getSelf());
			}
			monitorSelfGrpRef = mySystem.actorOf(Props.create(
					MonitorSelfGroup.class, mySystem, grplist, seedAddrlist,
					seedAddrStringlist, hostlist, nodeInfo, getSelf()),
					ActorNames.MONITOR_SELF_GROUP_ACTOR);

			isSuccess = true;
		} catch (akka.remote.RemoteTransportException e) {
			e.printStackTrace();
		}
		return isSuccess;
	}

	private Address getSeedAddress(int gid) {
		Boolean isFnd = false;
		Address addr = null;

		for (int i = 0; i < seedList.size() && !isFnd; i++) {
			if (seedList.get(i).getGid() == gid) {
				addr = seedList.get(i).getAddress();
				isFnd = true;
			}
		}
		return addr;
	}

	private Boolean processStartExtClientsRequest() {
		Boolean isSuccess = false;
		ArrayList<Integer> grplist = new ArrayList<Integer>();
		ArrayList<String> seedAddrStringlist = new ArrayList<String>();
		ArrayList<Address> seedAddrlist = new ArrayList<Address>();

		for (Seed seed : seedList) {
			if (nodeInfo.getGid() != seed.getGid()) {

				grplist.add(seed.getGid());
				seedAddrStringlist.add(seed.getAddressStr());
				seedAddrlist.add(seed.getAddress());
				System.out
						.println("[groupMgr class, processStartExtClientsRequest()]...Seed info: "
								+ seed.getGid()
								+ "Address String"
								+ seed.getAddressStr()
								+ "Address: "
								+ seed.getAddress());
			}
		}
		try {
			monitorDiffGrpRef = mySystem.actorOf(Props.create(
					MonitorDiffGroup.class, mySystem, nodeInfo.getNodeid(),
					grplist, seedAddrlist, seedAddrStringlist, hostlist,
					getSelf()), ActorNames.MONITOR_DIFF_GROUP_ACTOR);
			isSuccess = true;
		} catch (akka.remote.RemoteTransportException e) {
			e.printStackTrace();
		}
		return isSuccess;
	}

	private Boolean processExtGroupRequest(OperationInfo opInfo) {
		Boolean isSuccess = false;

		if (monitorDiffGrpRef != null) {
			monitorDiffGrpRef.forward(opInfo, getContext());
			isSuccess = true;
		}
		return isSuccess;
	}

	private void processRemoveMemberPartTwo() {
		if (monitorSelfGrpRef != null) {
			monitorSelfGrpRef = null;

			// Cluster.get(mySystem).leave(getSeedAddress(gid));
			exClientAction actionMsg = new exClientAction(nodeInfo.getGid(),
					action.ADD);
			monitorDiffGrpRef.tell(actionMsg, getSelf());
		}
		return;
	}

	private void processMergeGroupMsg(MergeGroupMsg mergeMsg) {

		memStatus[] memberStats = null;
		statusCBMap.get(mergeMsg.getOldGroup()).apply(memberStats);
		// Removed group status
		if (statusMap.containsKey(mergeMsg.getOldGroup())) {
			statusMap.remove(mergeMsg.getOldGroup());
		}

		Boolean isSuccess = mergeCBMap.get(nodeInfo.getNodeid())
				.apply(mergeMsg);

	}

	private void processSplitGroupMsg(SplitGroupMsg splitMsg) {
		try {
			System.out
					.println("[GroupMgr class, processSplitGroupMsg()]: Request: old: "
							+ splitMsg.getOldGroup()
							+ " new: "
							+ splitMsg.getNewGroup()
							+ "size: "
							+ splitMsg.getNewGroupSize()
							+ " Ids: "
							+ splitMsg.getIdList());
			// If node is part of split list, leave the old cluster
			if (splitMsg.getIdList().contains(nodeInfo.getNodeid())) {
				System.out
						.println("[groupMgr class, processSplitGroupMsg()]..Leaving cluster, my address: "
								+ nodeInfo.getAddress());
				Cluster.get(mySystem).leave(nodeInfo.getAddress());
				try {
					Timeout timeout = new Timeout(
							Duration.create(120, "seconds"));
					Future<Object> reply = Patterns.ask(monitorSelfGrpRef,
							StringMsg.IS_MEMBER_REMOVED, timeout);// in
																	// milliseconds

					Boolean isSuccess = (Boolean) Await.result(reply,
							timeout.duration());
					nodeInfo.setGid(0);
				} catch (Exception e) {
					e.printStackTrace();
				}
			} else {
				if(splitMsg.getOldGroup() == nodeInfo.getGid()) {
					OperationInfo opn = new OperationInfo(opnType.STOP_CLUSTER_CLIENT);
					opn.setGid(splitMsg.getNewGroup());
					//opn.setOperationType(opnType.STOP_CLUSTER_CLIENT);
					monitorSelfGrpRef.tell(opn, getSelf());
					monitorDiffGrpRef.tell(opn, getSelf());
				}
			}

			if (splitCBMap.containsKey(nodeInfo.getNodeid())) {
				Boolean isSuccess = splitCBMap.get(nodeInfo.getNodeid()).apply(
						splitMsg);

			}

		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	private void processRegisterCBMsg(RegisterCBMsg regMsg) {

		if (regMsg.getCBType() == CallBackType.ALL) {

			// Status receiver function is stored for future use on member
			// status change
			if (!statusCBMap.containsKey(regMsg.getGroupId())) {
				statusCBMap
						.put(regMsg.getGroupId(), regMsg.getstatusRecvFunc());
			}
			// Status receiver function is stored for future use
			// on getting group leader ids
			if (!leadersCBMap.containsKey(regMsg.getGroupId())) {
				leadersCBMap.put(regMsg.getGroupId(),
						regMsg.getleaderRecvFunc());
			}
			// Merge request receiver function is stored for future use
			if (!mergeCBMap.containsKey(regMsg.getNodeId())) {
				mergeCBMap
						.put(regMsg.getNodeId(), regMsg.getMergeReqRecvFunc());
			}
			// Split request receiver function is stored for future use
			if (!splitCBMap.containsKey(regMsg.getNodeId())) {
				System.out.println("Adding to splitCBMap");
				splitCBMap
						.put(regMsg.getNodeId(), regMsg.getSplitReqRecvFunc());
			}
			// Remove request receiver function is stored for future use
			if (!remCBMap.containsKey(regMsg.getNodeId())) {
				remCBMap.put(regMsg.getNodeId(), regMsg.getRemReqRecvFunc());
			}
			// Add request receiver function is stored for future use
			if (!addCBMap.containsKey(regMsg.getNodeId())) {
				System.out.println("Adding to AddCBMap");
				addCBMap.put(regMsg.getNodeId(), regMsg.getAddReqRecvFunc());
			}
		}
	}

	private void printOpnRequest(OperationInfo opn) {
		// System.out.println("[groupMgr class, printOpnRequest()]: Opn request: *Type: "
		// + opn.type.toString() + "* Group Id: " + opn.getGid() + "* Id List: "
		// + opn.getIdList()+"*" + "Sender: "+ getSender());
	}
}
