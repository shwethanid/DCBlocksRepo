package serviceActors;

import static java.util.concurrent.TimeUnit.SECONDS;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.Semaphore;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import messages.Timepass;
import messages.memStatus;
import scala.concurrent.duration.Duration;
import util.HostNode;
import constants.ActorNames;
import constants.Topics;
import akka.actor.*;
import akka.cluster.Cluster;
import akka.cluster.ClusterEvent.*;
import akka.contrib.pattern.ClusterClient;
import akka.contrib.pattern.ClusterReceptionistExtension;
import akka.contrib.pattern.ClusterClient.Publish;
import akka.event.Logging;
import akka.event.LoggingAdapter;

public class MonitorGroup extends UntypedActor {
	protected LoggingAdapter log = Logging.getLogger(getContext().system(),
			this);
	protected Cluster cluster;
	protected CurrentClusterState state;
	private int myNodeId = 0;
	protected ArrayList<externalGroupClient> exGrpList = new ArrayList<externalGroupClient>();
	protected ArrayList<HostNode> hostlist = new ArrayList<HostNode>();
	Semaphore sem = new Semaphore(1);
	static Lock lock = new ReentrantLock();
	static int cntr = 0;
	ActorRef mgrRef = null;
	Boolean isSelf = false;
	
	public MonitorGroup(ActorSystem system, int myNodeId,
			ArrayList<Integer> groupIdList, ArrayList<Address> seedAddressList,
			ArrayList<String> seedAddrStringlist, ArrayList<HostNode> hostlist,
			ActorRef mgrRef) {
		this.setMyNodeId(myNodeId);
		this.mgrRef = mgrRef;

		for (int i = 0; i < groupIdList.size(); i++) {
			exGrpList.add(new externalGroupClient(groupIdList.get(i),
					seedAddressList.get(i), seedAddrStringlist.get(i)));
		}

		this.hostlist = new ArrayList<HostNode>(hostlist);
	}

	@Override
	public void onReceive(Object message) throws Exception {

		// TODO Auto-generated method stub
		if (message instanceof ActorIdentity) {

			ActorRef seed = ((ActorIdentity) message).getRef();

			if (seed != null) {
				/*
				 * System.out.println("received actorIdentity message from " +
				 * seed.toString());
				 */

				Boolean isFnd = false;

				for (int i = 0; i < exGrpList.size() && !isFnd; i++) {

					/*
					 * System.out.println("Exgrp seedAddress " +
					 * exGrpList.get(i).seedAddress +
					 * "received identify address" + seed.path().address() + i);
					 */
					if (exGrpList.get(i).seedAddress.equals(seed.path()
							.address())) {
						isFnd = true;

						log.info("RECEIVED IDENTIFY");
						 System.out.println("Exgrp seedAddress " +
						 exGrpList.get(i).seedAddress +
						 "*******RECEIVED IDENTIFY REPLY ADDRESS" + seed.path().address() + i);
						/*System.out
								.println("[MonitorGroup class, onReceive()]..Adding Seed Ref for Group Id: "
										+ exGrpList.get(i).getGid());*/

						exGrpList.get(i).setSeedRef(seed);
						lock.lock();
						if (false == exGrpList.get(i).cIdentify.isCancelled()) {
							
							 System.out.println("cancel flag before  : "
							 + exGrpList.get(i).cIdentify.isCancelled() + "for gid " +
							  exGrpList.get(i).gid);
							
							exGrpList.get(i).cancelIdentifyRequest();
							exGrpList.get(i).setClusterClient();
							
							  System.out.println("cancel flag after : " +
							  exGrpList.get(i).cIdentify.isCancelled());
							 
						}

						lock.unlock();
					}
				}
			}else {
				System.out.println("Seed is null");
			}
		} else if (message instanceof ReceiveTimeout) {

			for (externalGroupClient grpClient : exGrpList) {
				// for(i = 0; i < exGrpList.size(); i++) {
				if (!grpClient.cIdentify.isCancelled()) {
					System.out.println("*****Identify message is not received..resending for grp: "+ grpClient.getGid());
					// If Identify message is not received, resend it
					grpClient.sendIdentifyRequest();
				}
			}
		}
	}

	public int getMyNodeId() {
		return myNodeId;
	}

	public void setMyNodeId(int myNodeId) {
		this.myNodeId = myNodeId;
	}

	protected void processRegisterRequest(String topic, ActorRef regRef) {
		System.out
				.println("[MonitorGroup class, processRegisterRequest()]..Topic: "
						+ topic);
		ClusterReceptionistExtension.get(getContext().system())
				.registerSubscriber(topic, regRef);
	}

	protected void processUnregisterRequest(String topic, ActorRef regRef) {
		ClusterReceptionistExtension.get(getContext().system())
				.unregisterSubscriber(topic, regRef);
	}
	
	protected class externalGroupClient {
		private int gid = 0;
		private ActorRef clusterClient = null;
		public Cancellable cIdentify = null;
		protected Address seedAddress;
		private String seedAddressStr = "";
		protected ActorRef seedRef = null;
		protected Boolean isAddMsgSent = false;
		protected Boolean isRemMsgSent = false;

		public externalGroupClient(int gid, Address address, String addressStr) {
			this.gid = gid;
			seedAddressStr = addressStr;
			seedAddress = address;
			//To be removed later
				sendIdentifyRequest();
				startIdentifyScheduler();
			
		}

		public Boolean publishMemberStatus(memStatus[] memStatsArray) {
			Boolean isSuccess = false;

			if (getClusterClient() != null) {
				
				  System.out.println(
				  "[MonitorGroup class, externalGroupClient()]...Publishing group status to: "
				  + getGid() + "memstatus: " + memStatsArray[0].getGId());
				 
				clusterClient.tell(new Publish(Topics.REPLY_GROUP_STATUS,
						memStatsArray), getSelf());
				isSuccess = true;
			} else {
				System.out.println("getClusterClient() is null! for gid:" + gid);
			}
			return isSuccess;
		}
		
		protected Boolean publishTimepass(int gid, int nid) {
			Boolean isSuccess = false;

			if (getClusterClient() != null) {
				Timepass tp = new Timepass(gid, nid);
				  System.out.println(
				  "[MonitorGroup class, externalGroupClient()]...Publishing tp to: "
				  + getGid() + "by: " + gid);
				 
				clusterClient.tell(new Publish(Topics.TIME_PASS,
						tp), getSelf());
				isSuccess = true;
			} else {
				System.out.println("getClusterClient() is null! for gid:" + gid);
			}
			return isSuccess;
		}
		
		public Boolean publishAppMsg(String topic, Object msg) {
			Boolean isSuccess = false;

			if (getClusterClient() != null) {
				
				  System.out.println(
				  "[MonitorGroup class, externalGroupClient()]...Publishing app msg to: "
				  + getGid());
				 
				clusterClient.tell(new Publish(topic,
						msg), getSelf());
				isSuccess = true;
			} else {
				System.out.println("getClusterClient is null");
			}
			return isSuccess;
		}
		
		public int getGid() {
			return this.gid;
		}

		public ActorRef getClusterClient() {
			return clusterClient;
		}

		public Boolean setClusterClient() {
			Boolean isSuccess = true;

			if (clusterClient == null) {
				cntr++;
				Set<ActorSelection> initialContacts = new HashSet<ActorSelection>();
				String clientStr;
				// Create cluster client for each cluster

				System.out
						.println("[MonitorGroup class, setClusterClient()]..For Group Id: "
								+ gid
								+ " intial contacts: "
								+ seedAddressStr
								+ "/user/receptionist");

				initialContacts.add(getContext().system().actorSelection(
						seedAddressStr + "/user/receptionist"));
				
				if(getIsSelf()) {
					clientStr = "clusterClientForSelf";
				} else {
					clientStr = "clusterClientForDiff";
				}
				// clientStr = "clusterClient" + myNodeInfo.getGid();
				clientStr = clientStr + getMyNodeId() + gid + cntr;

				clusterClient = getContext().system().actorOf(
						ClusterClient.defaultProps(initialContacts), clientStr);
				System.out
				.println("[MonitorGroup class, setClusterClient()]..clusterClient.path().toString()");
				if (clusterClient == null) {
					System.out.println("bummer!!!");
					isSuccess = false;
				}
				initialContacts.clear();
			}
			return isSuccess;
		}

		public Boolean stopClusterClient() {
			Boolean isSuccess = true;

			if (clusterClient != null) {
				System.out
				.println("[MonitorGroup class, stopClusterClient()]..stopping!!!!!!!!");
				getContext().system().stop(clusterClient);
				clusterClient = null;
				isSuccess = true;
			}
			return isSuccess;
		}
		
		protected void startIdentifyScheduler() {
			cIdentify = getContext()
					.system()
					.scheduler()
					.scheduleOnce(Duration.create(50, SECONDS), getSelf(),
							ReceiveTimeout.getInstance(),//ReceiveTimeout message is received
							getContext().dispatcher(), getSelf());
		}
		
		protected void cancelIdentifyScheduler() {
			cIdentify.cancel();
			
		}
		
		protected void sendIdentifyRequest() {

			// String path = contactAddress[gid - 1] + "/user/MonitorSelfGroup";
			String path = seedAddressStr + "/user/"+ActorNames.MONITOR_SELF_GROUP_ACTOR;
			
			 /*System.out.println(
			  "[MonitorGroup class, sendIdentifyRequest()]...Sending identify request to gid:"
			  + gid + " Path:" + path);*/
			 
			getContext().actorSelection(path).tell(new Identify(path),
					getSelf());

		}
		
		
		protected void setSeedRef(ActorRef seed) {
			seedRef = seed;
		}

		protected ActorRef getSeedRef() {
			return seedRef;
		}

		protected void cancelIdentifyRequest() {
			cIdentify.cancel();
		}
	}
	
	protected void setIsSelf(Boolean isSelf) {
		this.isSelf = isSelf;
	}
	
	protected Boolean getIsSelf() {
		return this.isSelf;
	}
}