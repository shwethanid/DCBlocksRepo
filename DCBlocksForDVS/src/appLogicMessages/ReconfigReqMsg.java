package appLogicMessages;

import java.io.Serializable;
/**
 * 
 */
public class ReconfigReqMsg implements Serializable{

	private static final long serialVersionUID = 1L;
	final Integer groupId;
	
	public enum reconfigReq {
		MERGE_REQ,
		SPLIT_REQ,
	};
	private final reconfigReq type;
	
	public ReconfigReqMsg(Integer groupId, reconfigReq type) {
		this.groupId = groupId;
		this.type = type;
	}
	
	public Integer getGroupId() {
		return groupId;
	}

	public reconfigReq getType() {
		return type;
	}
}
