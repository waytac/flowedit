package com.cbhstudios.tools.flowedit;

import java.util.ArrayList;

/**
 * @author wtackabury
 *
 *  This enforces behaviors on the view-based IPFix file elements which can do movement/container management of
 *  "child" objects
 */

public interface IIPFixElementContainerViewer<E extends IPFixFileElement> {
	public Boolean InsertAfter(E insertion, Integer index);
	public Boolean MoveUp(E moveCandidate);
	public Boolean RemoveFromView(E removeCandidate);
	public E AddToContainer(E addedElement);
	
	public ArrayList<E> GetContainedRecordList();
}
