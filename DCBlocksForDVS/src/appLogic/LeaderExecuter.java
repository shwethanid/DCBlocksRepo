package appLogic;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Set;

import serviceImplementation.GroupMgmtImpl;
import serviceInterfaces.GroupMgmt;
import constants.Misc;
import constants.Topics;
import appLogicMessages.BusDataMsg;
import appLogicMessages.BusListMsg;
import appLogicMessages.ExtContactIdListMsg;
import appLogicMessages.GetPowerMeasMsg;
import appLogicMessages.LineDataMsg;
import appLogicMessages.PowerMeasurementMsg;
import appLogicMessages.ReconfigOldIdMsg;
import appLogicMessages.ReconfigReqMsg;
import appLogicMessages.ReconfigReqMsg.reconfigReq;
import appLogicData.Complex;
import appLogic.DVSProcessor;
import akka.actor.*;
import messages.StringMsg;

/**
 * This actor class gets power measurements from the leader actor and sends to
 * DVS algorithm to check if measurements are OK
 * 
 * @author shwetha
 *
 */
public class LeaderExecuter extends UntypedActor {
	private PowerMeasurementMsg[] powMeas;
	private ActorRef leaderRef;
	private int cnt = 0;
	private Integer myNodeId = 0;
	private Integer myGroupId = 0;
	private Integer groupSize = 0;
	private ArrayList<Integer> nodeIdList = new ArrayList<Integer>();
	private ExtContactIdListMsg extIdMsg = new ExtContactIdListMsg();
	private HashMap<Integer, ArrayList<Integer>> idList = new HashMap<Integer, ArrayList<Integer>>();
	private Integer grps[];
	private GroupMgmtImpl gpMg;
	private BusListMsg busList;
	private double MIN_QEXTRA = 0.0;
	private Integer controlCount = 1;
	private Boolean isMerged = false;
	private Integer oldMergedGrpId = 0;
	private Integer caseType = 0;
	private int count = 0;
	private final String CASE1_FOLDER = "10/";
	private final String CASE2_FOLDER = "400/";
	private final String CASE3_FOLDER = "500/";
	private final int CASE1_FOLDER_LIMIT = 1;
	private final int CASE2_FOLDER_LIMIT = 4;
	private final int CASE3_FOLDER_LIMIT = 4;
	private String caseFolder = "";
	private double QExtra = 0.0;

	public LeaderExecuter(Integer myNodeId, Integer myGroupId,
			Integer caseType, ActorRef leader, ArrayList<Integer> nodeIdList,
			GroupMgmtImpl grpMgmt) {
		this.leaderRef = leader;
		this.myNodeId = myNodeId;
		this.myGroupId = myGroupId;
		this.caseType = caseType;

		/*
		 * this.nodeIdList = new ArrayList<Integer>(
		 * grpMgmt.getMemberList(myGroupId));
		 */

		this.nodeIdList = nodeIdList;
		Collections.sort(this.nodeIdList);
		this.groupSize = nodeIdList.size();
		System.out
				.println("leader Executer's parent: " + getContext().parent());
		powMeas = new PowerMeasurementMsg[groupSize];
		Set<Integer> groupIdList = grpMgmt.getGroupIds();
		grps = new Integer[grpMgmt.getGroupCount() - 1];
		gpMg = grpMgmt;

		for (int gid : groupIdList) {
			if (gid != myGroupId) {
				idList.put(gid, new ArrayList<Integer>());

			}
		}
	}

	@Override
	public void preStart() {

	}

	@Override
	public void postStop() {
		System.out.println("LeaderExecuter stopping");

	}

	@Override
	public void onReceive(Object message) {
		if (message instanceof PowerMeasurementMsg[]) {
			PowerMeasurementMsg[] pMsg = (PowerMeasurementMsg[]) message;
			processRxPowMsg((PowerMeasurementMsg[]) message);
		} else if (message instanceof BusListMsg) {
			busList = (BusListMsg) message;
			Boolean isSuccess = processPowerMeasMsg(powMeas);
			if (isSuccess) {
				System.out
						.println("[LeaderExecuter class, onReceive(), Group: "
								+ myGroupId
								+ " Node: "
								+ myNodeId
								+ "]: Leader has processed incoming messages correctly!!!");
			} else {
				System.out
						.println("[LeaderExecuter class, onReceive(), Group: "
								+ myGroupId
								+ " Node: "
								+ myNodeId
								+ "]: Leader found ERROR in processing incoming messages!!!");
			}
		} else if (message instanceof ReconfigOldIdMsg) {
			ReconfigOldIdMsg msg = (ReconfigOldIdMsg) message;
			oldMergedGrpId = msg.getOldMergeGrpId();
			isMerged = true;
		}
	}

	private void processRxPowMsg(PowerMeasurementMsg[] powMeas) {
		System.out.println("pow grp id:" + powMeas[0].getGroupId()
				+ "my group id:" + myGroupId);
		if (powMeas[0].getGroupId().intValue() == myGroupId.intValue()) {
			System.out
					.println("[LeaderExecuter class, processRxPowMsg(), Group: "
							+ myGroupId
							+ " Node: "
							+ myNodeId
							+ "]: Got group state from leader!!!");
			Boolean isSuccess = processPowerMeasMsg(powMeas);
			if (isSuccess) {
				System.out
						.println("[LeaderExecuter class, processRxPowMsg(), Group: "
								+ myGroupId
								+ " Node: "
								+ myNodeId
								+ "]: Leader has processed incoming messages correctly!!!");
			} else {
				System.out
						.println("[LeaderExecuter class, processRxPowMsg(), Group: "
								+ myGroupId
								+ " Node: "
								+ myNodeId
								+ "]:Leader found ERROR in processing incoming messages!!!");
			}
			cnt++;
			// System.out.println("LeaderExecuter: Cnt: " + cnt);

			// Send to PowerApp to do assessment
			if (cnt > 10) {
				cnt = 0;
				Boolean isFnd = false;
				for (int gid = 0; gid < grps.length && !isFnd; gid++) {
					if (gid != myGroupId) {
						// If power meas from other clusters are needed, ask
						// leader
						// to get it
						leaderRef.tell(new GetPowerMeasMsg(gid), getSelf());
						isFnd = true;

					}
				}

				// If power meas from other clusters are needed, ask leader
				// to get it
				leaderRef.tell(StringMsg.SEND_POWER_MSG, getSelf());
			}
		} else {
			System.out
					.println("[LeaderExecuter class, processRxPowMsg(), Group: "
							+ myGroupId
							+ " Node: "
							+ myNodeId
							+ "]: Got group state from ext leader!!!!!!");
			PowerMeasurementMsg[] powMsg = new PowerMeasurementMsg[groupSize
					+ powMeas.length];
			int i = 0;
			for (i = 0; i < groupSize; i++) {
				powMsg[i] = new PowerMeasurementMsg(powMeas[i]);
			}
			for (int j = 0; j < powMeas.length; j++) {
				powMsg[i] = new PowerMeasurementMsg(powMeas[j]);
				i++;
			}
			Boolean isSuccess = processPowerMeasMsg(powMsg);
			if (isSuccess) {
				System.out
						.println("Leader has processed incoming messages correctly!!!");
			} else {
				System.out
						.println("Leader found ERROR in processing incoming messages!!!");
			}

		}
	}

	/**
	 * Process the power measurements by running DVS algorithm
	 * 
	 * @return status Success/Failure
	 */
	private Boolean processPowerMeasMsg(PowerMeasurementMsg[] powMeas) {
		int nbus = powMeas.length;
		Boolean isSuccess = false;

		// Call the DVS switch control algorithm
		DVSProcessor dvsSC = new DVSProcessor(1, nbus, nodeIdList, controlCount);
		isSuccess = monitorAndControlAction(dvsSC, powMeas, nbus);

		if (isSuccess) {
			// If VVSI is greater than 0.7 then control action is performed
			if (dvsSC.getMax(dvsSC.VVSI) > 0.7) {
				// Check if QExtra is available in group for performing control
				// action within group
				QExtra = dvsSC.getQextra();
				controlCount++;
				System.out
						.println("********Crap! Max(VVSI) > 0.7 control action required, QExtra: "
								+ QExtra);
				// If QExtra i.e, reactive power is 0, contact other group
				// leader
				if (QExtra <= MIN_QEXTRA) {
					// Need to merge groups, inform all the group members and
					// other groups
					System.out.println("(QExtra == 0), contact other group");
					ReconfigReqMsg reqMsg = new ReconfigReqMsg(2,
							reconfigReq.MERGE_REQ);
					leaderRef.tell(reqMsg, getSelf());
				} else {
					System.out
							.println("(QExtra > 0), let's try control action within group");
				}
			} else {
				System.out
						.println("********Good! MAX(VVSI <= 0.7) No control action required!!!*********");
				controlCount = 0;
				if (isMerged) {
					ReconfigReqMsg reqMsg = new ReconfigReqMsg(oldMergedGrpId,
							reconfigReq.SPLIT_REQ);
					leaderRef.tell(reqMsg, getSelf());
				}
			}
		}
		return isSuccess;
	}

	void checkForExtToBus(PowerMeasurementMsg[] powMeas) {

		for (PowerMeasurementMsg pow : powMeas) {

			int lineCnt = pow.getLineData().getLineCount();
			System.out.println("Line count:" + lineCnt);
			for (int i = 0; i < lineCnt; i++) {
				int tb = pow.getLineData().getToBus().get(i);
				if (!nodeIdList.contains(tb)) {
					for (int k = 0; k < grps.length; k++) {
						if (gpMg.isMember(grps[k], tb)) {
							ArrayList<Integer> id = idList.get(grps[k]);
							id.add(tb);
							idList.put(grps[k], id);
						}
					}
					/*
					 * BusDataMsg bdata = new BusDataMsg();
					 * if(textParser.readBusData(tb[i], bdata)) { va.put(tb[i],
					 * bdata.getVolt().getVa()); ia.put(tb[i],
					 * bdata.getCurr().getIa()); }
					 */
				}
			}
		}
		extIdMsg.setIdList(idList);
		leaderRef.tell(extIdMsg, getSelf());
	}

	private Boolean monitorAndControlAction(DVSProcessor dvsSC,
			PowerMeasurementMsg[] powMeas, int nbus) {

		int len = 0;
		int nbranch = 0;
		Boolean isSuccess = false;

		ArrayList<Integer> bNo = new ArrayList<Integer>();
		ArrayList<Integer> bt = new ArrayList<Integer>();
		ArrayList<Double> sh = new ArrayList<Double>();
		ArrayList<Double> shuntR = new ArrayList<Double>();

		HashMap<Integer, Complex> va = new HashMap<Integer, Complex>();
		HashMap<Integer, Complex> ia = new HashMap<Integer, Complex>();

		ArrayList<Integer> fb = new ArrayList<Integer>();
		ArrayList<Integer> tb = new ArrayList<Integer>();
		ArrayList<Double> X = new ArrayList<Double>();
		ArrayList<Double> R = new ArrayList<Double>();
		ArrayList<Double> C = new ArrayList<Double>();
		ArrayList<Double> tap = new ArrayList<Double>();

		for (PowerMeasurementMsg pow : powMeas) {
			// pow.printValues();
			int lineCnt = pow.getLineData().getLineCount();

			for (int i = 0; i < lineCnt; i++) {
				// Line Data
				fb.add(pow.getLineData().getFromBus().get(i));
				tb.add(pow.getLineData().getToBus().get(i));
				R.add(pow.getLineData().getR().get(i));
				X.add(pow.getLineData().getX().get(i));
				C.add(pow.getLineData().getC().get(i));
				tap.add(pow.getLineData().getTap().get(i));
			}
			// BusData
			bNo.add(pow.getBusData().getBusNum());
			bt.add(pow.getBusData().getBusType());
			va.put(pow.getBusData().getBusNum(), pow.getBusData().getVolt()
					.getVa());
			ia.put(pow.getBusData().getBusNum(), pow.getBusData().getCurr()
					.getIa());
			sh.add(pow.getBusData().getShuntCap());
			shuntR.add(pow.getBusData().getShuntReserv());
		}
		setFolderNum();
		PowerDataTextParser textParser = null;
		try {
			textParser = new PowerDataTextParser(Misc.CONF_PATH + caseFolder
					+ getFolderNum() + "/" + Misc.BUS_FILENAME, Misc.CONF_PATH
					+ caseFolder + getFolderNum() + "/" + Misc.LINE_FILENAME);
			System.out.println("file:" + Misc.CONF_PATH + caseFolder + getFolderNum()
					+ "/" + Misc.BUS_FILENAME);
			isSuccess = true;
		} catch (Exception e) {
			e.printStackTrace();
			isSuccess = false;
		}
		if (isSuccess) {

			for (int m = 0; m < nodeIdList.size(); m++) {
				LineDataMsg ldata = new LineDataMsg();

				try {
					// Read data for to-bus in group but from-bus in diff group
					textParser.readLineDataBasedOnToBus(nodeIdList.get(m),
							nodeIdList, ldata);
					int lineCnt = ldata.getLineCount();
					if (lineCnt > 0) {
						// ldata.printValues();
					}
					for (int i = 0; i < lineCnt; i++) {
						fb.add(ldata.getFromBus().get(i));
						tb.add(ldata.getToBus().get(i));
						R.add(ldata.getR().get(i));
						X.add(ldata.getX().get(i));
						C.add(ldata.getC().get(i));
						tap.add(ldata.getTap().get(i));
					}
				} catch (Exception e) {
					e.printStackTrace();
				}
			}

			for (int i = 0; i < fb.size(); i++) {
				if (!nodeIdList.contains(tb.get(i))) {
					BusDataMsg bdata = new BusDataMsg();
					textParser.readBusData(tb.get(i), bdata);
					va.put(tb.get(i), bdata.getVolt().getVa());
					ia.put(tb.get(i), bdata.getCurr().getIa());
				} else if (!nodeIdList.contains(fb.get(i))) {

					BusDataMsg bdata = new BusDataMsg();
					textParser.readBusData(fb.get(i), bdata);
					va.put(fb.get(i), bdata.getVolt().getVa());
					ia.put(fb.get(i), bdata.getCurr().getIa());
				}
			}

			/*
			 * for (int i = 0; i < bNo.size(); i++) { // //
			 * powMeas[i].printValues(); System.out.println("bNo: " + bNo.get(i)
			 * + " 	bt: " + bt.get(i) + " 	sh: " + sh.get(i) + " 	shuntR: " +
			 * shuntR.get(i)); }
			 * 
			 * for (int i = 0; i < fb.size(); i++) {
			 * 
			 * System.out.println("fb: " + fb.get(i) + " 	tb: " + tb.get(i) +
			 * " 	R: " + R.get(i) + " 	X: " + X.get(i) + " 	C: " + C.get(i) +
			 * " 	tap: " + tap.get(i)); }
			 * 
			 * for (int key : va.keySet()) { System.out.println("bus: " + key +
			 * " va: " + va.get(key).getRe() + "j " + va.get(key).getImg() +
			 * " ia: " + ia.get(key).getRe() + "j " + ia.get(key).getImg()); }
			 * 
			 * nbranch = fb.size(); System.out.println("bus: " + nbus +
			 * "branch: " + nbranch + "VA size: " + va.size() +
			 * "node list size: " + nodeIdList.size());
			 * System.out.println("node list: " + nodeIdList);
			 */

			isSuccess = dvsSC.processDVSData(bNo, bt, va, ia, sh, shuntR, fb,
					tb, R, X, C, tap, nbranch);
		}
		return isSuccess;
	}

	/*
	 * private Boolean monitorAndControlAction(DVSProcessor dvsSC,
	 * PowerMeasurementMsg[] powMeas, int nbus) { int len = 0; int nbranch = 0;
	 * Boolean isSuccess = false;
	 * 
	 * ArrayList<Integer> bNo = new ArrayList<Integer>(); ArrayList<Integer> bt
	 * = new ArrayList<Integer>(); ArrayList<Double> sh = new
	 * ArrayList<Double>(); ArrayList<Double> shuntR = new ArrayList<Double>();
	 * 
	 * HashMap<Integer, Complex> va = new HashMap<Integer, Complex>();
	 * HashMap<Integer, Complex> ia = new HashMap<Integer, Complex>();
	 * 
	 * ArrayList<Integer> fb = new ArrayList<Integer>(); ArrayList<Integer> tb =
	 * new ArrayList<Integer>(); ArrayList<Double> X = new ArrayList<Double>();
	 * ArrayList<Double> R = new ArrayList<Double>(); ArrayList<Double> C = new
	 * ArrayList<Double>(); ArrayList<Double> tap = new ArrayList<Double>();
	 * 
	 * for (PowerMeasurementMsg pow : powMeas) { // pow.printValues(); int
	 * lineCnt = pow.getLineData().getLineCount();
	 * 
	 * for (int i = 0; i < lineCnt; i++) { // Line Data
	 * fb.add(pow.getLineData().getFromBus().get(i));
	 * tb.add(pow.getLineData().getToBus().get(i));
	 * R.add(pow.getLineData().getR().get(i));
	 * X.add(pow.getLineData().getX().get(i));
	 * C.add(pow.getLineData().getC().get(i));
	 * tap.add(pow.getLineData().getTap().get(i)); } // BusData
	 * bNo.add(pow.getBusData().getBusNum());
	 * bt.add(pow.getBusData().getBusType());
	 * va.put(pow.getBusData().getBusNum(), pow.getBusData().getVolt()
	 * .getVa()); ia.put(pow.getBusData().getBusNum(),
	 * pow.getBusData().getCurr() .getIa());
	 * sh.add(pow.getBusData().getShuntCap());
	 * shuntR.add(pow.getBusData().getShuntReserv()); }
	 * 
	 * PowerDataTextParser textParser = null; try {
	 * 
	 * textParser = new PowerDataTextParser(Misc.BASIC_CONF_PATH +
	 * "case1_bus.txt", Misc.BASIC_CONF_PATH + "case1_line.txt"); isSuccess =
	 * true; } catch (Exception e) { e.printStackTrace(); isSuccess = false; }
	 * if (isSuccess) { for (int m = 0; m < nodeIdList.size(); m++) {
	 * LineDataMsg ldata = new LineDataMsg();
	 * 
	 * try { // Read data for to-bus in group but from-bus in diff group
	 * textParser.readLineDataBasedOnToBus(nodeIdList.get(m), nodeIdList,
	 * ldata); int lineCnt = ldata.getLineCount(); if (lineCnt > 0) { //
	 * ldata.printValues(); } for (int i = 0; i < lineCnt; i++) {
	 * fb.add(ldata.getFromBus().get(i)); tb.add(ldata.getToBus().get(i));
	 * R.add(ldata.getR().get(i)); X.add(ldata.getX().get(i));
	 * C.add(ldata.getC().get(i)); tap.add(ldata.getTap().get(i)); } } catch
	 * (Exception e) { e.printStackTrace(); } }
	 * 
	 * for (int i = 0; i < fb.size(); i++) { if
	 * (!nodeIdList.contains(tb.get(i))) { BusDataMsg bdata = new BusDataMsg();
	 * textParser.readBusData(tb.get(i), bdata); va.put(tb.get(i),
	 * bdata.getVolt().getVa()); ia.put(tb.get(i), bdata.getCurr().getIa()); }
	 * else if (!nodeIdList.contains(fb.get(i))) {
	 * 
	 * BusDataMsg bdata = new BusDataMsg(); textParser.readBusData(fb.get(i),
	 * bdata); va.put(fb.get(i), bdata.getVolt().getVa()); ia.put(fb.get(i),
	 * bdata.getCurr().getIa()); } }
	 * 
	 * 
	 * for (int i = 0; i < bNo.size(); i++) { // //powMeas[i].printValues();
	 * System.out.println("bNo: " + bNo.get(i) + " bt: " + bt.get(i) + " sh: " +
	 * sh.get(i) + " shuntR: " + shuntR.get(i)); }
	 * 
	 * for (int i = 0; i < fb.size(); i++) {
	 * 
	 * System.out.println("fb: " + fb.get(i) + " tb: " + tb.get(i) + " R: " +
	 * R.get(i) + " X: " + X.get(i) + " C: " + C.get(i) + " tap: " +
	 * tap.get(i)); }
	 * 
	 * 
	 * for (int key: va.keySet()) { System.out.println("bus: " + key + " va: " +
	 * va.get(key).getRe() + "j " + va.get(key).getImg() + " ia: " +
	 * ia.get(key).getRe() + "j " + ia.get(key).getImg()); }
	 * 
	 * 
	 * nbranch = fb.size(); System.out.println("bus: " + nbus + "branch: " +
	 * nbranch + "VA size: " + va.size() + "node list size: " +
	 * nodeIdList.size()); System.out.println("node list: " + nodeIdList);
	 * 
	 * dvsSC.processDVSData(bNo, bt, va, ia, sh, shuntR, fb, tb, R, X, C, tap,
	 * nbranch); isSuccess = true; } return isSuccess; }
	 */

	private void setFolderNum() {

		if (caseType == Misc.CASE_NORMAL.intValue()) {
			System.out.println("case type 1");
			caseFolder = CASE1_FOLDER;
			count = CASE1_FOLDER_LIMIT;
		} else if (caseType == Misc.CASE_GROUP_RESOLVE.intValue()) {
			System.out.println("case type 2");

			caseFolder = CASE2_FOLDER;
			if (count < CASE2_FOLDER_LIMIT) {
				count++;
			}
		} else {
			System.out.println("case type 3");
			caseFolder = CASE3_FOLDER;
			if (count < CASE3_FOLDER_LIMIT) {
				count++;
			}
		}
	}
	
	private int getFolderNum() {
		return count;
	}
}
