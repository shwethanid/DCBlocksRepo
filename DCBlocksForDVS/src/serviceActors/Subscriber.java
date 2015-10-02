package serviceActors;

import java.util.function.Function;

import messages.GrpSubscribeMsg;
import messages.ProposedMsg;
import akka.actor.*;
import akka.contrib.pattern.DistributedPubSubMediator;
import akka.contrib.pattern.DistributedPubSubExtension;
import akka.event.*;

public class Subscriber extends UntypedActor {
	LoggingAdapter log = Logging.getLogger(getContext().system(), this);
	private ActorRef actorToFwd = null;
	Function<Object, Boolean> cbFunc;
	Boolean isCB = false;
	private ActorRef mediator = null;
	private String topic = "";

	public Subscriber(String topic) {
		mediator = DistributedPubSubExtension.get(getContext().system())
				.mediator();
		// subscribe to the topic
		mediator.tell(
				new DistributedPubSubMediator.Subscribe(topic, getSelf()),
				getSelf());
		this.topic = topic;
	}

	public Subscriber(ActorRef a, String topic) {
		this.actorToFwd = a;
		mediator = DistributedPubSubExtension.get(getContext().system())
				.mediator();
		// subscribe to the topic
		mediator.tell(
				new DistributedPubSubMediator.Subscribe(topic, getSelf()),
				getSelf());
		this.topic = topic;
	}

	@Override
	public void postStop() {
		// unsubscribe to the topic
		mediator.tell(new DistributedPubSubMediator.Unsubscribe(topic,
				getSelf()), getSelf());
		log.info("Subscriber Stopping");
	}

	public void onReceive(Object msg) {
		if (msg instanceof DistributedPubSubMediator.SubscribeAck) {
			log.info("subscribing");
		} else if (msg instanceof GrpSubscribeMsg) {
			GrpSubscribeMsg subMsg = (GrpSubscribeMsg) msg;
			cbFunc = subMsg.getSubscribeCallBack();
			getSender().tell(true, getSelf());
			isCB = true;
		} else {
			log.info("Got: {}", msg);
			// Forward to actorRef
			if (actorToFwd != null) {
				if(msg instanceof ProposedMsg) {
					ProposedMsg mg = (ProposedMsg)msg;
					System.out.println("Msg: Id: " + mg.getnodeId() + " Rnd: " + mg.getRnd() + " Values: " +mg.getValueList());
				}
				actorToFwd.forward(msg, getContext());
			} else if (isCB) {// Forward to CallBack Func
				System.out.println("Got Subscriber message from: "
						+ getSender());
				cbFunc.apply(msg);
			} else {
				// Do nothing
			}
		}
	}
}
