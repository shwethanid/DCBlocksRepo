package serviceImplementation;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.function.Function;

import messages.ICDecisionVectorMsg;
import messages.StringMsg;
import constants.ActorNames;
import akka.actor.*;
import akka.dispatch.OnComplete;
import akka.dispatch.OnSuccess;
import akka.pattern.Patterns;
import akka.util.Timeout;
import scala.concurrent.Await;
import scala.concurrent.Future;
import scala.concurrent.duration.Duration;
import serviceActors.*;
import serviceInterfaces.Consensus;

/**
 * implementation class for public interface Consensus
 * 
 * @author Shwetha
 *
 */
public class ConsensusImpl implements Consensus {
	private ActorRef consSimActorRef = null;
	private ActorRef consICActorRef = null;
	private ActorSystem system = null;
	private ArrayList<Integer> nodeIdList = new ArrayList<Integer>();
	private ArrayList<Integer> failureIdList = new ArrayList<Integer>();
	private Boolean isSimConsensusRunning = false;
	private Boolean isICConsensusRunning = false;
	private Integer myNodeId = 0;
	private Integer myGroupId = 0;
	Function<Integer, Boolean> appCallBackFunc = null;

	/**
	 * Constructor for ConsensusImpl
	 * 
	 * @param system
	 *            ActorSystem
	 * @param nodeIdList
	 *            list of group members
	 * @param failureIdList
	 *            list of failure members
	 */
	public ConsensusImpl(ActorSystem system, Integer myNodeId,
			Integer myGroupId, ArrayList<Integer> nodeIdList,
			ArrayList<Integer> failureIdList) {
		this.system = system;
		this.nodeIdList = nodeIdList;
		this.failureIdList = failureIdList;
		this.myNodeId = myNodeId;
		this.myGroupId = myGroupId;
	}

	public final static class PrintResult<T> extends OnSuccess<T> {
		@Override
		public final void onSuccess(T t) {
			System.out.println(t);
		}
	}

	/**
	 * Starts Simple Consensus by creating the SimpleCExecuter actor to perform
	 * the consensus
	 * 
	 * @param proposedValue
	 *            local value
	 * @param callBackFunc
	 *            function to call after consensus/agreement has reached
	 * @return True if start success
	 */
	@Override
	public Boolean startNonBlockingSimpleConsensus(Integer proposedValue,
			Function<Integer, Boolean> callBackFunc,
			Function<Integer[], Integer> decisionFunc) {
		// TODO Auto-generated method stub
		Boolean isSuccess = true;
		if (!isSimConsensusRunning && (system != null)) {
			try {
				// Start consensus
				consSimActorRef = system.actorOf(Props.create(
						SimpleCExecutor.class, myNodeId, myGroupId, nodeIdList,
						failureIdList, proposedValue, decisionFunc),
						ActorNames.SIMPLE_CONSENSUS_ACTOR);

				isSimConsensusRunning = true;
				Timeout timeout = new Timeout(Duration.create(120, "seconds"));
				String queryMsg = StringMsg.GET_CONSENSUS_RESULT;

				// Ast Simple Consensus actor for result
				Future<Object> execCompletion = Patterns.ask(consSimActorRef,
						queryMsg, timeout);// in milliseconds

				OnComplete<Object> onNBConsComplete = new OnComplete<Object>() {
					@Override
					public void onComplete(Throwable failure, Object result)
							throws Throwable {
						isSimConsensusRunning = false;
						if (result != null) {
							System.out.format("Got the answer: %s\n", result);
							callBackFunc.apply((Integer) result);
							system.stop(consSimActorRef);
							consSimActorRef = null;
						} else
							failure.printStackTrace();
					}
				};

				execCompletion
						.onComplete(onNBConsComplete, system.dispatcher());

			} catch (Exception e) {
				e.printStackTrace();
				isSuccess = false;
				system.stop(consSimActorRef);
				consSimActorRef = null;
				isSimConsensusRunning = false;
			}
		} else {
			isSuccess = false;
		}

		return isSuccess;

	}

	/**
	 * Starts Simple Consensus by creating the SimpleCExecuter actor to perform
	 * the consensus
	 * 
	 * @param proposedValue
	 *            local value
	 * @return Consensus/agreement result
	 */
	@Override
	public Integer startBlockingSimpleConsensus(Integer proposedValue,
			Function<Integer[], Integer> decisionFunc) {
		// TODO Auto-generated method stub
		Integer result = -1;

		if (!isSimConsensusRunning && (system != null)) {
			try {
				isSimConsensusRunning = true;
				consSimActorRef = system.actorOf(Props.create(
						SimpleCExecutor.class, myNodeId, myGroupId, nodeIdList,
						failureIdList, proposedValue, decisionFunc),
						ActorNames.SIMPLE_CONSENSUS_ACTOR);

				Timeout timeout = new Timeout(Duration.create(120, "seconds"));
				String queryMsg = StringMsg.GET_CONSENSUS_RESULT;

				/*
				 * System.out.println(
				 * "[groupCmdDispatcher class, getHealthStatus()]...Trying to ask member status from grpMgr for group: "
				 * + gid);
				 */

				Future<Object> execCompletion = Patterns.ask(consSimActorRef,
						queryMsg, timeout);// in milliseconds
				Object reply = Await.result(execCompletion,
						timeout.duration());
				if(reply instanceof Integer) {
					result = (Integer)reply;
					system.stop(consSimActorRef);
					consSimActorRef = null;
				} else {
					result = -1;
				}
				
				isSimConsensusRunning = false;
			} catch (Exception e) {
				e.printStackTrace();
				system.stop(consSimActorRef);
				consSimActorRef = null;
				isSimConsensusRunning = false;
			}
		}
		return result;
	}

	/**
	 * Starts IC Consensus by creating the ICExecuter actor to perform the
	 * consensus
	 * 
	 * @param proposedValue
	 *            local value
	 * @param callBackFunc
	 *            function to call after consensus/agreement vector has reached
	 * @return True if start success
	 */
	@Override
	public Boolean startNonBlockingICConsensus(Integer proposedValue,
			Function<Integer[], Boolean> callBackFunc) {
		// TODO Auto-generated method stub

		Boolean isSuccess = true;
		if (!isICConsensusRunning && (system != null)) {

			try {
				isICConsensusRunning = true;
				consICActorRef = system.actorOf(Props.create(ICExecutor.class,
						myNodeId, myGroupId, nodeIdList, failureIdList,
						proposedValue), ActorNames.IC_CONSENSUS_ACTOR);

				Timeout timeout = new Timeout(Duration.create(120, "seconds"));
				String queryMsg = StringMsg.GET_CONSENSUS_RESULT;

				Future<Object> execCompletion = Patterns.ask(consICActorRef,
						queryMsg, timeout);// in milliseconds

				OnComplete<Object> onNBConsComplete = new OnComplete<Object>() {
					@Override
					public void onComplete(Throwable failure, Object result)
							throws Throwable {

						if (result != null) {
							System.out
									.format("Got the IC answer: %s\n", result);

							ICDecisionVectorMsg decVectorMsg = (ICDecisionVectorMsg) result;
							Integer finalList[] = new Integer[decVectorMsg
									.getFinalList().size()];
							// Get the list
							Collection<Integer> temp = decVectorMsg
									.getFinalList().values();
							finalList = temp.toArray(new Integer[temp.size()]);
							// Call App defined callback function
							callBackFunc.apply((Integer[]) finalList);
							system.stop(consICActorRef);

						} else
							failure.printStackTrace();
						isICConsensusRunning = false;
					}
				};

				execCompletion
						.onComplete(onNBConsComplete, system.dispatcher());

			} catch (Exception e) {
				e.printStackTrace();
				system.stop(consICActorRef);
				isSuccess = false;
				consICActorRef = null;
				isICConsensusRunning = true;
			}
		} else {
			isSuccess = false;
		}
		return isSuccess;
	}

	/**
	 * Starts IC Consensus by creating the ICExecuter actor to perform the
	 * consensus
	 * 
	 * @param proposedValue
	 *            local value
	 * @return Consensus/agreement vector
	 */
	@Override
	public Integer[] startBlockingICConsensus(Integer proposedValue) {
		// TODO Auto-generated method stub
		Integer finalList[] = new Integer[nodeIdList.size()];

		if (!isICConsensusRunning && (system != null)) {
			try {
				isICConsensusRunning = true;
				consICActorRef = system.actorOf(Props.create(ICExecutor.class,
						myNodeId, myGroupId, nodeIdList, failureIdList,
						proposedValue), ActorNames.IC_CONSENSUS_ACTOR);

				Timeout timeout = new Timeout(Duration.create(120, "seconds"));
				String queryMsg = StringMsg.GET_CONSENSUS_RESULT;

				Future<Object> execCompletion = Patterns.ask(consICActorRef,
						queryMsg, timeout);// in milliseconds
				// Wait for result
				Object reply = Await
						.result(execCompletion, timeout.duration());
				if(reply instanceof ICDecisionVectorMsg) {
					ICDecisionVectorMsg decVectorMsg = (ICDecisionVectorMsg) Await
							.result(execCompletion, timeout.duration());
					finalList = new Integer[decVectorMsg.getFinalList().size()];
					// Get the list
					Collection<Integer> temp = decVectorMsg.getFinalList().values();
					finalList = temp.toArray(new Integer[temp.size()]);

					system.stop(consICActorRef);
					consICActorRef = null;
				} else {
					Arrays.fill(finalList, 0);
				}

				isICConsensusRunning = false;
			} catch (Exception e) {
				e.printStackTrace();
				system.stop(consICActorRef);
				consICActorRef = null;
				isICConsensusRunning = false;
			}
		}
		return finalList;
	}

	/**
	 * Stops Simple Consensus if running
	 * 
	 * @return True if success Else false
	 */
	@Override
	public Boolean stopSimpleConsensus() {
		// TODO Auto-generated method stub

		Boolean isSuccess = false;

		if (consSimActorRef != null) {
			system.stop(consSimActorRef);
			consSimActorRef = null;
			isSuccess = true;
		}
		return isSuccess;
	}

	/**
	 * Stops IC Consensus if running
	 * 
	 * @return True if success Else false
	 */
	@Override
	public Boolean stopICConsensus() {
		// TODO Auto-generated method stub
		Boolean isSuccess = false;
		if (consICActorRef != null) {
			system.stop(consICActorRef);
			consICActorRef = null;
			isSuccess = true;
		}
		return isSuccess;
	}

	/**
	 * Checks if Simple Consensus is in progress
	 * 
	 * @return True if running Else false
	 */
	@Override
	public Boolean isSimpleConsensusInProgress() {
		// TODO Auto-generated method stub
		return isSimConsensusRunning;
	}

	/**
	 * Checks if IC Consensus is in progress
	 * 
	 * @return True if running Else false
	 */
	@Override
	public Boolean isICConsensusInProgress() {
		// TODO Auto-generated method stub
		return isICConsensusRunning;
	}
}
