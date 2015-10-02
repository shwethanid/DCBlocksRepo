package serviceActors;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.function.Function;

import messages.*;
import constants.*;
import akka.actor.*;
import akka.event.Logging;
import akka.event.LoggingAdapter;

public class SimpleCExecutor extends UntypedActor {
	LoggingAdapter log = Logging.getLogger(getContext().system(), this);
	private HashMap<Integer, ArrayList<ProposedMsg>> rxMsgMap;
	private Integer myNodeId = 0;
	private Integer myGroupId = 0;
	private Integer failed = 0;// Number of failures
	private Integer rounds = 0;// Number of rounds
	private Integer myProposedValue;
	private ActorRef simSub = null;
	private ActorRef simPub = null;
	private ArrayList<Integer> nodeIdList = new ArrayList<Integer>();
	private ArrayList<Integer> failureList = new ArrayList<Integer>();
	private int groupSize = 0;
	private Function<Integer[], Integer> DecisionFunc = null;
	private ActorRef caller = null;
	private ArrayList<Integer> myListToSend = new ArrayList<Integer>();
	private Thread algoThread = null;
	private volatile boolean isAlgoStop = false;
	private volatile boolean isAlgoRunning = false;
	
	SimpleCExecutor(Integer nodeId, Integer gid, ArrayList<Integer> idList,
			ArrayList<Integer> failList, Integer proposedValue,
			Function<Integer[], Integer> DecisionFunc) {

		this.DecisionFunc = DecisionFunc;

		setMyNodeId(nodeId);
		setMyGroupId(gid);
		rxMsgMap = new HashMap<Integer, ArrayList<ProposedMsg>>();
		rounds = 0;
		setFailed(0);
		setMyProposedValue(proposedValue);
		setNodeIdList(idList);

		simSub = getContext().system().actorOf(
				Props.create(Subscriber.class, getSelf(),
						Topics.SIMPLE_CONSENSUS),
				ActorNames.SIM_SUBSCRIBER_ACTOR);

		groupSize = idList.size();
		System.out.println("nodeIdList size:" + nodeIdList.size());
		setFailureList(failList);
		if (failList.isEmpty()) {
			setFailed(0);
		} else {
			setFailed(failList.size());
		}
		simPub = getContext().system().actorOf(
				Props.create(Publisher.class, Topics.SIMPLE_CONSENSUS),
				ActorNames.SIM_PUBLISHER_ACTOR);

	}

	@Override
	public void preStart() {
		Boolean status = runSimpleConcensus();
		if (status == false) {
			if (caller != null) {
				caller.tell(ProposedMsg.ERR_VALUE, getSelf());
			}
			getContext().stop(getSelf());
		}
	}

	public void preRestart() {
		getContext().stop(simSub);
		getContext().stop(simPub);
	}

	@Override
	public void postStop() {
		System.out.println("SimpleCExecutor stopping");
		isAlgoStop = true;
		//Trying to stop when algorithm is still running
		if(isAlgoRunning) {
			System.out.println("kdjfkjdf");
			Throwable ex = new ActorKilledException("Target actor terminated.");
	        caller.tell(new Status.Failure(ex), self());
		}
		
		// run_ICA(); -- Todo
		preRestart();
		log.info("SimpleCExecuter Stopping");
	}

	@Override
	public void onReceive(Object message) throws Exception {
		// TODO Auto-generated method stub
		if (message instanceof ProposedMsg) {
			ProposedMsg msg = (ProposedMsg) message;
			// log.info("got CAmessage from node id: " + ca.getnodeId());
			addRxMessage(msg);
		} else if (message instanceof String) {
			if (message.equals(StringMsg.GET_CONSENSUS_RESULT)) {
				System.out.println("Got GET_CONSENSUS_RESULT");
				caller = getSender();
				// getSender().tell(34, getSelf());
			}
		} else {
			log.info("message: " + message);
			unhandled(message);
		}
	}

	private void addRxMessage(ProposedMsg msg) {
		System.out.println("Start-- addRxMsg--");
		ArrayList<ProposedMsg> msgArr = new ArrayList<ProposedMsg>();

		if (rxMsgMap.containsKey(msg.getnodeId())) {
			rxMsgMap.get(msg.getnodeId()).add(msg);
			msgArr = rxMsgMap.get(msg.getnodeId());
		} else {
			msgArr.add(msg);
			rxMsgMap.put(msg.getnodeId(), msgArr);
		}

		/*
		 * System.out.println("List after adding new incoming message for node:"
		 * + msg.getnodeId()); printArraylist(msgArr);
		 */
		System.out.println("End-- addRxMsg--");
	}

	private Boolean runSimpleConcensus() {
		Boolean isSuccess = true;

		// Check number of failure nodes
		if (getFailed() > ((nodeIdList.size() - 1) / 2)) {
			isSuccess = false;
			// System.out.println("Simple Consensus Algorithm");
			simPub.tell(ProposedMsg.ERR_VALUE, getSelf());
		} else {
			algoThread = new Thread(new algoRunnable());
			algoThread.start();
		}
		return isSuccess;
	}

	private ProposedMsg formMessage(Integer newRnd) {
		ProposedMsg newMsg = new ProposedMsg(getMyNodeId(), newRnd);
		int oldSize = myListToSend.size();

		System.out.println("Start-- formMessage--Rnd: " + newRnd);

		Boolean isAdded = formSendList(newRnd);

		// If new send list size is greater than older, then add to new
		// message
		if (isAdded) {
			newMsg.addProposedValueList(myListToSend);
		}

		System.out.println("End-- formMessage--");
		return newMsg;
	}

	private Integer[] getFinalList(Integer newRnd) {
		Integer[] finalArr = new Integer[myListToSend.size()];
		
		formSendList(newRnd);
		System.out.println("Final List: " + myListToSend);
		finalArr = myListToSend.toArray(finalArr);

		return finalArr;
	}

	private Boolean formSendList(Integer newRnd) {
		ArrayList<Integer> rxValueList = new ArrayList<Integer>();
		int oldSize = myListToSend.size();
		Boolean isAdded = false;

		System.out.println("Start-- formSendList--Rnd: " + newRnd);
		printCurrentRxList();
		for (Integer id : rxMsgMap.keySet()) {

			ArrayList<ProposedMsg> msgList = rxMsgMap.get(id);
			// If number of entries equal to number of rounds,
			// Check for new values
			if (msgList.size() >= (newRnd)) {
				// Get the values from previous rounds
				rxValueList = msgList.get(newRnd - 1).getValueList();
				// Check for new values
				for (Integer val : rxValueList) {
					// Add new values to the list to send
					if (!myListToSend.contains(val)) {
						System.out.println("added to send list:" + val);
						myListToSend.add(val);
					}
				}
			} else {
				System.out.println(" No message from previous rnd");
			}
		}
		if (myListToSend.size() > oldSize) {
			isAdded = true;
		}
		System.out.println("End-- formSendList--");
		return isAdded;
	}

	private void setNodeIdList(ArrayList<Integer> nodeIdList2) {
		// TODO Auto-generated method stub
		nodeIdList = nodeIdList2;
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

	public Integer getMyProposedValue() {
		return myProposedValue;
	}

	public void setMyProposedValue(Integer myProposedValue) {
		this.myProposedValue = myProposedValue;
	}

	public ArrayList<Integer> getFailureList() {
		return failureList;
	}

	public void setFailureList(ArrayList<Integer> failureList) {
		this.failureList = failureList;
	}

	public Integer getFailed() {
		return failed;
	}

	public void setFailed(Integer failed) {
		this.failed = failed;
	}

	private void printCurrentRxList() {
		for (Integer id : rxMsgMap.keySet()) {
			ArrayList<ProposedMsg> msgList = rxMsgMap.get(id);
			printArraylist(msgList);
		}
	}

	private void printArraylist(ArrayList<ProposedMsg> list) {
		System.out.println("/******ArrayList details******/: Size:"
				+ list.size());
		for (int i = 0; i < list.size(); i++) {
			System.out.println("NodeId: " + list.get(i).getnodeId() + " Rnd: "
					+ list.get(i).getRnd() + " Value: "
					+ list.get(i).getValueList());

		}
		System.out.println("/*******************************/");
	}

	public class algoRunnable implements Runnable {

		public void run() {
			isAlgoRunning = true;
			ProposedMsg msg = new ProposedMsg(myNodeId, 0);
			try {// Sleeps for 12 seconds
				Thread.sleep(5000);
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
			while (rounds <= (failed + 1) && !isAlgoStop) {
				
				if (rounds == 0) {
					myListToSend.add(myProposedValue);
					// Send local proposed value to all
					// other nodes
					msg.addProposedValue(myProposedValue);
					simPub.tell(msg, getSelf());
				} else {
					/*
					 * Form new round message by checking all received values
					 * from other nodes. Add only those values not sent in
					 * previous rounds
					 */
					msg = formMessage(rounds);
					// Send only if value list is non empty
					if (!msg.getValueList().isEmpty()) {
						simPub.tell(msg, getSelf());
						System.out.println("/**Sending out Values for Rnd: "
								+ rounds + " Values: " + msg.getValueList()
								+ "**/");
					}
				}
				rounds++;
				try {// Sleeps for 5 seconds
					Thread.sleep(15000);
				} catch (InterruptedException e) {
					e.printStackTrace();
				}
			}
			if (rounds > (failed + 1) && !isAlgoStop) {
				// Get the Final list of proposed values from all nodes
				Integer[] finalList = getFinalList(rounds);
				Integer decisionValue = 0;
				// Apply App defined decision function on the final list
				decisionValue = DecisionFunc.apply(finalList);
				System.out.println("Decision Value is: " + decisionValue);
				// Send the result to consensus mgr
				if (caller != null) {
					caller.tell(decisionValue, getSelf());
				} else {
					System.out.println("caller is null");
				}
			}
			isAlgoRunning = false;
		}
	}
}
