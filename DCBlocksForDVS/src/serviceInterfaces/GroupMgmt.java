package serviceInterfaces;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Set;
import java.util.function.Function;

import messages.MergeGroupMsg;


/**
 * Interface for handling group management activities
 * @author shwetha
 *
 */
public interface GroupMgmt {
	/**
	 * 
	 * @return
	 */
	Boolean registerToGroups(Boolean isStartUp);
	/**
	 * 
	 * @param nodeId
	 * @return
	 */
	int getGroupId(int nodeId);
	/**
	 * 
	 * @param groupId
	 * @param nodeId
	 * @return
	 */
	Boolean addMember(int groupId, int nodeId);
	/**
	 * 
	 * @param groupId
	 * @param nodeId
	 * @return
	 */
	Boolean removeMember(int groupId, int nodeId);
	/**
	 * 
	 * @param groupId
	 * @param nodeId
	 * @return
	 */
	Boolean isMember(int groupId, int nodeId);
	/**
	 * 
	 * @param groupId
	 * @return
	 */
	ArrayList<Integer> getMemberList(int groupId);
	/**
	 * 
	 * @param groupId
	 * @return
	 */
	HashMap<Integer, Boolean> getGroupHealthStatus(int groupId);
	/**
	 * 
	 * @param groupId
	 * @param nodeId
	 * @return
	 */
	Boolean getMemberHealthStatus(int groupId, int nodeId);
	/**
	 * 
	 * @param groupIdList
	 * @return
	 */
	Boolean mergeGroups(Integer groupIdToMerge, ArrayList<Integer> groupIdList);//Remove group and bookkeeping should be done within this method TBD
	
	/**
	 * 
	 * @param oldGroupId
	 * @param newGroupId
	 * @param memberList
	 * @return
	 */
	Boolean splitGroups(Integer oldGroupId, Integer newGroupId, ArrayList<Integer> memberList);
	
	/**
	 * 
	 * @param groupId
	 * @param nodeIdList
	 * @return
	 */
	Boolean addMemberList(int groupId, ArrayList<Integer> nodeIdList);
	/**
	 * 
	 * @param groupId
	 * @param nodeIdList
	 * @return
	 */
	Boolean removeMemberList(int groupId, ArrayList<Integer> nodeIdList);
	/**
	 * 
	 * @return
	 */
	Boolean unregisterFromGroups();
	/**
	 * 
	 * @return
	 */
	Integer getGroupCount();
	/**
	 * 
	 * @param groupId
	 * @return
	 */
	ArrayList<Integer> getFailedList(int groupId);
	/**
	 * 
	 * @param groupId
	 * @return
	 */
	int getPrimaryLeader(int groupId);
	/**
	 * 
	 * @param groupId
	 * @param leaderId
	 */
	void setPrimaryLeader(int groupId, int leaderId);
	/**
	 * 
	 * @param groupId
	 * @return
	 */
	int getSecondaryLeader(int groupId);
	/**
	 * 
	 * @param groupId
	 * @param leaderId
	 */
	void setSecondaryLeader(int groupId, int leaderId);
	/**
	 * 
	 * @param groupId
	 * @param callBackFunc
	 */
	void registerOnPriLeaderFailure(int groupId, Function<String, Boolean> callBackFunc);
	/**
	 * 
	 * @param groupId
	 * @param callBackFunc
	 */
	void registerOnSecLeaderFailure(int groupId, Function<String, Boolean> callBackFunc);
	
	/**
	 * 
	 * @param groupId
	 * @param callBackFunc
	 */
	void registorOnGroupReady(int groupId, Function<String, Boolean> callBackFunc);
	
	void registerOnMergeComplete(Function<MergeGroup, Boolean> callBackFunc);
	
	void registerOnSplitComplete(Function<SplitGroup, Boolean> callBackFunc);
	
	void registerOnAddComplete(Function<Integer, Boolean> callBackFunc);
	
	void registorOnAllGroupsReady(int groupId, Function<String, Boolean> callBackFunc);
	
	/**
	 * 
	 * @param topic
	 * @param ref
	 * @return
	 */
	Boolean subscribeToOtherGroups(String topic, Object ref);
	/**
	 * 
	 * @param topic
	 * @param ref
	 * @return
	 */
	Boolean unsubscribeToOtherGroups(String topic, Object ref);
	/**
	 * 
	 * @param groupId
	 * @param topic
	 * @param msg
	 * @return
	 */
	Boolean publishMsgToOtherGroup(int groupId, String topic, Object msg);
	/**
	 * 	
	 * @return
	 */
	Set<Integer> getGroupIds();
}
