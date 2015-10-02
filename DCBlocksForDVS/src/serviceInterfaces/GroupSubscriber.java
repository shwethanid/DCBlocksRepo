package serviceInterfaces;

import java.util.function.Function;

public interface GroupSubscriber {
	/**
	 * This method subscribes to receive message for specified topic
	 * 
	 * @param groupId
	 *            subscribe to specified group
	 * @param topic
	 *            topic
	 * @param recvCallBackFunc
	 *            Call back function for received message
	 * @return true if success else false
	 */
	public Boolean Subscribe(Integer groupId, String topic,
			Function<Object, Boolean> recvCallBackFunc);

	/**
	 * This method unsubscribes to receive message for specified topic
	 * 
	 * @param groupId
	 *            unsubscribe from specified group
	 * @param topic
	 *            topic
	 * @return true if success else false
	 */
	public Boolean UnSubscribe(Integer groupId, String topic);
}
