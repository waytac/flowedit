/**
 * 
 */
package com.cbhstudios.tools.flowedit;

import java.util.ArrayList;
import java.util.Collection;
import java.lang.reflect.Type;
import java.util.LinkedHashMap;
import com.cbhstudios.tools.flowedit.IPFixTemplateSet.TemplateSetType;

/**
 * @author wtackabury
 *
 */
public class IPFixDataTemplate extends IPFixFileElement implements IIPFixElementContainerViewer<IPFixDataRecord>
{	
	public IPFixDataTemplate(TemplateSetType setType) 
	{ 
		m_templateSetType = setType;
		m_templateDataRecords = new ArrayList<IPFixDataRecord>();
		m_templatedElementMap = new LinkedHashMap <Integer, IPFixInfoElementTemplate>();
		m_optionsTemplateScopeFieldCount = 0;
	}
	
	public Boolean Remove() 
	{
		while (! m_templateDataRecords.isEmpty())
		{
			m_templateDataRecords.remove(0).Remove();
		}
		m_templatedElementMap.clear();
		// primary aggregation of data records is done in data set, just clear our array here
		m_templateDataRecords.clear();
		return true; 
	};
	
	public Boolean InsertAfter(IPFixDataRecord insertion, Integer index) { return true; };
	public Boolean MoveUp(IPFixDataRecord moveCandidate) { return true; };
	public Boolean RemoveFromView(IPFixDataRecord removeCandidate) { return true; };
	public ArrayList<IPFixDataRecord> GetContainedRecordList() { return m_templateDataRecords; };
	
	public Short GetOptionScopeFieldCount() throws FloweditException
	{
		if (TemplateSetType.OPTION_TEMPLATE_SET != GetTemplateType())
		{
			throw new FloweditException("IPFixDataTemplate: attempt to query scope on non-option template");
		}
			
		return m_optionsTemplateScopeFieldCount;
	}
	// This represents a list of the data records conforming with this data template
	public boolean RegisterCorrespondingDataRecord(IPFixDataRecord ifxDataRec)
	{
		return (m_templateDataRecords.add(ifxDataRec));
	}
	
	// IPFixFileElement methods

	@Override
	public Integer Read(IPFixStreamer ifs) throws FloweditException
	{
		int fieldCount;
		int readSize = 0; // because template type and length have been read
		// At the point we get here, we have read in the set id and length, and are poised to
		// pick up the template ID and fields.
		m_templateId = ifs.ReadIntFromStreamer(OctetSize.Short()).shortValue();
		readSize += OctetSize.Short();
		fieldCount = ifs.ReadIntFromStreamer(OctetSize.Short()).shortValue();
		readSize += OctetSize.Short();
		
		// Going to try to fold option template and data template handling here together.  I just need to
		// know to extract and (later) persist the scope fields
		// +++wayne if we're really going to edit options templates, we need to have more depth
		// than that.  

		if (TemplateSetType.OPTION_TEMPLATE_SET == m_templateSetType)
		{
			m_optionsTemplateScopeFieldCount = ifs.ReadIntFromStreamer(OctetSize.Short()).shortValue();
			readSize += OctetSize.Short();
			// what will follow is the session or message scope IE-ID, which we can handle "normally".
		}
		
		for (int i = 0; i < fieldCount; i++)
		{
			// allocate a template item based on ID and length
			short elementIdValue = ifs.ReadIntFromStreamer(OctetSize.Short()).shortValue();
			readSize += OctetSize.Short();
			boolean isPEElement = ((elementIdValue & 0x8000) != 0);
			elementIdValue &= 0x7fff;
			int elementLength = ifs.ReadIntFromStreamer(OctetSize.Short());
			readSize += OctetSize.Short();
			IPFixInfoElementTemplate ifxElement = new IPFixInfoElementTemplate((short) elementIdValue, elementLength);
			
			if (isPEElement)
			{
				// need to set the element template and read the PEN
				ifxElement.set_isPENElement(true);
				ifxElement.setPeNumber(ifs.ReadIntFromStreamer(OctetSize.Integer()));
				readSize += OctetSize.Integer();
			}			
			// if for some reason this preexisted in the map, this is correct handling, since
			// this corresponds to a redefinition of the template type (per RFC 7012).  However,
			// if it gets sent with the expectation of duplicate instantiation (like Patrick used
			// to do with flow ID :) then flowedit can't handle it, so warn.
			
			if (null != m_templatedElementMap.get(ifxElement.CreateIndexableKey()))
			{
				System.err.println("Warning, reapplication on template " + m_templateId +
						" of element with PEN of " + ifxElement.m_peNumber + ", element ID "
						+ ifxElement.m_elementId);
			}
			AddToInfoElementList(ifxElement);
		}
				
		return readSize;
	}
	
	@Override
	public Integer Write(IPFixStreamer ifs) throws FloweditException
	{
		int writtenBytes = 0;
		// set ID has been written already, along with overall length, as properties of the
		// IPFixMessage
		ifs.WriteIntToStreamer((int) GetTemplateId(), OctetSize.Short());
		writtenBytes += OctetSize.Short();
		// this should be the number of entries associated with this template, each
		// with its own distinct key.  size() returns the number of key-value mappings, which
		// is what we want.
		ifs.WriteIntToStreamer(m_templatedElementMap.size(), OctetSize.Short());
		writtenBytes += OctetSize.Short();

		// See if we need to supply a scoping count
		if (m_templateSetType == TemplateSetType.OPTION_TEMPLATE_SET)
		{
			ifs.WriteIntToStreamer((int) m_optionsTemplateScopeFieldCount, OctetSize.Short());
		}
		
		// +++waynet this may need sorting
		for (IPFixInfoElementTemplate kTemplatedElement : m_templatedElementMap.values())
		{
			// write the key
			ifs.WriteIntToStreamer( (kTemplatedElement.get_isPENElement()) ?
						kTemplatedElement.m_elementId | 0x8000 : kTemplatedElement.m_elementId, 
						OctetSize.Short());
			
			writtenBytes += OctetSize.Short();
			
			ifs.WriteIntToStreamer(kTemplatedElement.m_elementSize, OctetSize.Short());
			writtenBytes += OctetSize.Short();

			if (kTemplatedElement.m_isPENElement)
			{
				// need to write the PEN
				ifs.WriteIntToStreamer(kTemplatedElement.getPeNumber().intValue(), OctetSize.Integer());				
				writtenBytes += OctetSize.Integer();				
			}
		}
		
		return writtenBytes;
	}
	
	// returns length of associated value, and 0 if not defined in this map
	public Integer DefinedTemplatedElementLength(short elementId, int elementPEN)
	{
		int elementKey = CreateIndexableLookupKey(elementId, elementPEN);
		
		IPFixInfoElementTemplate ifxElement = m_templatedElementMap.get(elementKey);
		
		if (null == ifxElement)
		{
			return 0;
		}
		else
		{
			return ifxElement.get_elementSize();
		}
	}
	
	public ArrayList<IPFixInfoElementTemplate> GetInfoElementList()
	{
		// +++wayne Might find a way to optimize on this instead of continual reconstruction....
		return new ArrayList<IPFixInfoElementTemplate>(m_templatedElementMap.values());
	}

	public void AddToInfoElementList(IPFixInfoElementTemplate ifxElement)
	{		
		m_templatedElementMap.put(ifxElement.CreateIndexableKey(), ifxElement);	
	}
	protected final ArrayList<IPFixDataRecord> m_templateDataRecords;
	protected LinkedHashMap <Integer, IPFixInfoElementTemplate> m_templatedElementMap;
	
	@Override
	public Integer WrittenFileElementSize() {
		// need size of template itself.
		int writtenSize = OctetSize.Short() + OctetSize.Short();  // that's the template id and field count.
		if ((m_templateSetType == TemplateSetType.OPTION_TEMPLATE_SET) &&
				(m_optionsTemplateScopeFieldCount > 0))
		{
			writtenSize += OctetSize.Short();  // scope field count
		}
			
		// then for each element
		for (IPFixInfoElementTemplate ifxIETempl : m_templatedElementMap.values())
		{
			// we know we have the basic ID plus length
			writtenSize += OctetSize.Short() + OctetSize.Short();
			
			// plus 4 bytes of PEN if the element is private
			if (ifxIETempl.get_isPENElement())
			{
				writtenSize += OctetSize.Integer();
			}			
		}

		return writtenSize;
	}

	public static class IPFixInfoElementTemplate
	{
		// Per RFC 7101, this is the indicator as to whether the size of an element is 
		// variable
		public static final int ELEMENT_SIZE_VARIABLE = -1;
		public static final byte VARIABLE_SIZE_EXTENDED = (byte) 0xff;
		
		public IPFixInfoElementTemplate (short elementId, int elementSize)
		{
			m_elementSize = elementSize;
			m_elementId = elementId;
		}
				
		public Integer get_elementSize() {	return m_elementSize; }
		
		public void set_isPENElement(boolean isPENElement) {
			this.m_isPENElement = isPENElement;
		}

		public boolean  get_isPENElement() { return m_isPENElement; }
		
		public void setPeNumber(int peNumber) 
		{
			this.m_peNumber = peNumber;
		}
		
		public boolean isVariableLength()
		{
			return (this.get_elementSize() == ELEMENT_SIZE_VARIABLE);
		}

		public Integer CreateIndexableKey()
		{
			return ((getElementId() << 16)
					+ getPeNumber().shortValue());
		}
				
		public Integer getPeNumber() 
		{ 
			return (get_isPENElement()) ? m_peNumber : 0;  // a PE of "0" is implicitly IANA 
		}
		
		public void setElementId(short elementId) { m_elementId = elementId; };
		public Short getElementId() { return m_elementId; };
		

		private short m_elementId;
		private boolean m_isPENElement;
		private int m_peNumber;
		private final int m_elementSize;
	}
	
	public Short GetTemplateId() { return m_templateId; };
	public void SetTemplateId(short sTemplateId) { m_templateId = sTemplateId; }
	public TemplateSetType GetTemplateType() { return m_templateSetType; }
	public Collection<IPFixInfoElementTemplate> GetTemplatedElements() 
	{ 
		return m_templatedElementMap.values();
	}

	// This is a static routine to allow the creation of a lookup into the m_templatedElementMap 
	public static Integer CreateIndexableLookupKey(short peElementNum, int peNum)
	{
		return (peElementNum << 16 + ((short) peNum & 0xffff ));
	}

	@Override
	public IPFixDataRecord AddToContainer(IPFixDataRecord addedElement) 
	{
		// This is the rote way to do this....
		GetContainedRecordList().add(addedElement);
		return addedElement;
	}

	// This is only relevant for option templates
	protected void SetScopeFieldCount(short sSFieldCount) throws FloweditException 
	{ 
		if (GetTemplateType() == TemplateSetType.OPTION_TEMPLATE_SET)
		{
			m_optionsTemplateScopeFieldCount = sSFieldCount;
		}
		else
		{
			throw new FloweditException("Attempt to set scope field count on data template");
		}
	}
	
	protected short m_templateId;
	protected short m_optionsTemplateScopeFieldCount;
	private final TemplateSetType m_templateSetType;
}
