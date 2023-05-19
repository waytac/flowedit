/**
 * 
 */
package com.cbhstudios.tools.flowedit;

import java.util.ArrayList;


/**
 * @author wtackabury
 *
 */
public class IPFixTemplateSet extends IPFixFileElement implements IIPFixElementContainerViewer<IPFixDataTemplate>
{
	public static enum TemplateSetType { INVALID_TEMPLATE_SET, DATA_TEMPLATE_SET, OPTION_TEMPLATE_SET };

	public IPFixTemplateSet(short setType, int templateLen)
	{
		if (2 == setType)	{ m_templateSetType = TemplateSetType.DATA_TEMPLATE_SET;	}
		else if (3 == setType) { m_templateSetType = TemplateSetType.OPTION_TEMPLATE_SET; 	}
		else { m_templateSetType = TemplateSetType.INVALID_TEMPLATE_SET; }
		
		m_reportedBytes = templateLen;
		placementIndex = SetsInFile++;
		m_templateList = new ArrayList<IPFixDataTemplate>();
		m_padBytes = 0;
	}
	
	@Override
	public Boolean InsertAfter(IPFixDataTemplate insertion, Integer index) {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public Boolean MoveUp(IPFixDataTemplate moveCandidate) {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public Boolean RemoveFromView(IPFixDataTemplate removeCandidate) {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public ArrayList<IPFixDataTemplate> GetContainedRecordList() {
		// TODO Auto-generated method stub
		return m_templateList;
	}

	@Override
	public Boolean Remove() {
		for (IPFixDataTemplate ipFtrRem : GetContainedRecordList())
		{
			ipFtrRem.Remove();
		}
		return true; 
	}

	@Override
	public Integer Read(IPFixStreamer ifs) throws FloweditException {
		int readBytesInSet =  0;  // template type and len have been read, but not by this method.
		
		// Now that we're someplace we can throw an exception, see if this
		//  was set up sensibly
		if (GetTemplateType() == TemplateSetType.INVALID_TEMPLATE_SET)
		{
			throw new FloweditException("Invalid set type @  " + GetIndex());
		}
	
		// after that, all this really does it to aggregate sets.
		while (readBytesInSet < m_reportedBytes - (OctetSize.Short() * 2))  // adjust limit by 2 elts for tType and len 
		{
			// if we entered here with less than four bytes to read, then
			// this isn't a well-formed data template which follows, but just
			// set padding.
			if (m_reportedBytes - readBytesInSet - (OctetSize.Short() * 2) < (OctetSize.Short() * 2))
			{
				// then that's padding
				m_padBytes = m_reportedBytes - readBytesInSet - (OctetSize.Short() * 2);
				// read them
				ifs.ReadByteArrayFromStreamer(m_padBytes);
				// bump to 'em
				readBytesInSet += m_padBytes;
				// and move on
				break;				
			}
			IPFixDataTemplate ifxTempl = new IPFixDataTemplate(GetTemplateType());
			readBytesInSet += ifs.ReadIPFixFileElement(ifxTempl);
			m_templateList.add(ifxTempl);
		}
		// TODO Auto-generated method stub
		return readBytesInSet;
	}

	@Override
	public Integer Write(IPFixStreamer ifs) throws FloweditException {
		int writtenBytes = 0;
		// This writes its own "header bytes".
		
		ifs.WriteIntToStreamer( (GetTemplateType() == TemplateSetType.DATA_TEMPLATE_SET) ? 2 : 3, 
										OctetSize.Short());
		writtenBytes += OctetSize.Short();
		ifs.WriteIntToStreamer(WrittenFileElementSize(), OctetSize.Short());
		writtenBytes += OctetSize.Short();
		
		// now, just loop to write each template itself
		for (IPFixDataTemplate ifxDT : GetContainedRecordList())
		{
			writtenBytes = ifxDT.Write(ifs);
		}
		
		if (m_padBytes > 0)
		{
			byte [] padBytesArr = new byte [m_padBytes];
			for (int i = 0; i < m_padBytes; i++)
			{
				padBytesArr[i] = (byte) 0;
			}
			ifs.WriteByteArrayToStreamer(padBytesArr, m_padBytes);
		}
		
		return writtenBytes;
	}

	@Override
	public Integer WrittenFileElementSize() {
		// be rigorous
		int bytesInSetToWrite = OctetSize.Short() + OctetSize.Short(); // type and len
		
		for (IPFixDataTemplate ifxDT : GetContainedRecordList())
		{
			bytesInSetToWrite += ifxDT.WrittenFileElementSize();
		}
		
		
		return bytesInSetToWrite;
	}

	@Override
	public IPFixDataTemplate AddToContainer(IPFixDataTemplate addedElement) 
	{
		GetContainedRecordList().add(addedElement);
		// update length
		m_reportedBytes += addedElement.WrittenFileElementSize();
		return addedElement;
	}

	public Integer GetSetPadBytes() { return m_padBytes; };
	public void SetPadBytes( int pBytes) {	m_padBytes = pBytes; };
	public TemplateSetType GetTemplateType() { return m_templateSetType; };

	private ArrayList <IPFixDataTemplate> m_templateList;
	protected final TemplateSetType m_templateSetType;
	protected int m_reportedBytes;
	protected int m_padBytes;
	protected static int SetsInFile = 0;
	
}
