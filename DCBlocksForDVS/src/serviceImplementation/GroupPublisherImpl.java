package serviceImplementation;

import messages.OperationInfo;
import messages.OperationInfo.opnType;
import constants.*;
import scala.concurrent.Await;
import scala.concurrent.Future;
import scala.concurrent.duration.Duration;
import serviceActors.Publisher;
import serviceInterfaces.GroupPublisher;
import akka.actor.*;
import akka.pattern.Patterns;
import akka.util.Timeout;

/**
 * This class implements public interface GroupPublisher
 * 
 * @author Shwetha
 *
 */
public class GroupPublisherImpl implements GroupPublisher {
	private ActorSystem system = null;
	private Integer myGroupId = 0;
	private ActorRef pubRef = null;

	/**
	 * Constructor for GroupPublisherImpl
	 * 
	 * @param system
	 *            ActorSystem
	 * @param myGroupId
	 *            my group Id
	 */
	public GroupPublisherImpl(ActorSystem system, Integer myGroupId) {
		this.system = system;
		setMyGroupId(myGroupId);
	}

	/**
	 * Publish the message to specified group
	 * @param groupId	specified group
	 * @param topic		topic
	 * @param msg		message corresponding to topic
	 */
	@Override
	public Boolean PublishMsg(Integer groupId, String topic, Object msg) {
		Boolean isSuccess = false;

		if (groupId == getMyGroupId()) {// If own group, directly publish
			System.out.println("[GroupPublisherImpl class, PublishMsg()]..publish to own group");
			pubRef = system.actorOf(Props.create(Publisher.class, topic),
					ActorNames.PUBLISHER_ACTOR);
			try {
				Timeout timeout = new Timeout(Duration.create(1, "seconds"));
				Future<Object> reply = Patterns.ask(pubRef, msg, timeout);
				isSuccess = true;
				system.stop(pubRef);
			} catch (Exception e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		} else {// If external group, publish through groupMgr
			try {
				OperationInfo opMsg = new OperationInfo(opnType.PUBLISH_EXTERNAL_GROUPS);
				//opMsg.type = OperationInfo.opnType.PUBLISH_EXTERNAL_GROUPS;
				opMsg.setMsgToPub(msg);
				opMsg.setGid(groupId);
				opMsg.setTopic(topic);
				//opMsg.topic = topic;
				//opMsg.msgToPub = msg;
				//opMsg.gid = groupId;
				System.out.println("[GroupPublisherImpl class, PublishMsg()]..publish to ext group Id: "+ groupId);
				String path = "akka://" + ActorNames.GROUP_ACTOR_SYSTEM
						+ "/user/" + ActorNames.GROUP_MGR;
				System.out.println("[GroupPublisherImpl class, PublishMsg()]..Path: "+ path);
				Timeout timeout = new Timeout(Duration.create(1, "seconds"));
				Future<Object> reply = Patterns.ask(
						system.actorSelection(path), opMsg, timeout);
				isSuccess = (Boolean) Await.result(reply, timeout.duration());

			} catch (Exception e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}

		}
		return isSuccess;
	}

	public Integer getMyGroupId() {
		return myGroupId;
	}

	public void setMyGroupId(Integer myGroupId) {
		this.myGroupId = myGroupId;
	}
}
