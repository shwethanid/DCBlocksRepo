package util;

import akka.actor.Address;

public class Seed {
	private int gid = 0;
	private String seedHostName = "";
	private int seedPort = 0;
	private Address address;
	private String addressStr = "";
	
	public Seed() {
		
	}
	public Seed(Seed seed) {
		this.gid = 0;
		this.seedHostName = seed.getSeedHostName();
		this.seedPort = seed.getSeedPort();
		this.address = seed.getAddress();
		this.addressStr = seed.getAddressStr();
	}
	public String getSeedHostName() {
		return seedHostName;
	}
	public void setSeedHostName(String seedHostName) {
		this.seedHostName = seedHostName;
	}
	public int getSeedPort() {
		return seedPort;
	}
	public void setSeedPort(int seedPort) {
		this.seedPort = seedPort;
	}
	public int getGid() {
		return gid;
	}
	public void setGid(int gid) {
		this.gid = gid;
	}
	public Address getAddress() {
		return address;
	}
	public void setAddress(Address address) {
		this.address = address;
	}
	public String getAddressStr() {
		return this.addressStr;
	}
	public void setAddressStr(String addressStr) {
		this.addressStr = addressStr;
	}
	
}
