/**
 * 
 */
package com.cbhstudios.tools.flowedit;

import java.util.ArrayList;


/**
 * @author wtackabury
 *
 */
public class IPFixDataSet extends IPFixFileElement implements IIPFixElementContainerViewer<IPFixDataRecord>
{

	public IPFixDataSet(IPFixMessage containingMessage, short setId)
	{
		m_setId = setId;
		m_dataRecordList = new ArrayList<IPFixDataRecord>();
		m_definingTemplate = containingMessage.GetDataTemplateByTemplateID(setId);

		m_padBytes = 0;
	}
	@Override
	public Boolean InsertAfter(IPFixDataRecord insertion, Integer index) {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public Boolean MoveUp(IPFixDataRecord moveCandidate) {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public Boolean RemoveFromView(IPFixDataRecord removeCandidate) {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public ArrayList<IPFixDataRecord> GetContainedRecordList() {
		// TODO Auto-generated method stub
		return m_dataRecordList;
	}

	@Override
	public Boolean Remove() {
		for (IPFixDataRecord ipFdRec : GetContainedRecordList())
		{
			ipFdRec.Remove();
		}
		return true; 
	}

	@Override
	public Integer Read(IPFixStreamer ifs) throws FloweditException {
				
		int readBytesInSet = 0;
		
		// the stream is currently positioned having read the set id, but not the set length.
		int sizeInSingleDataRecord = 0;

		m_reportedBytes = ifs.ReadIntFromStreamer(OctetSize.Short());
		readBytesInSet += OctetSize.Short();
		
		// we need the data template which will define hoqw each data record should parse
		// itself
		if (null == GetDefiningTemplate())
		{
			throw new FloweditException("Data set " + m_setId + ": cannot find defined template");
		}
		
		try
		{
			// we know we are poised to read at least one data record
			do
			{
				// As we may "walk" the reported templated set multiple times here....
				IPFixDataRecord ifxRec = new IPFixDataRecord(GetDefiningTemplate());
				sizeInSingleDataRecord = ifs.ReadIPFixFileElement(ifxRec);
				
				readBytesInSet += sizeInSingleDataRecord;
				m_dataRecordList.add(ifxRec);
				// also the template keeps track of its record instances
				GetDefiningTemplate().RegisterCorrespondingDataRecord(ifxRec);
				
			} while (readBytesInSet + 4 <=     // assume there would need to be four bytes in a next data record 
						m_reportedBytes - OctetSize.Short());
			
			// we deduct the size of a short here overall to reflect the 
			//  set ID (which this didn't read).
			// if there is a disparity, we call it pad bytes
			SetPadBytes(m_reportedBytes - OctetSize.Short() - readBytesInSet);
			
			if (GetPadBytes() > 0)
			{
				//read them
				ifs.ReadByteArrayFromStreamer(GetPadBytes());
				// account for them
				readBytesInSet += GetPadBytes();
			}

		}
		catch (FloweditException fEx)
		{
			throw new FloweditException("Data set " + m_setId + ": ", fEx);
		}
		return readBytesInSet;
	}

	@Override
	public Integer Write(IPFixStreamer ifs) throws FloweditException 
	{
		int writtenBytes = 0;

		// This writes its own "header bytes".
		
		// write out set ID and length
		ifs.WriteIntToStreamer((int) m_setId, OctetSize.Short());
		writtenBytes += OctetSize.Short();
		ifs.WriteIntToStreamer((int) m_reportedBytes, OctetSize.Short());
		writtenBytes += OctetSize.Short();

		for (IPFixDataRecord ifxRec : GetContainedRecordList())
		{
			writtenBytes += ifs.WriteIPFixFileElement(ifxRec);
		}
		
		if (GetPadBytes() > 0)
		{
			byte [] conjuredPadBytes = new byte [GetPadBytes()];
			for (int i = 0; i < GetPadBytes(); i++)
			{
				conjuredPadBytes[i] = (byte) 0;
			}
			ifs.WriteByteArrayToStreamer(conjuredPadBytes, GetPadBytes());
			writtenBytes += GetPadBytes();
		}
		
		return writtenBytes;
	}

	@Override
	public Integer WrittenFileElementSize() {
		// recompute reported bytes
		
		int newElementByteCount = 2 * OctetSize.Short();  // set id and length

		for (IPFixDataRecord ifxRec : GetContainedRecordList())
		{
			newElementByteCount += ifxRec.WrittenFileElementSize();
		}
		
		newElementByteCount += GetPadBytes();
		m_reportedBytes = newElementByteCount;
		return m_reportedBytes;
	}

	@Override
	public IPFixDataRecord AddToContainer(IPFixDataRecord addedElement) 
	{
		GetContainedRecordList().add(addedElement);
		return addedElement;
	}

	public Integer GetPadBytes() { return m_padBytes; }
	public void SetPadBytes(int iPB) { m_padBytes = iPB; };
	public Short GetSetId() { return m_setId; }
	public IPFixDataTemplate GetDefiningTemplate() { return m_definingTemplate; }

	protected int m_reportedBytes;
	private ArrayList <IPFixDataRecord> m_dataRecordList;
	protected final short m_setId;
	protected final IPFixDataTemplate m_definingTemplate;
	protected int m_padBytes; // count of set pad bytes
	
}
