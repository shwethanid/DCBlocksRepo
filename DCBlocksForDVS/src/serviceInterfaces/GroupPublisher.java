package serviceInterfaces;

public interface GroupPublisher {

	/**
	 * Publish the message to specified group
	 * @param groupId 	specified group
	 * @param topic		topic
	 * @param msg		message corresponding to topic
	 * @return			True if success, else false
	 */
	public Boolean PublishMsg(Integer groupId, String topic, Object msg);
}
