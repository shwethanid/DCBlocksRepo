package messages;

import java.io.Serializable;

import constants.ActorNames;

public class StringMsg implements Serializable{
	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;
	public final static String GET_CONSENSUS_RESULT = "GET_RESULT";
	public final static String START_LEADER_ELECTION = "START_LEADER_ELECTION";
	public final static String LEADER_ELECTION_STATUS = "LEADER_ELECTION_STATUS";
	public final static String LEADER_ELECTION_STOP = "LEADER_ELECTION_STOP";
	public final static String LEADER_ELECTION_START = "LEADER_ELECTION_START";
	public final static String GET_TOPOLOGY = "GET_TOPOLOGY";
	public static final String SEND_POWER_MSG = "SEND_POWER_MSG";
	public static final String SEND_TOPOLOGY = "SEND_TOPOLOGY";
	public static final String GET_RESULT_MSG = "GET_RESULT_MSG";
	public final static String GROUP_READY = "GROUP_READY";
	public final static String PRIMARY_LEADER_FAILURE = "PRI_LEADER_FAILURE";
	public final static String SECONDARY_LEADER_FAILURE = "SEC_LEADER_FAILURE";
	public final static String SYS_READY = "SYS_READY";
	public final static String UNSUBSCRIBE = "UNSUBSCRIBE";
	public final static String IC_START_REQ = "IC_START";
	public final static String IC_READY = "IC_READY";
	public final static String MERGER = "MERGER";
	public final static String IS_MEMBER_REMOVED = "IS_MEMBER_REMOVED";
	public final static String REREGISTER = "REREGISTER";
}
