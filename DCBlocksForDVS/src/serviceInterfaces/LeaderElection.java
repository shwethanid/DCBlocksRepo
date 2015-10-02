package serviceInterfaces;

import java.util.function.Function;

/**
 * Public interface for Leader Election (Based on IC Consensus)
 * @author Shwetha
 *
 */
public interface LeaderElection {
	/**
	 * NOTE: First function to be called by each member participating in Leader
	 * election Starts the leader Election Executer actor to perform the leader
	 * election
	 * 
	 * @param myVote
	 *            my vote
	 * @param decisionFunc
	 *            App defined decision function
	 * @return True if success else failure
	 */
	Boolean setUpForLeaderElection(Integer myVote,
			Function<Integer[], Integer[]> decisionFunc);

	/**
	 * Starts the leader election by start request to Leader Election Executor
	 * actor. Waits for the election result.
	 * 
	 * @return Elected leader Id
	 * @throws Exception
	 */
	Integer startElection() throws Exception;

	/**
	 * Gets the primary leader Id
	 * 
	 * @return Primary leader Id
	 */
	int getPrimaryLeader();

	/**
	 * Gets the secondary leader Id
	 * 
	 * @return Secondary leader Id
	 */
	int getSecondaryLeader();

	/**
	 * Checks if specified nodeId is primary leader
	 * 
	 * @param nodeId
	 * @return True if primary leader, else false
	 */
	Boolean isPrimaryLeader(int nodeId);

	/**
	 * Checks if specified nodeId is secondary leader
	 * 
	 * @param nodeId
	 * @return True if primary leader, else false
	 */
	Boolean isSecondaryLeader(int nodeId);

	/**
	 * Stops the leader election if running
	 * 
	 * @return True if success else failure
	 */
	Boolean stopElection();
	
	/**
	 * Removes all the dependencies for leader election
	 * 
	 * @return True if success else failure
	 */
	Boolean removeSetUpForLeaderElection();
}
