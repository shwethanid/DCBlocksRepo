package messages;

import java.io.Serializable;
import java.util.ArrayList;

import serviceInterfaces.MergeGroup;

public class MergeGroupMsg implements MergeGroup, Serializable{
	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;
	final int oldGroup;
	final int newGroup;
	final int newGroupSize;
	final int starterId;
	
	public MergeGroupMsg(int oldGroup, int newGroup, int newGroupSize, int starterId) {
		this.oldGroup = oldGroup;
		this.newGroup = newGroup;
		this.newGroupSize = newGroupSize;
		this.starterId = starterId;
	}
	
	public MergeGroupMsg(MergeGroupMsg merge) {
		this.oldGroup = merge.getOldGroup();
		this.newGroup = merge.getNewGroup();
		this.newGroupSize = merge.getNewGroupSize();
		this.starterId = merge.getStarterId();
	}
	
	public int getOldGroup() {
		return oldGroup;
	}
	public int getNewGroup() {
		return newGroup;
	}
	
	public int getNewGroupSize() {
		return this.newGroupSize;
	}
	
	public int getStarterId() {
		return starterId;
	}

	public void printParameters() {
		System.out.println("MergeGroupMsg info: "
				+ "old grp: " + getOldGroup()
				+ "new grp: " + getNewGroup()
				+ "new grp size: " + getNewGroupSize()
				+ "starter id: " + getStarterId());
	}
}
