package appLogic;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

import messages.LeaderIdMsg;
import messages.StringMsg;
import constants.Misc;
import constants.Topics;
import appLogicData.Complex;
import akka.actor.ActorSystem;
import appLogicMessages.BusDataMsg;
import appLogicMessages.LineDataMsg;
import appLogicMessages.PowerMeasurementMsg;
import serviceImplementation.ConsensusImpl;
import serviceImplementation.GroupMgmtImpl;
import serviceImplementation.GroupPublisherImpl;
import serviceImplementation.GroupSubscriberImpl;
import serviceImplementation.LeaderElectionImpl;
import util.GroupConfigXMLParser;
import util.InitGroupInfo;

public class TestApp {
	private static int gid = 0;
	private static int nodeId = 0;
	private static GroupMgmtImpl gMgmt = null;
	private static ActorSystem mySystem = null;
	private static Boolean isReady1 = false;
	private static Boolean isReady2 = false;
	private static ArrayList<Integer> nodeList = new ArrayList<Integer>();
	private static PowerDataTextParser textParser;
	private static int caseType = 0;
	
	
	private static Function<Object, Boolean> recvCallBackFunc = (msg) -> {
		LeaderIdMsg lIdMsg = (LeaderIdMsg) msg;

		System.out.println("Leader Ids of group: " + lIdMsg.getGroupId()
				+ " is: " + lIdMsg.getLeaderIds()[0] + " "
				+ lIdMsg.getLeaderIds()[1]);
		return true;
	};

	private static Function<String, Boolean> recvGrpReadyFunc1 = (msg) -> {
		Boolean status = false;
		if (msg.equals(StringMsg.GROUP_READY)) {
			status = true;
			isReady1 = true;
			System.out.println("Group 1 ready");
		}
		return true;
	};

	private static Function<String, Boolean> recvGrpReadyFunc2 = (msg) -> {
		Boolean status = false;
		if (msg.equals(StringMsg.GROUP_READY)) {
			status = true;
			System.out.println("Group 2 ready");
			isReady2 = true;
		}
		return true;
	};

	private static Function<Integer, Boolean> recvConsResultFunc = (result) -> {

		System.out.println("recvConsResultFunc[] Consensus result is: "
				+ result);
		return true;
	};

	private static Function<Integer[], Boolean> recvICConsResultFunc = (result) -> {

		System.out.println("recvICConsResultFunc[] ICConsensus vector is: "
				+ Arrays.toString(result));
		return true;
	};

	private static Function<Integer[], Integer> avgFunc = (valList) -> {
		Integer avg = 0;
		Integer sum = 0;

		for (Integer val : valList) {
			sum += val;
		}
		avg = sum / (valList.length);
		return avg;
	};

	private static Function<Integer[], Integer[]> computeMajority = (valList) -> {
		Integer[] majList = new Integer[2];
		int max = -1;
		int maj = -1;

		Map<Integer, Integer> m = new HashMap<Integer, Integer>();

		for (int val : valList) {
			Integer count = m.get(val);
			m.put(val, count != null ? count + 1 : 0);
		}
		for (Map.Entry<Integer, Integer> e : m.entrySet()) {
			if (e.getValue() > max) {
				maj = e.getKey();
				max = e.getValue();
			}
		}
		majList[0] = maj;
		majList[1] = 2;

		return majList;
	};

	private static Function<Integer[], Integer[]> computeMin = (valList) -> {
		Integer[] minList = new Integer[2];
		System.out.println("Final list: " + Arrays.toString(valList));
		Arrays.sort(valList);
		minList[0] = valList[0];
		minList[1] = valList[1];

		return minList;
	};

	private static Function<Integer, Boolean> reconfigCB = (n) -> {
		return true;
	};
	
/*	public static void main(String[] args) {
		if (args.length < 3) {
			// 23 2 127.0.0.1 2555 2553
			System.out.println("Usage: <node id> <group id> <case type>");
		} else {
			// Create Cluster node
			System.out.println("***************************************************************");
			System.out.println("********NODE ID: "+ args[0] + "  " + " GROUP ID: " + args[1] + "CASE TYPE: " + caseType + "**********");
			System.out.println("*****GROUP 1:[10, 12, 13, 14, 15, 16, 17, 18, 19, 20, 21]******");
			System.out.println("*****GROUP 2: [1, 2, 3, 5]    *********************************");
			System.out.println("*GROUP 3: [6, 7, 8, 9, 11, 22, 23, 24, 25, 26, 27, 28, 29, 30]*");
			System.out.println("***************************************************************");
			startNode(args);
		}
	}*/


	private static void startNode(String[] args) {
		// TODO Auto-generated method stub
		gid = new Integer(args[1]);
		nodeId = new Integer(args[0]);
		
		
		/*gMgmt = new GroupMgmtImpl(nodeId, reconfigCB);
		
		gMgmt.addMember(gid, nodeId);
		
		mySystem = gMgmt.getActorSystem();
		System.out.println("gid: "+gid + " nid: "+nodeId);
		gMgmt.registorOnGroupReady(gid, recvGrpReadyFunc1);
		if (nodeId == 1) {
			gMgmt.registorOnGroupReady(2, recvGrpReadyFunc2);
		}
		nodeList = gMgmt.getMemberList(gid);
		// isReady = true;

		while (!isReady1 || !isReady2) {
			try {// Sleeps for 10 seconds
				Thread.sleep(4000);

			} catch (InterruptedException e) {
				e.printStackTrace();
			}
		}*/
		runDVSPowerTest();
		//runGrpMgmtTestThread();
		// runGrpPubSubTestThread();
		// runConsTestThread();
		// runLETestThread();
	}

	private static void stopNode() {
		if (gMgmt != null) {
			gMgmt.unregisterFromGroups();
		}
	}

	private static void runDVSPowerTest() {
		String norm = "10/";
		String grpcontrol = "400/";
		String merge = "500/";
		String filename = Misc.CONF_PATH + merge + "4/";
		textParser = new PowerDataTextParser(filename+"case1_bus.txt",
				filename+"case1_line.txt");
		System.out.println("filename: "+ filename);
		Integer[] Grp1 = { 6, 7, 8, 9, 11, 22, 23, 24, 25, 26, 27, 28, 29, 30 };
		//Integer[] Grp1 = {1, 2, 3, 4, 5};
		//Integer[] Grp1 = {10, 12, 13, 14, 15, 16, 17, 18, 19, 20};
		PowerMeasurementMsg[] msg = new PowerMeasurementMsg[Grp1.length];
		// Integer[] id = {6, 7, 8, 9, 11, 13, 22, 23, 24, 25, 26, 27, 28, 29,
		// 30};
		Boolean isSuccess = true;
		// for (int i = 0; i < 10; i++) {
		for (int i = 0; i < Grp1.length && isSuccess; i++) {
			msg[i] = (PowerMeasurementMsg) new PowerMeasurementMsg(Grp1[i], gid);
			isSuccess = textParser.getPowerMeasurement(Grp1[i], Grp1.length,
					msg[i]);
			// msg[i].printValues();
			nodeList.add(Grp1[i]);
		}
		if(isSuccess) {
			processPowerMeasMsg(msg);
		}
	}

	private static void processPowerMeasMsg(PowerMeasurementMsg[] powMeas) {
		int len = 0;
		int nbranch = 0;
		int nbus = powMeas.length;

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
			va.put(pow.getBusData().getBusNum(), pow.getBusData().getVolt().getVa());
			ia.put(pow.getBusData().getBusNum(), pow.getBusData().getCurr().getIa());
			sh.add(pow.getBusData().getShuntCap());
			shuntR.add(pow.getBusData().getShuntReserv());
		}

		for (int m = 0; m < nodeList.size(); m++) {
			LineDataMsg ldata = new LineDataMsg();

			try {
				// Read data for to-bus in group but from-bus in diff group
				textParser.readLineDataBasedOnToBus(nodeList.get(m), nodeList,
						ldata);
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
			if (!nodeList.contains(tb.get(i))) {
				BusDataMsg bdata = new BusDataMsg();
				textParser.readBusData(tb.get(i), bdata);
				va.put(tb.get(i), bdata.getVolt().getVa());
				ia.put(tb.get(i), bdata.getCurr().getIa());
			} else if(!nodeList.contains(fb.get(i))) {

					BusDataMsg bdata = new BusDataMsg();
					textParser.readBusData(fb.get(i), bdata);
					va.put(fb.get(i), bdata.getVolt().getVa());
					ia.put(fb.get(i), bdata.getCurr().getIa());
			}
		}
		
		for (int i = 0; i < bNo.size(); i++) { //
			//powMeas[i].printValues();
			System.out.println("bNo: " + bNo.get(i) + " 	bt: " + bt.get(i)
					+ " 	sh: "
					+ sh.get(i) + " 	shuntR: " + shuntR.get(i));
		}

		for (int i = 0; i < fb.size(); i++) {

			System.out.println("fb: " + fb.get(i) + " 	tb: " + tb.get(i)
					+ " 	R: " + R.get(i) + " 	X: " + X.get(i) + " 	C: " + C.get(i)
					+ " 	tap: " + tap.get(i));
		}

		
		for (int key: va.keySet()) { System.out.println("bus: "
		+ key + " va: " + va.get(key).getRe() + "j " + va.get(key).getImg() +
		" ia: " + ia.get(key).getRe() + "j " + ia.get(key).getImg()); }
		

		nbranch = fb.size();
		System.out.println("bus: " + nbus + "branch: " + nbranch + "VA size: "
				+ va.size() + "node list size: " + nodeList.size());
		System.out.println("node list: " + nodeList);
		
		DVSProcessor dvsSC = new DVSProcessor(1, nodeList.size(),nodeList, 0);

		
		dvsSC.processDVSData(bNo, bt, va, ia, sh, shuntR, fb, tb, R, X, C,
		tap, nbranch);
		

	}

	private static void runLETestThread() {
		new Thread(new Runnable() {
			public void run() {
				int cmd = 1;

				while (cmd < 12) {
					switch (cmd) {
					case 1:
						Integer leaderId = startLeaderElection();
						cmd++;
						break;
					default:
						break;
					}
				}
			}
		}).start();
	}

	/**
	 * Method to start leader election
	 */
	private static Integer startLeaderElection() {

		Integer leaderId = 0;

		ArrayList<Integer> nodeIdList = new ArrayList<Integer>(
				gMgmt.getMemberList(gid));
		ArrayList<Integer> failureList = new ArrayList<Integer>(
				gMgmt.getFailedList(gid));
		Integer myVote = nodeId * 12;
		// Set your vote as per computational ability

		System.out.println("Setting up for leader Election");
		LeaderElectionImpl electionExecuter = new LeaderElectionImpl(mySystem,
				nodeId, gid, gMgmt);
		// Start leader election with your vote and decision function
		electionExecuter.setUpForLeaderElection(myVote, computeMin);
		try {
			leaderId = electionExecuter.startElection();
			System.out.println("Primary Leader is: " + leaderId
					+ "Secondary leader is: "
					+ electionExecuter.getSecondaryLeader());

		} catch (Exception e) {
			e.printStackTrace();
			System.out.println("Leader Election exception");
		}

		return leaderId;
	}

	private static void runConsTestThread() {
		// Run the thread

		new Thread(new Runnable() {
			public void run() {
				int cmd = 1;
				ArrayList<Integer> nodeIdList = gMgmt.getMemberList(gid);
				ArrayList<Integer> failureIdList = gMgmt.getFailedList(gid);
				if (gid == 1) {
					failureIdList.add(10);
				}
				ConsensusImpl cons = new ConsensusImpl(mySystem, nodeId, gid,
						nodeIdList, failureIdList);

				while (cmd < 12) {
					switch (cmd) {
					case 1:
						Integer proposedValue = nodeId * 3;
						Integer decValue = cons.startBlockingSimpleConsensus(
								proposedValue, avgFunc);
						System.out.println("Consensus result: " + decValue);
						cmd++;
						break;

					/*
					 * case 1: Integer propValue = nodeId*4;
					 * cons.startNonBlockingSimpleConsensus(propValue,
					 * recvConsResultFunc, avgFunc); cmd++; break;
					 */

					/*
					 * case 2: Integer propValue1 = nodeId * 23; Integer[]
					 * decVector = cons .startBlockingICConsensus(propValue1);
					 * System.out.println("IC Blocking Consensus result: " +
					 * Arrays.toString(decVector)); cmd++; break;
					 */

					/*
					 * case 2: Integer propValue1 = nodeId*23;
					 * cons.startNonBlockingICConsensus(propValue1,
					 * recvICConsResultFunc); cmd++; break;
					 */

					/*
					 * case 3: cmd++; break;
					 * 
					 * case 4: Boolean status = cons.stopSimpleConsensus();
					 * System.out.println("Stop Simple consensus status: " +
					 * status); cmd++; break;
					 * 
					 * case 5: Boolean status1 = cons.stopICConsensus();
					 * System.out.println("Stop IC consensus status: " +
					 * status1); cmd++; break;
					 */

					default:
						System.out.println("Looping");
						break;
					}

					try {// Sleeps for 5 seconds
						Thread.sleep(5000);

					} catch (InterruptedException e) {
						e.printStackTrace();
					}
				}
			}
		}).start();
	}

	private static void runGrpPubSubTestThread() {
		// Run the thread

		new Thread(new Runnable() {
			public void run() {
				int cmd = 1;

				GroupPublisherImpl grpPub = new GroupPublisherImpl(mySystem,
						gid);
				GroupSubscriberImpl grpSub = new GroupSubscriberImpl(mySystem,
						gid);
				int diffGrp = 2;
				Integer[] leaders = new Integer[2];

				while (cmd < 12) {
					switch (cmd) {
					case 1:
						grpSub.Subscribe(gid, Topics.LEADER_ID,
								recvCallBackFunc);
						cmd++;
						break;
					case 2:
						if (gid == 1) {
							diffGrp = 2;
						} else if (gid == 2) {
							diffGrp = 3;
						} else {
							diffGrp = 1;
						}
						grpSub.Subscribe(diffGrp, Topics.LEADER_ID,
								recvCallBackFunc);
						cmd++;
						break;

					case 3:
						LeaderIdMsg lIdMsg = new LeaderIdMsg(gid);

						leaders[0] = nodeId;
						if (gid == 1) {
							leaders[1] = 3;
						} else if (gid == 2) {

							leaders[1] = 7;
						} else {

							leaders[1] = 11;
						}
						lIdMsg.setLeaderIds(leaders);
						grpPub.PublishMsg(gid, Topics.LEADER_ID, lIdMsg);
						cmd++;
						break;

					case 4:
						LeaderIdMsg lIdMsg1 = new LeaderIdMsg(gid);

						lIdMsg1.setLeaderIds(leaders);
						grpPub.PublishMsg(diffGrp, Topics.LEADER_ID, lIdMsg1);
						cmd++;
						break;

					case 5:
						grpSub.UnSubscribe(gid, Topics.LEADER_ID);
						cmd++;
						break;

					case 6:
						grpSub.UnSubscribe(diffGrp, Topics.LEADER_ID);
						cmd++;
						break;
					default:
						break;
					}

					try {// Sleeps for 5 seconds
						Thread.sleep(5000);

					} catch (InterruptedException e) {
						e.printStackTrace();
					}
				}
			}
		}).start();
	}

	private static void runGrpMgmtTestThread() {
		// Run the thread

		new Thread(new Runnable() {
			public void run() {

				int cmd = 1;
				int c = 0;
				ArrayList<Integer> idList = new ArrayList<Integer>();
				while (cmd < 12) {

					switch (cmd) {
					/*
					 * case 1: if ((nodeId == 1) || (nodeId == 5) || (nodeId ==
					 * 9)) { System.out
					 * .println("[SampleApp class, runTestThread()]...COMMAND "
					 * + cmd + " ADD MEMBER: " + nodeId + 1);
					 * gMgmt.addMember(gid, nodeId + 1);
					 * 
					 * } cmd++; break;
					 * 
					 * case 2: if ((nodeId == 1) || (nodeId == 5) || (nodeId ==
					 * 9)) { idList.add(nodeId + 2); idList.add(nodeId + 3);
					 * System.out
					 * .println("[SampleApp class, runTestThread()]...COMMAND "
					 * + cmd + " ADD MEMBER LIST: " + idList);
					 * 
					 * gMgmt.addMemberList(gid, idList); } cmd++; break;
					 * 
					 * case 3: case 4: case 5: System.out
					 * .println("[SampleApp class, runTestThread()]...COMMAND "
					 * + cmd + " GET HEALTH STATUS OF GROUP: " + gid);
					 * HashMap<Integer, Boolean> statsMap = gMgmt
					 * .getGroupHealthStatus(gid); Set<Integer> keys =
					 * statsMap.keySet(); for (Integer id : keys) { System.out
					 * .println("[SampleApp class, runTestThread()]...Node Id: "
					 * + id + "Status: " + statsMap.get(id)); } cmd++;
					 * 
					 * break;
					 * 
					 * case 6: // Remove 1 node if ((nodeId == 1) || (nodeId ==
					 * 9)) { System.out
					 * .println("[SampleApp class, runTestThread()]...COMMAND "
					 * + cmd + " REMOVE MEMBER : " + (nodeId + 1));
					 * gMgmt.removeMember(gid, nodeId + 1); } cmd++; break;
					 * 
					 * case 7: case 8: System.out
					 * .println("[SampleApp class, runTestThread()]...COMMAND "
					 * + cmd + " GET HEALTH STATUS OF ALL GROUPS: "); for (int g
					 * = 1; g < 4; g++) { HashMap<Integer, Boolean> statsMap11 =
					 * gMgmt .getGroupHealthStatus(g); Set<Integer> keys11 =
					 * statsMap11.keySet(); for (Integer id : keys11) {
					 * System.out
					 * .println("[SampleApp class, runTestThread()]...Group Id: "
					 * + g + " Node Id: " + id + "Status: " +
					 * statsMap11.get(id)); } } cmd++; break;
					 * 
					 * case 9: idList.clear();
					 * 
					 * if ((nodeId == 1) || (nodeId == 5) || (nodeId == 9)) {
					 * idList.add(nodeId + 2); idList.add(nodeId + 3);
					 * System.out
					 * .println("[SampleApp class, runTestThread()]...COMMAND "
					 * + cmd + " REMOVE MEMBER LIST: " + idList); }
					 * gMgmt.removeMemberList(gid, idList); cmd++; break;
					 * 
					 * case 10: case 11: System.out
					 * .println("[SampleApp class, runTestThread()]...COMMAND "
					 * + cmd + " GET HEALTH STATUS OF ALL GROUPS: ");
					 * idList.clear(); for (int g = 1; g < 4; g++) {
					 * HashMap<Integer, Boolean> statsMap2 = gMgmt
					 * .getGroupHealthStatus(g); Set<Integer> keys2 =
					 * statsMap2.keySet(); for (Integer id : keys2) { System.out
					 * .
					 * println("[SampleApp class, runTestThread()]...Group Id: "
					 * + g + " Node Id: " + id + "Status: " +
					 * statsMap2.get(id)); } } System.out
					 * .println("[SampleApp class, runTestThread()]...COMMAND "
					 * + cmd + " GET MEMBER LIST OF ALL GROUPS: "); for (int g =
					 * 1; g < 4; g++) {
					 * 
					 * idList = gMgmt.getMemberList(g); System.out
					 * .println("[SampleApp class, runTestThread()]..Group Id: "
					 * + g + " Node Id Size " + idList.size()); for (Integer id
					 * : idList) { System.out
					 * .println("[SampleApp class, runTestThread()]..Group Id: "
					 * + g + " Node Id: " + id); } } // cmd++; break;
					 */

					case 1:
						if (nodeId == 1) {
							ArrayList<Integer> gidList = new ArrayList<Integer>();
							gidList.add(2);
							try {
								Thread.sleep(2500);
							} catch (InterruptedException e) {
								// TODO Auto-generated catch block
								e.printStackTrace();
							}
							if (gMgmt.mergeGroups(1, gidList)) {

								System.out.println("Grp 1 new member list: "
										+ gMgmt.getMemberList(gid));
							}
						}
						cmd++;
						break;

					case 2:
						System.out.println("Grp 1 new member list: "
								+ gMgmt.getMemberList(gid));
						System.out.println("Grp 2 new member list: "
								+ gMgmt.getMemberList(2));
						if(gMgmt.getMemberList(2).isEmpty() && (nodeId == 1)) {
							c++;
							if(c == 5) {
								idList.add(6); idList.add(7); idList.add(8); idList.add(9); idList.add(10);
								gMgmt.splitGroups(1, 2, idList);
								cmd++;
							}
						}
						break;
					
					case 3:
						if((gMgmt.getMemberList(2).size() == 5) && (gMgmt.getMemberList(1).size() == 5) && (nodeId == 1)) {
							
						}
						
						break;
						
					default:
						System.out.println("Looping");
						System.out.println("Grp 1 new member list: "
								+ gMgmt.getMemberList(gid));
						System.out.println("Grp 2 new member list: "
								+ gMgmt.getMemberList(2));
						break;

					}
					try {// Sleeps for 60 seconds
						Thread.sleep(10500);

					} catch (InterruptedException e) {
						e.printStackTrace();
					}
				}
			}
		}).start();
	}

}
