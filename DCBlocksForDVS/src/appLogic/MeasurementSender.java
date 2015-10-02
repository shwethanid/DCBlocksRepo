package appLogic;

import static java.util.concurrent.TimeUnit.SECONDS;
import constants.Misc;
import scala.concurrent.duration.Duration;
import appLogicMessages.PowerMeasurementMsg;
import akka.actor.*;

/**
 * This actor class sends local power measurement to the leader every few
 * seconds
 * 
 * @author shwetha
 *
 */
public class MeasurementSender extends UntypedActor {
	private String path = "";
	private ActorRef leader = null;
	Cancellable cIdentify;
	Cancellable cSender;
	Boolean first = true;
	private final int myNodeId;
	private final int myGroupId;
	// PowerDataTextParser txtParser;
	private int count = 0;
	private int limit = 0;
	private int caseType = 0;
	private final int CASE1_FOLDER_LIMIT = 1;
	private final int CASE2_FOLDER_LIMIT = 4;
	private final int CASE3_FOLDER_LIMIT = 4;
	private final String CASE1_FOLDER = "10/";
	private final String CASE2_FOLDER = "400/";
	private final String CASE3_FOLDER = "500/";
	private String caseFolder = "";

	private int sendLimit = 0;

	/**
	 * Constructor for MeasurementSender class. Sends Indentify request to
	 * leader
	 * 
	 * @param myNodeId
	 *            my node Id
	 * @param myGroupId
	 *            my group Id
	 * @param path
	 *            leader actor path
	 * @param groupSize
	 *            Number of nodes in the group
	 */
	public MeasurementSender(int myNodeId, int groupId, int caseType,
			String path, Integer groupSize) {
		this.path = path;
		this.myNodeId = myNodeId;
		this.myGroupId = groupId;
		this.caseType = caseType;
		this.limit = ((myGroupId - 1) * 10) + groupSize;
		System.out.println("MeasurementSender node id:" + myNodeId);
		sendIdentifyRequest();
	}

	@Override
	public void preStart() {

	}

	@Override
	public void postStop() {
		System.out.println("MeasurementSender stopping");

		// Stop sending the power measurements
		if (cSender != null) {
			cSender.cancel();
		}
	}

	/**
	 * Sends Indentify request to leader actor
	 */
	private void sendIdentifyRequest() {

		getContext().actorSelection(path).tell(new Identify(path), getSelf());
		cIdentify = getContext()
				.system()
				.scheduler()
				.scheduleOnce(Duration.create(3, SECONDS), getSelf(),
						ReceiveTimeout.getInstance(),
						getContext().dispatcher(), getSelf());
	}

	@Override
	public void onReceive(Object message) throws Exception {
		if (message instanceof ActorIdentity) {
			leader = ((ActorIdentity) message).getRef();
			if (leader == null) {
				System.out.println("leader not available: " + path);
				if (cSender != null && cIdentify != null) {
					cSender.cancel();
					cIdentify.cancel();
					first = true;
				}
			} else {
				if (first == true) {
					// System.out.println("leader is available now: " + path);
					first = false;
					// Watch on leader to check if leader is active or dead
					getContext().watch(leader);
					// Schedule to send local power state to leader periodically
					cSender = this
							.getContext()
							.system()
							.scheduler()
							.schedule(Duration.create(30, SECONDS),
									Duration.create(30, SECONDS),
									new dataSenderRunnable(),
									this.getContext().system().dispatcher());
				}
			}
		} else if (message instanceof ReceiveTimeout) {
			// If Identify message is not received, resend it
			sendIdentifyRequest();
		} else if (message instanceof Terminated) {
			System.out.println("leader terminated");
			// If leader terminated, wait for new leader to start
			cSender.cancel();
		} else if (message instanceof String) {
			String new_path = (String) message;
			this.path = new_path;
			// Send Identify message to new leader
			sendIdentifyRequest();
		} else {
			System.out.println("Not ready yet");
		}
	}

	public class dataSenderRunnable implements Runnable {
		public void run() {
			setFolderNum();
			// Get local measurements from
			// PowerApp and send to leader
			PowerDataTextParser textParser = new PowerDataTextParser(Misc.CONF_PATH + caseFolder
					+ getFolderNum() + "/" + Misc.BUS_FILENAME, Misc.CONF_PATH
					+ caseFolder + getFolderNum() + "/" + Misc.LINE_FILENAME);
			System.out.println("file:" + Misc.CONF_PATH + caseFolder + getFolderNum()
					+ "/" + Misc.BUS_FILENAME);

			System.out
					.println("[MeasurementSender class, dataSenderRunnable(), Group: "
							+ myGroupId
							+ " Node: "
							+ myNodeId
							+ "]: file:"
							+ Misc.CONF_PATH
							+ caseFolder
							+ getFolderNum()
							+ "/"
							+  Misc.BUS_FILENAME);
			System.out.println("Group id: " + myGroupId);
			PowerMeasurementMsg msg = new PowerMeasurementMsg(myNodeId,
					myGroupId);
			System.out.println("pow meas Group id: " + msg.getGroupId());
			if (textParser.getPowerMeasurement(myNodeId, limit, msg)) {
				/*
				 * System.out .println("Sending local measurement by node id:" +
				 * myNodeId); msg.printValues();
				 */
				leader.tell(msg, getSelf());
			}
		}

		private void setFolderNum() {

			if (caseType == Misc.CASE_NORMAL.intValue()) {
				System.out.println("case type 1");
				caseFolder = CASE1_FOLDER;
				count = CASE1_FOLDER_LIMIT;
			} else if (caseType == Misc.CASE_GROUP_RESOLVE.intValue()) {
				System.out.println("case type 2");

				caseFolder = CASE2_FOLDER;
				if (count < CASE2_FOLDER_LIMIT) {
					count++;
				}
			} else {
				System.out.println("case type 3");
				caseFolder = CASE3_FOLDER;
				if (count < CASE3_FOLDER_LIMIT) {
					count++;
				}
			}
		}
		
		private int getFolderNum() {
			return count;
		}

	}

}
