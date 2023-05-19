package com.cbhstudios.tools.flowedit;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;

import com.cbhstudios.tools.flowedit.IPFixDataTemplate.IPFixInfoElementTemplate;

public class IPFixDataRecord extends IPFixFileElement 
{
	public IPFixDataRecord(IPFixDataTemplate definingTemplate) 
	{
		m_governingTemplate = definingTemplate;
		m_dataValuesList = new ArrayList<IPFixDataRecordDataValue>();
	};
	
	// Since the data record is at the bottom of the linkage hierarchy, it has nothing dependent 
	// to signal to remove as a side-effect of its own removal
	public Boolean Remove() 
	{
		return true; 
	};

	@Override
	public Integer Read(IPFixStreamer ifs) throws FloweditException 
	{
 		int readRecordLength = 0;
		
		Iterator<IPFixInfoElementTemplate> templatedElementListIterator = m_governingTemplate.GetInfoElementList().iterator();
		
		try
		{
			int nextDataElementLen;
			// what we should get here is just a conflagration of 
			// data values.  We need to walk them in their templated form to find the read lengths 
			// for each
			while (templatedElementListIterator.hasNext())
			{
				nextDataElementLen = templatedElementListIterator.next().get_elementSize();
				boolean bVarLen = false;
				
				// check for variable length field
				if (IPFixInfoElementTemplate.ELEMENT_SIZE_VARIABLE == nextDataElementLen)
				{
					bVarLen = true;
					// read one byte length
					byte simpleElementLen = ifs.ReadIntFromStreamer(OctetSize.Byte()).byteValue();
					readRecordLength += OctetSize.Byte();
					
					if (IPFixInfoElementTemplate.VARIABLE_SIZE_EXTENDED == simpleElementLen)
					{
						short longerElementLen = ifs.ReadIntFromStreamer(OctetSize.Short()).shortValue();
						nextDataElementLen = (int) longerElementLen & 0xffff;
						readRecordLength += OctetSize.Short();
					}
					else
					{
						nextDataElementLen = (int) simpleElementLen & 0xff; // and we're positioned to read 						
					}
				}
				byte [] nextValue = ifs.ReadByteArrayFromStreamer(nextDataElementLen);
				// save off the value in the per-data element list...
				m_dataValuesList.add(new IPFixDataRecordDataValue(nextValue, bVarLen));
				readRecordLength += nextDataElementLen;
			}
		}
		catch (FloweditException fEx) 
		{ 
			throw new FloweditException("Reading DataRecord", fEx );
		}
		m_reportedLength = readRecordLength;
		return readRecordLength;
	}

	@Override
	public Integer Write(IPFixStreamer ifs) throws FloweditException 
	{
		try
		{
			// put together a continuous stream of data objects
			ByteArrayOutputStream payloadStream = new ByteArrayOutputStream();
			
			for (IPFixDataRecordDataValue baElt : this.m_dataValuesList)
			{
				// see if we need to encode length....
				if (baElt.m_varLen)
				{
					if (baElt.m_data.length >= 255)
					{
						// "Three byte" length
						short extendedDataLen = (short) (baElt.m_data.length & 0xffff);
						ByteBuffer extendedLenBB = ByteBuffer.allocate(OctetSize.Byte() + OctetSize.Short());
						extendedLenBB.put((byte) 0xff);
						extendedLenBB.putShort(extendedDataLen);
						payloadStream.write(extendedLenBB.array());						
					}
					else
					{
						// just lay down a byte of length
						payloadStream.write(baElt.m_data.length);  // doc indicates it will write a byte.
					}
				}
				payloadStream.write(baElt.m_data);
			}
			ifs.WriteByteArrayToStreamer(payloadStream.toByteArray(), 
					payloadStream.size());
			return payloadStream.size();			
		}
		catch (FloweditException fEx) { throw fEx; }
		catch (IOException ioEx) 
		{
			throw new FloweditException("Exception writing payload ", ioEx);
		}
	}

	public IPFixDataTemplate GetGoverningTemplate() { return m_governingTemplate; }
	
	protected static class IPFixDataRecordDataValue
	{
		public IPFixDataRecordDataValue(byte [] valueData, boolean isVarLen)
		{
			m_data = valueData;
			m_varLen = isVarLen;
		}
		
		protected final byte [] m_data;
		protected final boolean m_varLen;
		
		public ByteBuffer GetDataValue() 
		{
			ByteBuffer bbDV =  ByteBuffer.wrap(m_data);
			return bbDV;
		}
		
		public boolean IsVarLength() { return m_varLen; }
	}
	
	protected static class IPFIXDataRecordList extends IPFixDataRecordDataValue 
			implements Iterator<IPFixDataRecordDataValue> 
	{
		public IPFIXDataRecordList(byte[] valueData, boolean isVarLen) 
		{
			super(valueData, isVarLen);
			
			// now, pull out fields which are defined at the list level (semantic)
			bbListData = super.GetDataValue();
			bSemantic = bbListData.get();
			
			// and the element description
			int elementID = (int) bbListData.getShort();
			int elementLength = (int) bbListData.getShort();
			
			basicListElementMetadata = new IPFixInfoElementTemplate((short) (elementID & 0x7fff),
									elementLength);
			// see if a PEN follows
			if (0 != (elementID & 0x8000))
			{
				basicListElementMetadata.set_isPENElement(true);
				basicListElementMetadata.setPeNumber(bbListData.getInt());
			}
		}

		public IPFIXDataRecordList(IPFixDataRecordDataValue ipfDRecDVal)
		{
			this(ipfDRecDVal.m_data, ipfDRecDVal.IsVarLength());
		}
		
		public String semanticStringValue()
		{
			return IPFixXMLUtils.ipFixListSemanticString(bSemantic);
		}
		
		@Override
		public ByteBuffer GetDataValue()
		{
			return bbListData;
		}
		
		public IPFixInfoElementTemplate getBasicListElementMetadata()
		{
			return basicListElementMetadata;
		}

		@Override
		public boolean hasNext() {
			return (bbListData.remaining() != 0);
		}

		@Override
		public IPFixDataRecordDataValue next() {
			IPFixDataRecordDataValue idrNext;
			
			// see if we have a length to pick up here.
			int elementLen;
			
			if (basicListElementMetadata.isVariableLength())
			{
				// need to read a length value...and it could be 1 or 3 bytes....
				elementLen = (int) (bbListData.get() & 0xff);
				
				if ((byte) elementLen == IPFixInfoElementTemplate.VARIABLE_SIZE_EXTENDED)
				{
					elementLen = (int) (bbListData.getShort() & 0xffff);
				}
					
			}
			else
			{
				elementLen = basicListElementMetadata.get_elementSize();
			}
			
			// now, that gives us what we need to create the instance for this iterator
			// to return
			
			byte [] bArrayNext = new byte[elementLen];
			this.bbListData.get(bArrayNext, 0, elementLen);
			idrNext = new IPFixDataRecordDataValue(bArrayNext, false);
			
			return idrNext;
		}
		
		@Override
		public void remove()
		{
			throw new UnsupportedOperationException();
		}
		

		// we maintain our own ByteBuffer here reflecting current state
		private final ByteBuffer bbListData;
		final private byte bSemantic;
		private IPFixInfoElementTemplate basicListElementMetadata;
	}

	public Collection<IPFixDataRecordDataValue> GetDataValues() { return m_dataValuesList; }
	public int AddDataRecordValue(IPFixDataRecordDataValue ipfDRecDataVal)
	{
		m_dataValuesList.add(ipfDRecDataVal);
		return m_dataValuesList.size();
	}
	@Override
	public Integer WrittenFileElementSize() 
	{
		int payloadWrittenSize = 0;
		
		for (IPFixDataRecordDataValue baElt : this.m_dataValuesList)
		{
			payloadWrittenSize += baElt.m_data.length;
			
			// calculate the var length bytes
			if (baElt.IsVarLength())
			{
				// one byte length encoding or three?
				if (baElt.m_data.length > 255)
				{
					payloadWrittenSize += 3;
				}
				else // one byte
				{
					payloadWrittenSize++;
				}
			}
		}

		m_reportedLength = payloadWrittenSize;
		return m_reportedLength;
	}
	
	protected final IPFixDataTemplate m_governingTemplate;  // have to keep this to parse the template
	protected int m_reportedLength;
	protected ArrayList<IPFixDataRecordDataValue> m_dataValuesList;
}
