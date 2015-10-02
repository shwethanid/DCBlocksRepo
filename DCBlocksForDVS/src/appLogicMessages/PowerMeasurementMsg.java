package appLogicMessages;

import java.io.Serializable;
import java.util.ArrayList;

/**
 * This class contains all bus and line data -
 * 
 * @author shwetha
 */
public class PowerMeasurementMsg implements Serializable {
	private static final long serialVersionUID = 1L;
	private  Integer nodeId = 0;
	private  BusDataMsg busData = new BusDataMsg();
	private  LineDataMsg lineData = new LineDataMsg();
	private Integer groupId = 0;
	
	public PowerMeasurementMsg(int nodeId, int groupId) {
		this.setNodeId(nodeId);
		this.setGroupId(groupId);
	}

	public PowerMeasurementMsg(PowerMeasurementMsg pow) {
		this.getBusData().setBusName(pow.getBusData().getBusName());
		this.getBusData().setBusType(pow.getBusData().getBusType());
		
		this.getBusData().setBusNum(pow.getBusData().getBusNum());
		this.getBusData().setVolt(pow.getBusData().getVolt());
		this.getBusData().setCurr(pow.getBusData().getCurr());
		this.getBusData().setShuntCap(pow.getBusData().getShuntCap());
		this.getBusData().setShuntReserv(pow.getBusData().getShuntReserv());
		this.getLineData().getFromBus().addAll(pow.getLineData().getFromBus());
		this.getLineData().getToBus().addAll(pow.getLineData().getToBus());
		this.getLineData().getC().addAll(pow.getLineData().getC());
		this.getLineData().getR().addAll(pow.getLineData().getR());
		this.getLineData().getX().addAll(pow.getLineData().getX());
		this.getLineData().getTap().addAll(pow.getLineData().getTap());
		this.setNodeId(pow.getNodeId());
		this.setGroupId(pow.getGroupId());
	}

	public Integer getNodeId() {
		return nodeId;
	}

	public void setNodeId(Integer nodeId) {
		this.nodeId = nodeId;
	}

	public BusDataMsg getBusData() {
		return busData;
	}

	public void setBusData(BusDataMsg busData) {
		this.busData = busData;
	}

	public LineDataMsg getLineData() {
		return lineData;
	}

	public void setLineData(LineDataMsg lineData) {
		this.lineData = lineData;
	}
	

	public void printValues() {
		System.out.println("Bus Data");
		System.out.println("Bus Num: " + this.getBusData().getBusNum()
				+ "Bus Type: " + this.getBusData().getBusType()
				+ " Shunt Cap: " + this.getBusData().getShuntCap()
				+ " Shunt Cap Reserve: " + this.getBusData().getShuntReserv());
		this.getBusData().getVolt().printVoltage();
		this.getBusData().getCurr().printCurrent();
		System.out.println("Line data");
		System.out.println("From bus: " + this.getLineData().getFromBus()
				+ "To Bus: " + this.getLineData().getToBus()
				+ " R: " + this.getLineData().getR()
				+ " X: " + this.getLineData().getX()
				+ " C: " + this.getLineData().getC()
				+ " Tap: " + this.getLineData().getTap());
	}

	public Integer getGroupId() {
		return groupId;
	}

	public void setGroupId(Integer groupId) {
		this.groupId = groupId;
	}
}
