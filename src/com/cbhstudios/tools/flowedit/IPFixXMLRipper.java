/**
 * 
 */
package com.cbhstudios.tools.flowedit;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.io.IOException;

import org.apache.xerces.parsers.DOMParser;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.ErrorHandler;
import org.xml.sax.InputSource;

import com.cbhstudios.tools.flowedit.IPFixDataTemplate.IPFixInfoElementTemplate;
import com.cbhstudios.tools.flowedit.IPFixTemplateSet.TemplateSetType;
import com.cbhstudios.tools.flowedit.IPFixDataRecord.IPFixDataRecordDataValue;

/**
 * @author wtackabury
 *
 */
public class IPFixXMLRipper extends IPFixXMLProcessor {
	
	IPFixXMLRipper(String xmlFile)
	{
		m_inputFileName = xmlFile;
	}
	
	public IPFixModel ripIPFixXML() throws FloweditException
	{
		File fInput;
		int i;
		IPFixModel ipfModel = new IPFixModel();
	
		
		try
		{
			fInput = new File(m_inputFileName);
		}
		catch (Exception ex)
		{
			throw new FloweditException("Unable to open XML file " + m_inputFileName + ":", ex);
		}
		
		try
		{
			m_xmlParser.parse(new InputSource(new FileInputStream(fInput)));
			Document xDoc = m_xmlParser.getDocument();
			
			xDoc.getDocumentElement().normalize();  // BCP
			
			// get the data configuration we will use to guide ripping
			m_xmlDataConf = new IPFixXMLDataConf();
			
			if (!m_xmlDataConf.init())
			{
				throw new FloweditException("Unable to initialize XML data configuration");
			}
			
			// get a document element, and create its message-level attributes
			NodeList ipfMsgNodes = xDoc.getElementsByTagName("ipFixMessage");
			
			for ( i = 0; i < ipfMsgNodes.getLength(); i++)   // org.w3c.dom.NodeList isn't an Iterable, pfft
			{
				IPFixMessage ifxMsg = ripIPFixMessageFrom(ipfMsgNodes.item(i));
				ipfModel.AddToContainer(ifxMsg);
			}
			
		}
		catch (Exception ex)
		{
			throw new FloweditException("IPFixXMLRipper: unable to parse document: ", ex);
		}					
		return ipfModel;
	}

	
	protected IPFixMessage ripIPFixMessageFrom(Node ipfMsgNode) throws FloweditException
	{
		Element eMsgNode = (Element) ipfMsgNode;
		IPFixMessage ifxMsg = new IPFixMessage(IPFixXMLUtils.attrIntValueOrDefault(eMsgNode, "sequence", 0));

		ifxMsg.SetObservationDomain(IPFixXMLUtils.attrIntValueOrDefault(eMsgNode, "observationID", 1));
		ifxMsg.SetExportTime(IPFixXMLUtils.attrIntValueOrDefault(eMsgNode, "exportTime", 
				(int) System.currentTimeMillis() / 1000));
		
		// Need to cycle through the contained template sets, let them rip themselves
		NodeList ipfSetNodes = eMsgNode.getChildNodes();
		
		for (int i = 0; i < ipfSetNodes.getLength(); i++)
		{
			Node ipfSet = ipfSetNodes.item(i);
			
			switch (ipfSet.getNodeName())
			{
			case "templateSets":
				for (IPFixTemplateSet ipfTSet : ripIPFixTemplateSetsFromNode(ipfSet, ifxMsg))
				{
					ifxMsg.AddToContainer(ipfTSet);
				}
						
				break;
				
			case "dataSets":
				for (IPFixDataSet ipfDSet : ripIPFixDataSetsFromNode(ipfSet, ifxMsg))
				{
					ifxMsg.AddToContainedDataSets(ipfDSet);
				}
				break;
			}
		}

		return ifxMsg;
	}
	
	// This will rip both the template set and its consitutent templates.  Option and data templates
	// are interspersed in XML
	protected ArrayList<IPFixTemplateSet> ripIPFixTemplateSetsFromNode(Node ipfTSetsNode, IPFixMessage ifxMsg) throws FloweditException
	{
		ArrayList<IPFixTemplateSet> rippedTemplateSets = new ArrayList<IPFixTemplateSet>();
		
		NodeList ipfTemplateNodes = ipfTSetsNode.getChildNodes();

		for (int i = 0; i < ipfTemplateNodes.getLength(); i++)
		{
			IPFixTemplateSet ipfTSetObj = null;
			
			// filter out DOM metadata and cruft
			if (ipfTemplateNodes.item(i).getNodeType() != Node.ELEMENT_NODE)
			{
				continue;
			}
			
			Element ipfTemplateSetEl = (Element) ipfTemplateNodes.item(i);
		
		
			for (int j = 0; j < ipfTemplateSetEl.getChildNodes().getLength(); j++)
			{
				IPFixDataTemplate ipfDT = null;
				
				if (ipfTemplateSetEl.getChildNodes().item(j).getNodeType() != Node.ELEMENT_NODE)
				{
					continue;
				}

				Element ipfTemplateEl = (Element) ipfTemplateSetEl.getChildNodes().item(j);
				
				switch (ipfTemplateEl.getNodeName())
				{
				// +++waynet these constructors need to be much more ripper-friendly
				case "dataTemplate":
					if (null == ipfTSetObj)
					{
						// basic size: 2 header short values
						ipfTSetObj = new IPFixTemplateSet((short) 2, 2 * OctetSize.Short());
					}
					ipfDT = new IPFixDataTemplate(TemplateSetType.DATA_TEMPLATE_SET);				
					break;
					
				case "optionTemplate":
					if (null == ipfTSetObj)
					{
						// basic size: 2 header short values, plus field
						ipfTSetObj = new IPFixTemplateSet((short) 3, (2 * OctetSize.Short()) + OctetSize.Byte());
					}

					ipfDT = new IPFixDataTemplate(TemplateSetType.OPTION_TEMPLATE_SET);
					Short sSFieldCount = new Short (ipfTemplateEl.getAttribute("scopeFieldCount"));
					
					if (! sSFieldCount.toString().equals(""))
					{
						ipfDT.SetScopeFieldCount(sSFieldCount);
					}
					break;
					
				default:
					break;  // could be anything legit owing to organization, so just pass on it.
						
				}
	
				if (null != ipfDT)
				{
					Short sTemplateID = new Short (ipfTemplateEl.getAttribute("templateID"));
					ipfDT.SetTemplateId(sTemplateID);
					// record a correlation here between this template and ID for 
					// message-level lookup
					ifxMsg.RegisterTemplateMapping(sTemplateID, ipfDT);					

					// It's a bit  primitive here--we have to go back to rfc5655 and find 
					// that without any info elements at all
					// there are two shorts in an "empty" template set.
					// we know there's 4 bytes in the element info...

					// pick up templateElements into the template...					
					for (IPFixDataTemplate.IPFixInfoElementTemplate ipfInfoTmpl : 
						ripIPFixElementTemplateInfosFrom(ipfTemplateSetEl.getChildNodes().item(j), ipfDT))
					{
						ipfDT.AddToInfoElementList(ipfInfoTmpl);
					}	
					ipfTSetObj.AddToContainer(ipfDT);
				}
			}

			// pick up pad bytes into template set
			if (! ipfTemplateSetEl.getAttribute("padBytes").isEmpty())
			{
				Integer sPad = new Integer (ipfTemplateSetEl.getAttribute("padBytes"));
			
				if (! sPad.toString().equals(""))
				{
					ipfTSetObj.SetPadBytes(sPad);
				}
			}
			
			rippedTemplateSets.add(ipfTSetObj);
		}
		
		return rippedTemplateSets;
	}
	
	protected ArrayList<IPFixDataTemplate.IPFixInfoElementTemplate> ripIPFixElementTemplateInfosFrom
		(Node ipfTemplateNode, IPFixDataTemplate ipfDT)
		throws FloweditException
	{
		ArrayList<IPFixDataTemplate.IPFixInfoElementTemplate> rippedTemplateInfos = 
				new ArrayList<IPFixDataTemplate.IPFixInfoElementTemplate>();
		
		NodeList ipfTemplateInfoNodes = ipfTemplateNode.getChildNodes();

		for (int i = 0; i < ipfTemplateInfoNodes.getLength(); i++)
		{
			if (ipfTemplateInfoNodes.item(i).getNodeType() != Node.ELEMENT_NODE)
			{
				continue;
			}
			
			Element ipfTInfoEl = (Element) ipfTemplateInfoNodes.item(i);

			NodeList nl;
			Short tiElementID = null;
			// be sure to set element length as an Integer, since 65535 is a valid value and 
			// will cause underflow for a Java (irrepressibly signed) Short on assignment.
			// More on that in a second when we look at variable length elements.
			Integer tiElementLen = null;
			
			// length, id, and potentially administrative info
			nl = ipfTInfoEl.getElementsByTagName("elementLength");
			
			if (nl.getLength() > 0)
			{
				tiElementLen = new Integer(nl.item(0).getFirstChild().getNodeValue());
				// now, this Short/Integer kerfuffle we talked about above....if this has an 
				// assignment of 65536, it's come from an assignment of a variable length value, 
				// which is in fact spec'd to be -1.  So, we need to (sigh) convert it.
				if (65535 == tiElementLen)
				{
					tiElementLen = IPFixDataTemplate.IPFixInfoElementTemplate.ELEMENT_SIZE_VARIABLE;
				}

				nl = ipfTInfoEl.getElementsByTagName("elementID");
				if (nl.getLength() > 0)
				{
					tiElementID = new Short(nl.item(0).getFirstChild().getNodeValue());
				}
			}
			// hold over node list size to check for error
			if (nl.getLength() == 0)
			{
				throw new FloweditException("Required element for template info element missing");
			}
			
			IPFixDataTemplate.IPFixInfoElementTemplate ipfIET =	
					new IPFixDataTemplate.IPFixInfoElementTemplate(tiElementID, tiElementLen);
			
			nl = ipfTInfoEl.getElementsByTagName("administrativeID");
			
			if (nl.getLength() > 0)
			{
				ipfIET.set_isPENElement(true);
				ipfIET.setPeNumber(new Integer(nl.item(0).getFirstChild().getNodeValue()));
			}
			rippedTemplateInfos.add(ipfIET);
		}
		
		return rippedTemplateInfos;
	}
	protected ArrayList<IPFixDataSet> ripIPFixDataSetsFromNode (Node ipfDSetsNode, IPFixMessage ipfMsg) throws FloweditException
	{
		ArrayList<IPFixDataSet> alIPFDSet = new ArrayList<IPFixDataSet>();

		NodeList nlDataSets = ipfDSetsNode.getChildNodes();
		
		// These should all be instances of 'data set'
		for (int i = 0; i < nlDataSets.getLength(); i++)
		{
			if (nlDataSets.item(i).getNodeType() != Node.ELEMENT_NODE)
			{
				continue;
			}
			
			Element elDataSet = (Element) nlDataSets.item(i);
			
			// this is noted in theXML schema as optional, but I don't see how it 
			// could be at this moment
			Short dsSetID = new Short(elDataSet.getAttribute("setID"));
			
			IPFixDataSet ipfDSObj = new IPFixDataSet(ipfMsg, dsSetID);
			
			// if we can't correspond this to a template ID at the message level, then we won't get very far
			if (null == ipfDSObj.GetDefiningTemplate())
			{
				throw new FloweditException("Unable to resolve governing template for data set ID " + dsSetID.toString());
			}
			
			// now, need to find the padding attribute if present...
			if (elDataSet.hasAttribute("padBytes"))
			{
				ipfDSObj.SetPadBytes(new Integer(elDataSet.getAttribute("padBytes")));
			}
			
			
			for (IPFixDataRecord ipfDInstObj : ripIPFixDataInstancesFromNode(nlDataSets.item(i), ipfDSObj, 
						dsSetID, ipfMsg))
			{
				ipfDSObj.AddToContainer(ipfDInstObj);
				// amd for the template to keep track of...
				ipfDSObj.GetDefiningTemplate().RegisterCorrespondingDataRecord(ipfDInstObj);
			}
			alIPFDSet.add(ipfDSObj);
		}		
		return alIPFDSet;
	}
	
	protected ArrayList<IPFixDataRecord> ripIPFixDataInstancesFromNode(Node elDataSet, IPFixDataSet ipfDSet, 
			short setID, IPFixMessage ipfMsg)
		throws FloweditException
	{
		ArrayList<IPFixDataRecord> alDataInsts = new ArrayList<IPFixDataRecord>();
		
		NodeList nlDataInstances = elDataSet.getChildNodes();  // these should all be "dataInstance"
		
		for (int i = 0; i < nlDataInstances.getLength(); i++)
		{
			if (nlDataInstances.item(i).getNodeType() != Node.ELEMENT_NODE)
			{
				continue;
			}
			
			IPFixDataRecord ipfDRecObj = new IPFixDataRecord(ipfDSet.GetDefiningTemplate());
			// the "data record element" corresponds to the IPFixDataRecord.IPFixDataRecordDataValue
			NodeList nlDataInstValue = nlDataInstances.item(i).getChildNodes();
			
			// deal with walking the list of values in the dataRecordElement instances across the
			// declaration in the defining template
			

			for (int j = 0; j < nlDataInstValue.getLength(); j++)
			{
				if (nlDataInstValue.item(j).getNodeType() != Node.ELEMENT_NODE)
				{
					continue;
				}
				
				// this will be either "dataRecordElement" or "data list element"
				switch (nlDataInstValue.item(j).getNodeName())
				{
				case "dataRecordElement":
					ripIPFIXDataRecordFromNode(nlDataInstValue.item(j), ipfDRecObj, ipfDSet);
					break;
					
				case "dataListElement":
					ripIPFIXListRecordFromNode(nlDataInstValue.item(j), ipfDRecObj, ipfDSet, ipfMsg);
					break;
				}
				
			}
			
			if (!ipfDRecObj.GetDataValues().isEmpty())
			{
				alDataInsts.add(ipfDRecObj);
			}
		}
		return alDataInsts;
	}
	
	protected IPFixDataRecord ripIPFIXDataRecordFromNode(Node nDataNode, IPFixDataRecord ipfDRecObj,
			IPFixDataSet ipfDSet) throws FloweditException
	{
		Element dInstValue = (Element) nDataNode;

		IPFixInfoElementTemplate ifxIET = findMatchingTemplateElementToDataInstance(dInstValue,
				ipfDSet.GetDefiningTemplate());
		
		if (null == ifxIET)
		{
			throw new FloweditException("Missing definition for data element " + dInstValue.getNodeValue() + 
					" in template " + ipfDSet.GetDefiningTemplate().GetTemplateId());
		}
		else
		{
			
			// we have the item from which we can construct the element item
			byte [] sBInstItemVal;			
			// see if it uses a file to be able to pull the data in (particularly useful for binary)
			if (! dInstValue.getAttribute("dataFile").isEmpty())
			{
				try
				{
					System.out.println(new File( ".").getCanonicalPath());
					sBInstItemVal = Files.readAllBytes(new File(dInstValue.getAttribute("dataFile")).toPath());
				}
				catch (IOException ioEx)
				{
					throw new FloweditException("Unable to establish data value from file " + 
							dInstValue.getAttribute("dataFile") + ":  " + ioEx.getMessage());
				}
			}
			else
			{
				String sDInstItemVal = new String(
						dInstValue.getElementsByTagName("data").item(0).getFirstChild().getNodeValue());
				sBInstItemVal = IPFixXMLUtils.ipfixElementDataEncoding(sDInstItemVal, ifxIET, m_xmlDataConf);
			}
			IPFixDataRecordDataValue ipfDRecValue = new IPFixDataRecordDataValue(sBInstItemVal,	
					ifxIET.get_elementSize() == 
					IPFixDataTemplate.IPFixInfoElementTemplate.ELEMENT_SIZE_VARIABLE);
			ipfDRecObj.AddDataRecordValue(ipfDRecValue);
		}

		return ipfDRecObj;
				
	}
	
	protected IPFixDataRecord ripIPFIXListRecordFromNode(Node nDataNode, IPFixDataRecord ipfDRecObj,
			IPFixDataSet ipfDSet, IPFixMessage ipfMsg) throws FloweditException
	{
		Element dInstValue = (Element) nDataNode;

		// for lists, we get a semantic and a bit of a hint as to the list type before we dive
		// into the basic or templated list.

		NodeList nlElSemantic = dInstValue.getElementsByTagName("semantic");

		String sListType = new String(dInstValue.getAttribute("type"));

		
		if (nlElSemantic.getLength() == 0)
		{
			throw new FloweditException("Unable to find \'semantic\' field in list within " + ipfDSet.GetSetId().toString());
		}
		
		// we know that all list types have an element and admin ID...find the matching 
		// *list level* template item.
		IPFixInfoElementTemplate ifxIET = findMatchingTemplateElementToDataInstance(dInstValue,
				ipfDSet.GetDefiningTemplate());

		if (null == ifxIET)
		{
			throw new FloweditException("Missing definition for list element " + 
					" in template " + ipfDSet.GetDefiningTemplate().GetTemplateId());
		}
		
		// we are going to build up the in-list encoding as a byte sequence....the flowedit
		// model does not have object composition here.  A SWAG on the capacity required.
		ByteBuffer bbListEncoding = ByteBuffer.allocate(512);
		bbListEncoding.order(ByteOrder.BIG_ENDIAN);
		
		// We will set the length bytes in a moment, but will accumulate.  NOTE that for 
		// lists, the length value includes all list data but NOT the length bytes 
		// themselves.
		
		// slap down the semantic value...
		bbListEncoding.put(IPFixXMLUtils.ipFixListSemanticCode
				(nlElSemantic.item(0).getFirstChild().getNodeValue()));
		
		/** 
		sbListEncoding.putShort((short) 0x1234);
		sbListEncoding.putShort((short) 0x5678);
		System.out.println("Position is " + sbListEncoding.position());
		ByteBuffer sbListEncoding2 = ByteBuffer.allocate(sbListEncoding.position());
		sbListEncoding2.order(ByteOrder.BIG_ENDIAN);
		sbListEncoding2.put(sbListEncoding.array(), 0, sbListEncoding.position());
		IPFixDataRecordDataValue ipfDRecValueX = 
				new IPFixDataRecordDataValue(Arrays.copyOf(sbListEncoding.array(), sbListEncoding.position()), true);	
		ipfDRecObj.AddDataRecordValue(ipfDRecValueX);	
		
		return ipfDRecObj;
		*/


		// there is an inherent matching between the list type and what we fine below
		switch(sListType)
		{
		case "basic":
			bbListEncoding.put(encodeIPFixBasicList(dInstValue, ipfDSet));						
			break;
			
		case "subTemplate":
			bbListEncoding.put(encodeIPFixSubTemplateList(dInstValue,"subTemplateListData",
					ipfDSet, ipfMsg));
			break;
			
		case "subTemplateMulti":
			// variations on subTemplate
			bbListEncoding.put(encodeIPFixSubTemplateList(dInstValue,"subTemplateMultiListData",
					ipfDSet, ipfMsg));
			break;
			
		default:
			throw new FloweditException("unexpected list type attribute \'" + sListType +
					"\' in list within " + ipfDSet.GetSetId().toString());

		}
				
		// we need to account for the size of the list itself, which we've been accumulating.
		Short sEncodedListLen = new Short((short) bbListEncoding.position());
		
		ByteBuffer bbFullList = ByteBuffer.allocate(sEncodedListLen + OctetSize.Short() + OctetSize.Byte());
		bbListEncoding.order(ByteOrder.BIG_ENDIAN);
		
		// the "var length" byte
		bbFullList.put((byte) IPFixDataTemplate.IPFixInfoElementTemplate.ELEMENT_SIZE_VARIABLE);
		bbFullList.putShort(sEncodedListLen); 
		// now, for the rest of the list we've put together
		bbFullList.put(Arrays.copyOf(bbListEncoding.array(), sEncodedListLen));

		// and, add it to the data encoding itself.  We've just overridden the varlength stuff,
		// so mark it as not varlength.
		IPFixDataRecordDataValue ipfDRecValue = 
				new IPFixDataRecordDataValue(bbFullList.array(), false);	
		ipfDRecObj.AddDataRecordValue(ipfDRecValue);			
		
		return ipfDRecObj;  
	}
	
	private IPFixInfoElementTemplate findMatchingTemplateElementToDataInstance(Element eDataNode,
			IPFixDataTemplate ifxDTemp) throws FloweditException
	{
		// we will want to iterate the data template...
		Iterator<IPFixInfoElementTemplate> ifxIETitr =  
				ifxDTemp.GetInfoElementList().iterator();
		// pull up element ID (mandatory) and PEN (optional)
		NodeList nlElID = eDataNode.getElementsByTagName("elementID");
		
		if (nlElID.getLength() == 0)
		{
			throw new FloweditException("Unable to find \'element ID\' field in set " + ifxDTemp.GetTemplateId().toString());
		}
		Short sDInstItemID = new Short(nlElID.item(0).getFirstChild().getNodeValue());

		
		NodeList nDInstAdminID = null; 
		Integer iDInstAdminID = null;
		
		nDInstAdminID = eDataNode.getElementsByTagName("administrativeID");
						
		if (nDInstAdminID.getLength() != 0)
		{
			// this is optional, so don't deref it unless it's a real object ref
			iDInstAdminID = new Integer (nDInstAdminID.item(0).getFirstChild().getNodeValue());
		}

		/** we have to take into account that the iterator could be halfway through
		 	the sequence for the data template, therefore must have the ability to
		 	iterate to end, then back again.  We'll actually do to end, then through
		 	the list again.
		 */
		for (boolean bTraversedList = false; ; )
		{	
			while ( ifxIETitr.hasNext()) 
			{
				IPFixInfoElementTemplate ifxIET = ifxIETitr.next();
				
				if (ifxIET.getElementId().equals(sDInstItemID))
				{
					// We match on element ID....cool!  deal with the case where....
					// 1. the admin id is present in the template element, but not in the instance we just read
					// 2. there is no admin id in the template element, or it's a different admin element,
					// In these cases, continue on through the template, since the "matching" one may be yet to come.
					
					if (ifxIET.get_isPENElement() && null == iDInstAdminID)
					{
						continue;  // case (1) above
					}

					if ((!ifxIET.get_isPENElement()) || (iDInstAdminID.equals(ifxIET.getPeNumber()))) // happy path...
					{
						// match
						return ifxIET;
					}
					else
					{
						continue; // case (2) above
					}
				}
			}
			// we've gone through an iteration of the template items list.  Was it our "second" iteration?
			if (bTraversedList)
			{
				return null;
			}
			bTraversedList = true;
			// "reset" the iterator for a traversal from the top
			ifxIETitr =  
					ifxDTemp.GetInfoElementList().iterator();						
		}
	}
	
	private byte []  encodeIPFixBasicList(Element dInstValue,  IPFixDataSet ipfDSet) throws FloweditException
	{
		ByteBuffer bbBasicListEncoding = ByteBuffer.allocate(512);
		int i;

		bbBasicListEncoding.order(ByteOrder.BIG_ENDIAN);
		
		// look for basic list element here.
		NodeList nlBasicListData = dInstValue.getElementsByTagName("basicListData");
		if (0 == nlBasicListData.getLength())
		{
			listEncodingElementException("basicListData", ipfDSet);
		}

		Element eBasicListData = IPFixXMLUtils.getFirstNodeThatIsElementFrom(nlBasicListData);
		
		// now, let's just lay down the field and length
		NodeList nlFieldID = eBasicListData.getElementsByTagName("elementID");
		
		if (0 == nlFieldID.getLength())
		{
			listEncodingElementException("elementID", ipfDSet);
		}
		
		Short sListFieldElementID = new Short(nlFieldID.item(0).getFirstChild().getNodeValue());

		NodeList nlFieldPEN = eBasicListData.getElementsByTagName("administrativeID");
		Integer iFieldPEN = null;
		
		if (0 != nlFieldPEN.getLength())
		{
			iFieldPEN = new Integer(nlFieldPEN.item(0).getFirstChild().getNodeValue());
			sListFieldElementID = new Short((short) (sListFieldElementID.shortValue() | 0x8000));
		}
		// length....
		NodeList nlFieldLength = eBasicListData.getElementsByTagName("elementLength");
		
		if (0 == nlFieldLength.getLength())
		{
			listEncodingElementException("elementLength", ipfDSet);
		}
		
		Integer iFieldLength = new Integer(nlFieldLength.item(0).getFirstChild().getNodeValue());
		Short sFieldLength = new Short(iFieldLength.shortValue());
		
		// we 
		// now. lay the main fields down...
		bbBasicListEncoding.putShort(sListFieldElementID); 
		bbBasicListEncoding.putShort(sFieldLength); 

		if (null != iFieldPEN)
		{
			bbBasicListEncoding.putInt(iFieldPEN);
		}
		
		// now, for the field values themselves.  This is where we have to concern ourselves
		// with whether we have fixed or variable length items.
		
		NodeList nlBasicListItems = eBasicListData.getElementsByTagName("data");
		for ( i = 0; i < nlBasicListItems.getLength(); i++) 
		{
			Node nlBasicListItem = nlBasicListItems.item(i);

			String sBasicListItem = new String(nlBasicListItem.getFirstChild().getNodeValue());
			
			// this is when we worry about variable length.
			if (sFieldLength.intValue() == IPFixDataTemplate.IPFixInfoElementTemplate.ELEMENT_SIZE_VARIABLE)
			{
				// one byte length
				Integer iItemLen = new Integer(sBasicListItem.length());

				if (iItemLen > 255)
				{
					// "three byte" length -- 0xff plus two bytes len
					bbBasicListEncoding.put((byte) 0xff);
					bbBasicListEncoding.putShort(iItemLen.shortValue());
				}
				else
				{
					// one Byte Len
					bbBasicListEncoding.put(iItemLen.byteValue());					
				}
				// now, the string itself
				bbBasicListEncoding.put(sBasicListItem.getBytes());
			}
			else
			{
				// slap down another value, length is already specified
				bbBasicListEncoding.put(IPFixXMLUtils.ipFixNumericPayloadConversion(10, 
						iFieldLength.intValue(), sBasicListItem));
			}
		}			
		
		// make sure to truncate the byte buffer array for return
		return Arrays.copyOf(bbBasicListEncoding.array(), bbBasicListEncoding.position());
	}

	private byte []  encodeIPFixSubTemplateList(Element dInstValue, String sTopLevelNodeTag,
			IPFixDataSet ipfDSet, IPFixMessage ipfMsg) throws FloweditException
	{
		ByteBuffer bbSTListEncoding = ByteBuffer.allocate(512);
		int i, j;
		bbSTListEncoding.order(ByteOrder.BIG_ENDIAN);

		// look for basic list element here.
		NodeList nlSTListData = dInstValue.getElementsByTagName(sTopLevelNodeTag); 
		if (0 == nlSTListData.getLength())
		{
			listEncodingElementException(sTopLevelNodeTag, ipfDSet);
		}

		// Now, we will have either been called in the context of a subTemplate list,
		// or a subTemplateMultiList.  The fact we can accomodate multiple instances
		// of the child node (in nlSTListData) will work on both
		
		for (i = 0; i < nlSTListData.getLength(); i++)
		{
			Element eSTListData = (Element) nlSTListData.item(i).getFirstChild();
		
			// pull up the template ID
			NodeList nlFieldID = eSTListData.getElementsByTagName("templateID");
			
			if (0 == nlFieldID.getLength())
			{
				listEncodingElementException("templateID", ipfDSet);
			}
			
			Short sTemplateID = new Short(IPFixXMLUtils.getFirstNodeThatIsElementFrom(nlFieldID).getNodeValue());
			
			// create a flowedit IPFixDataSet based on that.
			IPFixDataSet ipfDSObj = new IPFixDataSet(ipfMsg, sTemplateID);
	
			if (null == ipfDSObj.GetDefiningTemplate())
			{
				throw new FloweditException("Unable to resolve governing list subelement template for template ID " + 
						sTemplateID.toString());
			}
			// lay down the template ID
			bbSTListEncoding.putShort(sTemplateID);
			
			// for each list element...
			NodeList nlSTListItems = eSTListData.getElementsByTagName("listElement");
			for ( j = 0; j < nlSTListItems.getLength(); j++) 
			{
				Element eSTListItem = IPFixXMLUtils.getFirstNodeThatIsElementFrom(nlSTListItems);
				
				//      find its template definition in the template using findMatchingTemplateElementToDataInstance
				IPFixInfoElementTemplate ifxIET = 
						findMatchingTemplateElementToDataInstance(eSTListItem, 
								ipfDSObj.GetDefiningTemplate());
				
				if (null == ifxIET)
				{
					throw new FloweditException("Ripping list data: Cannot find template " +
							sTemplateID.toString() + " element data definition");
				}
				//      extract the one data node
				
				NodeList nlSTListItemData = eSTListItem.getElementsByTagName("data");
				// there's only one, but obviously a necessary one
				if (0 == nlSTListItemData.getLength())
				{
					listEncodingElementException("subTemplateListData", ipfDSet);				
				}
				
				//      encode, using the fixed/varlength logic as found in the basic list case.
				String sListItemData = new String(IPFixXMLUtils.getFirstNodeThatIsElementFrom(nlSTListItemData).getNodeValue());
				if (IPFixDataTemplate.IPFixInfoElementTemplate.ELEMENT_SIZE_VARIABLE == ifxIET.get_elementSize())
				{
					Integer iItemLen = new Integer(sListItemData.length());
					// ++wayne what about instances > 1 byte in length?
					bbSTListEncoding.put(iItemLen.byteValue());
					// now, the string itself
					bbSTListEncoding.put(sListItemData.getBytes());
				}
				else
				{
					// length is already in the template, just lay down the data.
					bbSTListEncoding.put(IPFixXMLUtils.ipFixNumericPayloadConversion(10, 
							ifxIET.get_elementSize(), sListItemData));
				}
			}
		}
		
		// make sure to truncate to fit the return array
		return Arrays.copyOf(bbSTListEncoding.array(), bbSTListEncoding.position());
	}
	
	// this will always throw an exception
	private void listEncodingElementException(String sMissingNode, IPFixDataSet ipfDSet) 
			throws FloweditException
	{
			throw new FloweditException("Unable to find \'" + sMissingNode + 
					"\' field in list within " + ipfDSet.GetSetId().toString());			
	}
	
	protected final String m_inputFileName;

}
