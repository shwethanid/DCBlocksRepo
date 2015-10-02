package serviceImplementation;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.Callable;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Function;

import messages.*;
import messages.OperationInfo.opnType;
import messages.RegisterCBMsg.CallBackType;
import akka.actor.*;
import akka.pattern.Patterns;
import akka.util.Timeout;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;

import constants.ActorNames;
import constants.Misc;
import scala.concurrent.ExecutionContext;
import scala.concurrent.Future;
import scala.concurrent.Await;
import scala.concurrent.duration.Duration;
import static akka.dispatch.Futures.future;
import static java.util.concurrent.TimeUnit.SECONDS;
import serviceActors.GroupMgr;
import serviceInterfaces.GroupMgmt;
import serviceInterfaces.MergeGroup;
import serviceInterfaces.SplitGroup;
import util.*;
import util.GroupState.groupState;

/**
 * Class for Group management activities - Add/Remove members from group - Get
 * status of group - Send notifications on critical group status changes
 * (GroupReady, LeaderFailure)
 * 
 * @author Shwetha
 *
 */
public class GroupMgmtImpl implements GroupMgmt {
	private HashMap<Integer, GroupCmdDispatcher> groupMap;
	private ArrayList<Seed> seedList = new ArrayList<Seed>();
	private ArrayList<HostNode> hostlist = new ArrayList<HostNode>();
	private ArrayList<InitGroupInfo> initGrpList = new ArrayList<InitGroupInfo>();
	private String confName = "mgr";
	private String actorSystemName = ActorNames.GROUP_ACTOR_SYSTEM;
	private ActorSystem system;
	private MyNode nodeInfo = null;
	private ActorRef grpMgrRef = null;
	private int maxGroups = 0;
	private Boolean isReconfigure = false;
	final Lock reconfigLock = new ReentrantLock();
	private int newGroupSize = 0;
	private Function<Integer, Boolean> reconfigCB = null;
	private Function<MergeGroup, Boolean> mergeCompleteCBToApp = null;
	private Function<SplitGroup, Boolean> splitCompleteCBToApp = null;
	private Function<Integer, Boolean> addCompleteCBToApp = null;
	private Boolean isMergeOnGoing = false;
	private Boolean isSplitOnGoing = false;
	SplitGroupAction splitAction = null;
	MergeGroupAction mergeAction = null;

	// private SplitGroupReqInfo splitInfo = null;
	// private MergeGroupReqInfo mergeInfo = null;

	public GroupMgmtImpl(int nodeId, Function<Integer, Boolean> reconfigCB) {
		groupMap = new HashMap<Integer, GroupCmdDispatcher>();
		this.reconfigCB = reconfigCB;

		Path p1 = Paths.get("config/seedNodeList.xml");
		seedXMLParser sdXmlPsr = new seedXMLParser(p1.toString());
		Boolean isFnd = false;

		// Path p2 = Paths.get("config/hostlist.xml");
		Path p2 = Paths.get("config/hostlist.xml");
		HostXMLParser hostXmlPsr = new HostXMLParser(p2.toString());

		Path p3 = Paths.get("config/groupConfig.xml");
		GroupConfigXMLParser grpXmlPsr = new GroupConfigXMLParser(p3.toString());

		if (hostXmlPsr != null) {
			hostlist = hostXmlPsr.parseDocument();

			for (int i = 0; i < hostlist.size() && !isFnd; i++) {
				if (hostlist.get(i).getNodeid() == nodeId) {
					nodeInfo = new MyNode(hostlist.get(i));
					nodeInfo.setAddress(new Address("akka.tcp",
							actorSystemName, nodeInfo.getHostname(), nodeInfo
									.getPort()));
					isFnd = true;
				}
			}
		}
		if (grpXmlPsr != null) {
			initGrpList = grpXmlPsr.parseDocument();
			if ((sdXmlPsr != null) && (initGrpList != null)) {

				seedList = sdXmlPsr.parseDocument(actorSystemName);
				maxGroups = seedList.size();
				registerToGroups(true);
			}
		}

	}

	@Override
	public Boolean registerToGroups(Boolean onStartUp) {
		Boolean status = false;

		Config config = ConfigFactory.parseString(
				"akka.remote.netty.tcp.hostname=" + nodeInfo.getHostname())
				.withFallback(ConfigFactory.load(confName));
		system = ActorSystem.create(actorSystemName, config);

		try {
			grpMgrRef = system.actorOf(Props.create(GroupMgr.class, system,
					seedList, nodeInfo, hostlist), ActorNames.GROUP_MGR);

		} catch (akka.remote.RemoteTransportException e) {
			e.printStackTrace();
		}

		for (int i = 0; i < seedList.size(); i++) {

			if (!groupMap.containsKey(seedList.get(i).getGid())) {
				GroupCmdDispatcher gDispatcher = new GroupCmdDispatcher(
						nodeInfo, grpMgrRef, onStartUp, seedList.get(i),
						seedList.get(i).getGid(), initGrpList.get(i)
								.getInitGroupLimit());
				gDispatcher.registerForCallBack(addReqReceiver,
						removeReqReceiver, mergeReqReceiver, splitReqReceiver);
				gDispatcher.setGpState(groupState.STARTING);
				groupMap.put(seedList.get(i).getGid(), gDispatcher);

				status = true;
			}
		}

		Boolean isFnd = false;
		for (int i = 0; i < seedList.size() && !isFnd; i++) {

			GroupCmdDispatcher gDispatcher = groupMap.get(seedList.get(i)
					.getGid());

			if (nodeInfo.getHostname().equals(
					gDispatcher.getSd().getSeedHostName())) {
				// Don't add seed node to group if reconfiguration is on going
				if (!isReconfigure) {
					gDispatcher.addMember(nodeInfo.getNodeid());

					nodeInfo.setGid(seedList.get(i).getGid());
					System.out.println("Seed node group is sett:"
							+ nodeInfo.getGid());
				}

				groupMap.put(seedList.get(i).getGid(), gDispatcher);
				isFnd = true;
			}

		}

		OperationInfo opMsg = new OperationInfo(
				opnType.START_EXTERNAL_GROUP_CLIENTS);
		// opMsg.type = OperationInfo.opnType.START_EXTERNAL_GROUP_CLIENTS;

		try {
			Timeout timeout = new Timeout(Duration.create(1, "seconds"));
			Future<Object> reply = Patterns.ask(grpMgrRef, opMsg, timeout);// in
																			// milliseconds
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

		return status;

	}

	public ActorSystem getActorSystem() {
		return system;
	}

	public Boolean unregisterFromGroups() {
		Boolean status = false;

		groupMap.clear();
		if (grpMgrRef != null) {
			system.stop(grpMgrRef);
		}
		return status;
	}

	@Override
	public int getGroupId(int nodeId) {
		Boolean isDone = false;
		int gid = 0;

		for (int i = 0; i < groupMap.size() && !isDone; i++) {

			if (groupMap.get(i).getMemberList().contains(nodeId)) {
				isDone = true;
				gid = groupMap.get(i).getGid();
			}
		}

		return gid;
	}

	@Override
	public Boolean addMember(int groupId, int nodeId) {
		Boolean status = false;

		if (groupMap.containsKey(groupId)) {

			try {
				GroupCmdDispatcher gDispatcher = new GroupCmdDispatcher();
				gDispatcher = groupMap.get(groupId);

				status = gDispatcher.addMember(nodeId);
				if (status && (nodeId == nodeInfo.getNodeid())) {
					nodeInfo.setGid(groupId);
				}
				// gDispatcher.getMemberList().add(nodeId);
				groupMap.put(groupId, gDispatcher);

			} catch (Exception e) {
				e.printStackTrace();
			}
		}

		return status;
	}

	@Override
	public Boolean removeMember(int groupId, int nodeId) {
		Boolean status = false;
		if (groupMap.containsKey(groupId)) {

			try {
				GroupCmdDispatcher gDispatcher = new GroupCmdDispatcher();
				gDispatcher = groupMap.get(groupId);
				status = gDispatcher.removeMember(nodeId);

				groupMap.put(groupId, gDispatcher);

			} catch (Exception e) {
				e.printStackTrace();
			}
		}
		return status;
	}

	@Override
	public Boolean isMember(int groupId, int nodeId) {
		Boolean isMember = false;

		if (groupMap.containsKey(groupId)) {
			if (groupMap.get(groupId).getMemberList().contains(nodeId)) {
				isMember = true;
			}
		}
		return isMember;
	}

	@Override
	public ArrayList<Integer> getMemberList(int groupId) {
		ArrayList<Integer> memList = new ArrayList<Integer>();

		if (groupMap.containsKey(groupId)) {
			memList = groupMap.get(groupId).getMemberList();
		}
		return memList;
	}

	@Override
	public HashMap<Integer, Boolean> getGroupHealthStatus(int groupId) {
		HashMap<Integer, Boolean> statusMap = new HashMap<Integer, Boolean>();
		if (groupMap.containsKey(groupId)) {
			statusMap = groupMap.get(groupId).getHealthStatusList();
		}
		return statusMap;
	}

	@Override
	public Boolean getMemberHealthStatus(int groupId, int nodeId) {
		Boolean status = false;

		if (groupMap.containsKey(groupId)) {
			status = groupMap.get(groupId).getMemberHealthStatus(nodeId);
		}
		return status;
	}

	@Override
	public int getPrimaryLeader(int groupId) {
		int id = 0;
		// TODO Auto-generated method stub
		if (groupMap.containsKey(groupId)) {
			id = groupMap.get(groupId).getPriLeaderId();
		}
		return id;
	}

	@Override
	public void setPrimaryLeader(int groupId, int leaderId) {
		// TODO Auto-generated method stub
		if (groupMap.containsKey(groupId)) {
			// GroupCmdDispatcher gDispatcher = new GroupCmdDispatcher();
			GroupCmdDispatcher gDispatcher = groupMap.get(groupId);
			gDispatcher.setPriLeaderId(leaderId);
			groupMap.put(groupId, gDispatcher);
		}
	}

	@Override
	public int getSecondaryLeader(int groupId) {
		// TODO Auto-generated method stub
		int id = 0;
		// TODO Auto-generated method stub
		if (groupMap.containsKey(groupId)) {
			id = groupMap.get(groupId).getSecLeaderId();
		}
		return id;
	}

	@Override
	public void setSecondaryLeader(int groupId, int leaderId) {
		// TODO Auto-generated method stub
		if (groupMap.containsKey(groupId)) {
			// GroupCmdDispatcher gDispatcher = new GroupCmdDispatcher();
			GroupCmdDispatcher gDispatcher = groupMap.get(groupId);
			gDispatcher.setSecLeaderId(leaderId);
			groupMap.put(groupId, gDispatcher);
		}
	}

	@Override
	public Boolean mergeGroups(Integer groupIdToMerge,
			ArrayList<Integer> groupIdList) {

		Boolean isSuccess = false;
		Integer newGroupSize = 0;

		if (groupMap.containsKey(groupIdToMerge)) {
			newGroupSize = groupMap.get(groupIdToMerge).getMemberList().size();
			groupMap.get(groupIdToMerge).setGpState(groupState.MERGING);
		}
		try {
			// Step 1: Send the merge request to all group members
			for (Integer gpid : groupIdList) {
				if (groupMap.containsKey(gpid)) {
					GroupCmdDispatcher gDisp = groupMap.get(gpid);
					newGroupSize += gDisp.getMemberList().size();
					gDisp.setGpState(groupState.MERGING);
					isSuccess = gDisp.processMergeRequest(groupIdToMerge,
							newGroupSize);
				}
			}

			isMergeOnGoing = true;

		} catch (Exception e) {
			e.printStackTrace();
			System.out.println("9");
			isSuccess = false;
		}
		System.out.println("10");
		return isSuccess;
	}

	/**
	 * Split a group into 2 smaller groups. The list of members are moved to new
	 * group
	 * 
	 * @param oldGroupId
	 * @param newGroupId
	 * @param memberList
	 * @return
	 */
	public Boolean splitGroups(Integer oldGroupId, Integer newGroupId,
			ArrayList<Integer> memberList) {

		Boolean isSuccess = false;

		try {
			// Step 1: Send the split group request to all
			if (groupMap.containsKey(oldGroupId)) {
				GroupCmdDispatcher gDisp = groupMap.get(oldGroupId);
				int oldGrpSz = gDisp.getMemberList().size();
				// New group consists of nodes in the <memberList>
				int newGrpSz = memberList.size();
				splitAction = new SplitGroupAction(oldGroupId, newGroupId,
						memberList, oldGrpSz, nodeInfo.getNodeid());
				splitAction.setActionComplete(splitActionCompleteReceiver);
				gDisp.setSplitAction(splitAction);
				// gDisp.setGpState(groupState.SPLITTING);
				isSplitOnGoing = true;
				isSuccess = gDisp.processSplitRequest(oldGroupId, newGroupId,
						memberList, (oldGrpSz - newGrpSz));
			}
			// Step 2: Wait for result

		} catch (Exception e) {
			e.printStackTrace();
			System.out.println("9");
			isSuccess = false;
		}
		return isSuccess;
	}

	private Boolean mergeRequest(MergeGroupMsg mergeInfo) {
		Boolean isSuccess = false;
		System.out.println("ActorSystem is shutting down !!");
		int newGrpId = mergeInfo.getNewGroup();

		reconfigLock.lock();
		try {
			isReconfigure = true;
			system.shutdown();
			System.out.println("Clearing group map !!");
			groupMap.clear();
			grpMgrRef = null;
			System.out.println("wait for termination complete !!");
			system.awaitTermination(Duration.create(60, "seconds"));
			System.out.println("Register to groups again !!");
			System.out.println("Set new group id as : " + newGrpId);
			nodeInfo.setGid(newGrpId);
			registerToGroups(false);
			if (groupMap.containsKey(newGrpId)) {
				GroupCmdDispatcher gDisp = groupMap.get(newGrpId);
				mergeAction = new MergeGroupAction(mergeInfo.getOldGroup(),
						mergeInfo.getNewGroup(), mergeInfo.getNewGroupSize(),
						mergeInfo.getStarterId());
				mergeAction.setActionComplete(mergeActionCompleteReceiver);
				if (mergeAction != null) {
					gDisp.setMergeAction(mergeAction);
				}

				gDisp.setGpState(groupState.MERGING);

				isSuccess = reconfigCB.apply(newGrpId);
				System.out
						.println("Add member to new group: " + gDisp.getGid());
				isSuccess = gDisp.addMember(nodeInfo.getNodeid());

				System.out.println("6");
				isSuccess = true;
			}

			isReconfigure = false;

		} catch (Exception e) {
			e.printStackTrace();
		}
		reconfigLock.unlock();
		return isSuccess;
	}

	private Boolean splitRequest(SplitGroupMsg splitMsg) {
		Boolean isSuccess = false;
		System.out.println("ActorSystem is shutting down !!");
		int newGrpId = splitMsg.getNewGroup();
		int oldGrpId = splitMsg.getOldGroup();
		splitMsg.printParameters();
		reconfigLock.lock();
		try {
			isReconfigure = true;
			system.shutdown();
			System.out.println("Clearing group map !!");
			groupMap.clear();
			grpMgrRef = null;
			System.out.println("wait for termination complete !!");
			system.awaitTermination(Duration.create(60, "seconds"));
			System.out.println("Register to groups again !!");
			try {
				Thread.sleep(1000);
			} catch (Exception e) {
				e.printStackTrace();
			}
			nodeInfo.setGid(newGrpId);
			registerToGroups(false);
			System.out.println("New group Id: " + newGrpId);
			if (groupMap.containsKey(newGrpId)) {
				GroupCmdDispatcher gDisp = groupMap.get(newGrpId);
				splitAction = new SplitGroupAction(splitMsg.getOldGroup(),
						splitMsg.getNewGroup(), splitMsg.getIdList(),
						splitMsg.getOldGroupNewSize(), splitMsg.getStarterId());

				splitAction.setActionComplete(splitActionCompleteReceiver);
				if (splitAction != null) {
					gDisp.setSplitAction(splitAction);
				}

				if (groupMap.containsKey(oldGrpId)) {
					groupMap.get(oldGrpId).setSplitAction(splitAction);
					groupMap.get(oldGrpId).setGpState(groupState.SPLITTING);
				}
				gDisp.setGpState(groupState.SPLITTING);
				System.out.println("Set new group id as : " + newGrpId);

				isSuccess = reconfigCB.apply(newGrpId);
				System.out
						.println("Add member to new group: " + gDisp.getGid());
				isSuccess = gDisp.addMember(nodeInfo.getNodeid());

				System.out.println("6");
				isSuccess = true;
			}

			isReconfigure = false;

		} catch (Exception e) {
			e.printStackTrace();
		}
		reconfigLock.unlock();
		return isSuccess;
	}

	private Boolean remRequest() {
		Boolean isSuccess = false;
		System.out.println("ActorSystem is shutting down !!");

		reconfigLock.lock();
		try {
			isReconfigure = true;
			system.shutdown();
			System.out.println("Clearing group map !!");
			groupMap.clear();
			grpMgrRef = null;
			System.out.println("wait for termination complete !!");
			system.awaitTermination(Duration.create(30, "seconds"));
			System.out.println("Register to groups again !!");
			isSuccess = registerToGroups(false);
			isSuccess = reconfigCB.apply(0);
			isReconfigure = false;

		} catch (Exception e) {
			e.printStackTrace();
		}

		reconfigLock.unlock();
		return isSuccess;
	}

	@Override
	public Boolean addMemberList(int groupId, ArrayList<Integer> nodeIdList) {
		Boolean status = true;
		if (groupMap.containsKey(groupId)) {
			GroupCmdDispatcher gDispatcher = groupMap.get(groupId);
			// Step 1: Send Add request to all nodes
			final int newSz = gDispatcher.getMemberList().size()
					+ nodeIdList.size();

			status = gDispatcher.addMemberList(nodeIdList);
			groupMap.put(groupId, gDispatcher);
			if (status) {
				// Step 2: Wait for the remove to get completed
				// Blocking call, since further action is dangerous until all
				// nodes leave the group
				final ExecutionContext ec = system.dispatcher();
				Future<Boolean> f = future(new Callable<Boolean>() {
					public Boolean call() {
						Boolean isSuccess = false;

						while (gDispatcher.getMemberList().size() != newSz) {

							try {
								Thread.sleep(100);
							} catch (InterruptedException e) {
								// TODO Auto-generated catch block
								e.printStackTrace();
							}
						}
						isSuccess = true;

						return isSuccess;
					}
				}, system.dispatcher());
				try {
					status = (Boolean) Await.result(f,
							Duration.create(60, SECONDS));
					System.out.println("New size after addList: "
							+ gDispatcher.getMemberList().size());
				} catch (Exception e1) {
					// TODO Auto-generated catch block
					e1.printStackTrace();
				}
			}
		}

		return status;
	}

	@Override
	public Boolean removeMemberList(int groupId, ArrayList<Integer> nodeIdList) {
		Boolean status = false;

		if (groupMap.containsKey(groupId)) {
			GroupCmdDispatcher gDispatcher = groupMap.get(groupId);
			// Step 1: Send remove request to all nodes
			final int newSz = gDispatcher.getMemberList().size()
					- nodeIdList.size();
			status = gDispatcher.removeMemberList(nodeIdList);
			groupMap.put(groupId, gDispatcher);
			if (status) {
				// Step 2: Wait for the remove to get completed
				// Blocking call, since further action is dangerous until all
				// nodes leave the group
				final ExecutionContext ec = system.dispatcher();
				Future<Boolean> f = future(new Callable<Boolean>() {
					public Boolean call() {
						Boolean isSuccess = false;

						while (gDispatcher.getMemberList().size() != newSz) {

							try {
								Thread.sleep(100);
							} catch (InterruptedException e) {
								// TODO Auto-generated catch block
								e.printStackTrace();
							}
						}

						isSuccess = true;

						return isSuccess;
					}
				}, system.dispatcher());
				try {
					status = (Boolean) Await.result(f,
							Duration.create(60, SECONDS));
					System.out.println("New size after removeList: "
							+ gDispatcher.getMemberList().size());
				} catch (Exception e1) {
					// TODO Auto-generated catch block
					e1.printStackTrace();
				}
			}
		}

		return status;
	}

	@Override
	public ArrayList<Integer> getFailedList(int groupId) {
		ArrayList<Integer> failedList = new ArrayList<Integer>();

		if (groupMap.containsKey(groupId)) {
			failedList = groupMap.get(groupId).getFailedMemberList();
		}
		return failedList;
	}

	@Override
	public void registerOnPriLeaderFailure(int groupId,
			Function<String, Boolean> callBackFunc) {
		// TODO Auto-generated method stub
		if (groupMap.containsKey(groupId)) {
			GroupCmdDispatcher gDispatcher = groupMap.get(groupId);
			gDispatcher.getPriLeaderFailCBList().add(callBackFunc);
		}
	}

	@Override
	public void registerOnSecLeaderFailure(int groupId,
			Function<String, Boolean> callBackFunc) {
		// TODO Auto-generated method stub
		if (groupMap.containsKey(groupId)) {
			GroupCmdDispatcher gDispatcher = groupMap.get(groupId);
			gDispatcher.getSecLeaderFailCBList().add(callBackFunc);
		}
	}

	public void registorOnGroupReady(int groupId,
			Function<String, Boolean> callBackFunc) {
		if (groupMap.containsKey(groupId)) {
			GroupCmdDispatcher gDispatcher = groupMap.get(groupId);
			gDispatcher.getReadyCBList().add(callBackFunc);
		}
	}

	@Override
	public Boolean subscribeToOtherGroups(String topic, Object ref) {
		// TODO Auto-generated method stub

		Boolean isSuccess = false;

		if (grpMgrRef != null) {
			OperationInfo opMsg = new OperationInfo(
					opnType.REGISTER_EXTERNAL_GROUPS);
			// opMsg.type = OperationInfo.opnType.REGISTER_EXTERNAL_GROUPS;
			opMsg.setSubRef((ActorRef) ref);
			opMsg.setTopic(topic);
			// opMsg.topic = topic;
			// opMsg.subRef = (ActorRef) ref;

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

	@Override
	public Boolean unsubscribeToOtherGroups(String topic, Object ref) {
		// TODO Auto-generated method stub

		Boolean isSuccess = false;

		if (grpMgrRef != null) {
			OperationInfo opMsg = new OperationInfo(
					opnType.UNREGISTER_EXTERNAL_GROUPS);
			// opMsg.type = OperationInfo.opnType.UNREGISTER_EXTERNAL_GROUPS;
			opMsg.setTopic(topic);
			opMsg.setSubRef((ActorRef) ref);
			// opMsg.topic = topic;
			// opMsg.subRef = (ActorRef) ref;

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

	@Override
	public Boolean publishMsgToOtherGroup(int groupId, String topic, Object msg) {

		Boolean isSuccess = false;

		// TODO Auto-generated method stub
		if (grpMgrRef != null) {
			OperationInfo opMsg = new OperationInfo(
					OperationInfo.opnType.PUBLISH_EXTERNAL_GROUPS);
			// opMsg.type = OperationInfo.opnType.PUBLISH_EXTERNAL_GROUPS;
			opMsg.setTopic(topic);
			opMsg.setMsgToPub(msg);
			opMsg.setGid(groupId);
			// opMsg.topic = topic;
			// opMsg.msgToPub = msg;
			// opMsg.gid = groupId;

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

	@Override
	public Integer getGroupCount() {
		// TODO Auto-generated method stub
		return maxGroups;
	}

	@Override
	public Set<Integer> getGroupIds() {
		Set<Integer> gids = groupMap.keySet();
		// TODO Auto-generated method stub
		return gids;
	}

	@Override
	public void registerOnAddComplete(Function<Integer, Boolean> callBackFunc) {
		// TODO Auto-generated method stub
		System.out.println("App Register for add callback");
		this.addCompleteCBToApp = callBackFunc;
	}

	Function<MergeGroupMsg, Boolean> mergeActionCompleteReceiver = (merge) -> {
		System.out.println("mergeActionReceiver");

		Boolean isSuccess = false;
		// Integer[] ids = new Integer[2];
		// ids[0] = newGrpId;
		// ids[1] = mergeAction.getStarterId();
		isSuccess = mergeCompleteCBToApp.apply(merge);
		isSplitOnGoing = false;
		return isSuccess;
	};

	Function<MergeGroupMsg, Boolean> mergeReqReceiver = (mergeMsg) -> {
		System.out
				.println("[GroupMgmtImpl class, mergeReqReceiver()]..msg: old "
						+ +mergeMsg.getOldGroup() + " new: "
						+ mergeMsg.getNewGroup() + "size: "
						+ mergeMsg.getNewGroupSize() + " mynode gid: "
						+ nodeInfo.getGid());

		if (groupMap.containsKey(mergeMsg.getOldGroup())) {
			GroupCmdDispatcher gDisp = groupMap.get(mergeMsg.getOldGroup());
			gDisp.getMemberList().clear();
			gDisp.setGpState(groupState.INACTIVE);
		}
		if (groupMap.containsKey(mergeMsg.getNewGroup())) {
			GroupCmdDispatcher gDisp = groupMap.get(mergeMsg.getNewGroup());
			gDisp.setGpState(groupState.MERGING);
		}

		// Case 1: If old group == self group
		if (mergeMsg.getOldGroup() == nodeInfo.getGid()) {
			try {
				// shutdown actor system and join to new
				// group
				new Thread(new Runnable() {
					public void run() {
						mergeRequest(mergeMsg);
					}
				}).start();
			} catch (Exception e) {
				e.printStackTrace();
			}

		} else {
			if (groupMap.containsKey(mergeMsg.getNewGroup())) {
				// Case 2: (If new group != own group and old group == own
				// group) ||
				// (If new group != own group and old group != own group)
				GroupCmdDispatcher gDisp = groupMap.get(mergeMsg.getOldGroup());
				mergeAction = new MergeGroupAction(mergeMsg.getOldGroup(),
						mergeMsg.getNewGroup(), mergeMsg.getNewGroupSize(),
						mergeMsg.getStarterId());
				mergeAction.setActionComplete(mergeActionCompleteReceiver);
				gDisp.setMergeAction(mergeAction);
			}
		}

		return true;
	};

	Function<Integer, Boolean> splitActionCompleteReceiver = (gid) -> {
		System.out.println("splitActionCompleteReceiver for gid: " + gid);
		// Step 4: Call App Split CB function
		Boolean isSuccess = false;

		if (splitAction != null) {
			if (gid == splitAction.getOldGroup()) {
				splitAction.setOldGroupsizeComplete(true);
			} else {
				splitAction.setNewGroupsizeComplete(true);
			}
			if (splitAction.getOldGroupsizeComplete()
					&& splitAction.getNewGroupsizeComplete()) {
				// Integer[] ids = new Integer[2];
				// ids[0] = splitAction.getNewGroup();
				// ids[1] = splitAction.getStarterId();
				isSuccess = splitCompleteCBToApp.apply(splitAction);
				isSplitOnGoing = false;
				splitAction = null;
			}
		}

		return isSuccess;
	};

	Function<SplitGroupMsg, Boolean> splitReqReceiver = (splitMsg) -> {
		System.out
				.println("[GroupMgmtImpl class, splitReqReceiver()]..msg: old "
						+ +splitMsg.getOldGroup() + " new: "
						+ splitMsg.getNewGroup() + "node List:"
						+ splitMsg.getIdList() + "new grp size: "
						+ splitMsg.getNewGroupSize() + " old group new size: "
						+ splitMsg.getOldGroupNewSize() + " mynode gid: "
						+ nodeInfo.getGid());
		splitAction = new SplitGroupAction(splitMsg.getOldGroup(),
				splitMsg.getNewGroup(), splitMsg.getIdList(),
				splitMsg.getOldGroupNewSize(), splitMsg.getStarterId());

		splitAction.setActionComplete(splitActionCompleteReceiver);

		System.out
				.println("[GroupMgmtImpl class, splitReqReceiver()]..New split obj: old "
						+ +splitAction.getOldGroup()
						+ " new: "
						+ splitAction.getNewGroup()
						+ "node List:"
						+ splitAction.getIdList()
						+ "old grp new size: "
						+ splitAction.getOldGroupNewSize()
						+ " new grp new size: "
						+ splitAction.getNewGroupSize()
						+ " mynode gid: " + nodeInfo.getGid());

		if (groupMap.containsKey(splitMsg.getOldGroup())) {
			GroupCmdDispatcher gDisp = groupMap.get(splitMsg.getOldGroup());
			gDisp.setGpState(groupState.SPLITTING);
		}
		if (groupMap.containsKey(splitMsg.getNewGroup())) {
			GroupCmdDispatcher gDisp = groupMap.get(splitMsg.getNewGroup());
			gDisp.setGpState(groupState.SPLITTING);
		}
		Boolean isFnd = false;
		// Case 1: If old group == old group
		if (splitMsg.getOldGroup() == nodeInfo.getGid()) {
			System.out
					.println("[GroupMgmtImpl class, splitReqReceiver()]..old grp == my gid ");
			if (splitMsg.getIdList().contains(nodeInfo.getNodeid())) {
				System.out
						.println("[GroupMgmtImpl class, splitReqReceiver()]..node is split list!!!! ");

				try {
					// shutdown actor system and join to new
					// group
					isFnd = true;
					new Thread(new Runnable() {
						public void run() {
							splitRequest(splitMsg);
						}
					}).start();
				} catch (Exception e1) {
					e1.printStackTrace();
				}
			} else {
				System.out
						.println("[GroupMgmtImpl class, splitReqReceiver()]..node not in split list!!!! ");

			}
		}
		if (!isFnd) {

			if (groupMap.containsKey(splitMsg.getNewGroup())) {
				GroupCmdDispatcher gDisp = groupMap.get(splitMsg.getNewGroup());
				gDisp.setSplitAction(splitAction);
			}
			if (groupMap.containsKey(splitMsg.getOldGroup())) {
				GroupCmdDispatcher gDisp = groupMap.get(splitMsg.getOldGroup());
				gDisp.setSplitAction(splitAction);
			}
		}

		return true;
	};

	Function<Integer, Boolean> removeReqReceiver = (nid) -> {
		System.out.println("Got Remove Req: Node Id: " + nid);
		new Thread(new Runnable() {
			public void run() {
				remRequest();
			}
		}).start();

		return true;
	};

	Function<Integer, Boolean> addReqReceiver = (newGrpId) -> {
		System.out.println("Got add Req: new group Id: " + newGrpId);
		if (addCompleteCBToApp != null) {
			addCompleteCBToApp.apply(newGrpId);
		}
		return true;
	};

	@Override
	public void registorOnAllGroupsReady(int groupId,
			Function<String, Boolean> callBackFunc) {
		// TODO Auto-generated method stub

	}

	@Override
	public void registerOnMergeComplete(
			Function<MergeGroup, Boolean> callBackFunc) {
		// TODO Auto-generated method stub
		System.out.println("App Register for merge callback");
		this.mergeCompleteCBToApp = callBackFunc;
	}

	@Override
	public void registerOnSplitComplete(
			Function<SplitGroup, Boolean> callBackFunc) {
		// TODO Auto-generated method stub

		System.out.println("App Register for split callback");
		this.splitCompleteCBToApp = callBackFunc;
	}

}
