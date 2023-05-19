package com.cbhstudios.tools.flowedit;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Serializable;
import java.util.ArrayList;

// IPFixModel backed by a direct file

public class IPFixFileModel extends IPFixModel
{
	private IPFixFileModel() 
	{
		fileOpen = false;
	}
	
	public IPFixFileModel(String IPFixFilePath, ActionListener parentListener)
	{
		this();
		ipfFile = new IPFixFileStreamer(IPFixFilePath);
		eltListener = parentListener;		
	}
	
	public boolean InitRead() { return (open(false)); };
	public boolean InitWrite() { return (open(true)); };
	
	public void Reset() throws FloweditException 
	{
		if (fileOpen)
		{
			try
			{
				GetIPFixFile().CloseFSFile();
			}
			catch (FloweditException fEx)
			{
				throw fEx;
			}
			finally
			{
				fileOpen = false;
			}
		}
		else
		{
			throw new FloweditException("IPFix File not open");
		}
	}

	protected boolean open(boolean writable)
	{
		if (!fileOpen)
		{
			try
			{
				GetIPFixFile().OpenIPFixStream(writable);
				fileOpen = true;
			}
			catch (FloweditException feExc)
			{
				System.err.println("Unable to find or open file " + GetIPFixFile().GetName() + ": "
					+ feExc.getMessage());
			}
		}
		return fileOpen;
		
	}

	public boolean Checkpoint() throws FloweditException
	{
		try
		{
			if (fileOpen)
			{
				GetIPFixFile().SaveFSFile();				
			}
			else
			{
				throw new FloweditException("IPFix File not open");				
			}
		}
		catch (FloweditException fEx)
		{
			throw fEx;
		}
		
		return true;
	}
	
	public IPFixFileStreamer GetIPFixFile () { return ipfFile; };
	
	protected ActionListener eltListener;

	private IPFixFileStreamer ipfFile;
	private Boolean fileOpen;

}
