package messages;

import java.io.Serializable;

public class Timepass implements Serializable{
	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;
	final int gid;
	final int nid;
	
	public Timepass(int gid, int nid) {
		this.gid = gid;
		this.nid = nid;
	}
	
	public int getGid() {
		return gid;
	}
	
	public int getNid() {
		return nid;
	}
}
