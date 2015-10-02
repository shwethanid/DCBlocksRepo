package util;

import java.util.ArrayList;

public class InitGroupInfo {
	private int groupId = 0;
	private int initGroupLimit = 0;
	private ArrayList<Integer> nodeIdList = new ArrayList<Integer>();
	
	public InitGroupInfo() {
		
	}

	public int getInitGroupLimit() {
		return initGroupLimit;
	}

	public void setInitGroupLimit(int initGroupLimit) {
		this.initGroupLimit = initGroupLimit;
	}

	public ArrayList<Integer> getNodeList() {
		return nodeIdList;
	}

	public void setNodeIdList(ArrayList<Integer> nodeIdList) {
		this.nodeIdList = nodeIdList;
	}
	
	public void addToNodeIdList(Integer id) {
		this.nodeIdList.add(id);
	}

	public int getGroupId() {
		return groupId;
	}

	public void setGroupId(int groupId) {
		this.groupId = groupId;
	}
}
