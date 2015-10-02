package messages;

import java.io.Serializable;
import java.util.ArrayList;

/**
 * Message used for consensus algorithms
 * 
 * @param <T>
 */
public class ProposedMsg implements Serializable {

	private static final long serialVersionUID = 1L;
	private final Integer rnd;
	private final ArrayList<Integer> valueList = new ArrayList<>();
	private final Integer nodeId;
	public final static Integer ERR_VALUE = -1;

	public ProposedMsg(Integer nodeId, Integer rnd) {
		this.rnd = rnd;
		this.nodeId = nodeId;
	}

	public Integer getRnd() {
		return rnd;
	}

	public float getValueListSize() {
		return valueList.size();
	}

	public ArrayList<Integer> getValueList() {
		return valueList;
	}

	public void addProposedValue(Integer value) {
		valueList.add(value);
	}

	public void addProposedValueList(ArrayList<Integer> valList) {
		this.valueList.addAll(valList);
	}

	public Integer getnodeId() {
		return nodeId;
	}
}
