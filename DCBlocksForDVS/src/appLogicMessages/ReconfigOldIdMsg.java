package appLogicMessages;

import java.io.Serializable;
/**
 * 
 */
public class ReconfigOldIdMsg implements Serializable{

	private static final long serialVersionUID = 1L;
	public final Integer oldMergeGrpId;
	
	public ReconfigOldIdMsg(Integer oldMergeGrpId) {
		this.oldMergeGrpId = oldMergeGrpId;
	}
	
	public Integer getOldMergeGrpId() {
		return this.oldMergeGrpId;
	}
}
