package messages;

import java.io.Serializable;
import java.util.ArrayList;

import serviceInterfaces.SplitGroup;

public class SplitGroupMsg implements SplitGroup, Serializable{
	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;
	final int oldGroup;
	final int newGroup;
	public ArrayList<Integer> idList = new ArrayList<Integer>();
	final int oldGroupNewSize;
	final int newGroupSize;
	final int starterId;
	
	public SplitGroupMsg() {
		this.oldGroup = 0;
		this.newGroup = 0;
		this.newGroupSize = 0;
		this.oldGroupNewSize = 0;
		this.starterId = 0;
	}

	public SplitGroupMsg(int oldGroup, int newGroup, ArrayList<Integer> idList, int oldGroupNewSize, int newGroupSize, int starterId) {
		this.oldGroup = oldGroup;
		this.newGroup = newGroup;
		this.idList = new ArrayList<Integer>(idList);
		this.newGroupSize = newGroupSize;
		this.oldGroupNewSize = oldGroupNewSize;
		this.starterId = starterId;
	}
	
	
	public SplitGroupMsg(SplitGroupMsg split) {
		this.oldGroup = split.getOldGroup();
		this.newGroup = split.getNewGroup();
		this.idList = new ArrayList<Integer>(split.getIdList());
		this.newGroupSize = split.getNewGroupSize();
		this.oldGroupNewSize = split.getOldGroupNewSize();
		this.starterId = split.getStarterId();
	}
	
	public int getOldGroup() {
		return oldGroup;
	}
	public int getNewGroup() {
		return newGroup;
	}
	public int getOldGroupNewSize() {
		return this.oldGroupNewSize;
	}
	public int getNewGroupSize() {
		return this.newGroupSize;
	}
	public int getStarterId() {
		return this.starterId;
	}
	public void setIdList(ArrayList<Integer> idList) {
		this.idList = new ArrayList<Integer>(idList);
	}
	public ArrayList<Integer> getIdList() {
		return this.idList;
	}
	
	public void printParameters() {
		System.out.println("SplitGroupMsg info: "
				+ "old grp: " + getOldGroup()
				+ "new grp: " + getNewGroup()
				+ "new grp size: " + getNewGroupSize()
				+ "old group new size: " + getOldGroupNewSize()
				+ "member list: " + getIdList());
	}

}
