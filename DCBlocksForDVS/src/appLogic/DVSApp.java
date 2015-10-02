package appLogic;

import java.util.function.Function;

import constants.ActorNames;
import serviceImplementation.GroupMgmtImpl;
import akka.actor.*;

public class DVSApp {
	private static int gid = 0;
	private static int nodeId = 0;
	private static GroupMgmtImpl gMgmt = null;
	private static ActorSystem mySystem = null;
	private static int caseType = 0;
	
	public static void main(String[] args) {
		if (args.length < 3) {
			// 23 2 127.0.0.1 2555 2553
			System.out.println("Usage: <node id> <group id> <case type>");
		} else {
			// Create Cluster node
			System.out.println("***************************************************************");
			System.out.println("************NODE ID: "+ args[0] + "  " + " GROUP ID: " + args[1] + " CASE TYPE: " + caseType + "*************");
			System.out.println("*****GROUP 1:[10, 12, 13, 14, 15, 16, 17, 18, 19, 20, 21]******");
			System.out.println("*****GROUP 2: [1, 2, 3, 5]    *********************************");
			System.out.println("*GROUP 3: [6, 7, 8, 9, 11, 22, 23, 24, 25, 26, 27, 28, 29, 30]*");
			System.out.println("***************************************************************");
			startNode(args);
		}
	}

		
	private static void startNode(String[] args) {
		// TODO Auto-generated method stub
		nodeId = new Integer(args[0]);
		gid = new Integer(args[1]);
		caseType = new Integer(args[2]);
		 
		try {
			gMgmt = new GroupMgmtImpl(nodeId, reconfigNode);
			ActorSystem mySystem = gMgmt.getActorSystem();
			try {// Sleeps for 10 seconds
				Thread.sleep(10000);
				
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
			//Add self as member of group
			gMgmt.addMember(gid, nodeId);
			if (mySystem != null) {
				// Create DVSMgr to start the DVS app
				ActorRef dvsMgr = mySystem.actorOf(
						Props.create(DVSAppMgr.class, nodeId, gid,  caseType, gMgmt),
						ActorNames.DVS_APP_ACTOR);
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	static Function<Integer, Boolean> reconfigNode = (newGid) -> {

		mySystem = gMgmt.getActorSystem();
		if (mySystem != null) {
			try {
			// Create DVSMgr to start the DVS app
			ActorRef dvsMgr = mySystem.actorOf(
					Props.create(DVSAppMgr.class, nodeId, newGid, caseType, gMgmt),
					ActorNames.DVS_APP_ACTOR);
			} catch(Exception e) {
				e.printStackTrace();
			}
		}
		return true;
	};
}
