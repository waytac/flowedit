/**
 *    
 */
package com.cbhstudios.tools.flowedit;

import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.nio.ByteBuffer;
import java.util.Iterator;
import java.util.NoSuchElementException;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.bind.DatatypeConverter;

import org.w3c.dom.bootstrap.DOMImplementationRegistry;
import org.w3c.dom.ls.DOMImplementationLS;
import org.w3c.dom.ls.LSOutput;
import org.w3c.dom.ls.LSSerializer;
import org.w3c.dom.DOMImplementation;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Text;
import org.apache.commons.lang3.CharEncoding;
import org.apache.xerces.parsers.DOMParser;
import org.xml.sax.ErrorHandler;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;

import com.cbhstudios.tools.flowedit.IPFixDataRecord.IPFixDataRecordDataValue;
import com.cbhstudios.tools.flowedit.IPFixDataTemplate.IPFixInfoElementTemplate;
import com.cbhstudios.tools.flowedit.IPFixTemplateSet.TemplateSetType;
import com.cbhstudios.tools.flowedit.IPFixXMLDataConf.CoarseDataType;
import com.cbhstudios.tools.flowedit.IPFixXMLDataConf.DataConfItemElementAggregate;
import com.cbhstudios.tools.flowedit.IPFixXMLDataConf.DataConfStruct;
import com.cbhstudios.tools.flowedit.IPFixXMLDataConf.DataConfStruct.DataConfStructFieldAttrs;


/**
 * @author wtackabury
 * 
 */
public class IPFixXMLBurner  extends IPFixXMLProcessor {

	// Support for creation of a XML file from an IPFix model.
	
	IPFixXMLBurner(IPFixModel inputIPFixModel, String outputFile)
	{
		if (null == outputFile)
		{
			m_xmlOutput = System.out;			
		}
		else
		{
			try
			{
				m_xmlOutput = new FileOutputStream(outputFile, false);
			}
			catch (FileNotFoundException fnf)
			{
				System.err.println("Unable to find/open " + outputFile + "; writing to stdout");
				m_xmlOutput = System.out;
			}
		}
		m_ipFixInput = inputIPFixModel;
	}
	
	public boolean burnIPFixXML() throws FloweditException
	{
		// get the data configuration we will use to guide burning the XML
		m_xmlDataConf = new IPFixXMLDataConf();
		
		if (!m_xmlDataConf.init())
		{
			throw new FloweditException("Unable to initialize XML data configuration");
		}		
		
		// Allocate and kick off the IPFixXMLElementBurner, which knows what the heck we're
		// creating here.  Create an internal document to represent the model.
		IPFixElementXMLBurner ipfElementBurner = new IPFixElementXMLBurner();
		ipfElementBurner.BurnIPFixFileModel(m_ipFixInput);
		
		// now, initiate the actual XML file write.
		try
		{
			// now, need to persist root element to the file.
			DOMImplementationRegistry bDDomReg = DOMImplementationRegistry.newInstance();

			DOMImplementationLS bDomImplLS = 
			    (DOMImplementationLS)bDDomReg.getDOMImplementation("LS");
			LSOutput outputWriter = bDomImplLS.createLSOutput();
			outputWriter.setEncoding(CharEncoding.UTF_8);
			outputWriter.setByteStream(m_xmlOutput);
			
			// create file with numan readability
			LSSerializer outputSerializer = bDomImplLS.createLSSerializer();
			outputSerializer.getDomConfig().setParameter("format-pretty-print",true);
			
			if (! outputSerializer.write(ipfElementBurner.getBurnedDocument(), outputWriter))
			{
				throw new FloweditException("XML Burner: error writing XML to document");
			}
		}
		catch (Exception ex) // too many to count in the L3 DOM invoke
		{
			throw new FloweditException("XML Burner: error writing XML to document: ", ex);
		}
		return true;
	}	
	
	protected OutputStream m_xmlOutput;
	protected final IPFixModel m_ipFixInput;
	protected String m_encoding;
	
	// let fatal error methods throw to the parser invocation, where they can be turned into
	// flowedit exceptions.
	
	@Override
	public void error(SAXParseException exception) throws SAXException {
		// TODO Auto-generated method stub
		throw exception;
		
	}
	@Override
	public void fatalError(SAXParseException exception) throws SAXException {
		// TODO Auto-generated method stub
		throw exception;
		
	}
	@Override
	public void warning(SAXParseException exception) throws SAXException {
		// TODO Auto-generated method stub
		System.err.println("IPFixXMLBurner parse warning: " + exception.getMessage());
		return;  // we handled it.
	}
	
	// This class knows about the schema of the XML file to be written.
	protected class IPFixElementXMLBurner
	{
		public IPFixElementXMLBurner()
		{
			m_storedException = null;
			
			// create a bare document
			DocumentBuilderFactory bDFac = DocumentBuilderFactory.newInstance();
			try
			{
				DocumentBuilder bDBuild = bDFac.newDocumentBuilder();
				DOMImplementation bDomImpl = bDBuild.getDOMImplementation();
				// particularly here, there might be a way to do this with less code, but 
				// I don't feel like sorting through to find it.
				m_burnerDocument = bDomImpl.createDocument(null, null, null);
				m_rootElement = m_burnerDocument.createElement("ipFixMessageSet");
				m_rootElement.setAttributeNS("http://www.w3.org/2001/XMLSchema-instance", 
						"xsi:noNamespaceSchemaLocation", "IPFixMessageSet.xsd");
				m_burnerDocument.appendChild(m_rootElement);
			}
			catch (ParserConfigurationException pCEx)
			{
				m_storedException = pCEx;
			}
			
		}
		
		public boolean BurnIPFixFileModel(IPFixModel ipfModel) throws FloweditException
		{
			// start by seeing if we have a deferred exception to throw from construction
			if (null != m_storedException)
			{
				throw new FloweditException("IPFix Model Burner: ", m_storedException);
			}
			
			for (IPFixMessage ipfMsg : ipfModel.GetContainedRecordList())
			{
				BurnIPFixMessage(ipfMsg, m_rootElement);
			}
			
			
			return true;
		}

		private boolean BurnIPFixMessage(IPFixMessage ipfMsg, Element parent) throws FloweditException
		{
			Element elementMsg = CreateNewElement("ipFixMessage");
			
			elementMsg.setAttribute("exportTime", ipfMsg.GetExportTime().toString());
			elementMsg.setAttribute("observationID", ipfMsg.GetObservationDomain().toString());
			elementMsg.setAttribute("sequence", ipfMsg.GetSequenceNumber().toString());
			parent.appendChild(elementMsg);
			
			if (! ipfMsg.GetContainedRecordList().isEmpty())
			{
					
				// walk template sets
				Element elementTmpls = CreateNewElement("templateSets");
				
				for (IPFixTemplateSet ipfTempl : ipfMsg.GetContainedRecordList())
				{
					BurnIPFixTemplateSet(ipfTempl, elementTmpls);
				}
				elementMsg.appendChild(elementTmpls);
			}			

			// now for the data sets....if present
			
			if (! ipfMsg.GetContainedDataSets().isEmpty())
			{
				Element elementDatas = CreateNewElement("dataSets");
				
				for (IPFixDataSet ipfDSet : ipfMsg.GetContainedDataSets())
				{
					BurnIPFixDataSet(ipfDSet, elementDatas);
				}
				elementMsg.appendChild(elementDatas);
			}
			return true;
		}

		private boolean BurnIPFixTemplateSet(IPFixTemplateSet ipfTempSet, Element parent) throws FloweditException
		{
			Element elementThisTemplateSet = CreateNewElement("templateSet");
			
			if (0 != ipfTempSet.GetSetPadBytes())
			{
				elementThisTemplateSet.setAttribute("padBytes", ipfTempSet.GetSetPadBytes().toString());
			}
			
			parent.appendChild(elementThisTemplateSet);
			
			// note that in the IPFix model, all templates are just known as data templates...option template
			// is an internal artifact which only really differs by scope.
			for (IPFixDataTemplate ipfTempl : ipfTempSet.GetContainedRecordList())
			{
				if (TemplateSetType.DATA_TEMPLATE_SET == ipfTempl.GetTemplateType())
				{				
					BurnIPFixDataTemplate(ipfTempl, elementThisTemplateSet);
				}
				else
				{
					BurnIPFixOptionTemplate(ipfTempl, elementThisTemplateSet);
				}
				
			}
			return true;
		}

		private boolean BurnIPFixDataTemplate(IPFixDataTemplate ipfTempl, Element parent) throws FloweditException
		{
			Element eltDTemp = CreateNewElement("dataTemplate");
			eltDTemp.setAttribute("templateID", ipfTempl.GetTemplateId().toString());
			parent.appendChild(eltDTemp);
			
			// walk the id/length pairs here...
			for (IPFixDataTemplate.IPFixInfoElementTemplate ipfEltTempl : ipfTempl.GetTemplatedElements())
			{
				BurnIPFixTemplatedElement(ipfEltTempl, eltDTemp);
			}
			return true;
		}

		private boolean BurnIPFixOptionTemplate(IPFixDataTemplate ipfTempl, Element parent) throws FloweditException
		{
			Element eltOTemp = CreateNewElement("optionTemplate");
			eltOTemp.setAttribute("templateID", ipfTempl.GetTemplateId().toString());
			eltOTemp.setAttribute("scopeFieldCount", ipfTempl.GetOptionScopeFieldCount().toString());
			parent.appendChild(eltOTemp);

			// walk the id/length pairs here...
			for (IPFixDataTemplate.IPFixInfoElementTemplate ipfEltTempl : ipfTempl.GetTemplatedElements())
			{
				BurnIPFixTemplatedElement(ipfEltTempl, eltOTemp);
			}
			
			return true;
		}

		private boolean BurnIPFixTemplatedElement(IPFixDataTemplate.IPFixInfoElementTemplate ipfTemplElt, 
					Element parent) throws FloweditException
		{
			// create new node
			Element eltTemplatedIPFElt = CreateNewElement("templateElement");
			parent.appendChild(eltTemplatedIPFElt);

			// need to watch for a negative encoding here--not schema compliant
			Integer iELen = new Integer(ipfTemplElt.get_elementSize());
			if (iELen == -1)
			{
				iELen = 65535;
			}
			Element eltLen = CreateNewElementAndValue("elementLength", iELen.toString()); 
			eltTemplatedIPFElt.appendChild(eltLen);
			Element eltID = CreateNewElementAndValue("elementID", 
					ipfTemplElt.getElementId().toString());
			eltTemplatedIPFElt.appendChild(eltID);
			
			if (ipfTemplElt.get_isPENElement())
			{
				Element eltPEN = CreateNewElementAndValue("administrativeID", 
						ipfTemplElt.getPeNumber().toString());
				eltTemplatedIPFElt.appendChild(eltPEN);				
			}

			return true;
		}

		private boolean BurnIPFixDataSet(IPFixDataSet ipfDSet, Element parent) throws FloweditException
		{
			// we have a data set, and have to walk the records within.  
			Element elIpfDataSet = CreateNewElement("dataSet");
			// set pad byte and set id attrs on it...
			elIpfDataSet.setAttribute("setID", ipfDSet.GetSetId().toString()); 
					
			if (0 != ipfDSet.GetPadBytes())
			{
				elIpfDataSet.setAttribute("padBytes", ipfDSet.GetPadBytes().toString());
			}
			parent.appendChild(elIpfDataSet);
			
			// now. walk the data records
			
			for (IPFixDataRecord ipfDRec : ipfDSet.GetContainedRecordList())
			{
				BurnIPFixDataRecord(ipfDRec, elIpfDataSet);
			}
					
			return true;
		}

		private boolean BurnIPFixDataRecord(IPFixDataRecord ipfRec, Element parent) throws FloweditException
		{

			// need to walk the discrete instances here....the governing template values must be iterated
			// as well, in sync
			Iterator<IPFixInfoElementTemplate> templatedElementListIterator = 
					ipfRec.GetGoverningTemplate().GetInfoElementList().iterator();
			// set main node....
			Element elIpfDataRec = CreateNewElement("dataInstance");
			parent.appendChild(elIpfDataRec);


			for (IPFixDataRecord.IPFixDataRecordDataValue ipfDVal : ipfRec.GetDataValues())
			{
				IPFixInfoElementTemplate ipfDTemp;
				try
				{
					ipfDTemp = templatedElementListIterator.next();
					
					// need to make a determination as to whether this is a record element, or a 
					// RFC 6313 list.

					int confItemKey = m_xmlDataConf.DataItemKey(ipfDTemp);
					switch (m_xmlDataConf.GetOverallDataType(confItemKey))
					{
					case SIMPLE:
						
						Element elDRecEl = CreateNewElement("dataRecordElement");

						String sElID = ipfDTemp.getElementId().toString();

						Element dRecElID = 
								CreateNewElementAndValue("elementID", ipfDTemp.getElementId().toString());
						elDRecEl.appendChild(dRecElID);
						
						if (ipfDTemp.get_isPENElement())
						{
							Element dRecElPEN = CreateNewElementAndValue("administrativeID",
									ipfDTemp.getPeNumber().toString());
							elDRecEl.appendChild(dRecElPEN);
						}
						
						ByteBuffer bbDEltValue = ipfDVal.GetDataValue();
	
						String sDRecElValue = IPFixXMLUtils.ipfixElementXMLStringEncoding(bbDEltValue, ipfDTemp, 
								m_xmlDataConf);
						
						Element elDRecElValue = CreateNewElementAndValue("data",sDRecElValue); 
								// StringEscapeUtils.escapeXml(sDRecElValue));
						elDRecEl.appendChild(elDRecElValue);
						
						elIpfDataRec.appendChild(elDRecEl);
						break;
						
					case AGGREGATE:
						Element elDListEl = CreateNewElement("dataListElement");
						BurnIPFixListRecord(elDListEl, ipfDTemp, 
									new IPFixDataRecord.IPFIXDataRecordList(ipfDVal));
						elIpfDataRec.appendChild(elDListEl);
						break;
						
					default:
						throw new FloweditException("XMLBurner: Found inexplicable Coarse data type " + 
								m_xmlDataConf.GetOverallDataType(confItemKey) + " for template item " + 
								ipfDTemp.getElementId() + " (admin id " + ipfDTemp.getPeNumber() + ")");
					}
				}
				catch (NoSuchElementException nseEx)
				{
					throw new FloweditException("XMLBurner: Lack of corresponding template value in burning value for info element #");
				}
			}
			
			return true;
		}

		private void  BurnIPFixListRecord(Element elDListEl, IPFixInfoElementTemplate ipfDTemp, 
				IPFixDataRecord.IPFIXDataRecordList ipfDLVal) throws FloweditException
		{
			// This is where we interpret the data record to follow as a list element, 
			// burning the specialized list element XML along the way.
			int confItemKey = m_xmlDataConf.DataItemKey(ipfDTemp);

			try
			{
				// for now, all lists are of basic type
				elDListEl.setAttribute("type", "basic");
				
				DataConfItemElementAggregate listConfItem = 
					(DataConfItemElementAggregate) m_xmlDataConf.GetItemSchemaConfigObject(confItemKey);
				
				// now, let's find its structural definitions
				for (String  sDerivedType :	listConfItem.GetDerivedStructureTypes())
				{
					DataConfStruct listStructDef = 
							m_xmlDataConf.GetStructureConfigObject(sDerivedType);
					
				}				

				// now we can march through getting the items *in* the string.
				// start with the semantic which is in the instance.
				Element elSemantic = CreateNewElementAndValue("semantic",ipfDLVal.semanticStringValue()); 
				elDListEl.appendChild(elSemantic);
				// then the OVERALL list definition element
				Element elListElID = CreateNewElementAndValue("elementID", ipfDTemp.getElementId().toString());
				elDListEl.appendChild(elListElID);
				
				if (ipfDTemp.get_isPENElement())
				{
					Element elListPEN = CreateNewElementAndValue("administrativeID", 
							ipfDTemp.getPeNumber().toString());
					elDListEl.appendChild(elListPEN);					
				}
				
				// now, to create the basic list data
				Element elBasicList = CreateNewElement("basicListData");

				// append the descriptors for the list element
				elBasicList.appendChild( 
						CreateNewElementAndValue("elementID", 
								ipfDLVal.getBasicListElementMetadata().getElementId().toString())						
						);
				
				if (ipfDLVal.getBasicListElementMetadata().get_isPENElement())
				{
					elBasicList.appendChild( 
							CreateNewElementAndValue("administrativeID", 
									ipfDLVal.getBasicListElementMetadata().getPeNumber().toString())						
							);
					
				}

				if (ipfDLVal.getBasicListElementMetadata().get_isPENElement())
				{
					Integer iELen = ipfDLVal.getBasicListElementMetadata().get_elementSize();
					if (iELen == -1)
					{
						iELen = 65535;
					}
					elBasicList.appendChild( 
							CreateNewElementAndValue("elementLength", iELen.toString()));
					
				}

				// iterate values
				while (ipfDLVal.hasNext())
				{
					IPFixDataRecordDataValue iDRNext = ipfDLVal.next();
					String sLInstData = new String(iDRNext.GetDataValue().array(), "UTF-8");
					elBasicList.appendChild( 
							CreateNewElementAndValue("data", sLInstData));					
				}
				
				elDListEl.appendChild(elBasicList);									
			}
			catch (UnsupportedEncodingException usEEx)
			{
				return;
			}
			catch (ClassCastException cEx)
			{
				throw new FloweditException("XMLBurner: Cannot find mapping to struct from ItemKey " +
						confItemKey);

			}
		}

		private Element CreateNewElement(String sElTag)
		{
			return (m_burnerDocument.createElement(sElTag));
		}

		private Element CreateNewElementAndValue(String sElTag, String sElVal)
		{
			Element el = CreateNewElement(sElTag);
			Text tEl = m_burnerDocument.createTextNode(sElVal);
			el.appendChild(tEl);
			return el;
		}
		
		
		public Document getBurnedDocument () { return m_burnerDocument; }
		protected Document m_burnerDocument;
		protected Element m_rootElement;
		private Exception m_storedException;
	}
}
