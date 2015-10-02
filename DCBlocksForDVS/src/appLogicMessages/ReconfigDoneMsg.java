package appLogicMessages;

import java.io.Serializable;
/**
 * 
 */
public class ReconfigDoneMsg implements Serializable{

	private static final long serialVersionUID = 1L;
	final Integer oldGroupId;
	final Integer newGroupId;
	final Integer starterId;
	
	public enum reconfig {
		MERGE_DONE,
		SPLIT_DONE,
	};
	private final reconfig type;
	
	public ReconfigDoneMsg(Integer oldGroupId, Integer newGroupId, Integer starterId, reconfig type) {
		this.oldGroupId = oldGroupId;
		this.newGroupId = newGroupId;
		this.starterId = starterId;
		this.type = type;
	}
	
	public Integer getOldGroupId() {
		return oldGroupId;
	}
	
	public Integer getNewGroupId() {
		return newGroupId;
	}
	
	public Integer getStarterId() {
		return starterId;
	}

	public reconfig getType() {
		return type;
	}
}