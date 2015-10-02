package serviceInterfaces;

import java.util.function.Function;

/**
 * 
 * @author Shwetha
 *
 */
public interface Consensus {

	/**
	 * Starts Simple Consensus (Non blocking)
	 * 
	 * @param proposedValue
	 *            local value
	 * @param callBackFunc
	 *            function to call after consensus/agreement has reached
	 * @return True if start success
	 */
	public Boolean startNonBlockingSimpleConsensus(Integer proposedValue,
			Function<Integer, Boolean> callBackFunc,
			Function<Integer[], Integer> decisionFunc);

	/**
	 * Starts Simple Consensus (blocking)
	 * 
	 * @param proposedValue
	 *            local value
	 * @return Consensus/agreement result
	 */
	public Integer startBlockingSimpleConsensus(Integer proposedValue,
			Function<Integer[], Integer> decisionFunc);

	/**
	 * Starts IC Consensus (Non blocking)
	 * 
	 * @param proposedValue
	 *            local value
	 * @param callBackFunc
	 *            function to call after consensus/agreement vector has reached
	 * @return True if start success
	 */
	public Boolean startNonBlockingICConsensus(Integer proposedValue,
			Function<Integer[], Boolean> callBackFunc);

	/**
	 * Starts IC Consensus
	 * 
	 * @param proposedValue
	 *            local value
	 * @return Consensus/agreement vector
	 */
	public Integer[] startBlockingICConsensus(Integer proposedValue);

	/**
	 * Stops Simple Consensus if running
	 * 
	 * @return True if success Else false
	 */
	Boolean stopSimpleConsensus();// For future: Can be supported for security aspects


	/**
	 * Stops IC Consensus if running
	 * 
	 * @return True if success Else false
	 */
	Boolean stopICConsensus();// For future: Can be supported for security aspects
	
	/**
	 * Checks if Simple Consensus is in progress
	 * 
	 * @return True if running Else false
	 */
	Boolean isSimpleConsensusInProgress();
	
	/**
	 * Checks if IC Consensus is in progress
	 * 
	 * @return True if running Else false
	 */
	Boolean isICConsensusInProgress();
}
