package com.cbhstudios.tools.flowedit;

import java.awt.event.ActionListener;
import java.util.ArrayList;

public class IPFixModel implements IIPFixElementContainerViewer<IPFixMessage>
{
	IPFixModel()
	{
		modelMessages = new ArrayList<IPFixMessage>();
	}

	@Override
	public Boolean InsertAfter(IPFixMessage insertion, Integer index) { return true;}

	@Override
	public Boolean MoveUp(IPFixMessage moveCandidate) {	return true; }

	@Override
	public Boolean RemoveFromView(IPFixMessage removeCandidate) {	return true; }

	@Override
	public ArrayList<IPFixMessage> GetContainedRecordList() { return modelMessages; };

	@Override
	public IPFixMessage AddToContainer(IPFixMessage addedElement) 
	{
		modelMessages.add(addedElement);
		return addedElement;
	}

	
	protected ActionListener eltListener;

	protected final ArrayList<IPFixMessage> modelMessages;

	

}
