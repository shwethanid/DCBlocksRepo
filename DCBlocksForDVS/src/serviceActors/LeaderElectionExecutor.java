package serviceActors;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Map.Entry;
import java.util.function.Function;

import messages.*;
import messages.LEMsg.action;
import constants.*;
import akka.actor.ActorKilledException;
import akka.actor.ActorRef;
import akka.actor.Props;
import akka.actor.UntypedActor;
import akka.event.Logging;
import akka.event.LoggingAdapter;

/**
 * This actor class performs the Leader Election (LE). Currently leader Election
 * is based on Interactive Consistency algorithm
 * 
 * @author Shwetha
 */
public class LeaderElectionExecutor extends UntypedActor {
	LoggingAdapter log = Logging.getLogger(getContext().system(), this);
	private ActorRef leReqRef = null;
	private ActorRef consRef = null;
	private ArrayList<Integer> nodeIdList = new ArrayList<Integer>();
	private ArrayList<Integer> failureList = new ArrayList<Integer>();
	private Integer myNodeId = 0;
	private Integer myGroupId = 0;
	private Boolean isLERunning = false;
	private Integer myVote = 0;
	private Function<Integer[], Integer[]> DecisionFunc;
	private ActorRef leSub = null;
	private ActorRef lePub = null;

	LeaderElectionExecutor(Integer nodeId, Integer gid,
			ArrayList<Integer> idList, ArrayList<Integer> failList,
			Integer myVote, Function<Integer[], Integer[]> DecisionFunc) {

		setMyNodeId(nodeId);
		setMyGroupId(gid);

		setNodeIdList(idList);
		setFailureList(failList);
		// Save the local vote
		this.myVote = myVote;
		// Save the App defined LE decision function
		this.DecisionFunc = DecisionFunc;
		leSub = getContext().system().actorOf(
				Props.create(Subscriber.class, getSelf(), Topics.LE_ACTION),
				ActorNames.LE_SUBSCRIBER_ACTOR);
		lePub = getContext().system().actorOf(
				Props.create(Publisher.class, Topics.LE_ACTION),
				ActorNames.LE_PUBLISHER_ACTOR);
	}

	// subscribe to LE requests
	@Override
	public void preStart() {
		// #subscribe
	}

	// unsubscribe to LE requests changes
	@Override
	public void postStop() {
		System.out.println("LeaderElectionExecutor stopping");
		if (consRef != null) {
			getContext().stop(consRef);
		}
		getContext().stop(leSub);
		getContext().stop(lePub);
	}

	@Override
	public void onReceive(Object message) throws Exception {
		// TODO Auto-generated method stub

		// If request from App
		if (message instanceof LEMsg) {
			LEMsg msg = (LEMsg) message;
			// If request to start
			if (msg.getLeAction() == LEMsg.action.LE_START) {
				System.out.println("Received leader election start message");
				setNodeIdList(msg.getNodeIdList());
				setFailureList(msg.getFailureList());
				if (msg.getIsFromApp()) {
					processLEStartReqFromApp();
					leReqRef = getSender();
				} else {
					processLEStartMsg();
				}

			} else if (msg.getLeAction() == LEMsg.action.LE_STATUS) {// If
				// request to get status
				leReqRef = getSender();
				leReqRef.tell(isLERunning, getSelf());
			} else if (msg.getLeAction() == LEMsg.action.LE_STOP) {// if
				// request to process stop request
				if (msg.getIsFromApp()) {
					processLEStopReqFromApp();
					leReqRef.tell(true, getSelf());
				} else {
					processLEStopMsg();
				}
			}

		} else if (message instanceof ICDecisionVectorMsg) {
			// Got votelist from ICExecuter.
			ICDecisionVectorMsg decVectorMap = (ICDecisionVectorMsg) message;
			processICVectorMsg(decVectorMap);

		} else {
			log.info("message: " + message);
			unhandled(message);
		}
	}

	public ArrayList<Integer> getNodeIdList() {
		return nodeIdList;
	}

	public void setNodeIdList(ArrayList<Integer> nodeIdList) {
		this.nodeIdList = nodeIdList;
	}

	public ArrayList<Integer> getFailureList() {
		return failureList;
	}

	public void setFailureList(ArrayList<Integer> failureList) {
		this.failureList = failureList;
	}

	public Integer getMyNodeId() {
		return myNodeId;
	}

	public void setMyNodeId(Integer myNodeId) {
		this.myNodeId = myNodeId;
	}

	public Integer getMyGroupId() {
		return myGroupId;
	}

	public void setMyGroupId(Integer myGroupId) {
		this.myGroupId = myGroupId;
	}

	/**
	 * Inform all participants (group member) of start LE request Start the LE
	 * at local node
	 */
	private void processLEStartReqFromApp() {
		if (!isLERunning) {
			// Inform all participants of start LE
			LEMsg msg = new LEMsg(false, action.LE_START);
			msg.setFailureList(failureList);
			msg.setNodeIdList(nodeIdList);
			lePub.tell(msg, getSelf());
			// Start LE
			processLEStartMsg();
		}

	}

	/**
	 * Inform all participants (group member) of stop LE request Stop the LE at
	 * local node
	 */
	private void processLEStopReqFromApp() {
		if (isLERunning) {
			// Inform all participants of stop request
			lePub.tell(new LEMsg(false, action.LE_STOP), getSelf());
			// Stop the LE
			processLEStopMsg();
		}

	}

	/**
	 * Create ICExecutor actor to start LE election by passing local vote and
	 * App defined decision function
	 */
	private void processLEStartMsg() {
		if (!isLERunning) {// Ignore if LE already running
			/*
			 * Create ICExecutor actor to start LE election by passing local
			 * vote and App defined decision function
			 */
			consRef = getContext().system().actorOf(
					Props.create(ICExecutor.class, getMyNodeId(),
							getMyGroupId(), getNodeIdList(), getFailureList(),
							this.myVote), ActorNames.IC_CONSENSUS_ACTOR);
			isLERunning = true;
			consRef.tell(StringMsg.GET_CONSENSUS_RESULT, getSelf());
		}
	}

	/**
	 * Stop the ICExecutor actor to stop LE election Stop the LE at local node
	 */
	private void processLEStopMsg() {
		try {
			if (isLERunning) {
				getContext().stop(consRef);
				isLERunning = false;
			}
		} catch (ActorKilledException e) {
			e.printStackTrace();
		}
	}

	/**
	 * This method retrives decision vector and calls the App defined function
	 * to apply the leader election criterion and sends the corresponding node
	 * Ids as leaders
	 * 
	 * @param decVectorMsg
	 *            decision vector received from ICExecutor
	 */
	private void processICVectorMsg(ICDecisionVectorMsg decVectorMsg) {
		Integer finalVoteList[] = new Integer[decVectorMsg.getFinalList()
				.size()];
		// Get the vote list
		Collection<Integer> temp = decVectorMsg.getFinalList().values();
		finalVoteList = temp.toArray(new Integer[temp.size()]);
		// Call App defined function
		Integer result[] = this.DecisionFunc.apply(finalVoteList);
		Integer leaders[] = new Integer[2];
		// Get the node Ids that proposed the votes
		for (Entry<Integer, Integer> entry : decVectorMsg.getFinalList()
				.entrySet()) {
			if (entry.getValue().equals(result[0])) {
				leaders[0] = entry.getKey();
			} else if (entry.getValue().equals(result[1])) {
				leaders[1] = entry.getKey();
			}
		}
		System.out.println("Leader Ids: " + leaders[0] + " " + leaders[1]);

		// Inform the leader Ids to LeaderElectionImpl
		leReqRef.tell(leaders, getSelf());
		// Inform the leader Ids to groupMgr
		LeaderIdMsg lIdMsg = new LeaderIdMsg(myGroupId);
		lIdMsg.setLeaderIds(leaders);
		informGroupMgr(lIdMsg);
		try {
			isLERunning = false;
			// Stop the ICExecuter
			getContext().stop(consRef);
		} catch (ActorKilledException e) {
			e.printStackTrace();
		}
	}

	/**
	 * This method informs the group mgr act of the new elected leader, so that
	 * group mgmt system knows about the new group leaders
	 * 
	 * @param leaderMsg
	 *            Message containing group leader ids
	 */
	private void informGroupMgr(LeaderIdMsg leaderMsg) {

		String path = "akka://" + ActorNames.GROUP_ACTOR_SYSTEM + "/user/"
				+ ActorNames.GROUP_MGR;
		System.out.println("InformGroupMgr sending");
		try {
			getContext().actorSelection(path).tell(leaderMsg, getSelf());
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
}
