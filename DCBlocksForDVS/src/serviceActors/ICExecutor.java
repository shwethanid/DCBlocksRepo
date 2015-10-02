package serviceActors;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Function;

import akka.actor.*;
import akka.event.Logging;
import akka.event.LoggingAdapter;
import messages.ICDecisionVectorMsg;
import messages.ProposedMsg;
import messages.StringMsg;
import serviceActors.Subscriber;
import constants.ActorNames;
import constants.Topics;

/**
 * This class implements interactive consistency algorithm. Each node exchanges
 * its local proposed value with all group members. After each node gets
 * consistent vector containing all local values, it calls App defined decision
 * function
 * 
 * @author shwetha
 *
 */
public class ICExecutor extends UntypedActor {
	LoggingAdapter log = Logging.getLogger(getContext().system(), this);
	private HashMap<Integer, ArrayList<ProposedMsg>> rxMsgMap;
	private Integer myNodeId;
	private Integer myGroupId;
	private Integer failed;// Number of failures
	private Integer rounds;// Number of rounds
	private Integer myProposedValue;
	private Boolean[] foundList;
	private Integer leaderId;
	private ActorRef icSub = null;
	private ActorRef icPub = null;
	private ActorRef caller;
	private ArrayList<Integer> nodeIdList;
	private ArrayList<Integer> failureList;
	private int group_size = 0;
	private Thread algoThread = null;
	private volatile boolean isAlgoStop = false;
	private volatile boolean isAlgoRunning = false;
	final Lock msgLock = new ReentrantLock();
	private ArrayList<Integer> replyList = new ArrayList<Integer>();
			
	ICExecutor(Integer nodeId, Integer gid, ArrayList<Integer> idList,
			ArrayList<Integer> failList, Integer proposedValue) {

		System.out.println("ICExecutor::ICExecutor");
		setmyNodeId(nodeId);
		setmyGroupId(gid);
		rxMsgMap = new HashMap<Integer, ArrayList<ProposedMsg>>();
		rounds = 0;
		failed = 0;
		myProposedValue = proposedValue;
		// (nodeId * Constants.COMP_ABILITY_FACTOR);
		System.out.println("IC Parent: " + getContext().parent());
		// parent = getContext().parent(); //Does it work???
		nodeIdList = new ArrayList<Integer>(idList);
		Collections.sort(nodeIdList);
		group_size = idList.size();
		foundList = new Boolean[group_size];
		System.out.println("nodeIdList size:" + nodeIdList.size());
		setFailureList(new ArrayList<Integer>(failList));
		System.out.println("Failed list size: "+ failList.size());
		if (failList.isEmpty()) {
			failed = 0;
		} else {
			setFailed(failList.size());
		}
		icSub = getContext().system().actorOf(
				Props.create(Subscriber.class, getSelf(), Topics.IC_CONSENSUS),
				ActorNames.IC_SUBSCRIBER_ACTOR);
		icPub = getContext().system().actorOf(
				Props.create(Publisher.class, Topics.IC_CONSENSUS),
				ActorNames.IC_PUBLISHER_ACTOR);
		//ConsensusCoordMsg msg = new ConsensusCoordMsg(myNodeId, consStatus.START_REQ);
		//icPub.tell(msg, getSelf());
	}

	@Override
	public void preStart() {
		runICA();
	}

	public void preRestart() {
		getContext().stop(icSub);
		getContext().stop(icPub);
	}

	@Override
	public void postStop() {
		isAlgoStop = true;
		//Trying to stop when algorithm is still running
		if(isAlgoRunning) {
			//Throw an exception to caller
			Throwable ex = new ActorKilledException("Target actor terminated.");
	        caller.tell(new Status.Failure(ex), self());
		}
		preRestart();
		replyList.removeAll(replyList);
		log.info("ICExecuter Stopping");
	}

	@Override
	public void onReceive(Object message) {
		// log.info("message: " + message);

		if (message instanceof ProposedMsg) {
			ProposedMsg msg = (ProposedMsg) message;
			// log.info("got CAmessage from node id: " + ca.getnodeId());
			addRxMsg(msg);
		} else if (message instanceof String) {
			if (message.equals(StringMsg.GET_CONSENSUS_RESULT)) {
				caller = getSender();
			}
		} /*else if(message instanceof ConsensusCoordMsg) {
			ConsensusCoordMsg msg = (ConsensusCoordMsg) message;
			System.out.println("ICExecuter received: "+ msg.getStat().toString());
			if(msg.getStat() == consStatus.START_REQ) {
				getSender().tell(new ConsensusCoordMsg(myNodeId, consStatus.READY), getSelf());
			}
			if(!replyList.contains(msg.getNodeId())) {
					replyList.add(msg.getNodeId());
			}
			if(replyList.size() >= group_size) {
				runICA();
			}
		}*/ else {
			log.info("message: " + message);
			unhandled(message);
		}
	}

	// @Override
	private void runICA() {
		// Check number of failure nodes
		if (failed > ((nodeIdList.size() - 1) / 2)) {
			System.out.println("Cannot run Leader Election Algorithm");
			icPub.tell(ProposedMsg.ERR_VALUE, getSelf());
		} else {
			// Run IC algorithm
			algoThread = new Thread(new algoRunnable());
			algoThread.start();
		}
	}


	private ProposedMsg formMessage(ArrayList<ProposedMsg> rxMsg, Integer newRnd) {
		ProposedMsg newMsg = new ProposedMsg(myNodeId, newRnd);
		ProposedMsg tempMsg = null;
		Integer[] list = new Integer[nodeIdList.size()];
		Boolean isFnd = false;
		ArrayList<Integer> valList = new ArrayList<Integer>(nodeIdList.size());
		
		//System.out.println("Start-- formMessage. Rnd: " + newRnd);
		if (newRnd == 1) {
			// Iterate through each node
			for (Integer id : nodeIdList) {
				isFnd = false;
				// Get the received message for that node
				for (int j = 0; j < rxMsg.size() && !isFnd; j++) {
					tempMsg = rxMsg.get(j);
					if ((tempMsg != null)
							&& (id.intValue() == (tempMsg.getnodeId()
									.intValue()))) {
						isFnd = true;
						valList.add(tempMsg.getValueList().get(0));
						//newMsg.addProposedValue(tempMsg.getValueList().get(0));
					}
				}
				if (!isFnd) {
					//newMsg.addProposedValue(new Integer(ProposedMsg.ERR_VALUE));
					valList.add(ProposedMsg.ERR_VALUE);
				}
			}
			newMsg.addProposedValueList(valList);
		} else {
			// For each node, find the majority value to add into send list
			for (int i = 0; i < nodeIdList.size(); i++) {
				for (int j = 0; j < nodeIdList.size(); j++) {

					isFnd = false;
					for (int k = 0; k < rxMsg.size() && (!isFnd); k++) {
						tempMsg = rxMsg.get(k);

						/*System.out.println("Node id reguired: "
								+ nodeIdList.get(j) + "but got:"
								+ tempMsg.getnodeId());*/

						if ((tempMsg != null)
								&& (nodeIdList.get(j).intValue() == (tempMsg
										.getnodeId().intValue()))) {
							
							if(tempMsg.getValueList().get(i) != null) {
								list[j] = tempMsg.getValueList().get(i);
								isFnd = true;
							}
							/*System.out.println("list items: " + list[j]
									+ "index: " + j);*/
						}
					}
					if (!isFnd) {
						list[j] = ProposedMsg.ERR_VALUE;
					}
				}
				newMsg.addProposedValue(computeMajority(list));
			}
		}
		System.out.println("Printing list sending for round:" + newRnd
				+ " CA: " + newMsg.getValueList());
		System.out.println("End-- formMessage. Rnd: " + newRnd);

		return newMsg;
	}

	private Integer computeMajority(Integer[] raw_list) {
		Map<Integer, Integer> m = new HashMap<Integer, Integer>();
		int max = -1;
		int maj = -1;
		//System.out.println("Start-- computeMajority. ");

		for (int a : raw_list) {
			Integer freq = m.get(a);
			m.put(a, (freq == null) ? 1 : freq + 1);
		}

		for (Map.Entry<Integer, Integer> e : m.entrySet()) {
			if (e.getValue() > max) {
				maj = e.getKey();
				max = e.getValue();
			}
		}
		//System.out.println("Maj:" + maj);
		//System.out.println("End-- computeMajority. ");
		return maj;
	}

	public Integer getmyNodeId() {
		return myNodeId;
	}

	public void setmyNodeId(Integer myNodeId) {
		this.myNodeId = myNodeId;
	}

	public Integer getmyGroupId() {
		return myGroupId;
	}

	public void setmyGroupId(Integer myGroupId) {
		this.myGroupId = myGroupId;
	}

	public Integer getFailed() {
		return failed;
	}

	public void setFailed(Integer failed) {
		this.failed = failed;
	}

	public Integer getRounds() {
		return rounds;
	}

	public void setRounds(Integer rounds) {
		this.rounds = rounds;
	}

	public Integer getleaderId() {
		return leaderId;
	}

	public void setleaderId(Integer leaderId) {
		this.leaderId = leaderId;
	}

	public void addRxMsg(ProposedMsg msg) {
		// System.out.println("Start-- addRxMsg--");
		ArrayList<ProposedMsg> msgArr = new ArrayList<ProposedMsg>();
		msgLock.lock();
		if (rxMsgMap.containsKey(msg.getnodeId())) {
			rxMsgMap.get(msg.getnodeId()).add(msg);
			msgArr = rxMsgMap.get(msg.getnodeId());
		} else {
			msgArr.add(msg);
			rxMsgMap.put(msg.getnodeId(), msgArr);
		}
		msgLock.unlock();
		/*
		 * System.out.println("List after adding new incoming message for node:"
		 * + msg.getnodeId()); printArraylist(msgArr);
		 * System.out.println("End-- addRxMsg--");
		 */
	}

	public ArrayList<ProposedMsg> getRxMsg(Integer rnd) {
		ArrayList<ProposedMsg> list = new ArrayList<ProposedMsg>();
		ArrayList<ProposedMsg> temp = new ArrayList<ProposedMsg>();
		Boolean isFnd = false;

		msgLock.lock();
		System.out.println("Start-- getRxMsg for round" + rnd);
		for (Integer id : nodeIdList) {
			if (rxMsgMap.containsKey(id)) {
				temp = rxMsgMap.get(id);
				isFnd = false;
				for (int j = 0; j < temp.size() && !isFnd; j++) {
					if (rnd.equals(temp.get(j).getRnd())) {
						isFnd = true;
						list.add(temp.get(j));
					}
				}
			}
		}
		msgLock.unlock();
		System.out.println("Printing list received for round:" + rnd);
		printArraylist(list);
		System.out.println("End-- getRxMsg for round" + rnd);
		return list;
	}

	private void checkMessage(ArrayList<ProposedMsg> msg) {
		int j = 0;

		// System.out.println("Start-- check_message");
		for (ProposedMsg temp: msg) {
			
			if (nodeIdList.contains(temp.getnodeId())) {
				j = nodeIdList.indexOf(temp.getnodeId());
				foundList[j] = true;
			} else {
				
			}
		}

	//	 System.out.println("End-- check_message");
	}
	
	private void clearFoundList() {
		Arrays.fill(foundList, Boolean.FALSE);
	}
	
	private void printArraylist(ArrayList<ProposedMsg> list) {
		System.out.println("/******ArrayList details******/: Size:"
				+ list.size());
		for (int i = 0; i < list.size(); i++) {
			System.out.println("NodeId: " + list.get(i).getnodeId() + " Rnd: "
					+ list.get(i).getRnd() + " CA: "
					+ list.get(i).getValueList());

		}
		System.out.println("/*******************************/");
	}

	public Integer getmyProposedValue() {
		return myProposedValue;
	}

	public void setmyProposedValue(Integer proposedValue) {
		this.myProposedValue = proposedValue;
	}

	public ArrayList<Integer> getFailureList() {
		return failureList;
	}

	public void setFailureList(ArrayList<Integer> failureList) {
		this.failureList = failureList;
	}

	public class algoRunnable implements Runnable {
		public void run() {
			isAlgoRunning = true;
			ProposedMsg msg = new ProposedMsg(myNodeId, 0);
			System.out.println("Starting IC Consensus");

			ArrayList<ProposedMsg> rxMsg = new ArrayList<ProposedMsg>();
			try {// Sleeps for 12 seconds
				Thread.sleep(6000);
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
			while (rounds <= (failed + 1) && !isAlgoStop) {

				if (rounds == 0) {
					// Send local proposed value value to all other nodes
					msg.addProposedValue(myProposedValue);
					log.info("sending message for rnd 0");
					System.out.println("sending message: " + msg.getnodeId()
							+ " " + msg.getRnd() + " " + msg.getValueList());
					icPub.tell(msg, getSelf());
				} else {
					// Clear the found list
					//clearFoundList();
					// Get the rx messages for last round
					rxMsg = getRxMsg(rounds - 1);
					// Check for missing or corrupt messages
					//checkMessage(rxMsg);
					/*
					 * Form new tx message by checking all received values from
					 * other nodes. Take the majority value for each array index
					 * and add to the final rx array
					 */
					ProposedMsg newMsg = formMessage(rxMsg, rounds);
					/*ProposedMsg newMsg = new ProposedMsg(myNodeId, rounds);
					ArrayList<Integer> tempList = new ArrayList<Integer>();
					for(Integer i=0; i < 10; i++) {
						tempList.add(i*myNodeId);
					}
					newMsg.addProposedValueList(tempList);*/
					icPub.tell(newMsg, getSelf());
				}
				rounds++;
				try {// Sleeps for 5 seconds
					Thread.sleep(15000);
				} catch (InterruptedException e) {
					e.printStackTrace();
				}
			}
			if (rounds > (failed + 1) && !isAlgoStop) {
				// Clear the found list
				//clearFoundList();
				// Get the rx messages for last round
				rxMsg = getRxMsg(rounds - 1);
				// Check for missing or corrupt messages
				//checkMessage(rxMsg);
				/*
				 * Form new tx message by checking all received values from
				 * other nodes. Take the majority value for each array index and
				 * add to the final rx array
				 */
				msg = formMessage(rxMsg, rounds);
				// Get the final proposed list
				// Integer finalList[] = new Integer[group_size];
				ICDecisionVectorMsg finalListMsg = new ICDecisionVectorMsg();

				for (int k = 0; k < msg.getValueList().size(); k++) {
					finalListMsg.getFinalList().put(nodeIdList.get(k),
							msg.getValueList().get(k));

				}

				System.out.println("Sending result to parent: "
						+ finalListMsg.getFinalList().size());

				if (caller != null) {
					caller.tell(finalListMsg, getSelf());

				}
			}
			isAlgoRunning = false;
		}
	}
	
	Function<ProposedMsg, Boolean> rxReceiver = (msg) -> {
		
		new Thread(new Runnable() {

			@Override
			public void run() {
				addRxMsg(msg);
			}

		}).start();

		return true;
	};
}
