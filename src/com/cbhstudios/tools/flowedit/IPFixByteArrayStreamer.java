/**
 * 
 */
package com.cbhstudios.tools.flowedit;

import java.io.ByteArrayOutputStream;

import org.apache.commons.lang3.exception.ExceptionUtils;

/**
 * @author wtackabury
 * 
 * This class allows the creation of a vehicle for send of IPFix Models onto the network.
 * Logically, that would pair with the ability to receive IPFixMessage sets in streams
 * from some other exporter, but that capability does not currently exist.  **to that extent,
 * read operations will currently throw an exception
 *
 */
public class IPFixByteArrayStreamer extends IPFixStreamer {

	/**
	 * 
	 */
	public IPFixByteArrayStreamer() 
	{
	}

	@Override
	public void OpenIPFixStream(Boolean writable) throws FloweditException 
	{
		if (writable)
		{
			wStreamObject = new ByteArrayOutputStream();
			rStreamObject = null;			
		}
		else
		{
			throw new FloweditException("IPFixByteArrayStreamer::OpenIPFixStream -- read from stream not currently supported");
		}
	}	
	
	 /* * (non-Javadoc)
	 * @see com.cbhstudios.tools.flowedit.IPFixStreamer#ReadIPFixFileElement(com.cbhstudios.tools.flowedit.IPFixFileElement)
	 */
	@Override
	Integer ReadIPFixFileElement(IPFixFileElement fsElement)
			throws FloweditException {
		// This is not supported
		throw new FloweditException("Read into IPFixByteArrayStreamer not currently supported: " + ExceptionUtils.getStackTrace(new Throwable("unsupported")));
	}

	/* (non-Javadoc)
	 * @see com.cbhstudios.tools.flowedit.IPFixStreamer#WriteIPFixFileElement(com.cbhstudios.tools.flowedit.IPFixFileElement)
	 */
	@Override
	Integer WriteIPFixFileElement(IPFixFileElement fsElement)
			throws FloweditException {
		// TODO Auto-generated method stub
		try
		{
			return fsElement.Write(this);
		}
		catch (FloweditException fEx)
		{
			throw fEx;
		}
	}
	
	public byte [] getOutputStream()
	{
		return getByteArrayStream().toByteArray();
	}
	
	private ByteArrayOutputStream getByteArrayStream()
	{
		return (ByteArrayOutputStream) GetWritableStream();
	}

}
