package serviceActors;

import java.util.function.Function;

import messages.GrpSubscribeMsg;
import akka.actor.UntypedActor;
import akka.contrib.pattern.ClusterReceptionistExtension;

public class ExtGroupSubscriber extends UntypedActor{
	private Function<Object, Boolean> cbFunc = null;
	private String topic = "";
	
	// subscribe to cluster changes
	@Override
	public void postStop() {
		ClusterReceptionistExtension.get(getContext().system())
		.unregisterSubscriber(topic, getSelf());
	}
	
	@Override
	public void onReceive(Object message) throws Exception {
		// TODO Auto-generated method stub
		if(message instanceof GrpSubscribeMsg) {
			GrpSubscribeMsg subMsg = (GrpSubscribeMsg) message;
			processSubscribeRequest(subMsg);
		} else {
			if(cbFunc != null) {
				cbFunc.apply(message);
			}
		}
	}
	
	private void processSubscribeRequest(GrpSubscribeMsg msg) {
		System.out
				.println("[ExtGroupSubscriber class, processSubscribeRequest()]..Topic: "
						+ msg.getTopic());
		getSender().tell(true, getSelf());
		//save the callback function to use later
		this.cbFunc = msg.getSubscribeCallBack();
		this.topic = msg.getTopic();
		ClusterReceptionistExtension.get(getContext().system())
				.registerSubscriber(msg.getTopic(), getSelf());
	}
}
