package appLogic;

import static java.util.concurrent.TimeUnit.SECONDS;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.function.Function;

import messages.MergeGroupMsg;
import messages.SplitGroupMsg;
import messages.StringMsg;
import scala.concurrent.duration.Duration;
import serviceActors.Publisher;
import serviceActors.Subscriber;
import serviceImplementation.GroupMgmtImpl;
import serviceImplementation.LeaderElectionImpl;
import serviceInterfaces.MergeGroup;
import serviceInterfaces.SplitGroup;
import akka.actor.ActorRef;
import akka.actor.Address;
import akka.actor.Cancellable;
import akka.actor.Props;
import akka.actor.UntypedActor;
import akka.contrib.pattern.ClusterReceptionistExtension;
import akka.event.Logging;
import akka.event.LoggingAdapter;
import appLogicMessages.LeaderInfoMsg;
import appLogicMessages.ReconfigDoneMsg;
import appLogicMessages.ReconfigDoneMsg.reconfig;
import appLogicMessages.ReconfigOldIdMsg;
import appLogicMessages.ReconfigPubMsg;
import appLogicMessages.ReconfigReqMsg;
import appLogicMessages.ReconfigReqMsg.reconfigReq;
import constants.ActorNames;
import constants.Topics;

public class DVSAppMgr extends UntypedActor {
	LoggingAdapter log = Logging.getLogger(getContext().system(), this);
	private GroupMgmtImpl grpMgmt = null;
	private int myGroupId = 0;
	private int myNodeId = 0;
	private int leaderId = 0;
	private ActorRef leaderRef = null;
	private ActorRef leaderIdSub = null;
	private ActorRef leaderIdPub = null;
	private ActorRef measSender = null;
	private LeaderElectionImpl electionExecuter;
	private Boolean isPriFail = false;
	private Boolean isSecFail = false;
	private Boolean isSubSuccess = true;
	private ActorRef reconfigSub = null;
	private ActorRef reconfigPub = null;
	private ArrayList<Integer> mergedGroupMemberList = new ArrayList<Integer>();
	private int splitPubId = 0;
	private Integer caseType = 0;
	
	public DVSAppMgr(Integer nodeId, Integer groupId, Integer caseType, GroupMgmtImpl grpMgmt) {
		System.out.println("DVSAppMgr starting************");
		this.grpMgmt = grpMgmt;
		// Register for GroupReady, LeaderFailure events
		grpMgmt.registorOnGroupReady(groupId, grpMgmtCallBack);
		grpMgmt.registerOnPriLeaderFailure(groupId, grpMgmtCallBack);
		grpMgmt.registerOnSecLeaderFailure(groupId, grpMgmtCallBack);
		grpMgmt.registerOnMergeComplete(mergeCallback);
		grpMgmt.registerOnSplitComplete(splitGroupCallback);
		grpMgmt.registerOnAddComplete(addMemberCallback);
		// grpMgmt.registerOnAllGroupsReady(sysReadyCallback);
		myGroupId = groupId;
		myNodeId = nodeId;
		this.caseType = caseType;
		reconfigSub = getContext().system().actorOf(
				Props.create(Subscriber.class, getSelf(),
						Topics.APP_GROUP_RECONFIG),
				ActorNames.RECONFIG_SUBSCRIBER);
		
	}

	@Override
	public void preStart() {
		// Register for messages from other cluster/group leaders
		isSubSuccess = grpMgmt.subscribeToOtherGroups(Topics.APP_GROUP_MERGE,
				getSelf());
		isSubSuccess = grpMgmt.subscribeToOtherGroups(Topics.APP_GROUP_SPLIT,
				getSelf());
		// Create publisher to send reconfig message to own group
		reconfigPub = getContext().system().actorOf(
				Props.create(Publisher.class, Topics.APP_GROUP_RECONFIG),
				ActorNames.RECONFIG_PUBLISHER);
	}

	@Override
	public void postStop() {
		System.out.println("DvsMgr Stopping*************");
		if (reconfigSub != null) {
			getContext().stop(reconfigSub);
			reconfigSub = null;
		}
		
		if (reconfigPub != null) {
			getContext().stop(reconfigPub);
			reconfigPub = null;
		}
		if (leaderIdSub != null) {
			getContext().stop(leaderIdSub);
			leaderIdSub = null;
		}
		if (leaderIdPub != null) {
			getContext().stop(leaderIdPub);
			leaderIdPub = null;
		}
		if (isSubSuccess) {
			// grpMgmt.unsubscribeToOtherGroups(Topics.APP_GROUP_MERGE,
			// getSelf());
		}
	}

	@Override
	public void onReceive(Object message) throws Exception {
		// TODO Auto-generated method stub
		if (message instanceof String) {
			if (message.equals(StringMsg.SYS_READY)) {
				// All groups are ready
			} else if (message.equals(StringMsg.GROUP_READY)) {
				// Start leader election
				startLeaderElection(false);
			} else if (message.equals(StringMsg.PRIMARY_LEADER_FAILURE)) {
				System.out.println("Primary Leader failure!!");
				isPriFail = true;
				processLeaderFailure();
			} else if (message.equals(StringMsg.SECONDARY_LEADER_FAILURE)) {
				System.out.println("Secondary Leader failure!!");
				isSecFail = true;
				processLeaderFailure();
			}
		} else if (message instanceof Integer) {
			Integer myLeaderId = (Integer) message;
			processLeaderId(myLeaderId);
		} else if (message instanceof LeaderInfoMsg) {
			LeaderInfoMsg leaderInfo = (LeaderInfoMsg) message;
			processLeaderInfoMsg(leaderInfo);
		} else if (message instanceof ReconfigReqMsg) {
			ReconfigReqMsg msg = (ReconfigReqMsg) message;
			if (msg.getType() == reconfigReq.MERGE_REQ) {
				processMergeGrpRequest(msg.getGroupId());
			} else {
				processSplitGrpRequest(msg.getGroupId());
			}
		} else if (message instanceof ReconfigPubMsg) {
			ReconfigPubMsg msg = (ReconfigPubMsg) message;
			processReconfigMsg(msg);
		} else if (message instanceof ReconfigDoneMsg) {
			ReconfigDoneMsg msg = (ReconfigDoneMsg) message;
			if (msg.getType() == reconfig.MERGE_DONE) {
				processMergeDone(msg);
			} else {
				processSplitDone(msg);
			}

		} else {
			log.info("unhandled message is status: {}");
			unhandled(message);
		}
	}

	/**
	 * Method to start leader election
	 */
	private void startLeaderElection(Boolean isReElection) {

		new Thread(new Runnable() {

			@Override
			public void run() {
				if (!isReElection) {// If first election, then set up parameters
					// Set your vote as per computational ability
					Integer myVote = myNodeId * 3;
					System.out.println("*******************Setting up for leader Election********************");
					electionExecuter = new LeaderElectionImpl(getContext()
							.system(), myNodeId, myGroupId, grpMgmt);
					// Setup leader election with your vote and decision
					// function
					electionExecuter.setUpForLeaderElection(myVote, computeMax);
				}

				try {
					leaderId = electionExecuter.startElection();
					System.out.println("*****ELECTION COMPLETED!***LEADER ID: "+ leaderId + "  " + " GROUP ID: " + myGroupId + "******");
					getSelf().tell(leaderId, getSelf());
				} catch (Exception e) {
					e.printStackTrace();
					System.out.println("Leader Election exception");
				}

			}
		}).start();
	}

	/**
	 * Leader Election decision function to compute 2 highest votes
	 * 
	 * @param voteList
	 *            List of votes from all nodes
	 */
	Function<Integer[], Integer[]> computeMax = (voteList) -> {
		Integer[] max = new Integer[2];

		if (voteList.length > 1) {
			Arrays.sort(voteList);
			max[0] = voteList[voteList.length - 1];
			max[1] = voteList[voteList.length - 2];
		}

		return max;
	};

	/**
	 * Method to process leader Id. If it is the leader, start the leader
	 * activities. Else, subscribe to get leader ActorRef
	 * 
	 * @param myGrpLeaderId
	 */
	void processLeaderId(Integer myGrpLeaderId) {
		if (myGrpLeaderId == myNodeId) {
			System.out.println("processLeaderId: I'm the leader!!");
			// If my node is the leader,
			// start the leader actor to perform leader activities
			leaderRef = getContext().system().actorOf(
					Props.create(Leader.class, getSelf(), myNodeId, myGroupId, caseType, 
							grpMgmt), ActorNames.LEADER);
			if (leaderIdSub == null) {
				leaderIdSub = getContext().system().actorOf(
						Props.create(Subscriber.class, getSelf(),
								Topics.LEADER_REF),
						ActorNames.LEADER_ID_SUBSCRIBER);
			}
			if (leaderIdPub == null) {
				// Create publisher to publish leader id and leader ActorRef
				leaderIdPub = getContext().system().actorOf(
						Props.create(Publisher.class, Topics.LEADER_REF),
						ActorNames.LEADER_ID_PUBLISHER);
			}
			LeaderInfoMsg msg = new LeaderInfoMsg(leaderId, myGroupId,
					leaderRef);

			try {// Sleeps for 5 seconds
				Thread.sleep(5000);
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
			// Inform leader id (and corresponding ActorRef) to its own group
			// members
			leaderIdPub.tell(msg, getSelf());
			// } else if (myGrpLeaderId == ) {
			// Too many failed nodes, cannot function properly!!
			// Restart needed!!
			// getContext().stop(getSelf());
		} else {
			System.out
					.println("processLeaderId: Oh no!!! I'm not the leader!!");
			if (leaderIdSub == null) {
				// If it is not the leader, just subscribe to get leader
				// ActorRef
				leaderIdSub = getContext().system().actorOf(
						Props.create(Subscriber.class, getSelf(),
								Topics.LEADER_REF),
						ActorNames.LEADER_ID_SUBSCRIBER);
			}
		}
	}

	/**
	 * Process LeaderInfoMsg. Get the leader ActorRef and start Power
	 * measurement sender actor
	 * 
	 * @param leaderInfo
	 */
	private void processLeaderInfoMsg(LeaderInfoMsg leaderInfo) {
		// If leader info message from its own group, use it to create
		// measurement sender actor
		if (leaderInfo.getGroupId() == myGroupId) {
			System.out.println("My group Leader info msg: "
					+ leaderInfo.getLeaderId() + " " + leaderInfo.getGroupId());
			leaderRef = leaderInfo.getLeaderActor();

			Address addr = leaderRef.path().address();
			String path = leaderRef.path().toStringWithAddress(addr);

			System.out.println("leader path: " + path);
			// If new leader elected for first time, create measurementSender
			if (measSender == null) {
				measSender = getContext().system().actorOf(
						Props.create(MeasurementSender.class, myNodeId,
								myGroupId, caseType, path, grpMgmt.getGroupCount()),
						ActorNames.MEAS_SENDER);
			} else {
				System.out
						.println("processLeaderInfoMsg: Re elected leader path: "
								+ path);
				// Else leader is re-elected, send the new leader path to
				// measurementSender
				measSender.tell(path, getSelf());
			}
		}
	}

	private void processLeaderFailure() {
		if (isPriFail) {
			System.out.println("Secondary leader: "
					+ grpMgmt.getSecondaryLeader(myGroupId));
			// TBD!! Second Solution!
			/*
			 * if (leaderIdSub != null) { getContext().stop(leaderIdSub);
			 * leaderIdSub = null; }
			 */
			if (isSecFail) {
				// If both pri and sec leader has failed,
				// start new leader election
				isPriFail = false;
				isSecFail = false;
				startLeaderElection(true);
			} else {
				// Set the secondary leader as pri leader
				leaderId = grpMgmt.getSecondaryLeader(myGroupId);
				processLeaderId(leaderId);

			}
		}
	}

	/**
	 * Callback function to receive GroupReady, Leaderfailure notifications
	 */
	private Function<String, Boolean> grpMgmtCallBack = (s) -> {
		getSelf().tell(s, getSelf());
		return true;
	};
	Function<MergeGroup, Boolean> mergeCallback = (info) -> {
		System.out.println("*********DVSMGR: Reconfig is done***************");
		// Get leader Id of new group
		myGroupId = info.getNewGroup();
		
		ReconfigDoneMsg msg = new ReconfigDoneMsg(info.getOldGroup(), info.getNewGroup(), info.getStarterId(), reconfig.MERGE_DONE);
		System.out.println("getSelf(): " + getSelf().toString());
		getSelf().tell(msg, getSelf());

		return true;
	};

	Function<SplitGroup, Boolean> splitGroupCallback = (info) -> {
		System.out
				.println("********DVSMGR: Split group is done***************");
		ReconfigDoneMsg msg = new ReconfigDoneMsg(info.getOldGroup(), info.getNewGroup(), info.getStarterId(), reconfig.SPLIT_DONE);
		getSelf().tell(msg, getSelf());
		return true;
	};

	Function<Integer, Boolean> addMemberCallback = (newGrp) -> {
		System.out
				.println("*********DVSMGR: Add member is done***************");
		myGroupId = newGrp;
		return true;
	};

	private Function<String, Boolean> sysReadyCallback = (s) -> {
		getSelf().tell(s, getSelf());
		return true;
	};

	/**
	 * 
	 * @param newGrp
	 */
	private void processMergeDone(ReconfigDoneMsg msg) {
		System.out.println("*****DVSMGR: processMergeDone*****");

		if (myNodeId == msg.getStarterId()) {
			System.out
					.println("/************Merge Done Inform others**********/");
			// Inform to new group that reconfiguration is complete
			ArrayList<Integer> myGroupMemberList = grpMgmt
					.getMemberList(myGroupId);
			ReconfigPubMsg reconfigMsg = new ReconfigPubMsg(msg.getOldGroupId(), msg.getNewGroupId(),
					myGroupMemberList, ReconfigPubMsg.reconfig.MERGE,
					ReconfigPubMsg.action.COMPLETE);

			if (reconfigPub != null) {
				Cancellable cSSch = getContext()
						.system()
						.scheduler()
						.scheduleOnce(Duration.create(20, SECONDS), reconfigPub,
								reconfigMsg, getContext().dispatcher(),
								getSelf()); 
				//reconfigPub.tell(reconfigMsg, getSelf());
			}
		} else {
			System.out
					.println("/************Merge Done Wait for new leader**********/");
			if (reconfigSub != null) {
				getContext().stop(reconfigSub);
				reconfigSub = getContext().system().actorOf(
						Props.create(Subscriber.class, getSelf(),
								Topics.APP_GROUP_RECONFIG),
						ActorNames.RECONFIG_SUBSCRIBER + "er");
				if (leaderIdSub == null) {
					leaderIdSub = getContext().system().actorOf(
							Props.create(Subscriber.class, getSelf(),
									Topics.LEADER_REF),
							ActorNames.LEADER_ID_SUBSCRIBER);
				}
			}

		}
	}

	/**
	 * Start the merge process. Stop all on going activity and inform the same
	 * to new group as reconfiguration will mess up on going activity
	 * 
	 * @param newGroupId
	 */
	private void processMergeGrpRequest(Integer newGroupId) {
		ArrayList<Integer> gidList = new ArrayList<Integer>();
		gidList.add(myGroupId);
		ArrayList<Integer> myGroupMemberList = grpMgmt.getMemberList(myGroupId);
		mergedGroupMemberList = myGroupMemberList;
		ReconfigPubMsg reconfigMsg = new ReconfigPubMsg(myGroupId, newGroupId,
				myGroupMemberList, ReconfigPubMsg.reconfig.MERGE,
				ReconfigPubMsg.action.START);
		// Send reconfig message to new group
		grpMgmt.publishMsgToOtherGroup(newGroupId, Topics.APP_GROUP_MERGE,
				reconfigMsg);
		if(electionExecuter != null)
		{
			electionExecuter.removeSetUpForLeaderElection();
			electionExecuter = null;
		}
		if (reconfigPub != null) {
			reconfigPub.tell(reconfigMsg, getSelf());
			getContext().system().stop(reconfigPub);
		}

		// Stop all actors
		if (measSender != null) {
			getContext().stop(measSender);
			measSender = null;
		}

		if (myNodeId == leaderId) {
			getContext().stop(leaderRef);
			leaderRef = null;
		}
		try {
			Thread.sleep(100);
		} catch (InterruptedException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		Boolean isSuccess = grpMgmt.mergeGroups(newGroupId, gidList);
		System.out.println("Merge request is cmpleted. status: " + isSuccess);
	}

	/**
	 * Process reconfigMsg from external group leader
	 * 
	 * @param reconfigMsg
	 *            Reconfig START, COMPLETE or ERROR
	 */
	private void processReconfigMsg(ReconfigPubMsg reconfigMsg) {
		System.out.println("/********Reconfig msg: tyep: "
				+ reconfigMsg.getReconfigType().toString() + "action: "
				+ reconfigMsg.getActionType().toString() + "*********/");
		// If group reconfiguration has started, stop all activity
		if (reconfigMsg.getActionType() == ReconfigPubMsg.action.START) {
			
			mergedGroupMemberList = reconfigMsg.getMemberList();
			if (measSender != null) {
				getContext().stop(measSender);
				measSender = null;
			}

			if(electionExecuter != null)
			{
				electionExecuter.removeSetUpForLeaderElection();
				electionExecuter = null;
			}
			if ((myNodeId == leaderId) && (leaderRef != null)) {
				getContext().stop(leaderRef);
				leaderRef = null;
			}
		} else if (reconfigMsg.getActionType() == ReconfigPubMsg.action.COMPLETE) {
			if (reconfigMsg.getReconfigType() == ReconfigPubMsg.reconfig.MERGE) {
				// If group reconfiguration is completed, restart all activity
				processLeaderId(leaderId);
				if(myNodeId == leaderId) {
					leaderRef.tell(new ReconfigOldIdMsg(reconfigMsg.getOldGroupId()), getSelf());
				}
			} else {
				//If new group after split action, then run leader election
				if(reconfigMsg.getNewGroupId() == myGroupId) {
					getSelf().tell(StringMsg.GROUP_READY, getSelf());				
				} else {
					// If old group after split action, set new leader and restart all activity
					processLeaderId(leaderId);
				}
			}
		} else {
			// Fatal Error
		}
	}

	/**
	 * Start the split process. Stop all on going activity and inform the same
	 * to new group as reconfiguration will mess up on going activity
	 * 
	 * @param newGroupId
	 */
	private void processSplitGrpRequest(Integer newGroupId) {
		ReconfigPubMsg reconfigMsg = new ReconfigPubMsg(myGroupId, newGroupId,
				mergedGroupMemberList, ReconfigPubMsg.reconfig.SPLIT,
				ReconfigPubMsg.action.START);

		if (reconfigPub != null) {
			reconfigPub.tell(reconfigMsg, getSelf());
			//getContext().system().stop(reconfigPub);
		}

		// Stop all actors
		if (measSender != null) {
			getContext().stop(measSender);
			measSender = null;
		}

		if (myNodeId == leaderId) {
			getContext().stop(leaderRef);
			leaderRef = null;
		}
		try {
			Thread.sleep(100);
		} catch (InterruptedException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		//isSplitStarter = true;
		Boolean isSuccess = grpMgmt.splitGroups(myGroupId, newGroupId,
				mergedGroupMemberList);
		System.out.println("Split request is cmpleted. status: " + isSuccess);
	}

	/**
	 * 
	 * @param newGrp
	 */
	private void processSplitDone(ReconfigDoneMsg msg) {
		/*isSubSuccess = grpMgmt.subscribeToOtherGroups(Topics.APP_GROUP_RECONFIG,
				getSelf());*/
		int newGrp = msg.getNewGroupId();
		
		if((myNodeId == msg.getStarterId()) || (myNodeId == 30) ) {
			System.out.println("/************Splt Done Inform THE GROUP**********/"+ "starter id: " +msg.getStarterId() + "new group: " + msg.getNewGroupId());
			
			// Inform to new group that reconfiguration is complete
			ArrayList<Integer> newGrpMemberList = grpMgmt.getMemberList(newGrp);
			ReconfigPubMsg reconfigMsg = new ReconfigPubMsg(msg.getOldGroupId(), newGrp,
					newGrpMemberList, ReconfigPubMsg.reconfig.SPLIT,
					ReconfigPubMsg.action.COMPLETE);

			// Send reconfig complete message to new group
			/*grpMgmt.publishMsgToOtherGroup(newGrp, Topics.APP_GROUP_SPLIT,
					reconfigMsg);*/
			//isSplitStarter = false;
			/*if (reconfigPub == null) {
				// Create publisher to send reconfig message to own group
				reconfigPub = getContext().system().actorOf(
						Props.create(Publisher.class, Topics.APP_GROUP_RECONFIG),
						ActorNames.RECONFIG_PUBLISHER);
				Cancellable cDSch = getContext()
						.system()
						.scheduler()
						.scheduleOnce(Duration.create(20, SECONDS), reconfigPub,
								reconfigMsg, getContext().dispatcher(),
								getSelf()); 
			}*/
			reconfigPub.tell(reconfigMsg, getSelf());
		} else {
			System.out.println("/************Splt Done Wait for group sync up**********/"+ " starter id: " +msg.getStarterId() + " new group: " + msg.getNewGroupId());
				
			}
			
		}
	}

