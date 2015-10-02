package serviceInterfaces;

import java.util.ArrayList;

public interface SplitGroup {
	public int getOldGroup();
	public int getNewGroup();
	public int getOldGroupNewSize();
	public int getNewGroupSize();
	public int getStarterId();
	public void setIdList(ArrayList<Integer> idList);
	public ArrayList<Integer> getIdList();
}
