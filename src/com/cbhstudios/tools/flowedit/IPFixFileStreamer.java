/**
 * 
 */
package com.cbhstudios.tools.flowedit;

import java.io.File;
import java.io.IOException;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.ByteArrayOutputStream;

/**
 * @author wtackabury
 *
 *  Lowest interface to file system for file operations on IPFix file.
 *  
 *  The implementation is a bit unique in that the "write" stream is buffered internally
 *  until an operation to flush it to file is performed.  This is because of a need to 
 *  be able to do checksums on save (currently unimplemented).
 */
public class IPFixFileStreamer extends IPFixStreamer {
	IPFixFileStreamer(String fileName) { fsName = fileName; };
	
	@Override
	public void OpenIPFixStream(Boolean writable) throws FloweditException 
	{
		fsFile = new File(GetName());
		
		if ((! writable) &&  !( fsFile.exists()))
		{
			throw new FloweditException("File not found.");
		}
		
		try
		{
			if (writable)
			{
				// we create a byte array output stream, since we have to have the entire
				// IPFix stream written before 
				wStreamObject = new ByteArrayOutputStream();
				rStreamObject = null;
			}
			else
			{
				rStreamObject = new FileInputStream(fsFile);
				wStreamObject = null;
			}
		}
		catch (Exception ex)
		{
			throw new FloweditException("OpenIPFixStream: " + ex.getMessage());
		}
		s_offset = 0;
	}
	
	@Override
	public Integer ReadIPFixFileElement(IPFixFileElement fsElement) throws FloweditException 
	{ 
		try
		{
			return fsElement.Read(this);
		}
		catch (FloweditException fEx)
		{
			throw fEx;
		}
	}
	
	public void SaveFSFile() throws FloweditException
	{
		// This is where the byte array output stream which has been collected to this point
		// is written to the file.  A stream is maintained so as to be able to perform 
		// checksums across the entire byte array
		
		try
		{
			FileOutputStream wfsFileObject = new FileOutputStream(fsFile);
			wfsFileObject.write(this.GetOutputBuffer());
			wfsFileObject.close();
		}
		catch (IOException fnfEx)
		{
			throw new FloweditException("Error on save of file " + fsFile.getAbsolutePath() + 
						": " + fnfEx.getLocalizedMessage());
			
		}
	}
	
	public void CloseFSFile() throws FloweditException
	{ 
		if (null != rStreamObject)
		{
			try
			{
				rStreamObject.close();				
			}
			catch (IOException ioEx)
			{
				throw new FloweditException ("Closing input file : ", ioEx);
			}
		}
		fsFile = null; 
	}

	@Override
	public Integer WriteIPFixFileElement(IPFixFileElement fsElement) throws FloweditException 
	{ 
		try
		{
			return fsElement.Write(this);
		}
		catch (FloweditException fEx)
		{
			throw fEx;
		}
	}
		
	private  byte [] GetOutputBuffer()
	{
		ByteArrayOutputStream ipfsWStream = (ByteArrayOutputStream) GetWritableStream();
		return ipfsWStream.toByteArray();
	}
	
	@Override
	public String toString() { return "IPFix File Stream: " + fsName; };

	public String GetName() { return ((null == fsFile) ? fsName : fsFile.toString()); };
	private String fsName;
	private File fsFile;
	

}
