package com.cbhstudios.tools.flowedit;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.net.UnknownHostException;

// This class will replay a IPFixModel over the network.  It can either be run
// from a XML file or a serialized model.
public class IPFixPlayer {

	public enum IPFixConnectionProtocol { UDP, SCTP }
	
	
	IPFixPlayer (IPFixPlayerOptions playOpts)
	{
		m_playOpts = playOpts;
	}
	
	Integer sendIPFixFromModel (IPFixModel ipfMessageSet) throws FloweditException
	{
		try
		{
			int writeSize = 0;
			DatagramSocket sendClientSocket = new DatagramSocket();
			
			// check state of IPFixPlayerOptions to make sure it's real enough for send
			
			if ( (m_playOpts.getConnectionProtocol() != IPFixConnectionProtocol.UDP) ||
				(null == m_playOpts.getDestSocket()) ||
				null == m_playOpts.getDestAddr())
			{
				throw new FloweditException ("Transmit options not sufficiently parameterized to permit IPFixModel send");
			}

			// serialize IPFixModel into a ByteArrayOutputStream

			int i = 1;
			for (IPFixMessage ipfSendMsg : ipfMessageSet.GetContainedRecordList())
			{
				IPFixByteArrayStreamer ifxBAStream = new IPFixByteArrayStreamer();
				ifxBAStream.OpenIPFixStream(true);  // make sure it's writable!

				ifxBAStream.WriteIPFixFileElement(ipfSendMsg);

				// coerce output stream to a byte []
				byte [] baStreamByteArray = ifxBAStream.getOutputStream();
				
				// do send--make sure there's sufficient UDP buffering
				int msgWriteSize = baStreamByteArray.length;

				if (msgWriteSize > 64 * 1024)
				{
					throw new FloweditException("IPFix model too large for send over UDP");
				}
							
				sendClientSocket.setReceiveBufferSize(msgWriteSize);
				
				System.out.print("\tSending message " + i + " (" + msgWriteSize + " bytes)");
				DatagramPacket ifxPacket = new DatagramPacket(baStreamByteArray, msgWriteSize, m_playOpts.getDestAddr(),
						m_playOpts.getDestSocket()); 
				sendClientSocket.send(ifxPacket);
				
				writeSize += msgWriteSize;
				
				if (0 != m_playOpts.getInterMsgDelay())
				{
					System.out.println("...sleeping " + m_playOpts.getInterMsgDelay() + " secs....");
					Thread.sleep(m_playOpts.getInterMsgDelay() * 1000);
				}
				else
				{
					System.out.println("");
				}
				i++;
			}
			System.out.println("\tSent " + writeSize + " bytes total");
						
			return writeSize;  // return size sent.
		}
		catch (SocketException sEx)  // you heard that right, buster! :)
		{
			throw new FloweditException("Socket exception on IPFix model send", sEx);
		}
		catch (IOException ioEx)
		{
			throw new FloweditException("I/O exception on IPFix model send", ioEx);			
		}		
		catch (InterruptedException inEx)
		{
			// just return, I guess
			return 0;
		}
	}
	
	static protected class IPFixPlayerOptions
	{
		public IPFixPlayerOptions(IPFixPlayer.IPFixConnectionProtocol ipfProt)
		{
			connectionProtocol = ipfProt;
			// we can default the max MTU
			maxMTU = new Integer (65 * 1024);
			interMsgDelaySecs = new Integer(0);
		}
		
		public void setDestAddr(String sDestIPAddr) throws FloweditException
		{
			// only allow IPv4, IPv6 address options, at least syntactically
			if (! sDestIPAddr.matches("^([0-9.:]+)$"))
			{
				throw new FloweditException("IPFixPlayerOptions.setDestSocket: numeric ip address only");
			}
			
			// just let java.net do the hard work
			try
			{
				destIPAddr = InetAddress.getByName(sDestIPAddr);
			}
			catch (UnknownHostException uhEx)
			{
				throw new FloweditException("IPFixPlayerOptions.setDestSocket: ", uhEx);
			}
			return;
		}
		
		public InetAddress getDestAddr()
		{
			return destIPAddr;
		}
		
		public Integer getDestSocket()
		{
			return destSocket;
		}
		
		public void setDestSocket(int newDSocket)
		{
			destSocket = new Integer(newDSocket);
		}
		
		public IPFixPlayer.IPFixConnectionProtocol getConnectionProtocol()
		{
			return connectionProtocol;
		}
		
		public void setInterMsgDelay (int interMsgDelayNew)
		{
			interMsgDelaySecs = interMsgDelayNew;
		}
		
		public Integer getInterMsgDelay ()
		{
			return interMsgDelaySecs;
		}
		
		protected Integer destSocket;
		protected InetAddress destIPAddr;
		protected final IPFixPlayer.IPFixConnectionProtocol connectionProtocol;
		protected Integer maxMTU;
		protected Integer interMsgDelaySecs;
		
		
	}
	
	protected final IPFixPlayerOptions m_playOpts;
}
