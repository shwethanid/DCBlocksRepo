package serviceImplementation;

import java.util.ArrayList;
import java.util.function.Function;

import constants.ActorNames;
import messages.LEMsg;
import messages.LEMsg.action;
import akka.actor.*;
import akka.pattern.Patterns;
import akka.util.Timeout;
import scala.concurrent.Await;
import scala.concurrent.Future;
import scala.concurrent.duration.Duration;
import serviceActors.LeaderElectionExecutor;
import serviceInterfaces.LeaderElection;
import serviceImplementation.GroupMgmtImpl;

/**
 * This class implements LeaderElection interface
 * 
 * @author Shwetha
 *
 */
public class LeaderElectionImpl implements LeaderElection {
	private ActorSystem system = null;
	private ArrayList<Integer> nodeIdList = new ArrayList<Integer>();
	private ArrayList<Integer> failureIdList = new ArrayList<Integer>();
	private Boolean isLERunning = false;
	private ActorRef LEExecutor = null;
	private Integer priLeaderId = 0;
	private Integer secLeaderId = 0;
	private Function<Integer[], Integer[]> decisionFunc = null;
	private Integer myVote = 0;
	private Integer myNodeId = 0;
	private Integer myGroupId = 0;

	/**
	 * Constructor for LeaderElectionImpl
	 * 
	 * @param system
	 * @param nodeIdList
	 * @param failureIdList
	 */
	public LeaderElectionImpl(ActorSystem system, Integer myNodeId,
			Integer myGroupId, GroupMgmtImpl grpMgmt) {
		this.system = system;
		this.setNodeIdList(grpMgmt.getMemberList(myGroupId));
		this.setFailureIdList(grpMgmt.getFailedList(myGroupId));
		this.myGroupId = myGroupId;
		this.myNodeId = myNodeId;
	}

	/**
	 * Starts the leader Election Executer actor to perform the leader election
	 * 
	 * @param myVote
	 *            my vote
	 * @param decisionFunc
	 *            App defined decision function
	 * @return True if success else failure
	 */
	public Boolean setUpForLeaderElection(Integer myVote,
			Function<Integer[], Integer[]> decisionFunc) {
		Boolean isSuccess = false;

		if (system != null) {
			LEExecutor = system.actorOf(Props.create(
					LeaderElectionExecutor.class, myNodeId, myGroupId,
					nodeIdList, failureIdList, myVote, decisionFunc),
					ActorNames.LEADER_ELECTION_EXECUTER);
			this.setDecisionFunc(decisionFunc);
			this.setMyVote(myVote);
			isSuccess = true;
		}

		return isSuccess;
	}

	/**
	 * Starts the leader election by start request to Leader Election Executor
	 * actor. Waits for the election result.
	 * 
	 * @return elected leader Id
	 */
	@Override
	public Integer startElection() throws Exception {

		// Send Leader Election Start message
		if (system != null) {
			Integer[] result = new Integer[2];

			try {
				Timeout timeout = new Timeout(Duration.create(120, "seconds"));
				LEMsg queryMsg = new LEMsg(true, action.LE_START);
				queryMsg.setFailureList(failureIdList);
				queryMsg.setNodeIdList(nodeIdList);
				Future<Object> leCompletion = Patterns.ask(LEExecutor,
						queryMsg, timeout);// in milliseconds
				isLERunning = true;
				result = (Integer[]) Await.result(leCompletion,
						timeout.duration());
				priLeaderId = result[0];
				secLeaderId = result[1];
				System.out.println("Primary Leader : " + priLeaderId
						+ "Secondary Leader" + secLeaderId);
				// system.stop(LEExecutor);---Why??
				isLERunning = true;
			} catch (Exception e) {

				e.printStackTrace();
				throw (e);
			}
		}
		return priLeaderId;
	}

	/**
	 * @return Primary leader Id
	 */
	@Override
	public int getPrimaryLeader() {
		// TODO Auto-generated method stub
		return priLeaderId;
	}

	/**
	 * @return Primary leader Id
	 */
	@Override
	public int getSecondaryLeader() {
		// TODO Auto-generated method stub
		return secLeaderId;
	}

	/**
	 * @return True if primary leader, else false
	 */
	@Override
	public Boolean isPrimaryLeader(int nodeId) {
		// TODO Auto-generated method stub
		return (priLeaderId == nodeId) ? true : false;
	}

	/**
	 * @return True if primary leader, else false
	 */
	@Override
	public Boolean isSecondaryLeader(int nodeId) {
		// TODO Auto-generated method stub
		return (secLeaderId == nodeId) ? true : false;
	}

	/**
	 * Stops the leader election by sending stop request to Leader Election
	 * Executor actor
	 * 
	 * @return true if success else false
	 */
	@Override
	public Boolean stopElection() {
		Boolean isSuccess = false;

		if (isLERunning) {
			// TODO Auto-generated method stub
			try {
				Timeout timeout = new Timeout(Duration.create(1, "seconds"));
				LEMsg queryMsg = new LEMsg(true, action.LE_STOP);
				queryMsg.setFailureList(failureIdList);
				queryMsg.setNodeIdList(nodeIdList);
				Future<Object> leStatus = Patterns.ask(LEExecutor, queryMsg,
						timeout);// in milliseconds
				isSuccess = (Boolean) Await
						.result(leStatus, timeout.duration());
				isLERunning = false;
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
		return isSuccess;
	}

	/**
	 * Checks if leader election is running by sending leader status request to
	 * Leader Election Executor actor
	 * 
	 * @return true if success else false
	 */
	
	public Boolean isElectionInProgress() {
		Boolean isRunning = false;

		try {
			Timeout timeout = new Timeout(Duration.create(1, "seconds"));
			LEMsg queryMsg = new LEMsg(true, action.LE_STATUS);
			queryMsg.setFailureList(failureIdList);
			queryMsg.setNodeIdList(nodeIdList);
			Future<Object> leStatus = Patterns.ask(LEExecutor, queryMsg,
					timeout);// in milliseconds
			isRunning = (Boolean) Await.result(leStatus, timeout.duration());
			isLERunning = isRunning;
		} catch (Exception e) {
			e.printStackTrace();
		}
		return isRunning;
	}

	/**
	 * Removes all the dependencies for leader election. 
	 * Stops the Leader Election Executor actor
	 */
	public Boolean removeSetUpForLeaderElection() {
		Boolean isSuccess = false;

		if (system != null) {
			system.stop(LEExecutor);
			isSuccess = true;
		}

		return isSuccess;
	}
	
	public ArrayList<Integer> getNodeIdList() {
		return nodeIdList;
	}

	public void setNodeIdList(ArrayList<Integer> nodeIdList) {
		this.nodeIdList = nodeIdList;
	}

	public ArrayList<Integer> getFailureIdList() {
		return failureIdList;
	}

	public void setFailureIdList(ArrayList<Integer> failureIdList) {
		this.failureIdList = failureIdList;
	}

	public Function<Integer[], Integer[]> getDecisionFunc() {
		return decisionFunc;
	}

	public void setDecisionFunc(Function<Integer[], Integer[]> decisionFunc) {
		this.decisionFunc = decisionFunc;
	}

	public Integer getMyVote() {
		return myVote;
	}

	public void setMyVote(Integer myVote) {
		this.myVote = myVote;
	}
}
