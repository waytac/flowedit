package com.cbhstudios.tools.flowedit;

import java.io.IOException;
import java.nio.file.Files;
import java.util.List;
import java.util.ArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

import org.kohsuke.args4j.Argument;
import org.kohsuke.args4j.CmdLineException;
import org.kohsuke.args4j.CmdLineParser;
import org.kohsuke.args4j.Option;
import org.kohsuke.args4j.spi.BooleanOptionHandler;

import com.cbhstudios.tools.flowedit.IPFixPlayer.IPFixConnectionProtocol;

/**
 * @author wtackabury
 *
 *  Intended usage, in the fullness of time:
 *  
 *  flowedit  --interactive |
 *            (<input options> 
 *             [<output options>]
 *             [<send options>] )
 *             
 *             <input options> :== ---binInput <fileName>   |   # IPFix file to start with
 *                                 --xmlInput <fileName>       # XML file, consistent with IPFixMessageSet.xsd
 *             <output options> :== --binOutput <fileName>  |  # write to this IPFix file, intended from --xmlInput
 *                                  --xmlOutput <fileOutput>   # write to XML file
 *             <send options>   ::= --sendTo <IPAddr>/<port>   # format using sendProt to this address/port
 *                                  [--sendProt udp | sctp]     # SCTP not implemented currently, UDP default
 *                                  [--sendOpts <sendOpts> ]  # some tbd file to specify delay, burst, etc.               
 *                                 
 *             <sendOpts>   ::=  [d]#
 *                        where   d = delay between messages in seconds (UDP)                              
 *                        others not implemented   
 *   Effectively, the "interactive" menu based operation (like sbrt) was what I had in mind initially, but
 *   a more batch mode operation is what is appearing more compelling and necessary right now.  I'll return
 *   to the interactive/GUI operation later.
 *  
 */

public class FlowEditor {

	@Option(name="--binInput", usage="rfc 5655-compliant input file")
	private String sInputBinary;
	
	@Option(name="--binOutput", usage="output file to create in rfc 5655-compliant format")
	private String sOutputBinary;
	
	@Option(name="--pCapInput", usage="TCPDUMP pCap file with IPFix traffic for extract or send")
	private String sInputPCap;
	
	@Option(name="--xmlInput", usage="XML file with description of IPFix message(s) to transform or edit")
	private String sInputXml;

	@Option(name="--xmlOutput", usage="XML file to write transformation from input ")
	private String sOutputXml;

	@Option(name="--sendTo", usage="IP and port to send to using UDP (currently) ")
	private String sSendTo;
	
	@Option(name="--sendOpts", usage="Options for --sendTo")
	private String sSendOpts;
	
	

	
	@Option(name="-?", usage="request help display")
	private boolean helpRequested;

	// Apparently receives other command line arguments...
	@Argument
	private List<String> otherArgs = new ArrayList<String>();
	
	private IPFixModel ipfMessageModel;
	private IPFixFileModel ipfWFileModel;
	
	/**
	 * @param args
	 */
	public void doMain(String[] args ) throws IOException, FloweditException
	{
		// TODO Auto-generated method stub
		int sRec = 0;
		CmdLineParser clParser = new CmdLineParser(this);
		clParser.setUsageWidth(80);
		
		System.out.println("IPFix Message r/w i/o test [v1.2.4]...");
		
		try
		{
			clParser.parseArgument(args);

			if (helpRequested)
			{
				usage();  // explicit help requested
				return;
			}
			
			int nInputs = 0;
			// count exactly one input
			String [] sInputs = {sInputBinary, sInputXml, sInputPCap};
			for (String sInput : sInputs)
			{
				if (null != sInput)
				{
					nInputs++;
				}
			}
			if ((args.length >= 2) && (1 == nInputs) &&
					(null != sOutputBinary || null != sOutputXml || null != sSendTo))
			{
				// all is grand
			}
			else
			{
				throw new CmdLineException ("Currently need at least one input and one output (or sendTo) argument");
			}
			
			if (null != sSendOpts && null == sSendTo)
			{
				throw new CmdLineException("Cannot specify send options unless there is a send destination (\"--sendTo\")");
			}

			if (null != sInputPCap)
			{
				// So first, we don't want both input PCAP and input RFC 5655
				if (null != sInputBinary)
				{
					throw new CmdLineException("Specifying both --xmlInput and --pcapInput makes no sense; only select one");
				}
				
				// Otherwise, create a temp RFC 5655 file from the pcap
				IPFixPCapFileTranslation pcIpfFileXl = new IPFixPCapFileTranslation(sInputPCap);
				pcIpfFileXl.DoTranslation();
				// Now, just fudge the xml input and let base RFC 5655 file translation do its
				// work
				sInputBinary = pcIpfFileXl.toString();
			}
			
			if (null == sInputBinary)
			{
				// XML-based input
				IPFixXMLRipper ipfXmlIn = new IPFixXMLRipper(sInputXml);
				System.out.println("Reading from XML file: " + sInputXml);

				ipfMessageModel = ipfXmlIn.ripIPFixXML();
				
				if (null == ipfMessageModel)
				{
					throw new FloweditException ("Empty model returned from ripping" + sInputXml);
				}
			}
			else // RFC 5655 input file
			{
				IPFixFileModel ipfFileModel;
				ipfFileModel = new IPFixFileModel(sInputBinary, null);
				IPFixMessage ifxMess;
				System.out.println("Reading from IPFix file: " + sInputBinary);
				ipfFileModel.InitRead();
				// either opened, or threw an exception.
				
				// now, should be able to read from the beginning....
				
				do
				{
					ifxMess = new IPFixMessage(sRec);
					ipfFileModel.GetIPFixFile().ReadIPFixFileElement(ifxMess);
					ipfFileModel.AddToContainer(ifxMess);
					sRec += ifxMess.DataRecordsInMessage();
				} while (ipfFileModel.GetIPFixFile().ReadableBytes() > 
						ifxMess.m_messageHeader.WrittenFileElementSize());
				ipfFileModel.Reset();
				// we really don't need the file part of this anymore, so cast it to the 
				// member IPFixMessageModel
				ipfMessageModel = (IPFixModel) ipfFileModel;
				
			}
			
			// sending to network isn't exclusive of any other output
			if (null != sSendTo)
			{
				IPFixPlayer.IPFixPlayerOptions ipfPlayOpts = new IPFixPlayer.IPFixPlayerOptions(IPFixConnectionProtocol.UDP);
				// need to parse the sendTo options
				String [] sendToOpts;
				
				try
				{
					sendToOpts = sSendTo.split("/", 2);
					
					if ("" == sendToOpts[0])
					{
						throw new CmdLineException("Need an IPAddress supplied with --sendTo");
					}
				}
				catch (PatternSyntaxException psEx)
				{
					throw new FloweditException (psEx.getMessage()); 
				}

				// parse current form of sendOpts
				IPFixPlayer ipfNetOutput = new IPFixPlayer(ipfPlayOpts);
				ipfPlayOpts.setDestAddr(sendToOpts[0]);
				
				if ("" != sendToOpts[1])
				{
					ipfPlayOpts.setDestSocket(new Integer(sendToOpts[1]));
				}
				else
				{
					ipfPlayOpts.setDestSocket(2055);
				}
				
				// now, parse the sendOpts for other parameters. As of this writing, we only have one,
				// but set up an extensible regex for when we have more
				if (null != sSendOpts)
				{
					Pattern sendOptsPattern = Pattern.compile("(\\w)(\\d+)");
					Matcher sendOptsMatch = sendOptsPattern.matcher(sSendOpts);
					
					while (sendOptsMatch.find())
					{
						try
						{
							Integer sendOptsArg = new Integer(sendOptsMatch.group(2));
	
							switch (sendOptsMatch.group(1))
							{
							case "d":
								ipfPlayOpts.setInterMsgDelay(sendOptsArg);
								System.out.println("Inter IPFixMessage Delay set at " + sendOptsArg + " seconds between messages");
								break;
								
							default:
								throw new CmdLineException("Unidentifiable send option \'" +
										sendOptsMatch.group(1) + "\'");
							}
						}
						catch (NumberFormatException nfEx)
						{
							throw new CmdLineException("Unidentifiable argument for send option \'" +
									sendOptsMatch.group(1) + "\', \"" + sendOptsMatch.group(2) + "\"");
					
						}
					}					
				}
				
				System.out.println("Sending model to collector @ " + ipfPlayOpts.getDestAddr().toString() + 
						"; port " + ipfPlayOpts.getDestSocket());
				ipfNetOutput.sendIPFixFromModel(ipfMessageModel);
			}
			// write out file from iRRec.
			
			if (null != sOutputXml)
			{
				IPFixXMLBurner ipfXmlOut = new IPFixXMLBurner(ipfMessageModel, sOutputXml);
				System.out.println("Writing to XML file: " + sOutputXml);

				// XML output
				ipfXmlOut.burnIPFixXML();
				System.out.println("Done.");
			}
			else if (null != sOutputBinary)  // write to binary file
			{
				ipfWFileModel = new IPFixFileModel(sOutputBinary, null);
				System.out.println("Writing to IPFix file: " + ipfWFileModel.GetIPFixFile().GetName());
				ipfWFileModel.InitWrite();
				for (IPFixMessage ipfWriteMsg : ipfMessageModel.GetContainedRecordList())
				{
					ipfWFileModel.GetIPFixFile().WriteIPFixFileElement(ipfWriteMsg);
					ipfWFileModel.Checkpoint();
				}
				ipfWFileModel.Reset();
			}
				
		}
		catch (CmdLineException clEx)
		{
			System.err.println(clEx.getMessage());
			usage();
		}
	}
			

		/**
		 * @param args
		 */
		public static void main(String[] args) {
			try
			{
				new FlowEditor().doMain(args);
			}
			catch (FloweditException fEx)
			{
				System.out.println(fEx.getMessage());
				System.out.println("");
				usage();
				return;
			}
			catch (Exception ex)
			{
				System.out.println(ex.getMessage());
				ex.printStackTrace();
				return;
			}			
		}		
	
	private static void usage()
	{
		System.err.println();  // extra line
		System.err.println("flowedit  --interactive |\n"  +            
				"   (<input options>\n" +
				"   [<output options>]\n"  +
				"   [<send options>] )\n"  +
				"\n" +
				"   <input options> :== ---binInput <fileName>   |   # IPFix file to start with\n"  + 
				"     --xmlInput <fileName>       # XML file, consistent with IPFixMessageSet.xsd\n"  +
				"    --pCapInput <fileName>       # PCapFile, will extract IPFix packets\n" +
				"   <output options> :== --binOutput <fileName>  |  # write to this IPFix file, intended from --xmlInput\n"  +
				"     --xmlOutput <fileOutput>   # write to XML file\n"  +
				"   <send options>   ::= --sendTo <IPAddr>/<port>   # format using sendProt to this address/port \n" +
				"     [--sendProt udp | sctp]     # SCTP not implemented currently, UDP default\n"  +
				"    [--sendOpts <sendOpts> ]  # some tbd file to specify delay, burst, etc.\n" +               
				"          <sendOpts>   ::=  [d]#\n " +
				"                        where   d = delay between messages in seconds (UDP)\n" +                              
				"                        others not implemented\n" +   
				"\n" +                                 
				"   Effectively, the \"interactive\" menu based operation (like sbrt) was what I had in mind initially, but\n" +
				"  more batch mode operation is what is appearing more compelling and necessary right now.  I'll return\n" +
				"  to the interactive/GUI operation later.\n" +
				"\n");
		
	}
	
}
