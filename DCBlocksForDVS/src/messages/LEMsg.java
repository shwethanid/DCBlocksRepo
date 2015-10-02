package messages;

import java.io.Serializable;
import java.util.ArrayList;

/**
 * 
 */
public class LEMsg implements Serializable{

	private static final long serialVersionUID = 1L;
	private ArrayList<Integer> nodeIdList = new ArrayList<Integer>();
	private ArrayList<Integer> failureList = new ArrayList<Integer>();
	private Boolean isFromApp = false;
	
	public enum action {
		LE_START, LE_STOP, LE_STATUS
	};

	private action LEAction;

	public LEMsg(Boolean isApp, action act) {
		setIsFromApp(isApp);
		this.setLeAction(act);
	}

	public action getLeAction() {
		return LEAction;
	}

	public void setLeAction(action LEAction) {
		this.LEAction = LEAction;
	}

	public ArrayList<Integer> getNodeIdList() {
		return nodeIdList;
	}

	public void setNodeIdList(ArrayList<Integer> nodeIdList) {
		this.nodeIdList = nodeIdList;
	}

	public ArrayList<Integer> getFailureList() {
		return failureList;
	}

	public void setFailureList(ArrayList<Integer> failureList) {
		this.failureList = failureList;
	}

	public Boolean getIsFromApp() {
		return isFromApp;
	}

	public void setIsFromApp(Boolean isFromApp) {
		this.isFromApp = isFromApp;
	}
}
