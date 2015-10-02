package appLogicMessages;

import java.io.Serializable;

import appLogicData.YMatrix;


public class TopologyMsg implements Serializable{
	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;
	private int leaderId;
	private int groupId;
	private YMatrix ymat;
	private double[] QMargin;
	
	public TopologyMsg(int id, YMatrix y, double[] q)
	{
		setLeaderId(id);
		ymat = new YMatrix(y);
		QMargin = new double[q.length];
		System.arraycopy(QMargin, 0, q, 0, q.length);
	}
	public double[] getQMargin() {
		return QMargin;
	}
	
	public void setQMargin(double[] qMargin) {
		QMargin = qMargin;
	}
	public YMatrix getYmat() {
		return ymat;
	}
	public void setYmat(YMatrix ymat) {
		this.ymat = ymat;
	}
	public int getLeaderId() {
		return leaderId;
	}
	public void setLeaderId(int leaderId) {
		this.leaderId = leaderId;
	}
	public int getGroupId() {
		return groupId;
	}
	public void setGroupId(int groupId) {
		this.groupId = groupId;
	}
	
}
