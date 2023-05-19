package com.cbhstudios.tools.flowedit;

/**
 * @author wtackabury
 *
 */

import java.util.ArrayList;
import java.util.HashMap;

public class IPFixMessage extends IPFixFileElement implements IIPFixElementContainerViewer<IPFixTemplateSet> 
{
	public static final short STARTING_DATA_SET_SET_ID = 256;
	
	public IPFixMessage(int startingDataRecord) 
	{ 
		m_messageTemplateSets = new ArrayList<IPFixTemplateSet>();
		m_messageHeader = new IPFixMessageHeader(startingDataRecord, this);
		m_messageDataSets = new ArrayList<IPFixDataSet>();
	}
	
	public Boolean Remove() 
	{
		for (IPFixTemplateSet ipFtrRem : GetContainedRecordList())
		{
			ipFtrRem.Remove();
		}
		
		for (IPFixDataSet ipfDS : m_messageDataSets)
		{
			ipfDS.Remove();
		}
		return true; 
	};
	
	@Override
	public Boolean InsertAfter(IPFixTemplateSet insertion, Integer index) { return true; };

	@Override
	public Boolean MoveUp(IPFixTemplateSet moveCandidate) { return true; };

	@Override
	public Boolean RemoveFromView(IPFixTemplateSet removeCandidate) { return true; };
	
	@Override
	public ArrayList<IPFixTemplateSet> GetContainedRecordList() { return m_messageTemplateSets; };	

	public ArrayList<IPFixDataSet> GetContainedDataSets() { return m_messageDataSets; }
	
	public void AddToContainedDataSets(IPFixDataSet ipfDS) { m_messageDataSets.add(ipfDS); }
	@Override
	public IPFixTemplateSet AddToContainer (IPFixTemplateSet addedElement)
	{
		m_messageTemplateSets.add(addedElement);
		return addedElement;
	}
	
	public Integer DataRecordsInMessage()
	{
		int msgDataRecordCount = 0; 
		// Need to provide the message header with an updated count of elements...
		for ( IPFixDataSet ifFTRDRContainer : GetContainedDataSets())
		{
			msgDataRecordCount += ifFTRDRContainer.GetContainedRecordList().size();
		}
		return msgDataRecordCount;		
	}
	@Override
	public Integer Read(IPFixStreamer ifs) throws FloweditException {
		int readBytes;
		int templateBytes;
		// Note that setId is defined for purposes of local analysis as an int, but is encoded
		// as a short in the message.  This is because we have to do relative comparisons, and
		// Java's defaulting to signed quantities means that large set id values get picked up as 
		// negative numbers...
		long setId = 0;
		// start with a read of the message header...
		readBytes = ifs.ReadIPFixFileElement(m_messageHeader);

		// now, loop through templates to get to the end of the logical
		// message, or the first data set (which will be terminated within
		while (readBytes < m_messageHeader.ReportedMessageByteSize())
		{
			try
			{
				// yes, we are reading a short into an int variable.
				setId =  (long) 0xffff & ifs.ReadIntFromStreamer(OctetSize.Short()).shortValue();
				readBytes += OctetSize.Short();
			}
			catch (FloweditException fEx)
			{
				throw new FloweditException("Reading Message header : ",  fEx);
			}
				
			// If this is a set id for data itself, then we're done with
			// template sets
			if (setId < STARTING_DATA_SET_SET_ID)
			{
				// now, we're going to read the length field, which will allow us
				// to know how to find the templates within.  
				templateBytes = ifs.ReadIntFromStreamer(OctetSize.Short()).shortValue();
				readBytes += OctetSize.Short();
		
				// now let's loop through the template sets  here
				IPFixTemplateSet ifTempSet = new IPFixTemplateSet( (short) setId, templateBytes);
				readBytes += ifs.ReadIPFixFileElement(ifTempSet);
				AddToContainer(ifTempSet);
			
				// now, need to loop over the template sets to be able to create a top-level map between
				// template *id* and template record
				for (IPFixDataTemplate ifxDTemp : ifTempSet.GetContainedRecordList())
				{
					RegisterTemplateMapping(ifxDTemp.GetTemplateId(), ifxDTemp);
				}
			}
			else
			{
				// data set read....these can come in any order with respect to 
				// set id (of corresponding template).  Set ID is *assigned* at this point 
				try
				{
				
					IPFixDataSet ifxDataSet = new IPFixDataSet(this, (short) setId);
				
					// The following will cause callbacks to this message to try to do fixup of the
					// data elements with the length (so's it knows how to read them)
					readBytes += ifs.ReadIPFixFileElement(ifxDataSet);
					m_messageDataSets.add(ifxDataSet);
				
					if (readBytes >= m_messageHeader.ReportedMessageByteSize())
					{
						break;
					}
					
				}
				catch (FloweditException fEx)
				{
					throw new FloweditException("Reading data set " + setId ,  fEx);
				}
			}
		}
		
		return readBytes;
	}

	@Override
	public Integer Write(IPFixStreamer ifs) throws FloweditException {
		// start with a write of the message header...
		int writtenBytes = ifs.WriteIPFixFileElement(m_messageHeader);
		
		// followed by each of the templates...
		for ( IPFixTemplateSet ifFemplateSet : GetContainedRecordList())
		{
			writtenBytes += ifs.WriteIPFixFileElement(ifFemplateSet);
		}		
		// Now, followed by the *data records themselves*, which reference those templates
		for ( IPFixDataSet ifxDataSet : m_messageDataSets)
		{
			writtenBytes += ifs.WriteIPFixFileElement(ifxDataSet);
		}
		
		// This is where we would put the checksum itself.  Need to have written the 
		// checksum options template itself....
		
		return writtenBytes;
	}

	@Override
	public Integer WrittenFileElementSize() {
		int accumulatedWriteSize = m_messageHeader.WrittenFileElementSize();  // start with message header size
		
		for (IPFixTemplateSet ifTempl : GetContainedRecordList())
		{
			accumulatedWriteSize += ifTempl.WrittenFileElementSize();
		}
		
		for (IPFixDataSet ipfDS : GetContainedDataSets() )
		{
			accumulatedWriteSize += ipfDS.WrittenFileElementSize();
		}
		
		return accumulatedWriteSize;
	}
	public IPFixDataTemplate GetDataTemplateByTemplateID(short templateId)
	{
		return m_templateSetToTemplateRecordHash.get(templateId);
	}

	public void RegisterTemplateMapping(short templateID, IPFixDataTemplate ifxDTemp)
	{
		m_templateSetToTemplateRecordHash.put( templateID, ifxDTemp);						
	}
	
	public Integer GetObservationDomain() { return this.m_messageHeader.imhObservationDomain; }
	public Integer GetSequenceNumber() { return this.m_messageHeader.imhSequenceNumber; }
	public Integer GetExportTime() { return this.m_messageHeader.imhExportTime; }
	
	public Integer SetExportTime(int xpTime)
	{
		m_messageHeader.imhExportTime = xpTime;
		return GetExportTime();
	}
	
	public Integer SetObservationDomain(int obsDomainId)
	{
		m_messageHeader.imhObservationDomain = obsDomainId;
		return GetObservationDomain();
	}
	
	class IPFixMessageHeader extends IPFixFileElement
	{
		public static final short IPFIX_VERSION_DATA = 0x000a;
		
		public IPFixMessageHeader(int startingDataRecord, IPFixMessage associatedMessage)
		{
			// a bootstrap constructor until a file is read in
			
			
			this.imhExportTime =  (int) System.currentTimeMillis() / 1000;
			this.imhSequenceNumber = startingDataRecord;  // number of data records
			this.imhObservationDomain = 1;
			this.imhMessageReportedBytes = 0;
			// kind of gross, but  I need something to query the length of when this gets written
			imhMessage = associatedMessage;
		}
		
		@Override
		public Boolean Remove() {
			// Nothing to actually do here
			return false;
		}

		@Override
		public Integer Read(IPFixStreamer ifs) throws FloweditException {
			// Read and verify message data
			Integer vfCompare = ifs.ReadIntFromStreamer(OctetSize.Short());
			
			if (vfCompare != IPFIX_VERSION_DATA)
			{
				throw new FloweditException("File read detected incorrect IPFIX version : " + vfCompare);
			}
			
			imhMessageReportedBytes = ifs.ReadIntFromStreamer(OctetSize.Short()).shortValue();
			imhExportTime = ifs.ReadIntFromStreamer(OctetSize.Integer());
			imhSequenceNumber = ifs.ReadIntFromStreamer(OctetSize.Integer());
			imhObservationDomain = ifs.ReadIntFromStreamer(OctetSize.Integer());
			
			return this.WrittenFileElementSize();
		}
		
		@Override
		public Integer Write(IPFixStreamer ifs) throws FloweditException {
			// Start with a version....
			ifs.WriteIntToStreamer(IPFIX_VERSION_DATA + 0, OctetSize.Short());
			ifs.WriteIntToStreamer(this.imhMessage.WrittenFileElementSize(), OctetSize.Short());
			ifs.WriteIntToStreamer(imhExportTime, OctetSize.Integer());
			ifs.WriteIntToStreamer(imhSequenceNumber.intValue(), OctetSize.Integer());
			ifs.WriteIntToStreamer(imhObservationDomain.intValue(), OctetSize.Integer());
			
			return this.WrittenFileElementSize();
		}

		@Override
		public Integer WrittenFileElementSize() {
		
			return ( OctetSize.Short() +  // version
					OctetSize.Short() +  // size
					OctetSize.Integer() + // export time
					OctetSize.Integer() + // sequence number
					OctetSize.Integer()  // observation ID 
					);
		}
		
		public Integer ReportedMessageByteSize() { return imhMessageReportedBytes.intValue(); };

		protected Short imhMessageReportedBytes;
		protected Integer imhExportTime;
		protected Integer imhSequenceNumber;
		protected Integer imhObservationDomain;
		protected IPFixMessage imhMessage;
	}

	protected ArrayList<IPFixTemplateSet> m_messageTemplateSets;
	// +++waynet this is static since it must exist beyond this messsage.  This behavior
	// is better done by having it initialized by the file-level caller, but
	// this will do for now, until a single flowedit instance will take on 
	// several actual files.
	protected static HashMap<Short, IPFixDataTemplate> m_templateSetToTemplateRecordHash;
	
	 static
	 {
		 m_templateSetToTemplateRecordHash = new HashMap<Short, IPFixDataTemplate>();
	 }

	 public static Integer CurrentSystemTemplateRecords ()
	{
		return m_templateSetToTemplateRecordHash.values().size();
	}
	 
	protected ArrayList<IPFixDataSet> m_messageDataSets;
	IPFixMessageHeader m_messageHeader;
		
}

