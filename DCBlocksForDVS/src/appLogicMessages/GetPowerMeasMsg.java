package appLogicMessages;

import java.io.Serializable;
/**
 * 
 */
public class GetPowerMeasMsg implements Serializable{

	private static final long serialVersionUID = 1L;
	int groupId = 0;
	
	public GetPowerMeasMsg(int grpId) {
		setGroupid(grpId);
	}
	
	public void setGroupid(int grpId) {
		groupId = grpId;
	}
	
	public int getGroupId() {
		return groupId;
	}
}
