package messages;

import java.io.Serializable;

public class ExtGrpPublishMsg implements Serializable{

	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;
	
	private final int groupId;
	private final String topic;
	private final Object msg;
	
	public ExtGrpPublishMsg(int groupId, String topic, Object msg) {
		this.groupId = groupId;
		this.topic = topic;
		this.msg = msg;
	}

}
