package com.cbhstudios.tools.flowedit;

import java.math.BigInteger;
import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;

import javax.lang.model.element.UnknownElementException;
import javax.xml.bind.DatatypeConverter;

import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import com.cbhstudios.tools.flowedit.IPFixDataTemplate.IPFixInfoElementTemplate;
import com.cbhstudios.tools.flowedit.IPFixXMLDataConf.SimpleDataConfType;
import com.cbhstudios.tools.flowedit.IPFixXMLDataConf.DataEncoding;

public final class IPFixXMLUtils {
	private IPFixXMLUtils() { };
	

	public static Integer attrIntValueOrDefault(Element eAttributeContainer, String attrName, int iDefault)
	{
		Integer iAttr = new Integer(eAttributeContainer.getAttribute(attrName));
		if (iAttr.toString().isEmpty())
		{
			iAttr = 0;
		}
		
		return iAttr;	
	}
	
	
	public static Element getFirstNodeThatIsElementFrom(NodeList nl)
	{
		for (int i = 0; i < nl.getLength(); i++)
		{
			if (nl.item(i).getNodeType() == Node.ELEMENT_NODE)
			{
				return (Element) nl.item(i);
			}			
		}
		return null;
	}
	public static byte [] ipfixElementDataEncoding (String xmlDataAttrPayload,
			IPFixInfoElementTemplate ifxIET, IPFixXMLDataConf xmlDataConf) throws FloweditException
	{
		byte [] convertedPayload = null;
	
		// we can dispatch based on the type we find in the DataConf.  Format a byte [] to 
		// contain the binary encoding, or string encoding (if the type is indeed a string).
		
		// look up value
		int elementKey = xmlDataConf.DataItemKey(ifxIET);
		// extract data type
		
		SimpleDataConfType elItemType = xmlDataConf.GetItemDataType(elementKey);
		int itemRadix = 0;
		
		// Check for item type against template encoding.  If it doesn't match, warn, and revert to this
		// being an unknown element.
		
		// TODO: Need to convert for arbitrarily counted values in template
		
		if ((SimpleDataConfType.UNKNOWN != elItemType) &&
				(IPFixXMLDataConf.PresumedTemplateSizeFromConfType(elItemType) !=
						ifxIET.get_elementSize()))
		{
			// if this is a string, then anything goes...
			if (IPFixInfoElementTemplate.ELEMENT_SIZE_VARIABLE != IPFixXMLDataConf.PresumedTemplateSizeFromConfType(elItemType))
			{
				System.err.println("Note: template definition for element " + elementKey + " reflects length of " + 
					ifxIET.get_elementSize() + "; our configuration shows a proper length of " +
					IPFixXMLDataConf.PresumedTemplateSizeFromConfType(elItemType));
				// 	revert the type to unknown, let the template "win" here
				elItemType = SimpleDataConfType.UNKNOWN;
			}
		}
						
		
		if (SimpleDataConfType.UNKNOWN == elItemType)
		{
			// If we haven't got a type definition here, we need to do our best at inferencing what we have
			// been passed for encoding.
			//
			// this requires execution a best-effort heuristic at mostly figuring out whether the string
			// as passed should be interpreted as string data or binary data.  Without a data dictionary
			// (i.e., this should have an IPFixFields.conf or equivalent) this is just a best effort.  That
			// dictionary encoding will be introduced into this over time, I expect this is largely for
			// standard IEs anyways.
			//

			if (xmlDataAttrPayload.matches("^-?([0-9A-Fa-f]+)$"))
			{
				switch (ifxIET.get_elementSize())
				{
				case 1:
					elItemType = SimpleDataConfType.UNSIGNED8;
					break;
					
				case 2:
					elItemType = SimpleDataConfType.UNSIGNED16;
					break;
					
				case 4:
					elItemType = SimpleDataConfType.UNSIGNED32;
					// could also be float here, but the preponderance is uns32's.
					break;
					
				case 8:
					elItemType = SimpleDataConfType.UNSIGNED64;
					break;
					
				case 16:
					elItemType = SimpleDataConfType.UNSIGNED128;
					break;
					
				case IPFixInfoElementTemplate.ELEMENT_SIZE_VARIABLE:
					elItemType = SimpleDataConfType.STRING;
					break;

				default:
					throw new FloweditException("ipfixElementDataEncoding: unexpected inferred element size: " + 
							ifxIET.get_elementSize());
				}

				// Contrary to Java 7 docs, I find in this context, regex support a) only matches entire
				// string patterns, doesn't imply substring matching like Perl, and b) few if any of the
				// predefined character classes (certainly those for Posix) are supported.  All we want
				// to see is if there is a hex digit > 0x09 in the payload--which is obviously a pretty
				// bad way to infer hex anyways.
				//
				if (xmlDataAttrPayload.matches("^(.*[A-Fa-f].*)$"))
				{
					// this is hex, evidently
					itemRadix = 16;
				}
				else  // all decimal (well, that's a folly, but we don't find any hex-only numbers)
				{
					itemRadix = 10;
				}
			}
			else
			{
				elItemType = SimpleDataConfType.STRING;
			}
		}
		else  // item was in our configuration.  Let's pull up the radix, if any.
		{
			switch (xmlDataConf.GetItemDataEncoding(elementKey))
			{
			case DECIMAL:
				itemRadix = 10;
				break;
				
			case HEX:
				itemRadix = 16;
				break;
			}
		}
		
		// compute max precision, length for numeric types, just copy the string for a string type,
		// and, um, floats not supported, yet.
		int iMaxPrec = 0;
		switch (elItemType)
		{
		case SIGNED32:
		case UNSIGNED32:
			iMaxPrec = OctetSize.Integer();
			break;
			
		case UNSIGNED8:
			iMaxPrec = OctetSize.Byte();
			break;
			
		case UNSIGNED16:
			iMaxPrec = OctetSize.Short();
			break;
			
		case UNSIGNED64:
			iMaxPrec = OctetSize.Long();
			break;
			
		case UNSIGNED128:
			iMaxPrec = OctetSize.LongLong();
			break;
		
		case FLOAT:
			iMaxPrec = OctetSize.Float();
			break;
			
		case STRING:
			if (DataEncoding.HEX == xmlDataConf.GetItemDataEncoding(elementKey))
			{
				convertedPayload = IPFixXMLByteArrayEncoding(xmlDataAttrPayload);
			}
			else
			{
				convertedPayload = xmlDataAttrPayload.getBytes();
			}
			// fall thru
		default:
			iMaxPrec = 0;
			break;			
		}
				
		// copy out the bits to the precision into byte buffer
		if (iMaxPrec != 0)
		{		
			// look for alternate semantic translations from payload
			if (DataEncoding.IPV4ADDR == xmlDataConf.GetItemDataEncoding(elementKey))
			{
				convertedPayload =  ipv4DataElementEncoding(xmlDataAttrPayload, elementKey);
			}
			else if (DataEncoding.IPV6ADDR == xmlDataConf.GetItemDataEncoding(elementKey))
			{
				convertedPayload =  ipv6DataElementEncoding(xmlDataAttrPayload, elementKey);
			}			
			else if (0 == itemRadix)
			{
				// if we get here and don't have a radix to work with, something is wrong.  Assertion...
				throw new FloweditException("ipfixElementDataEncoding : no radix to work with for " +
						"presumed numeric item " + elementKey);
			}
			else
			{
				convertedPayload = ipFixNumericPayloadConversion(itemRadix, iMaxPrec,
							xmlDataAttrPayload); 
			}
		}
		else
		{
			if (null == convertedPayload)  // if we haven't figured this out through some other means
			{
				convertedPayload = xmlDataAttrPayload.getBytes();
			}
		}
		
		return convertedPayload;
	}
	
	public static byte [] ipFixNumericPayloadConversion(int iRadix, int iPrecision, String sPayload)
	{
		int i;
		int j;
		ByteBuffer bb = ByteBuffer.allocate(iPrecision);
		byte [] convertedNumericPayload = bb.array();

		// +++wayne TODO: This is purely little endian...need to work with if, e.g.,
		// running on Power systems
		
		BigInteger bi = new BigInteger(sPayload, iRadix);
		byte [] attrPayloadBytes = bi.toByteArray();
		// we have to concern ourselves with a possible need for leading zero bytes
		for (i = iPrecision, j = attrPayloadBytes.length; i > 0; i--, j--)
		{
			// are we still copying from the converted "attrPayloadBytes"?
			if (j > 0)
			{
				convertedNumericPayload[i-1] = attrPayloadBytes[j-1];
			}
			else
			{
				// we're zero-filling
				convertedNumericPayload[i-1] = (byte) 0;
			}
		}
		return convertedNumericPayload;
	}
	// Based on in-IPFIX-binary-file encoding (as represented by ipfixModelElementEncoding), return the string
	// which would store in the flowedit XML file for this item.
	//
	public static String ipfixElementXMLStringEncoding (ByteBuffer ipfixModelElementEncoding, 
			IPFixInfoElementTemplate ifxIET, IPFixXMLDataConf xmlDataConf) throws FloweditException
	{
		String xmlDataEncoding = null;

		// see if we can induce presence in the IPFix data conf
		int infoItemDCKey = -1;
		try
		{
			infoItemDCKey = xmlDataConf.DataItemKey(ifxIET);
		}
		catch (UnknownElementException ueEx)
		{
			// nothing..let defaulting code handle this
		}
		
		if (-1 != infoItemDCKey)
		{
			// we can let the data conf handle this for all cases requiring conversion, in some
			// cases handling conversion right here.

			if (DataEncoding.IPV4ADDR == xmlDataConf.GetItemDataEncoding(infoItemDCKey) || 
					DataEncoding.IPV6ADDR == xmlDataConf.GetItemDataEncoding(infoItemDCKey))
			{
				xmlDataEncoding = IPFixXMlIpAddrEncoding(ipfixModelElementEncoding.array(), infoItemDCKey);
			}
			else if (DataEncoding.HEX == xmlDataConf.GetItemDataEncoding(infoItemDCKey))
			{
				xmlDataEncoding = IPFixXMLHexStringEncoding(ipfixModelElementEncoding.array());
			}
			// we have to handle each of the unsigned cases.  These aren't well handled with Java data
			// conversions, since we have to "bump up" to the next-biggest size to be able to get around
			// the otherwise non-defeatable need for Java sign extension.
			//
			// We also need to bail on our presumption of length encoding if the template is actually providing this
			// as a nonstandard length.
			
			// TODO: Google Guava libs have some built in byte buffer functions to solve this....
			else
			{
				switch (xmlDataConf.GetItemDataType(infoItemDCKey))
				{
				case UNSIGNED8:
					if (OctetSize.Byte() == ipfixModelElementEncoding.capacity())
					{
						Short ub = new Short ( (short) (ipfixModelElementEncoding.get() & 0xff));
						xmlDataEncoding = new String(ub.toString());
					}
					break;
					
				case UNSIGNED16:
					if (OctetSize.Short() == ipfixModelElementEncoding.capacity())
					{
						Integer us = new Integer ((int) ipfixModelElementEncoding.getShort() & 0xffff);
						xmlDataEncoding = new String(us.toString());
					}
					break;

				case FLOAT:
					if (OctetSize.Float() == ipfixModelElementEncoding.capacity())
					{
						Float fl = new Float (ipfixModelElementEncoding.getFloat());
						xmlDataEncoding = new String(fl.toString());
					}
					break;
					
				case UNSIGNED32:
					if (OctetSize.Integer() == ipfixModelElementEncoding.capacity())
					{
						Long ui = new Long (ipfixModelElementEncoding.getInt() & 0xffffffffL);
						xmlDataEncoding = new String(ui.toString());
					}
					break;

				case UNSIGNED64:
				case UNSIGNED128:  // NB!  We have already handled the case of the ipv6 addr above.
					BigInteger ul = new BigInteger(1, ipfixModelElementEncoding.array());
					xmlDataEncoding = new String(ul.toString());
					break;					
					
				}
			}
			
			
		}

		// if we've not come up with anything yet....
		if (null == xmlDataEncoding)
		{
			int eSize = ifxIET.get_elementSize();
			
			
			if (  OctetSize.Byte() == eSize)
			{
				Byte b = new Byte (ipfixModelElementEncoding.get());
				xmlDataEncoding = new String(b.toString());
			}
			else if (OctetSize.Short() == eSize)
			{
				Short s = new Short (ipfixModelElementEncoding.getShort());
				xmlDataEncoding = new String(s.toString());
			}
			else if (OctetSize.Integer() == eSize)
			{
				Integer i = new Integer (ipfixModelElementEncoding.getInt());
				xmlDataEncoding = new String(i.toString());						
			}
			else if (OctetSize.Long() == eSize)
			{
				Long l = new Long (ipfixModelElementEncoding.getLong());
				xmlDataEncoding = new String(l.toString());						
			}
			else if (OctetSize.LongLong() == eSize)
			{
				BigInteger l1 = new BigInteger(1, ipfixModelElementEncoding.array());
				xmlDataEncoding = new String(l1.toString());
			}
			else if (ifxIET.get_elementSize() == IPFixInfoElementTemplate.ELEMENT_SIZE_VARIABLE)
			{
				xmlDataEncoding = new String(ipfixModelElementEncoding.array());
			}
			else
			{
				// hex string. 
				xmlDataEncoding = IPFixXMLHexStringEncoding(ipfixModelElementEncoding.array());
			}

		}
		
		return xmlDataEncoding;
	}
	
	public static byte ipFixListSemanticCode(String xmlSemanticValue)
	{
		int iSem;
		
		// straight out of RFC 6313, Appendix A
		
		switch (xmlSemanticValue)
		{
		case "noneOf":
			iSem = 0;
			break;
		
		case "exactlyOneOf":
			iSem = 1;
			break;
			
		case "oneOrMoreOf":
			iSem = 2;
			break;
			
		case "allOf":
			iSem = 3;
			break;
			
		case "ordered":
			iSem = 4;
			break;
			
		default:
			iSem = 255;  // the "undefined" case
		}
		
		return (byte) iSem;
	}
	
	public static String ipFixListSemanticString (byte bSemanticValue)
	{
		String sSemantic;
		
		switch (bSemanticValue)
		{
		case 0:
			sSemantic = new String ("noneOf");
			break;
			
		case 1:
			sSemantic = new String ("exactlyOneOf");
			break;
			
		case 2:
			sSemantic = new String ("oneOrMoreOf");
			break;
			
		case 3:
			sSemantic = new String ("allOf");
			break;
			
		case 4:
			sSemantic = new String ("ordered");
			break;
			
		default:
			sSemantic = new String ("unknown");
			break;
		}
		
		return sSemantic;
	}


	private static byte [] ipv4DataElementEncoding(String xmlDataAttrPayload, int elementKey) throws FloweditException
	{
		try
		{
			// really should validate the IPv4 address here...
			
			Inet4Address addrFromPayload = (Inet4Address) InetAddress.getByName(xmlDataAttrPayload);
			return addrFromPayload.getAddress();
		}
		catch (UnknownHostException uhEx)
		{
			throw new FloweditException("Unable to convert IP address from XML payload " + xmlDataAttrPayload);
		}
		
	}

	//  TODO: Really could combine all but one line of this with ipv4DEataElementEncoding
	
	private static byte [] ipv6DataElementEncoding(String xmlDataAttrPayload, int elementKey) throws FloweditException
	{
		try
		{
			// really should validate the IPv4 address here...
			
			Inet6Address addrFromPayload = (Inet6Address) InetAddress.getByName(xmlDataAttrPayload);
			return addrFromPayload.getAddress();
		}
		catch (UnknownHostException uhEx)
		{
			throw new FloweditException("Unable to convert IP (v6) address from XML payload " + xmlDataAttrPayload);
		}
		
	}

	private static String IPFixXMlIpAddrEncoding(byte [] rawDataEncoding, int elementKey) throws FloweditException
	{
		try
		{
			InetAddress inAddr = InetAddress.getByAddress(rawDataEncoding);  // will determine if ipv4 or ipv6 based on array len
			return inAddr.getHostAddress();
		}
		catch (UnknownHostException uhEx)
		{
			throw new FloweditException ("Unable to convert IP address (type " + elementKey + 
					" from " + rawDataEncoding.toString());
		}
				
	}
	
	private static byte [] IPFixXMLByteArrayEncoding( String sHexData)
	{
		return DatatypeConverter.parseHexBinary(sHexData);
	}
	
	private static String IPFixXMLHexStringEncoding( byte [] rawDataEncoding)
	{
		return new String(DatatypeConverter.printHexBinary(rawDataEncoding));
	}

}