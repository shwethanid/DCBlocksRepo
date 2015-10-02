package appLogic;

import static java.util.concurrent.TimeUnit.SECONDS;

import java.util.ArrayList;

import scala.concurrent.duration.Duration;
import serviceImplementation.GroupMgmtImpl;
import messages.*;
import constants.*;
import appLogicMessages.*;
import akka.actor.*;
import akka.contrib.pattern.DistributedPubSubMediator;
import akka.event.Logging;
import akka.event.LoggingAdapter;

/**
 * This actor class acts as leader. Main roles are: 1. Inform other groups of
 * its id 2. Collect local power measurements from other members of the group
 * periodically. Forward it to leaderleaderExecRefuter for analysis 3. Contact
 * and collect topology info from other group leaders if required
 * 
 * @author shwetha
 *
 */
public class Leader extends UntypedActor {
	LoggingAdapter log = Logging.getLogger(getContext().system(), this);
	private PowerMeasurementMsg[] localState;
	int msgCount;
	ActorRef leaderExecRef;
	ActorRef[] clusterClients;
	private Integer myNodeId = 0;
	private Integer myGroupId = 0;
	private GroupMgmtImpl grpMgmt = null;
	private int groupSize = 0;
	private Boolean isSubSuccess = false;
	private int maxGroups = 0;
	private ArrayList<externalLeaderInfo> extLeaderList = new ArrayList<externalLeaderInfo>();
	private Cancellable cResendSch = null;
	private ActorRef parent = null;

	/**
	 * Constructor to start LeaderleaderExecRefuter actor to perform leader
	 * activities
	 * 
	 * @param nodeId
	 * @param groupId
	 * @param grpMgmt
	 */
	public Leader(ActorRef parent, Integer nodeId, Integer groupId, Integer caseType,
			GroupMgmtImpl grpMgmt) {
		System.out.println("Start--Leader Constructor");

		System.out.println("Leader path: " + getSelf());
		this.myNodeId = nodeId;
		this.myGroupId = groupId;
		this.grpMgmt = grpMgmt;
		this.parent = parent;
		this.maxGroups = grpMgmt.getGroupCount();
		ArrayList<Integer> nodeIdList = new ArrayList<Integer>(
				grpMgmt.getMemberList(myGroupId));
		ArrayList<Integer> failedIdList = new ArrayList<Integer>(
				grpMgmt.getFailedList(myGroupId));

		this.groupSize = getGroupSize();

		for (int id : failedIdList) {
			if (nodeIdList.contains(id)) {
				System.out.println("Removing id: " + id);
				int i = nodeIdList.indexOf(id);
				nodeIdList.remove(i);
			}
		}

		leaderExecRef = getContext().system().actorOf(
				Props.create(LeaderExecuter.class, myNodeId, myGroupId,
						caseType, getSelf(), nodeIdList, grpMgmt),
				ActorNames.LEADER_EXECUTER);

		for (int i = 0; i < maxGroups; i++) {
			if (myGroupId != (i + 1)) {
				extLeaderList.add(new externalLeaderInfo(i + 1));
			}
		}
		localState = new PowerMeasurementMsg[grpMgmt.getMemberList(myGroupId)
				.size()];
		System.out.println("End--Leader Constructor");
	}

	@Override
	public void preStart() {
		// Register for messages from other cluster/group leaders
		isSubSuccess = grpMgmt.subscribeToOtherGroups(
				Topics.DIFF_CLUSTER_LEADER_ID, getSelf());

		for (int i = 0; i < extLeaderList.size(); i++) {
			extLeaderList.get(i).publishLeaderInfo();
		}
		// This will schedule to resend leaderInfoMsg after timeout
		cResendSch = getContext()
				.system()
				.scheduler()
				.scheduleOnce(Duration.create(20, SECONDS), getSelf(),
						Topics.RESEND_LEADER_INFO, getContext().dispatcher(),
						getSelf());
	}

	@Override
	public void postStop() {
		System.out.println("Leader Stopping");
		if (leaderExecRef != null) {
			getContext().system().stop(leaderExecRef);
			leaderExecRef = null;
		}

		if (isSubSuccess) {
			grpMgmt.unsubscribeToOtherGroups(Topics.DIFF_CLUSTER_LEADER_ID,
					getSelf());
			grpMgmt.unsubscribeToOtherGroups(Topics.GET_TOPOLOGY, getSelf());
		}

	}

	@Override
	public void onReceive(Object message) {
		// If power measurement from group member
		if (message instanceof PowerMeasurementMsg) {
			processPowMeasMsg((PowerMeasurementMsg) message);
		} else if (message instanceof PowerMeasurementMsg[]) {
			// If power measurement from other group leader
			leaderExecRef.forward(message, getContext());
		} else if (message instanceof String) {
			String msg = (String) message;
			// If message from LeaderExeccRef to get power meas from other group
			if (msg.equals(StringMsg.SEND_POWER_MSG)) {
				leaderExecRef.forward(localState, getContext());
			} else if (message.equals(Topics.RESEND_LEADER_INFO)) {
				// If request to resend leader info
				for (int i = 0; i < extLeaderList.size(); i++) {
					extLeaderList.get(i).publishLeaderInfo();
				}
				if (!cResendSch.isCancelled()) {
					cResendSch.cancel();
				}
			}
		} else if (message instanceof LeaderInfoMsg) {
			// message contains external group leader info
			processExtLeaderInfoMsg((LeaderInfoMsg) message);
		} else if (message instanceof Terminated) {
			// External group leader terminated
			processExtLeaderTermination((Terminated) message);
		} else if (message instanceof ExtContactIdListMsg) {
			// Power meas
			processExtContactIdMsg((ExtContactIdListMsg) message);

		} else if (message instanceof ExtIdMsg) {
			ExtIdMsg msg = (ExtIdMsg) message;
			ArrayList<BusDataMsg> busList = new ArrayList<BusDataMsg>();
			for (Integer id : msg.getIdList()) {
				int i = id - 1 - ((myGroupId - 1) * groupSize);// array element
				busList.add(localState[i].getBusData());
			}
			getSender().tell(busList, getSelf());
		} else if (message instanceof BusListMsg) {
			// power meas from other groups
			leaderExecRef.forward(message, getContext());
		} else if (message instanceof ReconfigReqMsg) {
			// Send the merge request to parent. Do not process any more
			// requests
			parent.tell(message, getSelf());
		} else if (message instanceof ReconfigOldIdMsg) {
			// Send the reconfig old group id to leader Executer
			leaderExecRef.forward(message, getContext());
		} else if (message instanceof DistributedPubSubMediator.SubscribeAck)
			log.info("subscribing to external leader");
		else {
			unhandled(message);
		}
	}

	void processExtLeaderInfoMsg(LeaderInfoMsg msg) {
		// log.info("Leader Got: {}", message);
		Boolean isFnd = false;

		for (int i = 0; i < extLeaderList.size() && !isFnd; i++) {
			if (extLeaderList.get(i).getGid() == msg.getGroupId()) {
				getContext().watch(msg.getLeaderActor());
				System.out.println("Leader Got: Ext leader info: "
						+ msg.getLeaderId() + " " + msg.getGroupId());
				extLeaderList.get(i).setLeaderInfo(msg);

				if (extLeaderList.get(i).getIsLeaderFaulty()) {
					extLeaderList.get(i).publishLeaderInfo();
				}
				extLeaderList.get(i).setIsLeaderFaulty(false);
				isFnd = true;
			}
		}
	}

	void processExtLeaderTermination(Terminated deadLeader) {

		Boolean isFnd = false;
		for (int i = 0; i < extLeaderList.size() && !isFnd; i++) {
			if(extLeaderList.get(i).getLeaderInfo() != null) {
				if (extLeaderList.get(i).getLeaderInfo().getLeaderActor() == deadLeader
						.actor()) {
					System.out
							.println("[Leader class, processExtLeaderTermination()]..Group size: "
									+ groupSize);
					System.out.println("External leader terminated for group id:"
							+ extLeaderList.get(i).getGid());
					getContext().unwatch(
							extLeaderList.get(i).getLeaderInfo().getLeaderActor());
					extLeaderList.get(i).setIsLeaderFaulty(true);
					// extLeaderList.remove(i);
					isFnd = true;
				}
			} else {
				System.out
				.println("[Leader class, processExtLeaderTermination()]..No reference of external leader "
						+ deadLeader.actor().toString());
			}

		}
	}

	private void processExtContactIdMsg(ExtContactIdListMsg message) {
		ExtContactIdListMsg msg = message;
		for (int k : msg.getIdList().keySet()) {
			for (externalLeaderInfo exLdInfo : extLeaderList) {
				if (exLdInfo.getGid() == k) {
					if (exLdInfo.getLeaderInfo().getLeaderActor() != null) {
						ExtIdMsg exMsg = new ExtIdMsg();
						exMsg.setIdList(msg.getIdList().get(k));
						exLdInfo.getLeaderInfo().getLeaderActor()
								.tell(exMsg, getSelf());
					}
				}
			}
		}
	}

	private void processPowMeasMsg(PowerMeasurementMsg msg) {
		PowerMeasurementMsg meas = (PowerMeasurementMsg) msg;
		
		System.out.println("[Leader class, processPowMeasMsg()]"
				+ "meas from: " + meas.getNodeId() + "..Group size: "
				+ groupSize);
		int i = 0;
		if (grpMgmt.getMemberList(myGroupId).contains(meas.getNodeId())) {
			//Get member index
			i = grpMgmt.getMemberList(myGroupId).indexOf(
					meas.getNodeId().intValue());
			if (i > getGroupSize()) {
				return;
			} else {
				//save power data in same index position
				if (msgCount < (getGroupSize() - 1)) {
					localState[i] = new PowerMeasurementMsg(meas);
					msgCount++;
				} else {
					localState[i] = new PowerMeasurementMsg(meas);
					leaderExecRef.tell(localState, getSelf());
					msgCount = 0;
				}
			}
			System.out.println("Msg Count: " + msgCount + "Group size: "
					+ groupSize);
		}
	}

	int getGroupSize() {
		if (grpMgmt.getFailedList(myGroupId).isEmpty()) {
			/*System.out
					.println("[Leader class, getGroupSize()]" + "member size: "
							+ grpMgmt.getMemberList(myGroupId).size());*/
			this.groupSize = grpMgmt.getMemberList(myGroupId).size();
		} else {
/*			System.out.println("[Leader class, getGroupSize()]"
					+ "member size: " + grpMgmt.getMemberList(myGroupId).size()
					+ "failed members : " + grpMgmt.getFailedList(myGroupId));*/
			this.groupSize = grpMgmt.getMemberList(myGroupId).size()
					- grpMgmt.getFailedList(myGroupId).size();
		}
		return this.groupSize;
	}

	/**
	 * Internal class to maintain external group info
	 * 
	 * @author Shwetha
	 *
	 */
	private class externalLeaderInfo {
		private int gid = 0;
		private Boolean isLeaderFaulty = false;
		private LeaderInfoMsg leaderInfo = null;

		public externalLeaderInfo(int gid) {
			this.gid = gid;
		}

		public int getGid() {
			return this.gid;
		}

		public void publishLeaderInfo() {
			grpMgmt.publishMsgToOtherGroup(gid, Topics.DIFF_CLUSTER_LEADER_ID,
					new LeaderInfoMsg(myNodeId, myGroupId, getSelf()));
		}

		public Boolean getIsLeaderFaulty() {
			return isLeaderFaulty;
		}

		public void setIsLeaderFaulty(Boolean isLeaderFaulty) {
			this.isLeaderFaulty = isLeaderFaulty;
		}

		public LeaderInfoMsg getLeaderInfo() {
			return leaderInfo;
		}

		public void setLeaderInfo(LeaderInfoMsg leaderInfo) {
			if (this.leaderInfo != null) {
				this.leaderInfo = null;
			}
			this.leaderInfo = new LeaderInfoMsg(leaderInfo);
		}
	}

}
