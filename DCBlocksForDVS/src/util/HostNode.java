package util;

/**
 * This class acts XML parser to get node properties like hostname, node id,
 * port etc
 * 
 * @author shwetha
 *
 */
public class HostNode {
	private int port;
	private String hostname;
	private int nodeid;

	public HostNode() {
		setPort(0);
		setNodeid(0);
	}

	public HostNode(HostNode host) {
		// TODO Auto-generated constructor stub
		setHostname(host.getHostname());
		setPort(host.getPort());
		setNodeid(host.getNodeid());
	}

	public String getHostname() {
		return hostname;
	}

	public void setHostname(String hostname) {
		this.hostname = hostname;
	}

	public int getPort() {
		return port;
	}

	public void setPort(int port) {
		this.port = port;
	}

	public int getNodeid() {
		return nodeid;
	}

	public void setNodeid(int nodeid) {
		this.nodeid = nodeid;
	}
}
