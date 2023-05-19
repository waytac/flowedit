/**
 *   IPFixPCapFileTranslation : within "flowedit", does a conversion of a PCAP file to a 
 *   RFC 5655 file format. 
 */
package com.cbhstudios.tools.flowedit;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.jnetpcap.Pcap;
import org.jnetpcap.nio.JMemory;
import org.jnetpcap.packet.PcapPacket;
import org.jnetpcap.protocol.network.Ip4;
import org.jnetpcap.protocol.tcpip.Udp;


/**
 * @author wtackabury
 *
 */
public class IPFixPCapFileTranslation extends File {

	protected static final long serialVersionUID = -1L;
	
	// use temporary path
	IPFixPCapFileTranslation(String xlSource) throws IOException
	{
		super(Files.createTempFile(pathStrToFile(xlSource) + ".", ".bin").toString());
		pXlOutput = toPath();
		xlPCapSource = new File(xlSource);
		// guarantee output file goes poof at end of app execution
		deleteOnExit();
	}
	
	public int DoTranslation()
	{
		StringBuilder errBuf = new StringBuilder();
		int nTranslatedPackets = 0;
		Pcap xlPCap = null;
		
		try
		{
			xlPCap = Pcap.openOffline(xlPCapSource.toString(), errBuf);
		}
		catch (Exception ex)
		{
			System.out.print("Error in opening PCAP " + xlPCapSource.toString() + "; " + ex.toString());
			if (errBuf.length() > 0)
			{
				System.out.print(" {" + errBuf.toString() + "}");
			}
			System.out.print("\n");
		}
		
		try
		{
			FileOutputStream xlOutputFileStream = new FileOutputStream(this);
		
			if (null == xlPCap)
			{
				System.err.println( "Error in PCap object creation: " + errBuf);
				return 0;
			}
			
			PcapPacket xlPCPacketSrc = new PcapPacket(JMemory.POINTER);
			
			while (Pcap.NEXT_EX_OK == xlPCap.nextEx(xlPCPacketSrc))
			{
				Udp xlPCPacketUDP = new Udp();
				// +++wayne not sure if a copy is needed, but the API doc recommends it
				PcapPacket xlPCPacket = new PcapPacket(xlPCPacketSrc);
				
				// Make sure this has proper headers to get to UDP source
				if (xlPCPacket.hasHeader(Ip4.ID) &&
						(xlPCPacket.hasHeader(xlPCPacketUDP)))
				{
					// check for port 2055
					if (IPFIX_UDP_DEST_PORT == xlPCPacketUDP.destination())
					{
						// if it passes all of those checks, then extract the UDP payload
						byte [] xlIPFixPayload = xlPCPacketUDP.getPayload(); 
						// write to output file
						xlOutputFileStream.write(xlIPFixPayload);
						nTranslatedPackets++;						
					}
					
				}
			}
			
			xlOutputFileStream.close();
		}
		catch (IOException fEx)
		{
			System.err.println("PCAP Translation - internal error - output file not found or writable or couldn\'t be closed cleanly\n");
			return 0;
		}
		
		xlPCap.close();
		return nTranslatedPackets;
	}
	
	public String toString() 
	{
		return pXlOutput.toString();
	}
	
	private static String pathStrToFile(String pathStr)
	{
		Path pPath = Paths.get(pathStr);
		return pPath.getFileName().toString();
	}
	
	private File xlPCapSource;
	private Path pXlOutput;
	final int IPFIX_UDP_DEST_PORT = 2055;
}
