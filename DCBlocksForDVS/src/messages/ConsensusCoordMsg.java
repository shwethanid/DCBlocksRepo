package messages;

import java.io.Serializable;
/**
 * 
 */
public class ConsensusCoordMsg implements Serializable{
	
	private static final long serialVersionUID = 1L;
	public  enum consStatus{
		START_REQ,
		READY
	}
	private final Integer nodeId;
	private final consStatus stat;
	
	public ConsensusCoordMsg(Integer nodeId, consStatus stat) {
		this.nodeId = nodeId;
		this.stat = stat;
	}

	public consStatus getStat() {
		return stat;
	}

	public Integer getNodeId() {
		return nodeId;
	}
}
