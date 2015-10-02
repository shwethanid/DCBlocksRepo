package serviceActors;

import static java.util.concurrent.TimeUnit.SECONDS;

import java.util.*;

import messages.*;
import messages.exClientAction.action;
import scala.concurrent.duration.Duration;
import util.HostNode;
import constants.Topics;
import akka.actor.*;

public class MonitorDiffGroup extends MonitorGroup {

	private HashMap<Integer, memStatus[]> statusMap = new HashMap<Integer, memStatus[]>();;
	private ArrayList<Integer> groupIdList = null;
	private ArrayList<Address> seedAddressList = null;
	private ArrayList<String> seedAddrStringlist = null;
	private Boolean isActorRefToBeRem = false;
	private Cancellable cStatsSch = null;
	private int myGroupId = 0;

	public MonitorDiffGroup(ActorSystem system, int myNodeId,
			ArrayList<Integer> groupIdList, ArrayList<Address> seedAddressList,
			ArrayList<String> seedAddrStringlist, ArrayList<HostNode> hostlist,
			ActorRef mgrRef) {

		super(system, myNodeId, groupIdList, seedAddressList,
				seedAddrStringlist, hostlist, mgrRef);
		setIsSelf(false);
		// System.out.println("[MonitorDiffGroup class, MonitorDiffGroup()]...Creating monitorDiffGroup for ");
		this.groupIdList = new ArrayList<Integer>(groupIdList);
		this.seedAddrStringlist = new ArrayList<String>(seedAddrStringlist);
		this.seedAddressList = new ArrayList<Address>(seedAddressList);
		for (int gid : groupIdList) {
			System.out.println("Gid: " + gid);
		}

	}

	// subscribe to cluster changes
	@Override
	public void preStart() {
		// #subscribe
		// Register for messages from other group status
		processRegisterRequest(Topics.REPLY_GROUP_STATUS, getSelf());
		// processRegisterRequest(Topics.MERGE_EXT, getSelf());
		// This will schedule to send remove actor Ref after timeout
		cStatsSch = getContext()
				.system()
				.scheduler()
				.scheduleOnce(Duration.create(20, SECONDS), getSelf(),
						Topics.REMOVE_ACTOR_REF, getContext().dispatcher(),
						getSelf());
		processRegisterRequest(Topics.MERGE_EXT, getSelf());
		processRegisterRequest(Topics.SPLIT_EXT, getSelf());
		processRegisterRequest(Topics.TIME_PASS, getSelf());
	}

	// unsubscribe to cluster changes
	@Override
	public void postStop() {
		System.out.println("MonitorDiffGroup stopping");
		// Unregister for member status messages
		processUnregisterRequest(Topics.REPLY_GROUP_STATUS, getSelf());
		processUnregisterRequest(Topics.MERGE_EXT, getSelf());
		processUnregisterRequest(Topics.SPLIT_EXT, getSelf());
		processRegisterRequest(Topics.TIME_PASS, getSelf());
	}

	@Override
	public void onReceive(Object message) throws Exception {

		// If ActorIdentity message from seed nodes of other groups
		if (message instanceof ActorIdentity) {
			super.onReceive(message);
			ActorRef act = ((ActorIdentity) message).getRef();
			if (act != null && !isActorRefToBeRem) {
				sendRefToGroups(OperationInfo.opnType.ADD_STANDALONE_NODE);
			}
			if (act == null) {
				System.out
						.println("[MonitorDiffGroup class, onreceive()] Identity seed is null!!!");
			}
		} else if (message instanceof ReceiveTimeout) {// ActorIdentity message
														// timeout
			super.onReceive(message);
		} else if (message instanceof memStatus[]) {// Member status from other
													// groups
			memStatus[] stats = (memStatus[]) message;
			updateGroupStatusMessage(stats);
		} else if (message instanceof MergeGroupMsg) {
			MergeGroupMsg msg = (MergeGroupMsg) message;
			// Removed group status
			if (statusMap.containsKey(msg.getOldGroup())) {
				statusMap.remove(msg.getOldGroup());
			}

			System.out
					.println("[MonitorDiffGroup class, onReceive()] Received MergeGroupMsg: ");

			mgrRef.forward(message, getContext());
		} else if (message instanceof SplitGroupMsg) {
			for (int i = 0; i < exGrpList.size(); i++) {
				SplitGroupMsg msg = (SplitGroupMsg) message;

				if (exGrpList.get(i).getGid() == msg.getNewGroup()) {
					System.out
							.println("[MonitorDiffGroup class, onreceive()] stoppping cluster client"
									+ exGrpList.get(i).getGid());
					exGrpList.get(i).stopClusterClient();
				}
			}
			System.out
					.println("[MonitorDiffGroup class, onReceive()] Received SplitGroupMsg: ");

			mgrRef.forward(message, getContext());
		} else if (message instanceof exClientAction) {
			log.info("message is : {}", message);
			// System.out.println("[MonitorDiffGroup class, onReceive()]...1: ");
			exClientAction actionMsg = (exClientAction) message;
			if (actionMsg.getActionType() == action.REMOVE) {
				/*
				 * System.out
				 * .println("[MonitorDiffGroup class, onReceive()]...REMOVE 2: "
				 * );
				 */
				Boolean isFnd = false;
				Integer gid = (Integer) actionMsg.getGroupId();
				isFnd = removeGroupClient(gid);
				myGroupId = gid;
			} else {
				/*
				 * System.out
				 * .println("[MonitorDiffGroup class, onReceive()]...ADD 2: ");
				 */
				Boolean isFnd = false;
				Integer gid = (Integer) actionMsg.getGroupId();
				isFnd = addGroupClient(gid);
				if (myGroupId == gid) {
					myGroupId = 0;
				}
			}

		} else if (message instanceof String) {
			if (message.equals(Topics.REMOVE_ACTOR_REF)) {
				/*
				 * System.out .println(
				 * "[MonitorDiffGroup class, onReceive()].. SENDING*** remove actor Ref after time out"
				 * );
				 */
				if (!cStatsSch.isCancelled()) {
					sendRefToGroups(OperationInfo.opnType.REMOVE_STANDALONE_NODE);
					cStatsSch.cancel();
				}
			}
		} else if (message instanceof OperationInfo) {
			OperationInfo opInfo = (OperationInfo) message;
			printOpnRequest(opInfo);
			if (opInfo.getOperationType().equals(
					OperationInfo.opnType.REGISTER_EXTERNAL_GROUPS)) {
				processRegisterRequest(opInfo.getTopic(), opInfo.getSubRef());
			} else if (opInfo.getOperationType().equals(
					OperationInfo.opnType.UNREGISTER_EXTERNAL_GROUPS)) {
				processUnregisterRequest(opInfo.getTopic(), opInfo.getSubRef());
			} else if (opInfo.getOperationType().equals(
					OperationInfo.opnType.PUBLISH_EXTERNAL_GROUPS)) {
				processPublishRequest(opInfo.getGid(), opInfo.getTopic(),
						opInfo.getMsgToPub());
			} else if (opInfo.getOperationType().equals(
					OperationInfo.opnType.STOP_CLUSTER_CLIENT)) {
				for (int i = 0; i < exGrpList.size(); i++) {
					if (exGrpList.get(i).getGid() == opInfo.getGid()) {
						System.out
								.println("[MonitorDiffGroup class, onreceive()] stopping cluster client"
										+ exGrpList.get(i).getGid());
						exGrpList.get(i).stopClusterClient();
					}
				}
				System.out
						.println("[MonitorDiffGroup class, onReceive()] Received SplitGroupMsg: ");
				System.out
						.println("[MonitorDiffGrpRef class, onreceive()]../*********reregister");
			} else if (opInfo.getOperationType().equals(
					OperationInfo.opnType.START_CLUSTER_CLIENT)) {
				for (int i = 0; i < exGrpList.size(); i++) {
					if (exGrpList.get(i).getGid() == opInfo.getGid()) {
						System.out
								.println("[MonitorDiffGroup class, onreceive()] setting cluster client"
										+ exGrpList.get(i).getGid());
						exGrpList.get(i).setClusterClient();
					}
				}

				// processUnregisterRequest(Topics.REPLY_GROUP_STATUS,
				// getSelf());
				// processRegisterRequest(Topics.REPLY_GROUP_STATUS, getSelf());
			} else {
				// Do nothing
			}
		} else if (message instanceof Timepass) {
			Timepass tp = (Timepass) message;
			System.out.println("Received Timepass from group: " + tp.getGid()
					+ tp.getNid());
		} else {
			log.info("unhandled message is status: {}", message);
			unhandled(message);
		}
	}

	private void updateGroupStatusMessage(memStatus[] stats) {
		/*
		 * System.out .println(
		 * "[MonitorDiffGroup class, updateGroupStatusMessage()]...Received stats: "
		 * );
		 */
		if ((stats != null) && (stats.length > 0)) {

			if (stats[0].getGId() != myGroupId) {
				System.out
						.println("[MonitorDiffGroup class, updateGroupStatusMessage()]...Received group status from Group: "
								+ stats[0].getGId()
								+ "members: "
								+ stats.length);
				statusMap.put(stats[0].getGId(), stats);
				sendGroupStatusToMgr(stats[0].getGId());
			}
		}

	}

	private void sendRefToGroups(OperationInfo.opnType type) {
		for (externalGroupClient cl : exGrpList) {
			if (cl.getSeedRef() != null) {
				if (type == OperationInfo.opnType.ADD_STANDALONE_NODE) {
					if (!cl.isAddMsgSent) {
						/*
						 * System.out .println(
						 * "[MonitorDiffGroup class, sendRefToGroups()]...Sending ADD ActorRef to SeedRef: "
						 * + cl.getSeedRef().toString());
						 */
						sendRefToGroup(cl.getSeedRef(), type);
						cl.isAddMsgSent = true;
					}
				} else {
					/*
					 * System.out .println(
					 * "[MonitorDiffGroup class, sendRefToGroups()]...Sending REM ActorRef to SeedRef: "
					 * + cl.getSeedRef().toString());
					 */
					sendRefToGroup(cl.getSeedRef(), type);
					cl.isRemMsgSent = true;
				}
			}
		}

	}

	private void sendRefToGroup(ActorRef seedActor, OperationInfo.opnType type) {
		OperationInfo op = new OperationInfo(type);
		// op.type = type;
		op.addId(getMyNodeId());
		seedActor.tell(op, getSelf());
	}

	private Boolean removeGroupClient(int gid) {
		Boolean isFnd = false;
		/*
		 * System.out
		 * .println("[MonitorDiffGroup class, onReceive()]...REMOVE 3: ");
		 */
		// System.out.println("[MonitorDiffGroup class, removeGroupClient()]...External group size: "
		// + exGrpList.size());
		isActorRefToBeRem = true;
		sendRefToGroups(OperationInfo.opnType.REMOVE_STANDALONE_NODE);
		for (int i = 0; i < exGrpList.size() && !isFnd; i++) {
			/*
			 * System.out
			 * .println("[MonitorDiffGroup class, addGroupClient()]...REMOVE 4: "
			 * );
			 */
			if (gid == exGrpList.get(i).getGid()) {
				if (exGrpList.get(i).getClusterClient() != null) {
					getContext().stop(exGrpList.get(i).getClusterClient());
				}
				exGrpList.remove(exGrpList.get(i));
				/*
				 * System.out .println(
				 * "[MonitorDiffGroup class, addGroupClient()]...REMOVE 5: ");
				 */

			}
			isFnd = true;
		}

		return isFnd;
	}

	private Boolean addGroupClient(int gid) {
		Boolean isFnd = false;
		/*
		 * System.out
		 * .println("[MonitorDiffGroup class, addGroupClient()]...ADD 3: ");
		 */
		for (int i = 0; i < groupIdList.size(); i++) {
			/*
			 * System.out
			 * .println("[MonitorDiffGroup class, addGroupClient()]...ADD 4: ");
			 */
			if (groupIdList.get(i) == gid) {
				/*
				 * System.out
				 * .println("[MonitorDiffGroup class, addGroupClient()]...ADD 5: "
				 * ); System.out .println(
				 * "[MonitorDiffGroup class, addGroupClient()]...seedAddressString: "
				 * + seedAddrStringlist.get(i));
				 */
				exGrpList.add(new externalGroupClient(gid, seedAddressList
						.get(i), seedAddrStringlist.get(i)));
				isActorRefToBeRem = false;
				isFnd = true;
			}
			exGrpList.get(i).isAddMsgSent = false;
		}
		sendRefToGroups(OperationInfo.opnType.ADD_STANDALONE_NODE);
		return isFnd;
	}

	private Boolean sendGroupStatusToMgr(int gid) {
		Boolean isSuccess = false;

		if (statusMap.containsKey(gid)) {
			/*
			 * System.out.println(
			 * "[MonitorDiffGroup class, sendGroupStatusToMgr()]...Trying to send group status for Gid: "
			 * + gid + " Size: " + statusMap.get(gid).length);
			 */
			mgrRef.tell(statusMap.get(gid), getSelf());
			isSuccess = true;
		}

		return isSuccess;
	}

	private void processPublishRequest(int gid, String topic, Object msg) {
		Boolean isFnd = false;

		for (int i = 0; i < exGrpList.size() && !isFnd; i++) {
			if (gid == exGrpList.get(i).getGid()) {
				System.out
						.println("[MonitorDiffGroup class, processPublishRequest()]..Topic: "
								+ topic);
				exGrpList.get(i).publishAppMsg(topic, msg);
				isFnd = true;
			}
		}

	}

	private void printOpnRequest(OperationInfo opn) {
		System.out
				.println("[MonitorDiffGroup class, printOpnRequest()]: Opn request: *Type: "
						+ opn.getOperationType().toString()
						+ "* Group Id: "
						+ opn.getGid()
						+ "* Id List: "
						+ opn.getIdList()
						+ "*"
						+ "Sender: " + getSender());
	}
}
