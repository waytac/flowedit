package com.cbhstudios.tools.flowedit;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;

import javax.lang.model.element.UnknownElementException;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

import com.cbhstudios.tools.flowedit.IPFixDataTemplate.IPFixInfoElementTemplate;

// we are using a long (32b) for now as a key for all data items.  I should
// be more cleverly abstracted than that, and will probably revisit this to 
// do so later.  I could try to get more opaque, but wanted to expose the
// key encoding to Ariel here.  

public class IPFixXMLDataConf extends IPFixXMLProcessor {
	private final String DATA_CONF_RES_NAME = "flowFieldsDataType-conf.xml";
	static int StructureNodesProcessed = 0;
	
	public IPFixXMLDataConf()
	{
		// initialize map
		m_itemKeyToDCItemHash = new HashMap<Integer, DataConfItemBase>();
		m_structNameToDefinitionHash = new HashMap<String, DataConfStruct>(); 
	}
	
	public boolean init() throws FloweditException
	{
		int i;
		// open resource as stream
		// get our own class loader to do it
		try 
		{
			// +++waynet NOTE FOR ECLIPSE PROJECT: the gradle script currently puts the conf XML file
			// into the flowedit.jar as a resource.  I haven't found a way to be able to point Eclipse to
			// load this resource as a file from where it's placed in the workspace, despite following a
			// number of recipes and "working" patterns.  The best way (and in fact the way to guarantee 
			// that you'll be debugging what was last built into the production environment) is to a) build
			// the flowedit jar as "normal", then, b) configure the eclipse project to put the flowedit.jar 
			// *as a part of its classpath*.  You don't want to be loading classes from the .jar (since it's
			// not updated/recompiled automatically on Eclipse code edits).  You do want to load resources
			// from there, since there doesn't appear to be any other way.
			//
			InputStream dataConfIS = this.getClass().getClassLoader().getResourceAsStream(DATA_CONF_RES_NAME);
			
			if (null == dataConfIS)
			{
				throw new FloweditException ("Unable to load " + DATA_CONF_RES_NAME + ".  If running from Eclipse, note comments in IPFixXMLDataConf::init()");
			}
						
			m_xmlParser.parse(new InputSource(dataConfIS));

			Document xDoc = m_xmlParser.getDocument();
			
			xDoc.getDocumentElement().normalize();  // BCP
			
			// get structure nodes
			NodeList ipfConfNodes = xDoc.getElementsByTagName("structure");
			
			for (i = 0; i < ipfConfNodes.getLength(); i++)
			{
				ProcessStructureNode(ipfConfNodes.item(i));
			}
			
			// and now for field elements themselves, possibly referencing structures
			ipfConfNodes = xDoc.getElementsByTagName("fieldElement");
			
			for (i = 0; i < ipfConfNodes.getLength(); i++)
			{
				ProcessFieldNode(ipfConfNodes.item(i));
			}
		}
		catch (Exception ex)
		{
			throw new FloweditException("Unable to process " + DATA_CONF_RES_NAME + "; " + ex.getMessage());
		}
		
		return true;
	}
	
	public int DataItemKey(IPFixInfoElementTemplate dataElement) throws UnknownElementException
	{
		int diKeyValue = dataElement.getElementId();
		
		if (dataElement.get_isPENElement())
		{
			int diKeyPenPart = dataElement.getPeNumber() << 16;
			
			diKeyValue |= diKeyPenPart;			
		}
		return diKeyValue;
	}
	
	public CoarseDataType GetOverallDataType (int dciKey)
	{
		Integer dciIKey = new Integer(dciKey);
		DataConfItemBase dcIItem = m_itemKeyToDCItemHash.get(dciIKey);
		
		if (null != dcIItem)
		{
			return dcIItem.GetDataItemCoarseType();
		}
		else
		{
			return CoarseDataType.UNKNOWN;
		}
	}
	
	public SimpleDataConfType GetItemDataType (int dciKey)
	{
		Integer dciIKey = new Integer(dciKey);
		DataConfItemBase dcIItem = GetItemSchemaConfigObject(dciIKey);
		
		if (null != dcIItem)
		{
			return dcIItem.m_dciType;
		}
		else
		{
			return SimpleDataConfType.UNKNOWN;			
		}
	}

	public DataConfItemBase GetItemSchemaConfigObject(int dciIKey)
	{
		return m_itemKeyToDCItemHash.get(dciIKey);
	}

	public DataConfStruct GetStructureConfigObject(String dcsName)
	{
		return m_structNameToDefinitionHash.get(dcsName);
	}
	
	public DataEncoding GetItemDataEncoding (int dciKey)
	{
		Integer dciIKey = new Integer(dciKey);
		DataConfItemBase dcIItem = GetItemSchemaConfigObject(dciIKey);
		
		if (null != dcIItem)
		{
			return dcIItem.m_dciEncoding;
		}
		else
		{
			return DataEncoding.LITERAL;			
		}
	}
	
	public List<SimpleDataConfType> GetListItemDataType (int dciKey)
	{
		ArrayList<SimpleDataConfType> lItemsTypes = new ArrayList<SimpleDataConfType>();		
		lItemsTypes.add(SimpleDataConfType.STRING);
		return lItemsTypes;		
	}
	
	
	public enum SimpleDataConfType
	{
		UNKNOWN, NONE, FLOAT, SIGNED32, 
		UNSIGNED8, UNSIGNED16, UNSIGNED32,
		UNSIGNED64, UNSIGNED128, STRING
	}

	public static Integer PresumedTemplateSizeFromConfType(SimpleDataConfType sdcType)
	{
		switch (sdcType)
		{
			case SIGNED32:
				return OctetSize.Long();
				
			case FLOAT:
				return OctetSize.Float();
				
			case UNSIGNED8:
				return OctetSize.Byte();
				
			case UNSIGNED16:
				return OctetSize.Short();
			
			case UNSIGNED32:
				return OctetSize.Integer();
				
			case UNSIGNED64:
				return OctetSize.Long();
			
			case UNSIGNED128:
				return OctetSize.LongLong();
		}
		 
		return IPFixInfoElementTemplate.ELEMENT_SIZE_VARIABLE;
	}
	
	protected static SimpleDataConfType MapEnumToSimpleType(String s)
	{
		switch (s)
		{
		case "none":
			return SimpleDataConfType.NONE;
			
		case "float":
			return SimpleDataConfType.FLOAT;
			
		case "signed32":
			return SimpleDataConfType.SIGNED32;
			
		case "unsigned8":
			return SimpleDataConfType.UNSIGNED8;
			
		case "unsigned16":
			return SimpleDataConfType.UNSIGNED16;
			
		case "unsigned32":
			return SimpleDataConfType.UNSIGNED32;
			
		case "unsigned64":
			return SimpleDataConfType.UNSIGNED64;
			
		case "unsigned128":
			return SimpleDataConfType.UNSIGNED128;
			
		case "string":
			return SimpleDataConfType.STRING;
		}
		return SimpleDataConfType.UNKNOWN;
	}
	
	protected enum CoarseDataType { UNKNOWN, SIMPLE, AGGREGATE, ENUMERATION, STRUCTURAL }

	protected static CoarseDataType MapEnumToCoarseType(String s)
	{
		switch (s)
		{
		case "simple":
			return CoarseDataType.SIMPLE;
			
		case "aggregate":
			return CoarseDataType.AGGREGATE;
			
		case "enumeration":
		case "enumerated":
			return CoarseDataType.ENUMERATION;
			
		case "structural":
			return CoarseDataType.STRUCTURAL;
			
		}
		return CoarseDataType.UNKNOWN;
	}
	
	protected enum DataEncoding { LITERAL, DECIMAL, HEX, IPV4ADDR, IPV6ADDR };
	
	protected void ProcessStructureNode(Node structNode) throws FloweditException
	{
		// rope structure node into m_structNameToDefinitionHash
		DataConfStruct dcs = new DataConfStruct(++StructureNodesProcessed);
		dcs.InitFromDCConfNode(structNode);
		
		if (dcs.m_isInitialized)
		{
			m_structNameToDefinitionHash.put(dcs.m_tagName, dcs);
		}
		else
		{
			throw new FloweditException("processing " + dcs.m_tagName);
		}
		
	}

	protected void ProcessFieldNode(Node structNode) throws FloweditException
	{
		// This is somewhat hacking around a true factory pattern.  Determine which "type" of
		// field node conf item to declare--simple or aggregate
		
		DataConfItemBase dci = (CoarseDataType.SIMPLE == InferConfTypeFromDCNode(structNode)) ?
				(DataConfItemBase) new DataConfItemElementSimple() :
					(DataConfItemBase) new DataConfItemElementAggregate();
				
		dci.InitFromDCConfNode(structNode);
		
		if (dci.m_isInitialized)
		{
			m_itemKeyToDCItemHash.put(dci.m_itemKey, dci);
		}
		else
		{
			throw new FloweditException ("processing field " + dci.m_tagName);
		}
	}

	protected static CoarseDataType InferConfTypeFromDCNode(Node nFieldElement)
	{
		NodeList aggregateFieldNodeFields = ((Element) nFieldElement).getElementsByTagName("aggregation");
		
		// We're really just hacking this based on whether we see an "aggregate" node in the 
		// field declaration
		if (aggregateFieldNodeFields.getLength() > 0)
		{
			return CoarseDataType.AGGREGATE;		
		}
		else
		{
			return CoarseDataType.SIMPLE;
		}
	}

	public abstract class DataConfItemBase
	{
		protected DataConfItemBase ()
		{
			m_isInitialized = false;
		}
		
		public abstract CoarseDataType GetDataItemCoarseType();		
		
		protected SimpleDataConfType m_dciType;
		protected DataEncoding m_dciEncoding;
		protected boolean m_isInitialized;
		protected String m_tagName;
		protected int m_itemKey;
		
		// this expects a "field element" node
		boolean InitFromDCConfNode(Node nFieldElement) throws FloweditException
		{
			NodeList neFields = nFieldElement.getChildNodes();

			// There will currently be two required child nodes of interest here: tag and type
			for (int i = 0; i < neFields.getLength(); i++)
			{
				if (neFields.item(i).getNodeType() != Node.ELEMENT_NODE)
				{
					continue;
				}
				else
				{
					Element elStructData = (Element) neFields.item(i);
					NodeList nlSubFields;

					switch (elStructData.getNodeName())
					{
					case "elementType":
						nlSubFields = elStructData.getElementsByTagName("type");
						
						if (nlSubFields.getLength() > 0)
						{
							m_dciType = MapEnumToSimpleType(nlSubFields.item(0).getFirstChild().getNodeValue());
						}
						else
						{
							throw new FloweditException ("Cannot extract type info for fieldElement node");
						}
						
						// data encoding, which we modify from a single case in the field semantic
						// make an inference here...
						if ((SimpleDataConfType.STRING ==  m_dciType) || 
								(SimpleDataConfType.NONE ==  m_dciType))
						{
							m_dciEncoding = DataEncoding.LITERAL;
						}
						else // all others expect numeric
						{
							m_dciEncoding = DataEncoding.DECIMAL;
						}
						
						// now, look for field semantic, which is optional.  If present, it specifies crucial
						// indicators as to how to encode and interpret XML payload.
						nlSubFields = elStructData.getElementsByTagName("fieldSemantic");
						
						if (nlSubFields.getLength() > 0)
						{
							switch (nlSubFields.item(0).getFirstChild().getNodeValue()) // only one instance
							{
							case "hexEncoding":
							case "bitmask":
								m_dciEncoding = DataEncoding.HEX;
								break;
								
							case "ipv4Addr":
								m_dciEncoding = DataEncoding.IPV4ADDR;
								break;
								
							case "ipv6Addr":
								m_dciEncoding = DataEncoding.IPV6ADDR;
								break;
								
							default:
								// other values not requiring handling at this time.
								break;
							}
						}
						// otherwise we're all set based on the inferences set above.
						
						break;
						
					case "Tag":
						nlSubFields = elStructData.getChildNodes();
						
						for (int j = 0; j < nlSubFields.getLength(); j++)
						{
							if (nlSubFields.item(j).getNodeType() != Node.ELEMENT_NODE)
							{
								continue;
							}
							switch (nlSubFields.item(j).getNodeName())
							{
							case "value":
								//Need to dive in to get element ID and PEN
								m_itemKey = 0;
								NodeList nlElementValueFields = nlSubFields.item(j).getChildNodes();
								for (int k = 0; k < nlElementValueFields.getLength(); k++)
								{
									if (nlElementValueFields.item(k).getNodeType() == Node.ELEMENT_NODE)
									{
										Integer iNeValue;
										switch (nlElementValueFields.item(k).getNodeName())
										{
										case "elementID":
											iNeValue = new Integer(nlElementValueFields.item(k).getFirstChild().getNodeValue());
											m_itemKey |= iNeValue.shortValue();
											break;
											
										case "elementPEN":
											iNeValue = new Integer(nlElementValueFields.item(k).getFirstChild().getNodeValue());
											m_itemKey |= (iNeValue.intValue() << 16);
											break;
										}
									}
								}
								break;
								
							case "canonicalName":
								m_tagName = nlSubFields.item(j).getFirstChild().getNodeValue();
								
								break;
								
							default:
								// a case we don't care about here
								break;
							}
						}
						break;
						
					}
				}
			}
			
			m_isInitialized = true;
			return m_isInitialized;
		}
		
	}

	public class DataConfItemElementSimple extends DataConfItemBase
	{
		@Override
		boolean InitFromDCConfNode(Node nFieldElement) throws FloweditException
		{
			boolean bResult = super.InitFromDCConfNode(nFieldElement);
			// There's nothing mutually exclusive about the simple field element node over
			// the base, currently--just return what we have.
			return bResult;
		}
		
		@Override
		public CoarseDataType GetDataItemCoarseType() { return CoarseDataType.SIMPLE; } 
	}
	
	public class DataConfItemElementAggregate extends DataConfItemBase
	{
		protected DataConfItemElementAggregate ()
		{
			super();
			m_derivedTypes = new ArrayList<String>();
		}

		@Override
		boolean InitFromDCConfNode(Node nFieldElement) throws FloweditException
		{
			boolean bResult = super.InitFromDCConfNode(nFieldElement);
			
			// Look into the aggregation here
			NodeList aggregateFieldNodeFields = ((Element) nFieldElement).getElementsByTagName("aggregation");

			if (0 == aggregateFieldNodeFields.getLength())
			{
				// It's hard to see how we could get here if properly constructed.  
				// leave this uninitialized..
				m_isInitialized = bResult = false;
			}
			else
			{
				// check to make sure this was typed correctly.
				if (m_dciType != SimpleDataConfType.NONE)
				{
					throw new FloweditException ("Node element " + m_tagName + 
							": cannot be aggregate and have full simple type");
				}

				NodeList agSubNodes = aggregateFieldNodeFields.item(0).getChildNodes();
				for (int i = 0; i < agSubNodes.getLength(); i++)
				{
					// look at everything below 'aggregate'
					if (agSubNodes.item(i).getNodeType() != Node.ELEMENT_NODE)
					{
						// +++wayne for the love of pete, finally implement an iterator that
						// keeps me from having to copy this for each XML scan context
						continue;   // cruft in the list
					}

					Element agSubNodeEl = (Element) agSubNodes.item(i);

					switch (agSubNodeEl.getNodeName())
					{
					case "elementType":
						NodeList stSubFields = agSubNodeEl.getElementsByTagName("structureType");
						//  m_derivedType needs to be an array list, to accomodate multiList's, where
						//   each subtemplate of a multilist is a separate structureType entry
						
						for (int j = 0; j < stSubFields.getLength(); j++)
						{
							m_derivedTypes.add(stSubFields.item(j).getFirstChild().getNodeValue());							
						}
						
						break;
						
					    // +++wayne there's only one value for "aggregateType", so ignore it for 
						// now
					case ("aggregationType"):
					default:
						break;
						
					}
					
				}
				
			}
			
			return bResult;
		}

		@Override
		public CoarseDataType GetDataItemCoarseType() { return CoarseDataType.AGGREGATE; }
		
		public List<String> GetDerivedStructureTypes()
		{
			return Collections.unmodifiableList(m_derivedTypes);
		}
		
		// This is an array list because: for subTemplateMultiList, each template in the
		//  "multi" is a separately defined derived type.  The "multilist" nature therefore comes in the
		//  actual element declaration therefore, as a series of template-id-attributed derived types.
		protected ArrayList<String> m_derivedTypes;
	}
	
	public class DataConfStruct extends DataConfItemBase
	{		
		public DataConfStruct (int instanceTag)
		{			
			m_itemKey = instanceTag;
			m_structFieldList = new ArrayList<DataConfStructFieldAttrs>();
		}

		@Override
		public CoarseDataType GetDataItemCoarseType() { return CoarseDataType.STRUCTURAL; } 
		
		@Override
		protected boolean InitFromDCConfNode(Node nFieldElement) throws FloweditException
		{
			Element eStruct = (Element) nFieldElement;
			
			NodeList nlStructFields = eStruct.getChildNodes();

			for (int i = 0; i < nlStructFields.getLength(); i++)
			{
				if (nlStructFields.item(i).getNodeType() != Node.ELEMENT_NODE)
				{
					continue;   // cruft in the list
				}
				else
				{
					Element elSubStructNode = (Element) nlStructFields.item(i);
					
					switch (elSubStructNode.getNodeName())
					{
					case "structureName":
						m_tagName = elSubStructNode.getFirstChild().getNodeValue();
						break;
						
					case "structureInstanceTagId":
						m_templateId = new Integer(elSubStructNode.getFirstChild().getNodeValue());
						break;
						
					case "structureFields":
						NodeList nFields = elSubStructNode.getElementsByTagName("structureField");
						NodeList sfSubNodes = nFields.item(0).getChildNodes();
						m_dciType = SimpleDataConfType.NONE; // we defer to the structureField types
						for (int j = 0; j < sfSubNodes.getLength(); j++)
						{
							// allocate array list element
							DataConfStructFieldAttrs dcsfAttrs = new DataConfStructFieldAttrs();
							m_structFieldList.add(dcsfAttrs);
							
							if (sfSubNodes.item(j).getNodeType() != Node.ELEMENT_NODE)
							{
								continue;
							}
							Element elSfSubEl = (Element) sfSubNodes.item(j);

							switch (elSfSubEl.getNodeName())
							{
							case "fieldType":
								// sigh....
								NodeList typeNL = elSfSubEl.getElementsByTagName("type");
								dcsfAttrs.m_dcsfType = MapEnumToSimpleType(typeNL.item(0).getFirstChild().getNodeValue());
								break;
								
							case "name":
								dcsfAttrs.m_dcsfName = elSfSubEl.getNodeValue();
								break;
							
							default:  // NB: Provide support for "name" here
								break;
							}
						}
					}
				}
			}
			
			if ((m_dciType != SimpleDataConfType.UNKNOWN) &&
					!m_tagName.isEmpty())
			{
				m_isInitialized = true;
			}
			return m_isInitialized;
		}
		
		
		public int GetStructTemplateId() 
		{
			if (null == m_templateId)
			{
				return 0;
			}
			else
			{
				return m_templateId.intValue();
			}
			
		}

		public class DataConfStructFieldAttrs
		{
			public DataConfStructFieldAttrs()
			{
				m_dcsfName = new String();
				m_dcsfType = SimpleDataConfType.UNKNOWN;
			}
			
			private SimpleDataConfType m_dcsfType;
			private String m_dcsfName;
		}

		public List<DataConfStructFieldAttrs> GetStructFields() 
		{
			return Collections.unmodifiableList(m_structFieldList);
		}
		
		protected ArrayList<DataConfStructFieldAttrs> m_structFieldList;
		
		protected Integer m_templateId;  // if this is template identified
	}

	
	private HashMap<Integer, DataConfItemBase> m_itemKeyToDCItemHash;
	private HashMap<String, DataConfStruct> m_structNameToDefinitionHash;
}
