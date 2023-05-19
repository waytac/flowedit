package com.cbhstudios.tools.flowedit;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * @author wtackabury
 *
 *   This class allows the derivation of multiple types of "streamed" input and output subclasses,
 *   each allowing the input or distribution of the IPFix binary model to different media, be 
 *   that a file, a network send or receive, or the like.
 */

public abstract class IPFixStreamer {

	abstract Integer ReadIPFixFileElement(IPFixFileElement fsElement) throws FloweditException; 
	abstract Integer WriteIPFixFileElement(IPFixFileElement fsElement) throws FloweditException;
	
	abstract void OpenIPFixStream(Boolean writable) throws FloweditException;
	
	public Integer WriteIntToStreamer(Integer intData, int integerLength) throws FloweditException
	{
		try
		{
			// this really seems like overkill....but it works.
			ByteBuffer intBb = ByteBuffer.allocate(integerLength);
			intBb.order(ByteOrder.BIG_ENDIAN);

					// n.b, putChar will write two bytes, causing buffer overflow
			if (OctetSize.Byte() == integerLength) {intBb.put( (byte) (intData & 0xff)); }
			else  if (OctetSize.Short() == integerLength) {intBb.putShort( (short) (intData & 0xffff));}  
			else  if (OctetSize.Integer() == integerLength) {intBb.putInt( (intData));}
			else  if (OctetSize.Long() == integerLength) {intBb.putLong( (long) (intData));}
			else {throw new FloweditException("Cannot do a write of integer with size " + integerLength);}
			
			this.GetWritableStream().write(intBb.array());
			UpdateOffset(integerLength);
			return intData;
		}
		catch (IOException ioEx)
		{
			throw new FloweditException("Error writing integer value", ioEx);
		}
	}
	
	public Integer WriteByteArrayToStreamer(byte [] baData, int dataLength) throws FloweditException
	{
		try
		{
			this.GetWritableStream().write(baData, 0, dataLength);
			UpdateOffset(dataLength);
			return dataLength;
		}
		catch (IOException ioEx)
		{
			throw new FloweditException("Error writing string data of size " + dataLength, ioEx);
		}		
	}

	/* Routines to handle reads of given lengths and given types
	 * 
	 */
	
	// Reads up to a 4-byte integer
	
	public Integer ReadIntFromStreamer(int integerLength) throws FloweditException
	{
		try
		{
			byte [] dataArray = new byte[integerLength];
			GetReadableStream().read(dataArray, 0, integerLength);
			UpdateOffset(integerLength);
			
			// now, prepare int for return
			ByteBuffer dataBB = ByteBuffer.wrap(dataArray);
			// +++wayne handle endian concerns here:
			dataBB.order(ByteOrder.BIG_ENDIAN);

			if (OctetSize.Byte() == integerLength) { return (int) dataBB.get(); }
			else  if (OctetSize.Short() == integerLength) {return (int) dataBB.getShort();}  
			else  if (OctetSize.Integer() == integerLength) {return (int) dataBB.getInt();}
			else  if (OctetSize.Long() == integerLength) {return (int) dataBB.getLong();}
			else {throw new FloweditException("Cannot do a read of integer with size " + integerLength);}
			
		}
		catch (IOException ioEx)
		{
			throw new FloweditException("Error reading integer value", ioEx);
		}
	}
	
	public byte [] ReadByteArrayFromStreamer (int strDataLength) throws FloweditException
	{
		try
		{
			byte [] dataArray = new byte[strDataLength];
			GetReadableStream().read(dataArray, 0, strDataLength);
			UpdateOffset(strDataLength);
			
			return dataArray;					
		}
		catch (IOException ioEx)
		{
			throw new FloweditException("Error reading flow data", ioEx);
		}		
	}

	public Integer ReadableBytes () 
	{
		try
		{
			return GetReadableStream().available();
		}
		catch (IOException ioEx)
		{
			return 0;
		}
	}
		
	
	protected void UpdateOffset(int offsetBump) { s_offset += offsetBump; };
	public int getOffset() { return s_offset; }

	protected int s_offset;

	public OutputStream GetWritableStream() { return wStreamObject; }
	public InputStream GetReadableStream() { return rStreamObject; }
	
	protected OutputStream wStreamObject;
	protected InputStream rStreamObject;
	
	
}
