package serviceActors;

import akka.actor.*;
import akka.contrib.pattern.DistributedPubSubExtension;
import akka.contrib.pattern.DistributedPubSubMediator;
import akka.event.Logging;
import akka.event.LoggingAdapter;

public class Publisher extends UntypedActor {
	private final LoggingAdapter log = Logging.getLogger(getContext().system(),
			this);

	private ActorRef mediator = null;
	private String topic;

	public Publisher(String topic) {
		this.topic = topic;
		this.mediator = DistributedPubSubExtension.get(getContext().system())
				.mediator();
	}

	@Override
	public void onReceive(Object message) {
		log.info("got: " + message);
			publish(message);
	}

	void publish(Object ob) {
		log.info("Sending: " + ob);
		mediator.tell(new DistributedPubSubMediator.Publish(topic, ob),
				getSelf());
		log.info("Sent.");
	}
}