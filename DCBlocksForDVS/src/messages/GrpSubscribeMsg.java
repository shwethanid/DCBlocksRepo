package messages;

import java.io.Serializable;
import java.util.function.Function;

/**
 * 
 */
public class GrpSubscribeMsg implements Serializable {

	private static final long serialVersionUID = 3642683844299175619L;
	private String topic;
	private Function<Object, Boolean> subscribeCallBack;
	
	public GrpSubscribeMsg(String topic, Function<Object, Boolean> subscribeCallBack) {
		this.setTopic(topic);
		setSubscribeCallBack(subscribeCallBack);
	}

	public Function<Object, Boolean> getSubscribeCallBack() {
		return subscribeCallBack;
	}

	public void setSubscribeCallBack(Function<Object, Boolean> subscribeCallBack) {
		this.subscribeCallBack = subscribeCallBack;
	}

	public String getTopic() {
		return topic;
	}

	public void setTopic(String topic) {
		this.topic = topic;
	}

}
