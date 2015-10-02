package util;

import akka.actor.Address;

public class MyNode extends HostNode {
	private int gid;
	private int seedPort;
	private Address address;
	
	public MyNode(HostNode node) {
		super(node);
	}
	public MyNode(HostNode node, int gid, int seedPort) {
		super(node);
		setSeedPort(seedPort);
		setGid(gid);
	}

	public MyNode(MyNode node) {
		super();
		setHostname(node.getHostname());
		setPort(node.getPort());
		setGid(node.getGid());
		setSeedPort(node.getSeedPort());
		setNodeid(node.getNodeid());
		setAddress(node.getAddress());
	}

	public int getGid() {
		return gid;
	}

	public void setGid(int gid) {
		this.gid = gid;
	}

	public int getSeedPort() {
		return seedPort;
	}

	public void setSeedPort(int seedPort) {
		this.seedPort = seedPort;
	}

	public Address getAddress() {
		return address;
	}

	public void setAddress(Address address) {
		this.address = address;
	}
}
