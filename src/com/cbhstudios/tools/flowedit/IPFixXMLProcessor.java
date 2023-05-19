package com.cbhstudios.tools.flowedit;

import org.apache.xerces.parsers.DOMParser;
import org.xml.sax.ErrorHandler;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;

public abstract class IPFixXMLProcessor implements ErrorHandler
{

	protected IPFixXMLProcessor () 
	{
		m_xmlParser = new DOMParser();
		m_xmlParser.setErrorHandler(this);		
	}
	
	@Override
	public void error(SAXParseException exception) throws SAXException {
		throw exception;
	}

	@Override
	public void fatalError(SAXParseException exception) throws SAXException {
		throw exception;
	}

	@Override
	public void warning(SAXParseException exception) throws SAXException {
		System.out.println("IPFix XML Processor: warning received, warning exception: " + 
				exception.getMessage());
	}
	
	protected DOMParser m_xmlParser;
	protected IPFixXMLDataConf m_xmlDataConf;
	
}
