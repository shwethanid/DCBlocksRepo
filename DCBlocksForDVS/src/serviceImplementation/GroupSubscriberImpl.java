package serviceImplementation;

import java.util.HashMap;
import java.util.function.Function;

import messages.GrpSubscribeMsg;
import constants.ActorNames;
import akka.actor.*;
import akka.pattern.Patterns;
import akka.util.Timeout;
import scala.concurrent.Await;
import scala.concurrent.Future;
import scala.concurrent.duration.Duration;
import serviceActors.*;
import serviceInterfaces.GroupSubscriber;

/**
 * This class implements public interface GroupSubscriber
 * 
 * @author Shwetha
 *
 */
public class GroupSubscriberImpl implements GroupSubscriber {
	private ActorSystem system = null;
	private Integer myNodeId = 0;
	private Integer myGroupId = 0;
	private HashMap<String, ActorRef> topicToRefMap = new HashMap<String, ActorRef>();

	/**
	 * Constructor for GroupSubscriberImpl
	 * 
	 * @param system
	 *            ActorSystem
	 * @param myGroupId
	 *            my group Id
	 */
	public GroupSubscriberImpl(ActorSystem system, Integer myGroupId) {
		this.system = system;
		setMyNodeId(myNodeId);
		setMyGroupId(myGroupId);
	}

	/**
	 * This method subscribes to receive message of specified topic
	 * 
	 * @param groupId
	 *            specified group
	 * @param topic
	 *            topic
	 * @param recvCallBackFunc
	 *            Call back function for received message
	 */
	@Override
	public Boolean Subscribe(Integer groupId, String topic,
			Function<Object, Boolean> recvCallBackFunc) {
		Boolean isSuccess = false;
		ActorRef subRef = null;
		GrpSubscribeMsg msg = new GrpSubscribeMsg(topic, recvCallBackFunc);

		// If its own group, then create local subscriber
		if (groupId == getMyGroupId()) {
			subRef = system.actorOf(Props.create(Subscriber.class, topic),
					ActorNames.SUBSCRIBER_ACTOR);
		} else {// If external group, create subscriber through
				// ClusterReceptionist
			subRef = system.actorOf(Props.create(ExtGroupSubscriber.class),
					ActorNames.EXT_GROUP_SUBSCRIBER);
		}
		String key = topic + groupId.toString();
		topicToRefMap.put(key, subRef);

		try {
			Timeout timeout = new Timeout(Duration.create(1, "seconds"));
			Future<Object> reply = Patterns.ask(subRef, msg, timeout);// in //
																		// milliseconds
			isSuccess = (Boolean) Await.result(reply, timeout.duration());
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

		return isSuccess;
	}

	/**
	 * This method unsubscribes to receive message of specified topic
	 * 
	 * @param groupId
	 *            specified group
	 * @param topic
	 *            topic
	 */
	@Override
	public Boolean UnSubscribe(Integer groupId, String topic) {
		Boolean isSuccess = false;
		ActorRef subRef = null;

		String key = topic + groupId.toString();
		if (topicToRefMap.containsKey(key)) {
			
			subRef = topicToRefMap.get(key);
			try {
				System.out.println("Found key: "+ key + "Removing key and stopping subscriber actor");
				system.stop(subRef);
				topicToRefMap.remove(key);
				isSuccess = true;
			} catch (Exception e) {
				e.printStackTrace();
				isSuccess = false;
			}
		}

		return isSuccess;
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

}
