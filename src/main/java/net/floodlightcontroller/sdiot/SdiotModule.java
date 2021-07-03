package net.floodlightcontroller.sdiot;


import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.DatagramSocket;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.net.URL;
import java.net.URLConnection;
import java.net.UnknownHostException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Scanner;
import java.util.Set;

import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.projectfloodlight.openflow.protocol.OFMessage;
import org.projectfloodlight.openflow.protocol.OFPacketOut;
import org.projectfloodlight.openflow.protocol.OFType;
import org.projectfloodlight.openflow.protocol.action.OFAction;
import org.projectfloodlight.openflow.types.DatapathId;
import org.projectfloodlight.openflow.types.EthType;
import org.projectfloodlight.openflow.types.IPv4Address;
import org.projectfloodlight.openflow.types.IpProtocol;
import org.projectfloodlight.openflow.types.MacAddress;
import org.projectfloodlight.openflow.types.OFPort;
import org.projectfloodlight.openflow.types.TransportPort;

import net.floodlightcontroller.core.FloodlightContext;
import net.floodlightcontroller.core.IFloodlightProviderService;
import net.floodlightcontroller.core.IOFMessageListener;
import net.floodlightcontroller.core.IOFSwitch;
import net.floodlightcontroller.core.internal.IOFSwitchService;
import net.floodlightcontroller.core.internal.OFSwitch;
import net.floodlightcontroller.core.module.FloodlightModuleContext;
import net.floodlightcontroller.core.module.FloodlightModuleException;
import net.floodlightcontroller.core.module.IFloodlightModule;
import net.floodlightcontroller.core.module.IFloodlightService;
import net.floodlightcontroller.core.types.NodePortTuple;
import net.floodlightcontroller.core.types.SwitchMessagePair;
import net.floodlightcontroller.linkdiscovery.ILinkDiscovery.LinkType;
import net.floodlightcontroller.linkdiscovery.ILinkDiscoveryListener;
import net.floodlightcontroller.linkdiscovery.ILinkDiscoveryService;
import net.floodlightcontroller.linkdiscovery.Link;
import net.floodlightcontroller.linkdiscovery.internal.LinkInfo;
import net.floodlightcontroller.packet.Data;
import net.floodlightcontroller.packet.Ethernet;
import net.floodlightcontroller.packet.IPv4;
import net.floodlightcontroller.packet.TCP;
import net.floodlightcontroller.packet.UDP;
import net.floodlightcontroller.restserver.IRestApiService;
import net.floodlightcontroller.sdiot.web.IoTAppEntry;
import net.floodlightcontroller.sdiot.web.IoTBigReqEntry;
import net.floodlightcontroller.sdiot.web.SdiotBigReqEntry;
import net.floodlightcontroller.sdiot.web.SdiotCluster;
import net.floodlightcontroller.sdiot.web.SdiotWebRoutable;
import net.floodlightcontroller.util.ConcurrentCircularBuffer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.DatagramSocket;
import java.net.DatagramPacket;


/**
 * Main tasks of the sdiot module
 * 1. Receive iot request from user (using scanner)
 * 2. Get switches and host information from json
 * 3. Send the iot request to a host or all hosts connected to the switches known to the floodlight (sendPktOutMessage())
 * 
 *  
 * 
 * new big request format with big request ID
 * the request ID enables the destination host to know which result is for which big IoT request
 * 
 * for tesing cluster response
 * 
 * SET_OFF|SID01,SID05|Area1,10,5,121|1|dst6|1
 * GET|SID05,SID03,SID04|Area1,10,5,121|1|dst6|1
 * GET|SID02,SID01|Area1,10,5,121|1|dst6|1
 * GET|SID01,SID05|Area1,10,5,121|1|dst7|1
 * SET_ON|SID03,SID05,SID04,SID01,SID02|Area1,10,5,121|1|dst6|1
 * GET|SID03,SID05,SID04,SID02|Area1,10,5,121|1|dst6|1
 * SET_OFF|SID01,SID05,SID04,SID03,SID02|Area1,10,5,121|1|dst6|1
 * 
 * 
 * 
 * sub-requests send to sdiot
 * 	SET_ON|SID01,SID02|LOC01,20,10,121|1
 * 	GET|SID03|LOC01,20,10,121|1
 * 	SET_OFF|SID04,SID05|LOC01,20,10,121|1
 * @author chaunguyen
 *
 */                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                   

public class SdiotModule implements IFloodlightModule, IOFMessageListener, ISdiotService

{
	//essential variables
	protected static Logger log = LoggerFactory.getLogger(SdiotModule.class);

	/**
	 * for getting input iot request
	 */
	public byte[] requestInByteArr; 
	public String requestInString;

	/**
	 * analysing the big input request and decomposing it into sub-requests that sent to associated sidot sytems
	 */
	public static RequestAnalyser requestAnalyser= new RequestAnalyser();

	/**
	 * includes pairs of sdiot name and an associated sub-request
	 */
	public ArrayList<String> pairSdiotNameAndSubRequest = new ArrayList<String>();
	
	/**
	 * includes pairs of sdiot name and an associated sub-request and executed status
	 */
	public ArrayList<String> pairSdiotNameAndSubRequestWithExeStatus = new ArrayList<String>();

	/**
	 * includes pairs of ip address of the mininet host (that running the sdiot) and an associated sub-request
	 */
	public ArrayList<String> pairSdiotIpAddressAndSubRequest = new ArrayList<String>();
	
	/**
	 * it is used by startListeningToSocket() and startListeningToSocketWitBigReqID()
	 */
	public ArrayList<String> listSwitchHost = new ArrayList<String>();
	
	
	/**
	 * used inside the startListeningToSocketWitBigReqID()
	 */
	String[] inputReq = null;

	/**
	 * numbering the big request ID
	 * used inside the startListeningToSocketWitBigReqID()
	 */
	public int countReq = 1;
	
	/**
	 * numbering the big request ID
	 * the subrequest are posted to REST API
	 */
	public int countReqFromApi = 1;
	
	/**
	 * contain a list of all big IoT requests for sdiot services and sub-requests
	 * used inside the startListeningToSocketWitBigReqID()
	 */
	public ArrayList<SdiotBigReqEntry> listSdiotBigReq = new ArrayList<>();
	
	/**
	 * contain a list of all big IoT requests
	 */
	public ArrayList<IoTBigReqEntry> bigIoTReqList = new ArrayList<>();
	
	/**
	 * contain a list of all IoT App Result status
	 */
	public ArrayList<IoTAppEntry> bigIoTAppList = new ArrayList<>();
	
	
	/**
	 * 
	 */
	public ArrayList<SdiotCluster> listSdiotClusters = new ArrayList<>();
	
	
	/**
	 * it is used by startCheckingAndReschedulingSubReqFromSdiot(String bigReq, String clusterName)
	 */
	public ArrayList<String> listSdiotAndSubReq = new ArrayList<>();
	
	
	/**
	 * used inside the startListeningToSocketWitBigReqID()
	 */
	public SdiotBigReqEntry bigReqEntry;


	/**
	 * record the time when the application starts sending a request to the SDN controller
	 * used in exeIoTBigRequestFrGUI()
	 */
	public long startReq;
	
	/**
	 * record the time when the Floodlight starts processing an IoT input request
	 */
	public long startProcess;
	
	/**
	 * record the time when the Floodlight stop processing an IoT input request
	 * used in exeIoTBigRequestFrGUI()
	 */
	public long stopProcess;
	
	/**
	 * record the time when the application starts rescheduling a sub-request from sdiot
	 */
	public long startReschedule;
	

	/**
	 * the application receives its all required sensing services
	 */
	public long endReq;
	
	/**
	 * end of rescheduling a sub-request
	 */
	public long endReschedule;

	/**
	 * the total processing for a sensing service provision 
	 * total processing time from an application sending a request till it achieves all required services 
	 * processingReq = endReq - startReq
	 */
	public long processingReq;


	// Module Dependencies
	protected IFloodlightProviderService floodlightProviderService;
	protected IOFSwitchService switchService;
	protected IRestApiService restApiService;

	/**
	 * getting list switch and all connected hosts
	 */

	@Override
	public String getName() {
		return "sdiotservice";
		//		return null; //error happens
	}

	/**
	 * method is called before the sdiot module
	 */
	@Override
	public boolean isCallbackOrderingPrereq(OFType type, String name) {
		return false;
	}

	@Override
	public boolean isCallbackOrderingPostreq(OFType type, String name) {
		return false;
	}


	@Override
	public Collection<Class<? extends IFloodlightService>> getModuleServices() {
		//add 30-7-2019
		Collection<Class<? extends IFloodlightService>> l = new ArrayList<Class<? extends IFloodlightService>>();
	    l.add(ISdiotService.class); //the service provided by the module
	    return l;
//	    return null; //commnent 30-7-2019
	}

	/**
	 * Tell the module system that we provide the ISdiotService
	 * we modify the getModuleServices() and getServiceImpls() methods
	 */
	@Override
	public Map<Class<? extends IFloodlightService>, IFloodlightService> getServiceImpls() {
		//add 30-7-2019
		Map<Class<? extends IFloodlightService>, IFloodlightService> m = new HashMap<Class<? extends IFloodlightService>, IFloodlightService>();
		m.put(ISdiotService.class, this); // We are the class that implements the service
		return m;
			    
//		return null; //commnent 30-7-2019
	}

	/**
	 * To add services that our module depend on 
	 * and guarantee the service is loaded prior to the time our module's init() function is invoked
	 */
	@Override
	public Collection<Class<? extends IFloodlightService>> getModuleDependencies() {
		Collection<Class<? extends IFloodlightService>> l =  new ArrayList<Class<? extends IFloodlightService>>();
		l.add(IFloodlightProviderService.class);
		l.add(IRestApiService.class);//27-7-2019 for rest api
		return l; 
		
	}

	/**
	 * The function of the module is invoked by the method
	 * GET|SID01,SID05|Area1,10,5,121|1|dst6|2
	 */
	@Override
	public void init(FloodlightModuleContext context) throws FloodlightModuleException {

		floodlightProviderService = context.getServiceImpl(IFloodlightProviderService.class);
		switchService = context.getServiceImpl(IOFSwitchService.class);
		restApiService = context.getServiceImpl(IRestApiService.class);//27-7-2019 for rest api
		
	}

	@Override
	public Command receive(IOFSwitch sw, OFMessage msg, FloodlightContext cntx) {
		return Command.CONTINUE;
		//		return null;//run not ok
	}

	@Override
	public void startUp(FloodlightModuleContext context) throws FloodlightModuleException {

		/**
		 * approach 1: getting big iot requests and send sub-requests to sidot clusters
		 * 
		 * to always listen to users input request
		 * function: get input from user and send packet out messages to sdiot
		 * if the function is place here,
		 * while(true) the function run forever -> the sdiot module run forever -> floodlight can not start the GUI -> fail to load the floodlight GUI
		 * 
		 * to make the function running forever - >using startListeningToSocket();
		 */
//		startListeningToOneBigRequest();
		//comment the line below since from now we dont send sub-requests via packet out messages to sdiot clusters
		//the sdiot clusters always check the sub-requests from API
		//startListeningToMultiBigReqWitBigReqID();
//		startTCPSocketServer();//for testing sending tcp packet to sdiot systems
		
		/**
		 * approach 2: getting big iot requests and send sub-requests to sidot clusters
		 * 
		 * only getting input iot big requests from users
		 * then decompose it into sub-requests -> sdiot systems can get it from API
		 * no need for sending the sub-requests via packet out messages
		 */
//		startListeningToMultiBigReqWitBigReqIDPUTTOAPI();
		
		
		/**
		 * always check response "CANNNOT/PARTIALLY ACHIEVE" from sdiot cluster and reschedule sdiot resources
		 * approach1: using information from database
		 */
//		startCheckingAndReschedulingSubReqFromSdiotUsingDatabase();
		
		/**
		 * always check response "CANNNOT/PARTIALLY ACHIEVE" from sdiot cluster and reschedule sdiot resources
		 * approach2: using information from available sdiot resources
		 * 
		 * only run the method in the startUp since whenever the IoT request comes to the API, it is executed automatically 
		 * IoT input is received via addBigIoTReq()
		 * IoT input request is executed via exeIoTBigRequestFrGUI()
		 */
		startCheckingAndReschedulingSubReqFromSdiotUsingAvailableSdiot();

		/**
		 * register our Restlet Routable with the REST API service
		 */
		restApiService.addRestletRoutable(new SdiotWebRoutable());//27-7-2019 for rest api
//		System.out.println("register API Service SdIOT: " + restApiService );
		//get json cannot be here -> connection refused
	}

	/**
	 * Task 1: get IoT input requests via Scanner and return it in ByteArray
	 * @return
	 */
	public static byte[] getInputRequestInByteArr() {
		Scanner scanIn = new Scanner(System.in);
		String request = "";
		log.info("Please enter the input request: ");
		request = scanIn.nextLine();
		log.info("IoT request is: {}", request);
		byte[] reqInByteArray = request.getBytes();
		return reqInByteArray;
	}
	
	/**
	 * Task 1: get IoT input requests via Scanner and return it in String
	 * @return
	 */
	public String getInputRequestInString() {
		IoTBigReqEntry reqEntry = new IoTBigReqEntry();
		String[] reqArr = null;
		Scanner scanIn = new Scanner(System.in);
		String request = "";
		log.info("Please enter the input request: ");
		request = scanIn.nextLine();
		log.info("IoT request is: {}", request); //GET|SID05,SID03,SID04,SID01,SID02|Area1,10,5,121|1|dst6|3
		reqArr = request.split("\\|");
//		listBigIoTReq.add(e);
		return request;
	}
	
	
	/**
	 * Task 1: get IoT input requests via TCP socket and return it in String
	 * @return
	 */
	public String getInputForTcpServerSocket() {
		Scanner scanIn = new Scanner(System.in);
		String request = "";
		log.info("Please enter the input message to tcp client: ");
		request = scanIn.nextLine();
		log.info("Message to client is: {}", request);
		return request;
	}

	/**
	 * Task 3: send packet-out messages to switches and their connected hosts
	 * only change the IPv4Address -> change the direction of packet out message
	 * e.g if we want to send a packet out to host 1, set IPv4Address to 10.0.0.1
	 * 
	 * this function is only applied for a network with one switch
	 * if there are more than one switch, it has to select the switch that connects to the required host
	 * to do it, it needs another input that is the ipv4 address of the required sdiot: , String strIPv4
	 * 
	 * -> using sendSubRequestToSdiot()
	 * 
	 * @param inputRequest
	 * @param sw
	 */
	public static void sendPacketoutMessage(byte[] inputRequest, IOFSwitch sw){

		try{
			byte[] data = inputRequest;
			IOFSwitch mySwitch = sw;

			log.info("----------------Begin of SendPacketoutMessage-------");

			Ethernet l2 = new Ethernet();
			l2.setSourceMACAddress(MacAddress.of(mySwitch.getId()));
			l2.setDestinationMACAddress(MacAddress.BROADCAST);
			//    	  l2.setDestinationMACAddress(MacAddress.of("00:00:00:00:00:01"));
			l2.setEtherType(EthType.IPv4);

			IPv4 l3 = new IPv4();
			l3.setSourceAddress(IPv4Address.of("10.0.0.254"));
			l3.setDestinationAddress(IPv4Address.of("10.255.255.255"));//broadcast: 10.255.255.255, 10.0.0.1
			l3.setTtl((byte) 64);
			l3.setProtocol(IpProtocol.UDP);

			UDP l4 = new UDP();
			l4.setSourcePort(TransportPort.of(6653));
			l4.setDestinationPort(TransportPort.of(8000));

			Data l7 = new Data();
			l7.setData(data);

			l2.setPayload(l3);
			l3.setPayload(l4);
			l4.setPayload(l7);

			byte[] serializedData = l2.serialize();

			OFPacketOut po = mySwitch.getOFFactory() /*mySwitch is some IOFSwitch object*/
					.buildPacketOut()
					.setData(serializedData)
					.setActions(Collections.singletonList((OFAction) mySwitch.getOFFactory().actions().output(OFPort.FLOOD, 0xffFFffFF)))
					.setInPort(OFPort.CONTROLLER)
					.build();

			mySwitch.write(po);
		} catch(IllegalArgumentException e){
			log.error("Exception inside SendPktOutMessage method", e);
		}
	}

	/**
	 * the function sends each sub request to associated sdiot controller 
	 * in this case it send the sub request to the ipv4 address of the mininet host that running the sdiot controller
	 * @param inputRequest
	 * @param sw
	 * @param ipv4
	 */
	public static void sendSubRequestToSdiotUsingPktOutInUdp(byte[] inputRequest, IOFSwitch sw, String ipv4, String hostMacAddr){
		try{
			byte[] data = inputRequest;
			IOFSwitch mySwitch = sw;

			//log.info("\n---------------------Begin of sendSubRequestToSdiot to {}-------------", ipv4);

			//System.out.println("----------sub-request to be sent: "+ inputRequest.toString());

			Ethernet l2 = new Ethernet();
			l2.setSourceMACAddress(MacAddress.of(mySwitch.getId()));
			//l2.setDestinationMACAddress(MacAddress.BROADCAST);
			//l2.setDestinationMACAddress(MacAddress.of("00:00:00:00:00:01"));
			l2.setDestinationMACAddress(MacAddress.of(hostMacAddr));
			l2.setEtherType(EthType.IPv4);

			IPv4 l3 = new IPv4();
			l3.setSourceAddress(IPv4Address.of("10.0.0.254"));
			l3.setDestinationAddress(IPv4Address.of(ipv4));
			l3.setTtl((byte) 64);
			l3.setProtocol(IpProtocol.UDP);

			UDP l4 = new UDP();
			l4.setSourcePort(TransportPort.of(6653));
			l4.setDestinationPort(TransportPort.of(8000));

			Data l7 = new Data();
			l7.setData(data);

			l2.setPayload(l3);
			l3.setPayload(l4);
			l4.setPayload(l7);

			byte[] serializedData = l2.serialize();

			OFPacketOut po = mySwitch.getOFFactory() /*mySwitch is some IOFSwitch object*/
					.buildPacketOut()
					.setData(serializedData)
					.setActions(Collections.singletonList((OFAction) mySwitch.getOFFactory().actions().output(OFPort.FLOOD, 0xffFFffFF)))
					.setInPort(OFPort.CONTROLLER)
					.build();

			mySwitch.write(po);
			System.out.println("\n --------------------End of sending sub-request to "+ ipv4);

		} catch(IllegalArgumentException e){
			log.error("Exception inside sendSubRequestToSdiot method", e);
		}
	}


	/**
	 * @param inputRequest
	 * @param sw
	 * @param ipv4
	 * @param hostMacAddr
	 */
	public static void sendSubRequestToSdiotUsingPktOutInTcp(byte[] inputRequest, IOFSwitch sw, String ipv4, String hostMacAddr){
		try{
			byte[] data = inputRequest;
			IOFSwitch mySwitch = sw;

			//log.info("\n---------------------Begin of sendSubRequestToSdiot to {}-------------", ipv4);

			Ethernet l2 = new Ethernet();
			l2.setSourceMACAddress(MacAddress.of(mySwitch.getId()));
			//l2.setDestinationMACAddress(MacAddress.BROADCAST);
			//l2.setDestinationMACAddress(MacAddress.of("00:00:00:00:00:01"));
			l2.setDestinationMACAddress(MacAddress.of(hostMacAddr));
			l2.setEtherType(EthType.IPv4);

			IPv4 l3 = new IPv4();
			l3.setSourceAddress(IPv4Address.of("10.0.0.254"));
			l3.setDestinationAddress(IPv4Address.of(ipv4));
			l3.setTtl((byte) 64);
			l3.setProtocol(IpProtocol.UDP);

			TCP l4 = new TCP();
			//l4.setSourcePort(TransportPort.of(6653));
			l4.setSourcePort(TransportPort.of(6653));
			l4.setDestinationPort(TransportPort.of(8000));

			Data l7 = new Data();
			l7.setData(data);

			l2.setPayload(l3);
			l3.setPayload(l4);
			l4.setPayload(l7);

			byte[] serializedData = l2.serialize();

			OFPacketOut po = mySwitch.getOFFactory() /*mySwitch is some IOFSwitch object*/
					.buildPacketOut()
					.setData(serializedData)
					.setActions(Collections.singletonList((OFAction) mySwitch.getOFFactory().actions().output(OFPort.FLOOD, 0xffFFffFF)))
					.setInPort(OFPort.CONTROLLER)
					.build();

			mySwitch.write(po);
			System.out.println("\n ----------------------End of sending sub-request to "+ ipv4);

		} catch(IllegalArgumentException e){
			log.error("Exception inside sendSubRequestToSdiot method", e);
		}
	}


	/**
	 * to always listen to the user input (big request) and then decompose it into sub-requests
	 * these sub-requests are sent to involved sdiot (ip of mininet host that running the sdiot system)
	 * 
	 * this function only listens to one input request at a time
	 * 
	 * long start;//start receiving the application request n analyse
	 * long finish;
	 * long processingTime; //processing of the controller
	 * 
	 */
	public void startListeningToOneBigRequest() {
		//code for getting switch info using API cannot be here since error happens here
		//ArrayList<String> listSwitchHost = new ArrayList<String>(); //error is here

		/**
		 * start recording the processing time
		 */
		startReq = System.currentTimeMillis();

		Runnable task = new Runnable() {
			public void run() {
				while(true) {
					log.info("-------------------------Task 1: get iot requests-----------------------------------------------");

					/**
					 * start listening to input request (big request)
					 */
					requestInString = getInputRequestInString();


					/**
					 * decompose the big request into sub-requests and produce a list of sdiot name and associated sub-request
					 */
					pairSdiotNameAndSubRequest = RequestAnalyser.decomposeBigRequestIntoSubReqList(requestInString);

					/**
					 * getting a list of sdiot ipv4 address (ipv4 address of a mininet host) and its associated sub-request 
					 * based on the list of sdiot name and its associated sub-request
					 */
					pairSdiotIpAddressAndSubRequest = RequestAnalyser.getPairsOfSdiotIpAddrAndSubRequest(pairSdiotNameAndSubRequest);


					/**
					 * getting a list of all active switches and their connected hosts in order to only choose the switch that connects to the required sdiot system
					 * retrieve the information using API
					 * 
					 * other example
					 * final String DEVICES_API = "http://localhost:8080/wm/device/";
					 * final String SWITCHES_API = "http://localhost:8080/wm/core/controller/switches/json";
					 * System.out.println(sendPostRequestForSwitchInfo(SWITCHES_API, ""));
					 * System.out.println(sendPostRequestForDeviceInfo(DEVICES_API, ""));
					 * 
					 */
					//					log.info("-------------------getting devices info from API-----------------------------------------------");

					final String DEVICES_API = "http://localhost:8080/wm/device/";
					listSwitchHost = getSwitchHostList(DEVICES_API, "");

					//					log.info("------------------------------End of getting devices info from API-----------------------------------------------------");


					String[] strArr,strArrSwHost = null;
					String ipv4 = "";
					String swMacAddr = "";
					IOFSwitch sw = null;
					byte[] subRequestInByteArr = null;

					for(int i=0;i<pairSdiotIpAddressAndSubRequest.size();i++) {
						strArr = pairSdiotIpAddressAndSubRequest.get(i).split("\\:");
						ipv4 = strArr[0];
						subRequestInByteArr = strArr[1].getBytes();

						for(int j = 0; j<listSwitchHost.size();j++) {
							strArrSwHost = listSwitchHost.get(j).split("\\;");
							swMacAddr = strArrSwHost[0];
							if(ipv4.equalsIgnoreCase(strArrSwHost[1])) {
								//System.out.println("switch mac addr = " + swMacAddr);
								//System.out.println("ipv4 addr = " + strArrSwHost[1]);
								System.out.println("\nPair[SwitchMAC : HostIPv4] : " + swMacAddr + " : " + strArrSwHost[1]);

								for (DatapathId dpid : switchService.getAllSwitchDpids()) {
									sw = switchService.getActiveSwitch(dpid);
									//System.out.println("switch id = " + sw.getId().toString());
									//System.out.println("switch mac addr = " + swMacAddr);
									//System.out.println("switch id = " + sw.getId().toString().equalsIgnoreCase(swMacAddr));
									if(sw.getId().toString().equalsIgnoreCase(swMacAddr)) {
										//System.out.println("dpid = " + dpid.toString());
										if(strArrSwHost[1].equalsIgnoreCase(ipv4)) {
											//sendSubRequestToSdiot(subRequestInByteArr,sw,ipv4);
										}
									}

								}
							}
						}
					}

					/**
					 * recording the ending time of processing the big IoT request and sending all sub-requests to associated sdiot systems
					 */
					endReq = System.currentTimeMillis();
					processingReq = endReq - startReq;
					long t = processingReq;
					System.out.println("\n processing time for the request = " +processingReq/1000 + " seconds\n");

				}
			}
		};

		// Run the task in a background thread
		Thread backgroundThread = new Thread(task);
		// Terminate the running thread if the application exits
		backgroundThread.setDaemon(true);
		// Start the thread
		backgroundThread.start();
	}

	/**
	 * the big request with the big request ID can represent a number of simultaneous requests
	 * this mean the system can listen and receive multiple simultaneous input big requests
	 * 
	 * GET|SID03|Area1,10,5,121|1|dst6|1
	 * GET|SID03|Area1,10,5,121|1|dst6|1
	 * GET|SID03|Area1,10,5,121|1|dst6|50
	 * GET|SID03|Area1,10,5,121|1|dst6|100
	 * 
	 * SET_OFF|SID03|Area1,10,5,121|1|dst6|1
	 * SET_OFF|SID01,SID05,SID03|Area1,10,5,121|1|dst6|1
	 * 
	 * GET|SID01,SID05,SID03|Area1,10,5,121|1|dst6|3
	 * GET|SID01,SID05,SID03|Area1,10,5,121|1|dst6|1  (sdiot 2,5,4)
	 * GET|SID01,SID05,SID03|Area1,10,5,121|1|dst6|3
	 * GET|SID01,SID05|Area1,10,5,121|1|dst7|1
	 * GET|SID02,SID04|Area1,10,5,121|1|dst6|1
	 * GET|SID02,SID04|Area1,10,5,121|1|dst7|1
	 * GET|SID03,SID04|Area1,10,5,121|1|dst6|1
	 * GET|SID03,SID04|Area1,10,5,121|1|dst7|1
	 * GET|SID01,SID05|Area1,10,5,121|1|dst6|3
	 * GET|SID01,SID05|Area1,10,5,121|1|dst6|10
	 * GET|SID02,SID04|Area1,10,5,121|1|dst6|5
	 * 
	 */
	public void startListeningToMultiBigReqWitBigReqID() {

		/**
		 * start recording the processing time
		 */
		startReq = System.currentTimeMillis();
		

		Runnable task = new Runnable() {

			public void run() {

				while(true) {
					log.info("-------------------------Task 1: get iot requests-----------------------------------------------");

					/**
					 * start listening to input request (big request)
					 */
					requestInString = getInputRequestInString();
					inputReq = requestInString.split("\\|");

					/**
					 * bigReqID represents the total number of simultaneous input requests
					 */
					int bigReqID = Integer.parseInt(inputReq[5]);
					int n = bigReqID; //the total number of simultaneous input requests

					/**
					 * 1 loop represents one time of processing one input request
					 */
					for(int k=1;k<=n;k++) {

						System.out.println("\n---------big req ID = " + countReq +"\n");
						/**
						 * decompose the big request into sub-requests and produce a list of sdiot name and associated sub-request
						 */
//						pairSdiotNameAndSubRequest = RequestAnalyser.decomposeBigReqIntoSubReqList(requestInString, countReq);
						
						/**
						 * work out the best sdiot for a required SID -> produce a list of the best sdiot and an associated required-SID
						 */
						pairSdiotNameAndSubRequest = RequestAnalyser.decomposeBigRequestWithBigReqIDIntoSubReqListBySIDUsingDatabase(requestInString, countReq);


						/**
						 * getting a list of sdiot ipv4 address (ipv4 address of a mininet host) and its associated sub-request 
						 * based on the list of sdiot name and its associated sub-request
						 */
						pairSdiotIpAddressAndSubRequest = RequestAnalyser.getPairsOfSdiotIpAddrAndSubRequest(pairSdiotNameAndSubRequest);
						
						/**
						 * building list of big IoT Requests
						 */
						for(int m=0; m< pairSdiotNameAndSubRequest.size();m++) {
							SdiotBigReqEntry entry ;
							String bigIoTReqID = String.valueOf(countReq);
//							System.out.println("---k= "+k);
							bigReqEntry = buildASDIoTReqEntry(bigIoTReqID, pairSdiotNameAndSubRequest.get(m));
							
							/*String[] aPair = pairSdiotNameAndSubRequest.get(m).split("\\:");
							String clusterName = aPair[0];
							String subRequest = aPair[1];
							
							String[] subRequestArr = subRequest.split("\\|");
							String reqServices = subRequestArr[1];
							
							entry = new SdiotBigReqEntry(bigIoTReqID, clusterName, "Can achieve all required services", reqServices);
							System.out.println(" entry = " + entry.toString());*/
							
							listSdiotBigReq.add(bigReqEntry);
							//GET|SID01,SID05,SID03|Area1,10,5,121|1|dst6|3
						}
						

						/**
						 * getting a list of all active switches and their connected hosts in order to only choose the switch that connects to the required sdiot system
						 * retrieve the information using API
						 * 
						 * other example
						 * final String DEVICES_API = "http://localhost:8080/wm/device/";
						 * final String SWITCHES_API = "http://localhost:8080/wm/core/controller/switches/json";
						 * System.out.println(sendPostRequestForSwitchInfo(SWITCHES_API, ""));
						 * System.out.println(sendPostRequestForDeviceInfo(DEVICES_API, ""));
						 * 
						 */
//						log.info("-------------------getting devices info from API-----------------------------------------------");

						final String DEVICES_API = "http://localhost:8080/wm/device/";
						listSwitchHost = getSwitchHostList(DEVICES_API, "");

//						log.info("------------------------------End of getting devices info from API-----------------------------------------------------");


						String[] strArr,strArrSwHost = null;
						String ipv4 = "";
						String swMacAddr = "";
						String hostMacAddr = "";
						IOFSwitch sw = null;
						byte[] subRequestInByteArr = null;

						for(int i=0;i<pairSdiotIpAddressAndSubRequest.size();i++) {
							strArr = pairSdiotIpAddressAndSubRequest.get(i).split("\\:");
							ipv4 = strArr[0];
							subRequestInByteArr = strArr[1].getBytes();

							for(int j = 0; j<listSwitchHost.size();j++) {
								strArrSwHost = listSwitchHost.get(j).split("\\;");
								swMacAddr = strArrSwHost[0];
								hostMacAddr = strArrSwHost[2];

								if(ipv4.equalsIgnoreCase(strArrSwHost[1])) {
									//System.out.println("switch mac addr = " + swMacAddr);
									//System.out.println("ipv4 addr = " + strArrSwHost[1]);
									//System.out.println("\nPair[SwitchMAC:HostIPv4:HostMACAddr] : " + swMacAddr + " : " + strArrSwHost[1] +" : "+ strArrSwHost[2]);

									for (DatapathId dpid : switchService.getAllSwitchDpids()) {
										sw = switchService.getActiveSwitch(dpid);
										//System.out.println("switch id = " + sw.getId().toString());
										//System.out.println("switch mac addr = " + swMacAddr);
										//System.out.println("switch id = " + sw.getId().toString().equalsIgnoreCase(swMacAddr));
										if(sw.getId().toString().equalsIgnoreCase(swMacAddr)) {
											//System.out.println("dpid = " + dpid.toString());
											if(strArrSwHost[1].equalsIgnoreCase(ipv4)) {
												//sendSubRequestToSdiotUsingPktOutInTcp(subRequestInByteArr,sw,ipv4, hostMacAddr);
												sendSubRequestToSdiotUsingPktOutInUdp (subRequestInByteArr,sw,ipv4, hostMacAddr);
											}
										}

									}
								}
							}
						}
						
						countReq++;
					}

					/**
					 * recording the ending time of processing the big IoT request and sending all sub-requests to associated sdiot systems
					 */
					endReq = System.currentTimeMillis();
					processingReq = endReq - startReq;
					long t = processingReq;
					System.out.println("\n------Total processing time of the SDN controller for " + n +" is " +processingReq/1000 + " seconds\n");
					System.out.println("listBigIoTReq size = " + listSdiotBigReq.size());
					

				}
			}
		};

		// Run the task in a background thread
		Thread backgroundThread = new Thread(task);
		// Terminate the running thread if the application exits
		backgroundThread.setDaemon(true);
		// Start the thread
		backgroundThread.start();
		
		
	}
	
	/**
	 * the SDN controller always listens to input big IoT request (the input request is entered via Scanner)
	 * then decomposes the big request into sub-requests
	 * then put them to REST API
	 * if any sdiot system is available and checks its task, it will get the list from API and executes their tasks
	 */
	public void startListeningToMultiBigReqWitBigReqIDPUTTOAPI() {

		/**
		 * start recording the processing time
		 */
		startReq = System.currentTimeMillis();
		

		Runnable task = new Runnable() {

			public void run() {

				while(true) {
					log.info("\n-------------------------Task 1: get iot requests-----------------------------------------------");

					/**
					 * start listening to input request (big request)
					 */
					requestInString = getInputRequestInString();
					inputReq = requestInString.split("\\|");

					/**
					 * bigReqID represents the total number of simultaneous input requests
					 */
					int bigReqID = Integer.parseInt(inputReq[5]);
					int n = bigReqID; //the total number of simultaneous input requests

					/**
					 * 1 loop represents one time of processing one input request
					 */
					for(int k=1;k<=n;k++) {

						System.out.println("\n---------big req ID = " + countReqFromApi +"\n");
						/**
						 * decompose the big request into sub-requests and produce a list of sdiot name and associated sub-request
						 */
//						pairSdiotNameAndSubRequest = RequestAnalyser.decomposeBigReqIntoSubReqList(requestInString, countReq);
						
						/**
						 * work out the best sdiot for a required SID -> produce a list of the best sdiot and an associated required-SID
						 */
						pairSdiotNameAndSubRequest = RequestAnalyser.decomposeBigRequestWithBigReqIDIntoSubReqListBySIDUsingDatabase(requestInString, countReqFromApi);
						
						/**
						 * building list of big IoT Requests
						 */
						for(int m=0; m< pairSdiotNameAndSubRequest.size();m++) {
							SdiotBigReqEntry entry ;
							String bigIoTReqID = String.valueOf(countReqFromApi);
//							System.out.println("---k= "+k);
							bigReqEntry = buildASDIoTReqEntry(bigIoTReqID, pairSdiotNameAndSubRequest.get(m));
							
							/*String[] aPair = pairSdiotNameAndSubRequest.get(m).split("\\:");
							String clusterName = aPair[0];
							String subRequest = aPair[1];
							
							String[] subRequestArr = subRequest.split("\\|");
							String reqServices = subRequestArr[1];
							
							entry = new SdiotBigReqEntry(bigIoTReqID, clusterName, "Can achieve all required services", reqServices);
							System.out.println(" entry = " + entry.toString());*/
							
							listSdiotBigReq.add(bigReqEntry);
						}
						
						countReqFromApi++;
					}

					/**
					 * recording the ending time of processing the big IoT request and sending all sub-requests to associated sdiot systems
					 */
					endReq = System.currentTimeMillis();
					processingReq = endReq - startReq;
					long t = processingReq;
					System.out.println("\n------Total processing time of the SDN controller for " + n +" is " +processingReq/1000 + " seconds\n");
					System.out.println("listBigIoTReq size = " + listSdiotBigReq.size());
					

				}
			}
		};

		// Run the task in a background thread
		Thread backgroundThread = new Thread(task);
		// Terminate the running thread if the application exits
		backgroundThread.setDaemon(true);
		// Start the thread
		backgroundThread.start();
		
	}
	
	/**
	 * this one is currently used
	 * 
	 * case b) executing an IoT input request with smart orchestration
	 * 
	 * we can use this method to get processing time with smart orchestration strategy of the sdn-sdiot controller
	 * 
	 * executing an IoT input request that is entered in the web-GUI
	 * for any input request, the controller always orchestrates sdiot resources to work out the best suitable candidate handling the request
	 * with CONSIDERATION of
	 * - if any sdiot cluster that currently serves IoT requests also provides the same required services
	 * 		and ask it to retrieve its current data and provide results for the incoming request without further configuration on the associated SDVSs
	 * 
	 * how: the sdiot cluster with "GET" action "ONGOING" and "ISEXECUTED" would be selected
	 * 
	 * @param inReq
	 */
	public void exeIoTBigRequestFrGUI(String inReq) {
		
		/**
		 * get a list of currently-available sdiot resources
		 */
		ArrayList<SdiotCluster> clusterList = getSdiotClusterServices();
		ArrayList<SdiotBigReqEntry> sdiotReqList = getListSubReqForBigReq();
		log.info("\n-------------------------Start processing IoT Requests------------------------------------------");

		/**
		 * start listening to input request (big request)
		 */
//		requestInString = getInputRequestInString();
//		requestInString = inReq;
		String[] inputArr = inReq.split("\\|");

		/**
		 * bigReqID represents the total number of simultaneous input requests
		 */
		int bigReqID = Integer.parseInt(inputArr[5]);
		int n = bigReqID; //the total number of simultaneous input requests
		
		String reqAct = inputArr[0];
		String reqSer = inputArr[1];
		String[] reqConditionArr = inputArr[2].split("\\,");
		String reqArea = reqConditionArr[0];
		
		/**
		 * start recording the time when the SDN controller receives one or multiple simultaneous IoT requests + processing them
		 */
		startProcess = System.currentTimeMillis();
		
		/**
		 * starting time of an IoT request
		 * the time is recorded when an IoT request is built
		 * 
		 */
		long startTimeOfAnIoTReq;
		
		/**
		 * 1 loop represents one time of processing one input request
		 */
		
		for(int k=1;k<=n;k++) {
			
			startTimeOfAnIoTReq = System.currentTimeMillis();

			System.out.println("\n--Big req ID = " + countReqFromApi +"\n");
			
			/**
			 * build a list of big IoT requests
			 */
			bigIoTReqList.add(new IoTBigReqEntry(String.valueOf(countReqFromApi), reqSer, reqArea, startTimeOfAnIoTReq,0 ));
//			System.out.println("\n" + reqSer);
//			System.out.println("\n" + reqArea);
//			System.out.println("\nBig IoT request is: " + (new IoTBigReqEntry(String.valueOf(countReqFromApi), reqArea, reqSer, startProcess )).toString());
					
			/**
			 * decompose the big request into sub-requests and produce a list of sdiot name and associated sub-request
			 */
//			pairSdiotNameAndSubRequest = RequestAnalyser.decomposeBigReqIntoSubReqList(requestInString, countReq);
			
			/**
			 * work out the best sdiot for a required SID -> produce a list of the best sdiot and an associated required-SID
			 */
			if(clusterList.size()==0) {
				pairSdiotNameAndSubRequest = RequestAnalyser.decomposeBigRequestWithBigReqIDIntoSubReqListBySIDUsingDatabase(inReq, countReqFromApi);
			}
			else {
				pairSdiotNameAndSubRequest = RequestAnalyser.decomposeBigReqIntoSubReqListBySIDUsingAvailableSdiotResources(clusterList,sdiotReqList,inReq, countReqFromApi);
			}
			

			/**
			 * building list of SDIoT Requests
			 */
			for(int m=0; m< pairSdiotNameAndSubRequest.size();m++) {
				SdiotBigReqEntry entry ;
				String bigIoTReqID = String.valueOf(countReqFromApi);
				bigReqEntry = buildASDIoTReqEntry(bigIoTReqID, pairSdiotNameAndSubRequest.get(m));
				
				listSdiotBigReq.add(bigReqEntry);
			}
			
			countReqFromApi++;
		}
		
		//print forever for testing the list
		/*System.out.println("\nSize of bigRequest list in exeIoTBigRequestFrGUI = "+ getListSubReqForBigReq().size());
		for(int i=0;i<getListSubReqForBigReq().size();i++) {
			System.out.println("bigReq entry = "+ getListSubReqForBigReq().get(i).toString());
		}*/ 

		/**
		 * recording the ending time of processing one or multiple big IoT requests and sending all sub-requests to associated sdiot systems
		 */
		stopProcess = System.currentTimeMillis();
		
		/**
		 * the processing time of one or multiple IoT requests
		 * this is also the orchestration time
		 * i) process the requests
		 * ii) buid a list of sub-requests for the IoT requests
		 */
		processingReq = stopProcess - startProcess;
//		long t = processingReq;
		System.out.println("\n------Total processing time (process IoT requests & orchestrate sdiot resources) of the SDN controller for " + n +" IoT requests is " +processingReq/1000 + " seconds\n");
//		System.out.println("listBigIoTReq size = " + listSdiotBigReq.size());
//		updateClusterStateToDatabaseUsingInfoFrMysql(reqArea);
		updateClusterStateToDatabaseUsingInfoFrAvailableResources(reqArea);
	}
	
	/**
	 * 
	 * case b) executing an IoT input request with smart orchestration
	 * 
	 * we can use this method to get processing time with smart orchestration strategy of the sdn-sdiot controller
	 * 
	 * executing an IoT input request that is entered in the web-GUI
	 * for any input request, the controller always orchestrates sdiot resources to work out the best suitable candidate handling the request
	 * with CONSIDERATION of
	 * - if any sdiot cluster that currently serves IoT requests also provides the same required services
	 * 		and ask it to retrieve its current data and provide results for the incoming request without further configuration on the associated SDVSs
	 * 
	 * how: the sdiot cluster with "GET" action "ONGOING" and "ISEXECUTED" would be selected
	 * 
	 * @param inReq
	 * @throws IOException 
	 * @throws UnknownHostException 
	 * @throws SocketException 
	 */
	public void exeIoTBigRequestFrGUIWithSmartOrch(String inReq) throws SocketException, UnknownHostException, IOException {
		
		/**
		 * get a list of currently-available sdiot resources
		 */
		ArrayList<SdiotCluster> clusterList = getSdiotClusterServices();
		ArrayList<SdiotBigReqEntry> sdiotReqList = getListSubReqForBigReq();
		log.info("\n-------------------------Start processing IoT Requests------------------------------------------");

		/**
		 * start listening to input request (big request)
		 */
//		requestInString = getInputRequestInString();
//		requestInString = inReq;
		String[] inputArr = inReq.split("\\|");

		/**
		 * bigReqID represents the total number of simultaneous input requests
		 */
		int bigReqID = Integer.parseInt(inputArr[5]);
		int n = bigReqID; //the total number of simultaneous input requests
		
		String reqAct = inputArr[0];
		String reqSer = inputArr[1];
		String[] reqConditionArr = inputArr[2].split("\\,");
		String reqArea = reqConditionArr[0];
		
		/**
		 * start recording the time when the SDN controller receives one or multiple simultaneous IoT requests + processing them
		 */
		startProcess = System.currentTimeMillis();
		
		/**
		 * starting time of an IoT request
		 * the time is recorded when an IoT request is built
		 * 
		 */
		long startTimeOfAnIoTReq;
		
		/**
		 * 1 loop represents one time of processing one input request
		 */
		
		for(int k=1;k<=n;k++) {
			
			startTimeOfAnIoTReq = System.currentTimeMillis();

			System.out.println("\n--Big req ID = " + countReqFromApi +"\n");
			
			/**
			 * build a list of big IoT requests
			 */
			bigIoTReqList.add(new IoTBigReqEntry(String.valueOf(countReqFromApi), reqSer, reqArea, startTimeOfAnIoTReq,0 ));
//			System.out.println("\n" + reqSer);
//			System.out.println("\n" + reqArea);
//			System.out.println("\nBig IoT request is: " + (new IoTBigReqEntry(String.valueOf(countReqFromApi), reqArea, reqSer, startProcess )).toString());
					
			/**
			 * decompose the big request into sub-requests and produce a list of sdiot name and associated sub-request
			 */
//			pairSdiotNameAndSubRequest = RequestAnalyser.decomposeBigReqIntoSubReqList(requestInString, countReq);
			
			/**
			 * work out the best sdiot for a required SID -> produce a list of the best sdiot and an associated required-SID
			 */
			if(clusterList.size()==0) {
				pairSdiotNameAndSubRequest = RequestAnalyser.decomposeBigRequestWithBigReqIDIntoSubReqListBySIDUsingDatabase(inReq, countReqFromApi);
			}
			else {
				pairSdiotNameAndSubRequest = RequestAnalyser.decomposeBigReqIntoSubReqListBySIDUseAvailSdiotResourcesForSmartOrch(clusterList,sdiotReqList,inReq, countReqFromApi);
			}
			

			/**
			 * building list of SDIoT Requests
			 * how can we know which pair is marked with executed
			 */
			for(int m=0; m< pairSdiotNameAndSubRequest.size();m++) {
				SdiotBigReqEntry entry ;
				String bigIoTReqID = String.valueOf(countReqFromApi);
//				bigReqEntry = buildASDIoTReqEntry(bigIoTReqID, pairSdiotNameAndSubRequest.get(m));
				bigReqEntry = buildASDIoTReqEntryForSmartOrch(bigIoTReqID, pairSdiotNameAndSubRequest.get(m));
				//when building sub-requests need to take note of the SID that are currently provided by other sdiot system and make it executed
				
				listSdiotBigReq.add(bigReqEntry);
			}
			
			countReqFromApi++;
		}
		
		//print forever for testing the list
		/*System.out.println("\nSize of bigRequest list in exeIoTBigRequestFrGUI = "+ getListSubReqForBigReq().size());
		for(int i=0;i<getListSubReqForBigReq().size();i++) {
			System.out.println("bigReq entry = "+ getListSubReqForBigReq().get(i).toString());
		}*/ 

		/**
		 * recording the ending time of processing one or multiple big IoT requests and sending all sub-requests to associated sdiot systems
		 */
		stopProcess = System.currentTimeMillis();
		
		/**
		 * the processing time of one or multiple IoT requests
		 * i) process the requests
		 * ii) buid a list of sub-requests for the IoT requests
		 */
		processingReq = stopProcess - startProcess;
//		long t = processingReq;
		System.out.println("\n------Total processing time (process IoT requests & orchestrate sdiot resources) of the SDN controller for " + n +" IoT requests is " + processingReq + " milliseconds\n");
//		System.out.println("listBigIoTReq size = " + listSdiotBigReq.size());
//		updateClusterStateToDatabaseUsingInfoFrMysql(reqArea);
		updateClusterStateToDatabaseUsingInfoFrAvailableResources(reqArea);
	}
	
	/**
	 *   
	 * for any input request, the controller always orchestrates sdiot resources to work out the best suitable candidate handling the request
	 * without considering if any sdiot cluster that currently serves IoT requests also provides the same required services
	 * and ask it to provide results for the incoming request
	 * 
	 * how: the sdiot cluster with least "state" would be selected
	 * 
	 * @param inReq
	 */
	public void exeIoTBigRequestFrGUIVersion1(String inReq) {
		
		/**
		 * get a list of currently-available sdiot resources
		 */
		ArrayList<SdiotCluster> clusterList = getSdiotClusterServices();
		log.info("\n-------------------------Start processing IoT Requests------------------------------------------");

		/**
		 * start listening to input request (big request)
		 */
//		requestInString = getInputRequestInString();
//		requestInString = inReq;
		String[] inputArr = inReq.split("\\|");

		/**
		 * bigReqID represents the total number of simultaneous input requests
		 */
		int bigReqID = Integer.parseInt(inputArr[5]);
		int n = bigReqID; //the total number of simultaneous input requests
		
		String reqAct = inputArr[0];
		String reqSer = inputArr[1];
		String[] reqConditionArr = inputArr[2].split("\\,");
		String reqArea = reqConditionArr[0];
		
		/**
		 * start recording the time when the SDN controller receives one or multiple simultaneous IoT requests
		 */
		startProcess = System.currentTimeMillis();
		
		/**
		 * 1 loop represents one time of processing one input request
		 */
		
		for(int k=1;k<=n;k++) {

			System.out.println("\n--Big req ID = " + countReqFromApi +"\n");
			
			/**
			 * build a list of big IoT requests
			 */
			bigIoTReqList.add(new IoTBigReqEntry(String.valueOf(countReqFromApi), reqSer, reqArea, startProcess,0 ));
//			System.out.println("\n" + reqSer);
//			System.out.println("\n" + reqArea);
//			System.out.println("\nBig IoT request is: " + (new IoTBigReqEntry(String.valueOf(countReqFromApi), reqArea, reqSer, startProcess )).toString());
					
			/**
			 * decompose the big request into sub-requests and produce a list of sdiot name and associated sub-request
			 */
//			pairSdiotNameAndSubRequest = RequestAnalyser.decomposeBigReqIntoSubReqList(requestInString, countReq);
			
			/**
			 * work out the best sdiot for a required SID -> produce a list of the best sdiot and an associated required-SID
			 */
			if(clusterList.size()==0) {
				pairSdiotNameAndSubRequest = RequestAnalyser.decomposeBigRequestWithBigReqIDIntoSubReqListBySIDUsingDatabase(inReq, countReqFromApi);
			}
			else {
				pairSdiotNameAndSubRequest = RequestAnalyser.decomposeBigRequestWithBigReqIDIntoSubReqListBySIDUsingAvailableSdiotResources(clusterList,inReq, countReqFromApi);
			}
			

			/**
			 * building list of SDIoT Requests
			 */
			for(int m=0; m< pairSdiotNameAndSubRequest.size();m++) {
				SdiotBigReqEntry entry ;
				String bigIoTReqID = String.valueOf(countReqFromApi);
				bigReqEntry = buildASDIoTReqEntry(bigIoTReqID, pairSdiotNameAndSubRequest.get(m));
				
				listSdiotBigReq.add(bigReqEntry);
			}
			
			countReqFromApi++;
		}
		
		//print forever for testing the list
		/*System.out.println("\nSize of bigRequest list in exeIoTBigRequestFrGUI = "+ getListSubReqForBigReq().size());
		for(int i=0;i<getListSubReqForBigReq().size();i++) {
			System.out.println("bigReq entry = "+ getListSubReqForBigReq().get(i).toString());
		}*/ 

		/**
		 * recording the ending time of processing one or multiple big IoT requests and sending all sub-requests to associated sdiot systems
		 */
		stopProcess = System.currentTimeMillis();
		
		/**
		 * the processing time of one or multiple IoT requests
		 * i) process the requests
		 * ii) buid a list of sub-requests for the IoT requests
		 */
		processingReq = stopProcess - startProcess;
//		long t = processingReq;
		System.out.println("\n------Total processing time (process IoT requests n orchestrate sdiot resources) of the SDN controller for " + n +" IoT requests is " +processingReq/1000 + " seconds\n");
//		System.out.println("listBigIoTReq size = " + listSdiotBigReq.size());
		updateClusterStateToDatabaseUsingInfoFrMysql(reqArea);
		updateClusterStateToDatabaseUsingInfoFrAvailableResources(reqArea);
	}
	
	/**
	 * case a) executing an IoT input request without smart orchestration
	 * 
	 * we can use this method to get processing time without smart orchestration strategy of the sdn-sdiot controller
	 *  
	 * for any input request, the controller always orchestrates sdiot resources to work out the best suitable candidate handling the request
	 * without considering if any sdiot cluster that currently serves IoT requests also provides the same required services
	 * and ask it to provide results for the incoming request
	 * 
	 * how: the sdiot cluster with least "state" would be selected
	 * 
	 * @param inReq
	 */
	public void exeIoTBigRequestFrGUIWithoutSmartOrch(String inReq) {
		
		/**
		 * get a list of currently-available sdiot resources
		 */
		ArrayList<SdiotCluster> clusterList = getSdiotClusterServices();
		log.info("\n-------------------------Start processing IoT Requests------------------------------------------");

		/**
		 * start listening to input request (big request)
		 */
//		requestInString = getInputRequestInString();
//		requestInString = inReq;
		String[] inputArr = inReq.split("\\|");

		/**
		 * bigReqID represents the total number of simultaneous input requests
		 */
		int bigReqID = Integer.parseInt(inputArr[5]);
		int n = bigReqID; //the total number of simultaneous input requests
		
		String reqAct = inputArr[0];
		String reqSer = inputArr[1];
		String[] reqConditionArr = inputArr[2].split("\\,");
		String reqArea = reqConditionArr[0];
		
		/**
		 * start recording the time when the SDN controller receives one or multiple simultaneous IoT requests
		 */
		startProcess = System.currentTimeMillis();
		
		/**
		 * 1 loop represents one time of processing one input request
		 */
		
		for(int k=1;k<=n;k++) {

			System.out.println("\n--Big req ID = " + countReqFromApi +"\n");
			
			/**
			 * build a list of big IoT requests
			 */
			bigIoTReqList.add(new IoTBigReqEntry(String.valueOf(countReqFromApi), reqSer, reqArea, startProcess,0 ));
//			System.out.println("\n" + reqSer);
//			System.out.println("\n" + reqArea);
//			System.out.println("\nBig IoT request is: " + (new IoTBigReqEntry(String.valueOf(countReqFromApi), reqArea, reqSer, startProcess )).toString());
					
			/**
			 * decompose the big request into sub-requests and produce a list of sdiot name and associated sub-request
			 */
//			pairSdiotNameAndSubRequest = RequestAnalyser.decomposeBigReqIntoSubReqListWithouSmart(requestInString, countReq);
			
			/**
			 * work out the best sdiot for a required SID -> produce a list of the best sdiot and an associated required-SID
			 */
			if(clusterList.size()==0) {
				pairSdiotNameAndSubRequest = RequestAnalyser.decomposeBigRequestWithBigReqIDIntoSubReqListBySIDUsingDatabase(inReq, countReqFromApi);
			}
			else {
				pairSdiotNameAndSubRequest = RequestAnalyser.decomposeBigReqIntoSubReqListBySIDUseAvailSdiotResources(clusterList,inReq, countReqFromApi);
			}
			

			/**
			 * building list of SDIoT Requests
			 */
			for(int m=0; m< pairSdiotNameAndSubRequest.size();m++) {
				SdiotBigReqEntry entry ;
				String bigIoTReqID = String.valueOf(countReqFromApi);
				bigReqEntry = buildASDIoTReqEntry(bigIoTReqID, pairSdiotNameAndSubRequest.get(m));
				
				listSdiotBigReq.add(bigReqEntry);
			}
			
			countReqFromApi++;
		}
		
		//print forever for testing the list
		/*System.out.println("\nSize of bigRequest list in exeIoTBigRequestFrGUI = "+ getListSubReqForBigReq().size());
		for(int i=0;i<getListSubReqForBigReq().size();i++) {
			System.out.println("bigReq entry = "+ getListSubReqForBigReq().get(i).toString());
		}*/ 

		/**
		 * recording the ending time of processing one or multiple big IoT requests and sending all sub-requests to associated sdiot systems
		 */
		stopProcess = System.currentTimeMillis();
		
		/**
		 * the processing time of one or multiple IoT requests
		 * i) process the requests
		 * ii) buid a list of sub-requests for the IoT requests
		 */
		processingReq = stopProcess - startProcess;
//		long t = processingReq;
		System.out.println("\n------Total processing time (process IoT requests n orchestrate sdiot resources) of the SDN controller for " + n +" IoT requests is " +processingReq + " milliseconds\n");
//		System.out.println("listBigIoTReq size = " + listSdiotBigReq.size());
//		updateClusterStateToDatabaseUsingInfoFrMysql(reqArea);
		updateClusterStateToDatabaseUsingInfoFrAvailableResources(reqArea);
	}
	
	
	/**
	 * executing an IoT input request
	 * for any input request, the controller always orchestrates sdiot resources to work out the best suitable candidate handling the request
	 * with considering if any sdiot cluster that currently serves IoT requests also provides the same required services
	 * and ask it to provide results for the incoming request
	 * 
	 * we can use this method to get processing time with smart orchestration strategy of the sdn-sdiot controller
	 * prove that with the use of sdn-sdiot system we can save processing time in orchestrating IoT resources
	 * 
	 * @param inReq
	 */
	public void exeIoTBigRequestFrGUIConcerningCurrIoTReq(String inReq) {
		
		/**
		 * get a list of currently-available sdiot resources
		 */
		ArrayList<SdiotCluster> clusterList = getSdiotClusterServices();
		ArrayList<SdiotBigReqEntry> sdiotReqList = getListSubReqForBigReq();
		log.info("\n-------------------------Start processing IoT Requests------------------------------------------");

		/**
		 * start listening to input request (big request)
		 */
//		requestInString = getInputRequestInString();
//		requestInString = inReq;
		String[] inputArr = inReq.split("\\|");

		/**
		 * bigReqID represents the total number of simultaneous input requests
		 */
		int bigReqID = Integer.parseInt(inputArr[5]);
		int n = bigReqID; //the total number of simultaneous input requests
		
		String reqAct = inputArr[0];
		String reqSer = inputArr[1];
		String[] reqConditionArr = inputArr[2].split("\\,");
		String reqArea = reqConditionArr[0];
		
		/**
		 * start recording the time of processing one or multiple simultaneous IoT requests
		 */
		startProcess = System.currentTimeMillis();
		
		/**
		 * 1 loop represents one time of processing one input request
		 */
		
		for(int k=1;k<=n;k++) {

			System.out.println("\n--Big req ID = " + countReqFromApi +"\n");
			
			/**
			 * build a list of big IoT requests
			 */
			bigIoTReqList.add(new IoTBigReqEntry(String.valueOf(countReqFromApi), reqSer, reqArea ));
			
			switch(reqAct) {
			case "GET":{
				//need to check if required services of an arriving IoT request is also requested by current IoT requests
				//if "YES" : no need for orchestration of sdiot clusters to handle the request 
				//and thus only ask the cluster in the currently working cluster to serve the request
				//this approach is only applied for GET action
				//-> need to get a list of required services and list of associated sdiot clusters
				if(clusterList.size()==0) {
					pairSdiotNameAndSubRequest = RequestAnalyser.decomposeBigRequestWithBigReqIDIntoSubReqListBySIDUsingDatabase(inReq, countReqFromApi);
				}
				else {
					pairSdiotNameAndSubRequest = RequestAnalyser.decomposeBigReqIntoSubReqListBySIDUsingAvailableSdiotResources(clusterList,sdiotReqList,inReq, countReqFromApi);
				}
			}
			break;
			case "SET_ON":{
				if(clusterList.size()==0) {
					//if we use the following method, we can not demonstrate the operation of the reschedule function
//					pairSdiotNameAndSubRequest = RequestAnalyser.decomposeBigReqIntoSubReqListUsingDatabaseForCaseSET(inReq, countReqFromApi);
					pairSdiotNameAndSubRequest = RequestAnalyser.decomposeBigRequestWithBigReqIDIntoSubReqListBySIDUsingDatabase(inReq, countReqFromApi);
					
				}
				else {
					pairSdiotNameAndSubRequest = RequestAnalyser.decomposeBigRequestWithBigReqIDIntoSubReqListBySIDUsingAvailableSdiotResources(clusterList,inReq, countReqFromApi);
				}
			}
			break;
			case "SET_OFF":{
				if(clusterList.size()==0) {
					//if we use the following method, we can not demonstrate the operation of the reschedule function
//					pairSdiotNameAndSubRequest = RequestAnalyser.decomposeBigReqIntoSubReqListUsingDatabaseForCaseSET(inReq, countReqFromApi);
					pairSdiotNameAndSubRequest = RequestAnalyser.decomposeBigRequestWithBigReqIDIntoSubReqListBySIDUsingDatabase(inReq, countReqFromApi);
				}
				else {
					pairSdiotNameAndSubRequest = RequestAnalyser.decomposeBigRequestWithBigReqIDIntoSubReqListBySIDUsingAvailableSdiotResources(clusterList,inReq, countReqFromApi);
				}
			}
			break;
			}
			
			/**
			 * decompose the big request into sub-requests and produce a list of sdiot name and associated sub-request
			 */
//			pairSdiotNameAndSubRequest = RequestAnalyser.decomposeBigRequestWithBigReqIDIntoSubReqList(requestInString, countReq);
			
			/**
			 * work out the best sdiot for a required SID -> produce a list of the best sdiot and an associated required-SID 
			 */
			
			
			

			/**
			 * building list of SDIoT Requests
			 */
			for(int m=0; m< pairSdiotNameAndSubRequest.size();m++) {
				SdiotBigReqEntry entry ;
				String bigIoTReqID = String.valueOf(countReqFromApi);
				bigReqEntry = buildASDIoTReqEntry(bigIoTReqID, pairSdiotNameAndSubRequest.get(m));
				
				listSdiotBigReq.add(bigReqEntry);
			}
			
			countReqFromApi++;
		}
		
		//print forever for testing the list
		/*System.out.println("\nSize of bigRequest list in exeIoTBigRequestFrGUI = "+ getListSubReqForBigReq().size());
		for(int i=0;i<getListSubReqForBigReq().size();i++) {
			System.out.println("bigReq entry = "+ getListSubReqForBigReq().get(i).toString());
		}*/ 

		/**
		 * recording the ending time of processing one or multiple big IoT requests and sending all sub-requests to associated sdiot systems
		 */
		stopProcess = System.currentTimeMillis();
		processingReq = stopProcess - startProcess;
//		long t = processingReq;
		System.out.println("\n------Total processing time of the SDN-SDIoT controller for " + n +" IoT requests is " +processingReq/1000 + " seconds\n");
//		System.out.println("listBigIoTReq size = " + listSdiotBigReq.size());
	}
	
	

	/**
	 * to always check the sdiot clusters' responses,
	 * then to reschedule a big iot request that is produced from the sub-request of the cluster response
	 * 
	 */
	public void startCheckingAndReschedulingSubReqFromSdiotUsingDatabase() {

		/**
		 * start recording the processing time
		 */
		startReschedule = System.currentTimeMillis();
		
		Runnable task = new Runnable() {
			public void run() {
				while(true) {
					
//					listSdiotBigReq = getListSubReqForBigIoTRequest();//getListSubReqForBigReq
					listSdiotBigReq = getListSubReqForBigReq();
					
					/**
					 * get list of sub-requests and associated sdiot cluster that cannot handle the allocated sub-request
					 */
					listSdiotAndSubReq = checkClusterResponse(listSdiotBigReq);
					
					
					/**
					 * start listening to input request (big request)
					 */
					if(listSdiotAndSubReq.size()>=1) {
						log.info("-------------------------Start rescheduling a sub-request from a sdiot system-----------------------------------------------");
						
						//get list of sdiot and associate sub-request for rescheduling
						
						System.out.println("\nResponse list size = " + listSdiotAndSubReq.size());
//						System.out.println("\n");
						for(int m=0;m<listSdiotAndSubReq.size();m++) {
//							System.out.println("listSdiotAndSubReq item : " + listSdiotAndSubReq.get(m));
							String[] tmp = listSdiotAndSubReq.get(m).split("\\:");
							String clusterName = tmp[0];
							String subReq = tmp[1];
							System.out.println("\nSubReq that cannot be achieved = "+subReq);
							String newBigReq = requestAnalyser.produceBigRequestFromSubRequest(subReq);
							requestInString = newBigReq;
							
							/**
							 * decompose the big request into sub-requests and produce a list of sdiot name and associated sub-request
							 */
//							pairSdiotNameAndSubRequest = RequestAnalyser.decomposeBigRequestIntoSubReqList(requestInString);
//							pairSdiotNameAndSubRequest = RequestAnalyser.rescheduleABigRequest(newBigReq, clusterName,listBigIoTReq);
							pairSdiotNameAndSubRequest = RequestAnalyser.rescheduleABigRequestUsingDatabase(newBigReq, clusterName);
							
							/**
							 * remove the sdiot cluster with CANNOT/PARTIALLY ACHIEVE response from the list of big requests
							 */
							listSdiotBigReq = RequestAnalyser.removeClusterWithResponseCANNOTFrBigReqList(listSdiotBigReq, clusterName, subReq);
							System.out.println("After removing the cluster with response: listBigIoTReq size : " + listSdiotBigReq.size());
							
							/**
							 * update the new big request that is the replacement of the removed one to the list of big requests
							 */
							listSdiotBigReq = RequestAnalyser.addNewSubReqToBigReqList(listSdiotBigReq, clusterName, pairSdiotNameAndSubRequest);
							System.out.println("After adding the replaced cluster: listBigIoTReq size : " + listSdiotBigReq.size());
							
							/**
							 * recording the ending time of processing the big IoT request and sending all sub-requests to associated sdiot systems
							 */
							endReschedule = System.currentTimeMillis();
							processingReq = endReschedule - startReschedule;
							long t = processingReq;
							System.out.println("\n------Processing time for reschedule all sub-requests = " +processingReq/1000 + " seconds\n");

						}
						}
					}
			}
		};

		// Run the task in a background thread
		Thread backgroundThread = new Thread(task);
		// Terminate the running thread if the application exits
		backgroundThread.setDaemon(true);
		// Start the thread
		backgroundThread.start();
	}
	
	public void startCheckingAndReschedulingSubReqFromSdiotUsingAvailableSdiot() {
		
		/**
		 * get a list of currently-available sdiot clusters
		 */
		ArrayList<SdiotCluster> clusterList = getSdiotClusterServices();
		System.out.println("\nstartCheckingAndReschedulingSubReqFromSdiotUsingAvailableSdiot: cluster list = " + clusterList.size());
		
		/**
		 * start recording the processing time of Rescheduling one or multiple simultaneous IoT requests
		 */
		startReschedule = System.currentTimeMillis();
		
		Runnable task = new Runnable() {
			public void run() {
				while(true) {
					
					listSdiotBigReq = getListSubReqForBigReq();
					
					/**
					 * get list of sub-requests and associated sdiot cluster that cannot handle the allocated sub-request
					 */
					listSdiotAndSubReq = checkClusterResponse(listSdiotBigReq);
//					System.out.println("\n	Response list size = " + listSdiotAndSubReq.size());//can not be here since it run forever
					/*for(int p=0;p< listSdiotAndSubReq.size();p++) {
						System.out.println("	List sdiot with response");
						System.out.println(listSdiotAndSubReq.get(p).toString());
					}*/
					if(listSdiotBigReq.size()>0) {
//						checkCompletionStatusOfSdiotReq(listSdiotBigReq);
						checkExecutionStatusOfSdiotReq();
						checkCompletionStatusOfSdiotReq();
					}
					
					
					/**
					 * start listening to input request (big request)
					 */
					if(listSdiotAndSubReq.size()>=1) {
						log.info("\n-------------------------Start rescheduling a sub-request from a sdiot system-----------------------------------------------");
						
						//get list of sdiot and associate sub-request for rescheduling
//						System.out.println("\n	Response list size = " + listSdiotAndSubReq.size());
//						System.out.println("\n");
						
						for(int m=0;m<listSdiotAndSubReq.size();m++) {
//							System.out.println("listSdiotAndSubReq item : " + listSdiotAndSubReq.get(m));
							String[] tmp = listSdiotAndSubReq.get(m).split("\\:");
							String clusterName = tmp[0];
							String subReq = tmp[1];
							System.out.println("\n	SubReq that cannot be achieved : "+subReq);
							String newBigReq = requestAnalyser.produceBigRequestFromSubRequest(subReq);
							System.out.println("\n	Big request produced from the sub-request : "+newBigReq);
							requestInString = newBigReq;
							
							/**
							 * decompose the big request into sub-requests and produce a list of sdiot name and associated sub-request
							 */
//							pairSdiotNameAndSubRequest = RequestAnalyser.decomposeBigRequestIntoSubReqList(requestInString);
//							pairSdiotNameAndSubRequest = RequestAnalyser.rescheduleABigRequest(newBigReq, clusterName,listBigIoTReq);
							pairSdiotNameAndSubRequest = RequestAnalyser.rescheduleABigRequestUsingAvailableSdiotResource(clusterList,newBigReq, clusterName);
							
							/**
							 * remove the sdiot cluster with CANNOT/PARTIALLY ACHIEVE response from the list of big requests
							 */
							System.out.println("\nStart removing big iot request entry with response----\n");
							listSdiotBigReq = RequestAnalyser.removeClusterWithResponseCANNOTFrBigReqList(listSdiotBigReq, clusterName,subReq);
//							System.out.println("After removing the cluster with response: listBigIoTReq size : " + listSdiotBigReq.size());
							
							/**
							 * update the new big request that is the replacement of the removed one to the list of big requests
							 */
							System.out.println("\n-------start adding new replaced big iot request entry----");
							listSdiotBigReq = RequestAnalyser.addNewSubReqToBigReqList(listSdiotBigReq, clusterName, pairSdiotNameAndSubRequest);
//							System.out.println("After adding the replaced cluster: listBigIoTReq size : " + listSdiotBigReq.size());
							
							/**
							 * recording the ending time of rescheduling one or multiple simultaneous big IoT request and sending all sub-requests to associated sdiot systems
							 */
							endReschedule = System.currentTimeMillis();
							processingReq = endReschedule - startReschedule;
//							long t = processingReq;
							System.out.println("\n------Processing time for reschedule all sub-requests = " +processingReq/1000 + " seconds\n");

						}
						}
					}
			}
		};

		// Run the task in a background thread
		Thread backgroundThread = new Thread(task);
		// Terminate the running thread if the application exits
		backgroundThread.setDaemon(true);
		// Start the thread
		backgroundThread.start();
	}
	
	/**
	 * build an entry of an SD-IoT request
	 * @param reqID
	 * @param aPairSdiotNameAndSubRequest
	 * @return
	 */
	public SdiotBigReqEntry buildASDIoTReqEntry(String bigReqID, String aPairSdiotNameAndSubRequest) {
		SdiotBigReqEntry entry = null;
		String bigIoTReqID = bigReqID;
		String[] aPair = aPairSdiotNameAndSubRequest.split("\\:");
		String clusterName = aPair[0];
		String subRequest = aPair[1];
		
		String[] subRequestArr = subRequest.split("\\|");
		String reqServices = subRequestArr[1];
		
		entry = new SdiotBigReqEntry(bigIoTReqID, clusterName, "Can achieve all required services", reqServices, subRequest, "NO", "OnGoing");
//		System.out.println("From buildABigIoTReqEntry(): BigRequest entry: "+entry.toString());
		
		return entry;
	}
	
	/**
	 * build an entry of an entry of an SD-IoT request
	 * 
	 * this method is used by exeIoTBigRequestFrGUIWithSmartOrch()
	 * any required SID of an incoming request is currently provided by a sdiot systems, 
	 * so the sdn controller no need for further configuration and only needs to retrieve historical data and send to the desired destination
	 * currently we only set the execution status to "YES" and send the first data to expected destination
	 * future work needs to do more about retrieving data and send them according to required time, period
	 * @param reqID
	 * @param aPairSdiotNameAndSubRequest
	 * @return
	 */
	public SdiotBigReqEntry buildASDIoTReqEntryForSmartOrch(String bigReqID, String aPairSdiotNameAndSubRequest) {
		SdiotBigReqEntry entry = null;
		String bigIoTReqID = bigReqID;
		String[] aPair = aPairSdiotNameAndSubRequest.split("\\:");
		String clusterName = aPair[0];
		String subRequest = aPair[1];
		String exeStatus = aPair[2];
		
		String[] subRequestArr = subRequest.split("\\|");
		String reqServices = subRequestArr[1];
		
		entry = new SdiotBigReqEntry(bigIoTReqID, clusterName, "Can achieve all required services", reqServices, subRequest, exeStatus, "OnGoing");
//		System.out.println("From buildABigIoTReqEntry(): BigRequest entry: "+entry.toString());
		
		return entry;
	}
	
	/**
	 * this function can be only applied for the network with only one switch
	 * since it cannot know which switch is connected to the required mininet host
	 */
	public void startListeningToSocketForSendingPktOutMessage() {

		Runnable task = new Runnable() {
			public void run() {
				while(true) {
					log.info("------------------------Task 1: get iot requests-----------------------------------------------");

					for (DatapathId dpid : switchService.getAllSwitchDpids()) {
						IOFSwitch sw = switchService.getActiveSwitch(dpid);
						log.info("----------------Inside startUp()----dpid = {}", dpid.toString());
						log.info("----------Task 3: send packet out messages to switches and hosts-----------------------------------------------");
						sendPacketoutMessage(requestInByteArr,sw);

						String[] strArr = null;
						String ipv4 = "";
						byte[] subRequestInByteArr = null;
						for(int i=0;i<pairSdiotIpAddressAndSubRequest.size();i++) {
							strArr = pairSdiotIpAddressAndSubRequest.get(i).split(":");
							ipv4 = strArr[0];
							log.info("Sub-request sent by floodlight is: {}", strArr[1]);
							subRequestInByteArr = strArr[1].getBytes();
							//							sendSubRequestToSdiot(subRequestInByteArr,sw,ipv4);
						}
					}
				}
			}
		};

		// Run the task in a background thread
		Thread backgroundThread = new Thread(task);
		// Terminate the running thread if the application exits
		backgroundThread.setDaemon(true);
		// Start the thread
		backgroundThread.start();


	}
	
	
	public void startTCPSocketServer() {

		Runnable task = new Runnable() {
			public void run() {
				while(true) {
					try {
						String messToClient = getInputForTcpServerSocket();
						sendSubRequestToSdiotUsingTCPSocket(messToClient);
						
					} catch (IOException e) {
						e.printStackTrace();
					}
				}
			}
		};

		// Run the task in a background thread
		Thread backgroundThread = new Thread(task);
		// Terminate the running thread if the application exits
		backgroundThread.setDaemon(true);
		// Start the thread
		backgroundThread.start();
	}

	public JSONArray sendPostRequestForSwitchInfo(String requestUrl, String payload) {
		try {
			URL url = new URL(requestUrl);
			HttpURLConnection connection = (HttpURLConnection) url.openConnection();

			connection.setDoInput(true);
			connection.setDoOutput(true);
			connection.setRequestMethod("GET");
			BufferedReader in = new BufferedReader(new InputStreamReader(connection.getInputStream()));
			String inputLine = "";
			String strJSON = "";

			while((inputLine = in.readLine()) != null) {
				strJSON += inputLine;
			}
			in.close();
			return getSwitchesFromFloodlight(strJSON);
		} catch (Exception e) {
			throw new RuntimeException(e.getMessage());
		}

	}

	public JSONArray sendPostRequestForDeviceInfo(String requestUrl, String payload) {
		try {
			URL url = new URL(requestUrl);
			HttpURLConnection connection = (HttpURLConnection) url.openConnection();

			connection.setDoInput(true);
			connection.setDoOutput(true);
			connection.setRequestMethod("GET");
			BufferedReader in = new BufferedReader(new InputStreamReader(connection.getInputStream()));
			String inputLine = "";
			String strJSON = "";
			while((inputLine = in.readLine()) != null) {
				strJSON += inputLine;
			}
			in.close();
			return getDevicesFromFloodlight(strJSON);
		} catch (Exception e) {
			throw new RuntimeException(e.getMessage());
		}

	}



	public JSONArray getSwitchesFromFloodlight(String strJSON) {
		// Sample {"inetAddress":"\/192.168.174.217:33144","connectedSince":1560667010568,"openFlowVersion":"OF_13","switchDPID":"00:00:00:00:00:00:00:01"}
		System.out.println("\n");
		log.info("------------------------------inside getSwitchesFromFloodlight-----------------------------------------------------");
		System.out.println("\n");
		if (!strJSON.isEmpty()) {
			try {
				JSONParser parser = new JSONParser();
				JSONArray jsonArr = (JSONArray)parser.parse(strJSON);
				for (int i = 0; i < jsonArr.size(); i++) {
					JSONObject obj = (JSONObject)jsonArr.get(i);
					System.out.printf("------getSwitchesFromFloodlight() method: %s \n", obj.toString());
				}
				return jsonArr;
			} catch(Exception ex) {
				ex.printStackTrace();
			}

		}
		return null;
	}


	public JSONArray getDevicesFromFloodlight(String strJSON) {
		if (!strJSON.isEmpty()) {
			try {
				JSONParser parser = new JSONParser();
				System.out.println(parser.parse(strJSON).toString());
				JSONObject jsonObj = (JSONObject)parser.parse(strJSON);
				JSONArray jsonArr = null;
				if (jsonObj.get("devices") != null) {
					jsonArr = (JSONArray)parser.parse(jsonObj.get("devices").toString());
					for (int i = 0; i < jsonArr.size(); i++) {
						JSONObject obj = (JSONObject)jsonArr.get(i);
						System.out.printf("Mac Address:%s, IPv6:%s \n", obj.get("mac"), obj.get("ipv6"));
					}
				}
				return jsonArr;
			} catch(Exception ex) {
				ex.printStackTrace();
			}

		}
		return null;
	}	

	/**
	 * this function retrieves the API to get info about all active switches and their connected hosts
	 * then produces a list of switches MAC addresses and their connected hosts
	 * 
	 * final String DEVICES_API = "http://localhost:8080/wm/device/";
	 *	listSwitchHost = getSwitchHostList(DEVICES_API, "");
	 *
	 * * for tesing cluster response
	 * 
	 * SET_OFF|SID01,SID05|Area1,10,5,121|1|dst6|1
	 * GET|SID05,SID03,SID04|Area1,10,5,121|1|dst6|1
	 * GET|SID02,SID01|Area1,10,5,121|1|dst6|1
	 * GET|SID01,SID05|Area1,10,5,121|1|dst7|1
	 * SET_ON|SID03,SID05,SID04,SID01,SID02|Area1,10,5,121|1|dst6|1
	 * GET|SID03,SID05,SID04,SID02|Area1,10,5,121|1|dst6|1
	 * SET_OFF|SID01,SID05,SID04,SID03,SID02|Area1,10,5,121|1|dst6|1
	 * 
	 * @param requestUrl
	 * @param payload
	 * @return
	 */
	public ArrayList<String> getSwitchHostList(String requestUrl, String payload) {
		//		System.out.println("\n");
		//		log.info("------------------------------inside getDevicesFromFloodlight-----------------------------------------------------");
		//		System.out.println("\n");

		/**
		 * establish a connection to the API using url
		 * to get the JSON string of devices information
		 */
		ArrayList<String> pairSwMacAddrAndHostIPAddr = new ArrayList<String>();
		String strJSON = "";
		try {
			URL url = new URL(requestUrl);
			HttpURLConnection connection = (HttpURLConnection) url.openConnection();

			connection.setDoInput(true);
			connection.setDoOutput(true);
			connection.setRequestMethod("GET");
			BufferedReader in = new BufferedReader(new InputStreamReader(connection.getInputStream()));
			String inputLine = "";

			while((inputLine = in.readLine()) != null) {
				strJSON += inputLine;
			}
			in.close();
		} catch (Exception e) {
			throw new RuntimeException(e.getMessage());
		}

		/***
		 * analysing the achieved JSON string to produce the expected list
		 */
		if (!strJSON.isEmpty()) {
			try {
				JSONParser parser = new JSONParser();
				//				System.out.println(parser.parse(strJSON).toString());
				JSONObject jsonObj = (JSONObject)parser.parse(strJSON);
				JSONArray jsonArr = null;
				JSONArray subJsonArrSwitchMac = null;
				JSONArray jsonArrHostIPv4 = null;
				JSONArray jsonArrHostMac = null;

				if (jsonObj.get("devices") != null) {
					jsonArr = (JSONArray)parser.parse(jsonObj.get("devices").toString());//contain 10 attachments
					System.out.println("\n"+jsonArr);
//					System.out.println("get(\"devices\") size "+jsonArr.size());//always size 10
					for (int i = 0; i < jsonArr.size(); i++) {
//						System.out.println("\njsonArr size = " + jsonArr.size());
						JSONObject obj = (JSONObject)jsonArr.get(i);
						subJsonArrSwitchMac = (JSONArray)parser.parse(obj.get("attachmentPoint").toString());//subJsonArrSwitchMac[{"port":"1","switch":"00:00:00:00:00:00:00:01"}]
//						System.out.println("\nsubJsonArrSwitchMac" + subJsonArrSwitchMac);
						jsonArrHostIPv4 =  (JSONArray)parser.parse(obj.get("ipv4").toString());
//						System.out.println("\njsonArrHostIPv4: "+jsonArrHostIPv4);
						jsonArrHostMac = (JSONArray)parser.parse(obj.get("mac").toString());
//						System.out.println("\njsonArrHostMac: "+jsonArrHostMac);
						
//						System.out.println("\nsubJsonArrSwitchMac size = " + subJsonArrSwitchMac.size());//subJsonArrSwitchMac size = 1
//						System.out.println("\njsonArrHostIPv4 size = " + jsonArrHostIPv4.size());//jsonArrHostIPv4 size = 2: "ipv4":["10.0.0.254","172.19.121.76"],
//						System.out.println("\njsonArrHostMac size = " + jsonArrHostMac.size());//jsonArrHostMac size = 1

						for(int j=0;j<subJsonArrSwitchMac.size();j++) {
							JSONObject objTmp = (JSONObject)subJsonArrSwitchMac.get(j);
							for(int k=0;k<jsonArrHostMac.size();k++) {//Mac addr is always 1
//								System.out.println("\n jsonArrHostIPv4 size = " + jsonArrHostIPv4.size());
//								System.out.printf("--------Pair[Switch-MAC, IPv4]: %s:%s \n", objTmp.get("switch"), jsonArrHostIPv4.get(k));
								if(jsonArrHostIPv4.size()!=0) { //in some cases, jsonArrHostIPv4 size =0 or 2
									pairSwMacAddrAndHostIPAddr.add(objTmp.get("switch") + ";" + jsonArrHostIPv4.get(k) + ";" + jsonArrHostMac.get(k));
								}
								
							}

						}

					}
				}

				/**
				 * for testing the value of the pairSwMacAddrAndHostIPAddr
				 */
				/*System.out.println("size of pairSwMacAddrAndHostIPAddr = " + pairSwMacAddrAndHostIPAddr.size());
				for(int n = 0; n< pairSwMacAddrAndHostIPAddr.size();n++) {
					String[] itemArr = pairSwMacAddrAndHostIPAddr.get(n).split("\\;");
					System.out.println("MAC;IPv4 = " + itemArr[0] + ", " + itemArr[1]);
				}*/

			} catch(Exception ex) {
				ex.printStackTrace();
			}
		}

		return pairSwMacAddrAndHostIPAddr;
	}

	/**
	 * this function send sub-request to sdiot via UDP socket, not using packet out message
	 * @param subRequest
	 * @param dst
	 * @param bigReqId
	 * @throws SocketException
	 */
	public void sendSubRequestToSdiotUsingUDPSocket(String subRequest, String dst, int bigReqId) throws SocketException{
		DatagramSocket serverSocket = null;
		String request = subRequest;
		byte[] sendData = new byte[65508];
		sendData = request.getBytes();
		int serverPort= 8000;
		int i = 1;
		while(i<=2){
			try{
				serverSocket = new DatagramSocket();
				serverSocket.setReuseAddress(true);
				serverSocket.setSoTimeout(50);
				InetAddress ipAddr = InetAddress.getByName(dst);
				DatagramPacket sendPacket = new DatagramPacket(sendData,sendData.length,ipAddr,serverPort);
				serverSocket.send(sendPacket);
				i++;
				serverSocket.close();
			} catch(SocketException ex){
				System.err.println("Exception SocketException senddata " + Integer.toString(4002) + ex.getMessage());
			} catch (IOException ex){
				System.err.println("Exception IOException senddata " + Integer.toString(4002) + ex.getMessage());
			} finally{
				serverSocket.close();
			}
		}
	}

	/**
	 * this function send sub-request to sdiot via TCP socket, not using packet out message
	 * @param subRequest
	 * @param dst
	 * @param bigReqId
	 * @throws IOException 
	 */
	public void sendSubRequestToSdiotUsingTCPSocket(String messageToClient) throws IOException{
		int SERVER_PORT = 6653;
//		int SERVER_PORT = 8000;
		ServerSocket serverSocket = new ServerSocket(SERVER_PORT);
		while (true) {
			try {
				Socket socket = serverSocket.accept();
				System.out.println("Server socket: " + socket);
				OutputStream os = socket.getOutputStream();
				InputStream is =  socket.getInputStream();
				BufferedReader inFromClient = new BufferedReader (new InputStreamReader (is));
				DataOutputStream outToClient = new DataOutputStream (os);
				String messFromClient = inFromClient.readLine ();
				System.out.println ("Client Message: " + messFromClient);

				//message to client
				String messToClient = "Message from sdiot module";
//				String messToClient = messageToClient;
				
				outToClient.writeBytes(messToClient);
//				socket.close();
				
			} catch (IOException e) {
				System.err.println(" Connection Error: " + e);
			}

			finally {
				if (serverSocket != null) {
					serverSocket.close();
				}
			}


		}

	}

	/**
	 * get the list of current sdiot requests that are decomposed from big IoT Requests
	 * 
	 * execution of GET request for sdiotResource
	 */
	@Override
	public ArrayList<SdiotBigReqEntry> getListSubReqForBigReq() {
//		listSdiotBigReq = getListSubReqForBigIoTRequest();//createDummyBigRequestList
//		listBigIoTReq = createDummyBigRequestList();
		return listSdiotBigReq;
	}
	
/*	public ArrayList<SdiotBigReqEntry> getListSubReqForBigIoTRequest() {
//		System.out.println("\n sdiotModule: getListBigIoTRequest.size() = "+  this.listBigIoTReq.size());
		return this.listSdiotBigReq;
	}*/

	
	/**
	 * this method cause exception because it uses index for updated cluster response
	 * when the big request entry is removed and replaced by a new big request entry, the index is changed
	 * 
	 * execution of PUT request for sdiotResource
	 * PUT response of sdiot cluster to API
	 */
	@Override
	/*public boolean updateClusterResponseUsingEntryIndex(Map<String, Object> entry) {
		
		if (entry == null) {
			try {
				throw new Exception("Entry cannot be null!");
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
		
		ArrayList<SdiotBigReqEntry> list = this.getListBigReq();
		
		if (listBigIoTReq.size() == 0) {
			try {
				throw new Exception("There is no items in the list!");
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
		int entryID = Integer.parseInt(entry.get("bigReqID").toString());
		String entryClusterName = entry.get("clusterName").toString();
		String reqSer = entry.get("requiredService").toString();
		
//		int entryIndex = this.findIndexByID(entryID);
		int entryIndex = 0;
		try {
			entryIndex = this.findIndexByIDAndClusterName(entryID, entryClusterName, reqSer);
		} catch (Exception e) {
			e.printStackTrace();
		}
		
		if(entryIndex == -1) {
			try {
				throw new Exception("Cannot find item with ID: " + entryID + " in the list");//error happens here in SET_OFF cases: java.lang.Exception: Cannot find item with ID: 15 in the list
			} catch (Exception e) {
				e.printStackTrace();
			}
		} 
				
		if(entryIndex != -1) {
			System.out.println("EntryIndex is: " + entryIndex);
			SdiotBigReqEntry bigReqEntry = listBigIoTReq.get(entryIndex);
			if (bigReqEntry == null) {
				try {
					throw new Exception("SdiotBigReqEntry object retrieved is null!");
				} catch (Exception e) {
					e.printStackTrace();
				}
			}
			bigReqEntry.setBigReqID(entry.get("bigReqID").toString());
			bigReqEntry.setClusterName(entry.get("clusterName").toString());
			bigReqEntry.setClusterResponse(entry.get("clusterResponse").toString());
			bigReqEntry.setRequiredServices(entry.get("requiredService").toString());
			listBigIoTReq.set(entryIndex, bigReqEntry);
		}
		
		return true;
	}*/
	
	/**
	 * execution of PUT request for sdiotResource
	 * PUT response of sdiot cluster to API
	 */
	public boolean updateClusterResponse(Map<String, Object> entry) {
		ArrayList<SdiotBigReqEntry> listBigReq = this.getListSubReqForBigReq();
		SdiotBigReqEntry bigReqEntry = null;
		
		if (entry == null) {
			try {
				throw new Exception("Entry cannot be null!");
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
		
		
		
		if (listSdiotBigReq.size() == 0) {
			try {
				throw new Exception("There is no items in the list!");
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
		String entryID = entry.get("bigReqID").toString();
		String entryClusterName = entry.get("clusterName").toString();
		String entryReqSer = entry.get("requiredService").toString();
		String entryClusterRes = entry.get("clusterResponse").toString();
		
		String bigEnryId = "";
		String bigEntryClusterName = "";
		String bigEntryRequiredService = "";
//		String bigEntryClusterResponse = "";
		for(int i=0;i<listBigReq.size();i++) {
			bigReqEntry = listBigReq.get(i);
			bigEnryId = bigReqEntry.getBigReqID();
			bigEntryClusterName = bigReqEntry.getClusterName();
			bigEntryRequiredService = bigReqEntry.getRequiredServices();
			if(bigEnryId.equalsIgnoreCase(entryID) &&
					bigEntryClusterName.equalsIgnoreCase(entryClusterName) &&
					bigEntryRequiredService.equalsIgnoreCase(entryReqSer)) {
				bigReqEntry.setClusterResponse(entryClusterRes);
			}
		}
		
		
		return true;
	}
	
	
	
	/**
	 * execution of PUT request for sdiotResource
	 * PUT new Execuated Status for a sub-request of sdiot cluster to API
	 */
	@Override
	public boolean updateExecStatus(Map<String, Object> entry) {
		ArrayList<SdiotBigReqEntry> listBigReq = this.getListSubReqForBigReq();
		
		if (entry == null) {
			try {
				throw new Exception("Entry cannot be null!");
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
		
		if (listSdiotBigReq.size() == 0) {
			try {
				throw new Exception("There is no items in the list!");
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
		
		
		String entryID = entry.get("bigReqID").toString();
		String entryClusterName = entry.get("clusterName").toString();
		String entryReqService = entry.get("requiredService").toString();
		String entryExeStatus = entry.get("executed").toString();
		
		String bigEntryId = "";
		String bigEntryClusterName = "";
		String bigEntryRequiredService = "";
//		String bigExecutedStatus = "";
		
		for(int i=0;i<listBigReq.size();i++) {
			bigReqEntry = listBigReq.get(i);
			bigEntryId = bigReqEntry.getBigReqID();
			bigEntryClusterName = bigReqEntry.getClusterName();
			bigEntryRequiredService = bigReqEntry.getRequiredServices();
			
			if ((bigEntryId.equalsIgnoreCase(entryID)) 
					&& bigEntryClusterName.equalsIgnoreCase(entryClusterName)
					&& bigEntryRequiredService.equalsIgnoreCase(entryReqService)) {
				bigReqEntry.setExecuted(entryExeStatus); 
			}
			
		}
		
		return true;
	}
	
	/**
	 * execution of POST request for sdiot cluster Resource
	 * sdiot cluster send its update status about its available resources to the SDN controller
	 * when it firstly join the SDN domain or communicates with the SDN controller
	 */
	public boolean postSdiotClusterResources(Map<String, Object> entry) {//postSdiotClusterResources
		ArrayList<SdiotCluster> listSdiotCluster = this.getSdiotClusterServices();
		SdiotCluster sdiotClusterEntry = new SdiotCluster();
		
		if (entry == null) {
			try {
				throw new Exception("Entry cannot be null!");
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
		
		//clusterName managedArea managedLocation providedServices
		String entryClusterName = entry.get("clusterName").toString();
		String entryArea = entry.get("managedArea").toString();
		String entryLocations = entry.get("managedLocation").toString();
		String entryProvidedServices = entry.get("providedServices").toString();
				
		sdiotClusterEntry.setClusterName(entryClusterName);
		sdiotClusterEntry.setManagedArea(entryArea);
		sdiotClusterEntry.setManagedLocation(entryLocations);
		sdiotClusterEntry.setProvidedServices(entryProvidedServices);
		sdiotClusterEntry.setNumTask("0");
//		System.out.println("\n numtask = " + sdiotClusterEntry.getNumTask());
		
		listSdiotCluster.add(sdiotClusterEntry);
		
		return true;
	}
	
	/**
	 * execution of POST request for sdiot cluster Resource
	 * sdiot cluster send its update status about its available resources to the SDN controller
	 * when it firstly join the SDN domain or communicates with the SDN controller
	 */
	public boolean updateSdiotClusterResources(Map<String, Object> entry) {
		ArrayList<SdiotCluster> listSdiotCluster = this.getSdiotClusterServices();
		SdiotCluster sdiotClusterEntry = null;
		
		if (entry == null) {
			try {
				throw new Exception("Entry cannot be null!");
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
		
		
		
		if (listSdiotBigReq.size() == 0) {
			try {
				throw new Exception("There is no items in the list!");
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
		//clusterName managedArea managedLocation providedServices
		String entryClusterName = entry.get("clusterName").toString();
		String entryArea = entry.get("managedArea").toString();
		String entryLocations = entry.get("managedLocation").toString();
		String entryProvidedServices = entry.get("providedServices").toString();
		
		
		String sdiotClusterEntryName = "";
		String sdiotClusterEntryArea = "";
		String sdiotClusterEntryLocations = "";
		String sdiotClusterEntryServices = "";
		for(int i=0;i<listSdiotCluster.size();i++) {
			sdiotClusterEntry = listSdiotCluster.get(i);
			sdiotClusterEntryName = sdiotClusterEntry.getClusterName();
			sdiotClusterEntryArea = sdiotClusterEntry.getManagedArea();
			sdiotClusterEntryLocations = sdiotClusterEntry.getManagedLocation();
			sdiotClusterEntryServices = sdiotClusterEntry.getProvidedServices();
			
			if(sdiotClusterEntryName.equalsIgnoreCase(entryClusterName) 
					&& sdiotClusterEntryArea.equalsIgnoreCase(entryArea)) {
//					&& sdiotClusterEntryLocations.equalsIgnoreCase(entryLocations) 
//					&& sdiotClusterEntryServices.equalsIgnoreCase(entryProvidedServices)) {
				sdiotClusterEntry.setProvidedServices(entryProvidedServices);
			}
		}
		
		
		return true;
	}
	
	////the followings are copied from SdiotService
	/**
	 * get list of all cluster names serving for all big IoT Requests
	 */
	public ArrayList<String> getAllClusterName(int reqID){
		listSdiotBigReq = this.createDummyBigRequestList();
		ArrayList<String> list = new ArrayList<String>();
		for(int i=0;i<listSdiotBigReq.size();i++) {
			list.add(listSdiotBigReq.get(i).getClusterName());
		}
		return list;
	}
	
	public void setListBigReq(ArrayList<SdiotBigReqEntry> list) {
		this.listSdiotBigReq = list;
	}

	/**
	 * create a dummy list of big iot requests
	 */
	public ArrayList<SdiotBigReqEntry> createDummyBigRequestList(){
		ArrayList<SdiotBigReqEntry> listIoTReq = new ArrayList<SdiotBigReqEntry>();
		listIoTReq.add(new SdiotBigReqEntry("1", "sdiot1", "can achieve all", "SID01"));
		listIoTReq.add(new SdiotBigReqEntry("2", "sdiot2", "can achieve all", "SID02"));
		listIoTReq.add(new SdiotBigReqEntry("3", "sdiot3", "can achieve all", "SID03"));
		listIoTReq.add(new SdiotBigReqEntry("4", "sdiot4", "can achieve all", "SID04"));
		return listIoTReq;
	}
	
	public boolean updateBigRequestEntry(Map<String, Object> entry) throws Exception {
		if (entry == null) {
			throw new Exception("Entry cannot be null!");
		}
		
		ArrayList<SdiotBigReqEntry> list = this.getListSubReqForBigReq();
		
		if (list.size() == 0) {
			throw new Exception("There is no items in the list!");
		}
		int entryID = Integer.parseInt(entry.get("bigReqID").toString());
		String entryClusterName = entry.get("clusterName").toString();
		String reqSer = entry.get("requiredService").toString();
		
//		int entryIndex = this.findIndexByID(entryID);
		int entryIndex = this.findIndexByIDAndClusterName(entryID, entryClusterName, reqSer);
		
		if(entryIndex == -1) {
			throw new Exception("Cannot find item with ID: " + entryID + " in the list");
		}
		System.out.println("EntryIndex is: " + entryIndex);
		SdiotBigReqEntry bigReqEntry = list.get(entryIndex);
		if (bigReqEntry == null) {
			throw new Exception("SdiotBigReqEntry object retrieved is null!");
		}
		bigReqEntry.setClusterName(entry.get("clusterName").toString());
		bigReqEntry.setClusterResponse(entry.get("clusterResponse").toString());
		bigReqEntry.setRequiredServices(entry.get("requiredService").toString());
		list.set(entryIndex, bigReqEntry);
		return true;
	}
	
	/**
	 * find a big iot request entry using big request ID
	 * @param ID
	 * @return
	 * @throws Exception
	 */
	public int findIndexByID(int ID) throws Exception {
		ArrayList<SdiotBigReqEntry> list = this.getListSubReqForBigReq();
		System.out.println("\nfindIndexByID---input ID = " + ID);
		
		if (list.size() == 0) {
			throw new Exception("There is no items in the list!");
		}
		
		for (int i = 0; i < list.size(); i++) {
			SdiotBigReqEntry currentEntry = list.get(i);
			System.out.println("currentEntryId " + currentEntry.getBigReqID() + " compare to entryID: " + String.valueOf(ID));
			if (Integer.parseInt(currentEntry.getBigReqID()) == ID) {
				return i;
			}
		}
		return -1;
	}
	
	/**
	 * find a big iot request entry using big request ID and cluster name
	 * 
	 * we need para reqSer since one big request may be decomposed into some sub-requests for different SID but for the same sdiot
	 * @param ID
	 * @return
	 * @throws Exception
	 */
	public int findIndexByIDAndClusterName(int ID, String clusterName, String reqSer) throws Exception {
		ArrayList<SdiotBigReqEntry> list = this.getListSubReqForBigReq();
		System.out.println("\nfindIndexByID---input ID = " + ID);
		
		if (list.size() == 0) {
			throw new Exception("There is no items in the list!");
		}
		
		for (int i = 0; i < list.size(); i++) {
			SdiotBigReqEntry currentEntry = list.get(i);
//			System.out.println("currentEntryId " + currentEntry.getBigReqID() + " compare to entryID " + String.valueOf(ID) + "; clusterName = " +currentEntry.getClusterName());
			if ((Integer.parseInt(currentEntry.getBigReqID()) == ID) && currentEntry.getClusterName().equalsIgnoreCase(clusterName)
					&& currentEntry.getRequiredServices().equalsIgnoreCase(reqSer)) {
				return i;
			}
		}
		return -1;
	}
	
	/**
	 * check if there is any response of "CAN NOT or PARTIALLY ACHIEVE ..." from a sidot system
	 * then reschedule the sub-request
	 * then remove the orchestrated sdiot for the required service from the list of SdiotBigReqEntry
	 * 
	 * @param list of cluster name with the CAN NOT response and corresponding sub-request
	 * @return
	 */
	public ArrayList<String> checkClusterResponse(ArrayList<SdiotBigReqEntry> list) {
		/**
		 * the listClusterNameAndSubRequest contain list of pair [sdiotName:sub-request]
		 * sdiotName is the name of sdiot system that cannot achieve the required services
		 * sub-request that allocated to the sdiot, it needs to be sent back the SDN controller for further rescheduling resources
		 * 
		 */
		ArrayList<String> listClusterNameAndSubRequest = new ArrayList<>();
		String aPairSdiotNameAndSubReq ="";
		SdiotBigReqEntry entry;
		String clusterName ="";
		String subRequest = "";
		for(int i=0;i<list.size();i++) {
			entry = list.get(i);
			if(entry.getClusterResponse().equalsIgnoreCase("Cannot achieve all") || //java.lang.NullPointerException
					entry.getClusterResponse().equalsIgnoreCase("Partially achieve")) {
				clusterName = entry.getClusterName();
				subRequest = entry.getSubReq();
				aPairSdiotNameAndSubReq = clusterName + ":" + subRequest;
//				System.out.println("aPairSdiotNameAndSubReq = " + aPairSdiotNameAndSubReq);
				listClusterNameAndSubRequest.add(aPairSdiotNameAndSubReq);
			}
		}
		return listClusterNameAndSubRequest;
	}

	@Override
	public ArrayList<SdiotCluster> getSdiotClusterServices() {
		return this.listSdiotClusters;
	}
	
	/**
	 * get a list of Big IoT Requests
	 */
	@Override
	public ArrayList<IoTBigReqEntry> getBigReqList() {
//		System.out.println("SdiotModule: getBigReqList() size = " + bigIoTReqList.size());
		return this.bigIoTReqList;
	}
	
	/**
	 * any Big IoT Request is entered in GUI is POST to API
	 * the input request is executed by exeIoTBigRequestFrGUI(input)
	 */
	@Override
	public boolean addBigIoTReq(Map<String, Object> entry) {
		if (entry == null) {
			try {
				throw new Exception("Entry cannot be null!");
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
		
		String entryInput = entry.get("input").toString();
//		exeIoTBigRequestFrGUI(entryInput);
		/*try {
			exeIoTBigRequestFrGUIWithSmartOrch(entryInput);
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}*/
		exeIoTBigRequestFrGUIWithoutSmartOrch(entryInput);
		return true;
	}
	
	/**
	 * receiving info from destination of iot application via POST message
	 * adding IoT results to API
	 */
	@Override
	public boolean addIoTAppEntry(Map<String, Object> entry) {
		if (entry == null) {
			try {
				throw new Exception("Entry cannot be null!");
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
		
		String entryDest = entry.get("dest").toString();
		String entrySender = entry.get("sender").toString();
		String entryResult = entry.get("result").toString();

		IoTAppEntry appEntry = new IoTAppEntry(entryDest,entrySender,entryResult);
//		System.out.println("SdiotModule: AppEntry = " + appEntry.toString());
		bigIoTAppList.add(appEntry);
		
		return true;
	}
	
	/**
	 * get a list of IoT Applicaiont entries representation of destination of iot requests
	 */
	@Override
	public ArrayList<IoTAppEntry> getIoTAppList() {
		return this.bigIoTAppList;
	}
	
	/**
	 * the state of each sdiot cluster = the total number of sdiot requests that is serving by the sdiot cluster
	 * @param reqArea
	 */
	public void updateClusterStateToDatabaseUsingInfoFrAvailableResources(String reqArea) {
		
		/**
		 * get a list of current sdiot requests that are decomposed from IoT big requests
		 */
		ArrayList<SdiotBigReqEntry> listSdiotReq = this.getListSubReqForBigReq();
		SdiotBigReqEntry sdiotReqEntry = null;
		
		
		/**
		 * get a list of currently-available sdiot resources
		 */
		ArrayList<SdiotCluster> clusterList = this.getSdiotClusterServices();
		SdiotCluster clusterNameEntry = null;
		String clusterName ="";
		
		/**
		 * get a list of sdiot name in the database
		 */
/*		ArrayList<String> clusterNameList = RequestAnalyser.getIoTClusterListByLocIdFrDatabase(reqArea);
		String clusterNameEntry = "";
		String clusterName ="";*/
		
//		System.out.println("\nupdateClusterStateToDatabase() ");
		
		for(int i=0;i<clusterList.size();i++) {
			int countClusterState = 0;
			clusterNameEntry = clusterList.get(i);
			clusterName = clusterNameEntry.getClusterName();
//			clusterName = clusterEntry.getClusterName();
			for(int j=0;j<listSdiotReq.size();j++) {
				sdiotReqEntry = listSdiotReq.get(j);
				if(sdiotReqEntry.getClusterName().equalsIgnoreCase(clusterName)) {
					countClusterState = countClusterState +1;
				}
			}
//			System.out.println("state of " + clusterName + " is "+ countClusterState);
			updateToMysql(countClusterState, clusterName);
		}
	}
	
	/**
	 * the state of each sdiot cluster = the total number of sdiot requests that is serving by the sdiot cluster
	 * @param reqArea
	 */
	public void updateClusterStateToDatabaseUsingInfoFrMysql(String reqArea) {
		
		/**
		 * get a list of current sdiot requests that are decomposed from IoT big requests
		 */
		ArrayList<SdiotBigReqEntry> listSdiotReq = this.getListSubReqForBigReq();
		SdiotBigReqEntry sdiotReqEntry = null;
		
		
		/**
		 * get a list of currently-available sdiot resources
		 */
//		ArrayList<SdiotCluster> clusterNameList = this.getSdiotClusterServices();
//		SdiotCluster clusterNameEntry = null;
		
		/**
		 * get a list of sdiot name in the database
		 */
		ArrayList<String> clusterNameList = RequestAnalyser.getIoTClusterListByLocIdFrDatabase(reqArea);
		String clusterNameEntry = "";
		String clusterName ="";
		
//		System.out.println("\nupdateClusterStateToDatabase() ");
		
		for(int i=0;i<clusterNameList.size();i++) {
			int countClusterState = 0;
			clusterNameEntry = clusterNameList.get(i);
			clusterName = clusterNameEntry;
//			clusterName = clusterEntry.getClusterName();
			for(int j=0;j<listSdiotReq.size();j++) {
				sdiotReqEntry = listSdiotReq.get(j);
				if(sdiotReqEntry.getClusterName().equalsIgnoreCase(clusterNameEntry)) {
					countClusterState = countClusterState +1;
				}
			}
//			System.out.println("state of " + clusterName + " is "+ countClusterState);
			updateToMysql(countClusterState, clusterName);
		}
	}
	
	
	public void updateToMysql(int state, String name) {
		String strSQL = "UPDATE tab_iot_cluster set state = " + state + " WHERE cluster_name = '" + name + "';";
//		System.out.println("\nupdateToMysql() ");
        try {
            ConnectDatabase c = new ConnectDatabase();
            Statement stmt = (Statement) c.getConnection().createStatement();
            stmt.executeUpdate(strSQL);

        } catch (SQLException e) {
            System.out.println(e.getMessage());
        }
	}

	/**
	 * whenever a sdiot cluster complete an sdiot sub-request, it remove the app entry in its AppTable, then
	 * it sends announcement to the floodlight 	to update on the status of execution of a sdiot sub-request
	 * status = "Completely done"
	 */
	@Override
	public boolean updateStatus(Map<String, Object> entry) {
		ArrayList<SdiotBigReqEntry> listSubReq = this.getListSubReqForBigReq();
		SdiotBigReqEntry subReqEntry = null;
		
		if (entry == null) {
			try {
				throw new Exception("Entry cannot be null!");
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
		
		if (listSdiotBigReq.size() == 0) {
			try {
				throw new Exception("There is no items in the list!");
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
		//get values from input JSON entry sent from sdiot clusters
//		String entryID = entry.get("bigReqID").toString();
		String entryClusterName = entry.get("clusterName").toString();
		String entrySubReq = entry.get("subReq").toString();
		String entryStatus = entry.get("status").toString();
		
		//values of an sdiot subrequest entry in the SDIoT Requests list
//		String subReqEntryId = "";
		String subReqEntryClusterName = "";
		String subReqEntrySubReq = "";
		
		for(int i=0;i<listSubReq.size();i++) {
			subReqEntry = listSubReq.get(i);
//			subReqEntryId = subReqEntry.getBigReqID();
			subReqEntryClusterName = subReqEntry.getClusterName();
			subReqEntrySubReq = subReqEntry.getSubReq();
			if( //subReqEntryId.equalsIgnoreCase(entryID) &&
					subReqEntryClusterName.equalsIgnoreCase(entryClusterName) &&
					subReqEntrySubReq.equalsIgnoreCase(entrySubReq)) {
				subReqEntry.setStatus(entryStatus);
//				System.out.println("\nUpdate status of SDIoT SubReq: " + entryStatus);
//				System.out.println("\nSDIoT SubReq with new status: " + subReqEntry.toString());
			}
		}
		
		
		return true;
	}
	
	/**
	 * if all sdiot requests of an IoT request completely achieve all required services in the required period,
	 * the SDN controller notices that all required services for the IoT request have been completely achieved during the required period
	 * 
	 * notes: when an application entry in an AppTable of each SDVS is removed, which means that an SDIoT request has been completely done, then
	 * the sdiot controller of that SDVS announces the SDN controller that the allocated SDIoT request has been completely done
	 * 
	 */
//	public void checkCompletionStatusOfSdiotReq(ArrayList<SdiotBigReqEntry> listSubReq) {
	public void checkCompletionStatusOfSdiotReq() {	
		
		ArrayList<IoTBigReqEntry> listIoTBigReq = this.getBigReqList();
		ArrayList<SdiotBigReqEntry> listSubReq = this.getListSubReqForBigReq();
		String status = "Completely Done";
		IoTBigReqEntry bigReqEntry = null;
		String bigReqID = "";
		int numOfSdiotReqPerBigReq =0;
		int numOfSdiotReqIsDone =0;
		
		for(int i=0;i<listIoTBigReq.size();i++) {
			bigReqEntry = listIoTBigReq.get(i);
			bigReqID = bigReqEntry.getReqID();
			numOfSdiotReqPerBigReq = countNumOfSdiotReqPerBigReq(bigReqID,listSubReq);
			numOfSdiotReqIsDone = countNumOfSdiotReqIsCompletelyDone(bigReqID,listSubReq);
//			System.out.println("bigReqEntry="+bigReqEntry.toString());
//			System.out.println("numOfSdiotReqPerBigReq="+numOfSdiotReqPerBigReq);
//			System.out.println("numOfSdiotReqIsDone="+numOfSdiotReqIsDone);
			if((numOfSdiotReqPerBigReq!=0) && (numOfSdiotReqIsDone!=0) &&(numOfSdiotReqPerBigReq == numOfSdiotReqIsDone)) {
//				System.out.println("\nSet status for an IoT request = Completely Done");
				bigReqEntry.setStatus(status);
				//need to get the time when the first result for an IoT request is completely achieved
			}
		}
		 
	}
	
	/**
	 * this method records provisioning time (provisioning time = orchestration time + response time)
	 * from when an IoT request is generated 
	 * till all of its sdiot requests are completely executed and sent first results to desired destinations
	 * 
	 * orchestration time is recorded by exeIoTBigRequestFrGUI()
	 * is from when the SDN controller receives one/multiple IoT requests till it generates a list of SD-IoT requests for needed SD-IoT clusters
	 * 
	 * checking if all SDIoT requests of an IoT request are executed
	 * if yes, we get the time when all SDIoT requests of an IoT request are executed
	 * this time is seen as an end time of the process of an IoT request from when the SDN controller receives the IoT request
	 */
	public void checkExecutionStatusOfSdiotReq() {	
		
		ArrayList<IoTBigReqEntry> listIoTBigReq = this.getBigReqList();
		ArrayList<SdiotBigReqEntry> listSubReq = this.getListSubReqForBigReq();
		IoTBigReqEntry bigReqEntry = null;
		String bigReqID = "";
		int numOfSdiotReqPerBigReq =0;
		int numOfSdiotReqIsExecuted =0;
		/**
		 * the start time is started when the SDN controller start building the IoT request
		 */
		long startTime;
		long processTime;
		
		/**
		 * endTime is recorded when an IoT request is completely done 
		 * which means all SD-IoT requests of the IoT request are completed (their executed status = "Completely Done")
		 * 
		 * notes:
		 * Execution_status of each SD-IoT request is set by the SD-IoT controller 
		 * whenever the SD-IoT controller execute a sdiot request from floodlight and already sent results to destionations
		 * it changes the Executed status of a big IoT request from NO to YES by sending a message to API
		 * 
		 */
		long endTime;
		long processTimeInSec = 0;
		long totalProcessTimeForNReq = 0, entryProcessingTime=0;//in ms
		int lastBigReqID = 0;
		int numRequest = 0;
		
		for(int i=0;i<listIoTBigReq.size();i++) {
			bigReqEntry = listIoTBigReq.get(i);
			bigReqID = bigReqEntry.getReqID();
			startTime = bigReqEntry.getStartTime();// the time when an IoT request is built
			endTime = bigReqEntry.getEndTime();
			processTime = bigReqEntry.getProcessingTime();
			
			numOfSdiotReqPerBigReq = countNumOfSdiotReqPerBigReq(bigReqID,listSubReq);
			numOfSdiotReqIsExecuted = countNumOfSdiotReqForAnIoTReqIsExecuted(bigReqID,listSubReq);
//			System.out.println("bigReqEntry="+bigReqEntry.toString());
//			System.out.println("numOfSdiotReqPerBigReq="+numOfSdiotReqPerBigReq);
//			System.out.println("numOfSdiotReqIsDone="+numOfSdiotReqIsDone);
			if((numOfSdiotReqPerBigReq!=0) && (numOfSdiotReqIsExecuted!=0) &&(numOfSdiotReqPerBigReq == numOfSdiotReqIsExecuted) && (endTime==0) && (processTime==0)) {
//				System.out.println("\nEqual");
//				System.out.println("\ncheckExecutionStatusOfSdiotReq(): endTime before setting = " + endTime);
				endTime = System.currentTimeMillis();//the time when all sdiot requests of an IoT request are all executed (means sdiot controller sent first results to desired destination)
				processTime = endTime - startTime;
				processTimeInSec = processTime/3600;
				bigReqEntry.setEndTime(endTime);
				bigReqEntry.setProcessingTime(processTime);
				
				System.out.println("\nTotal provisioning time for the big request ID "+ bigReqID + " is: "+ bigReqEntry.getProcessingTime() + " milliseconds.");
				System.out.println("\n");
//				System.out.println("\nprocessTimeInSec = " + processTimeInSec);
				//need to get the time when the first result for an IoT request is completely achieved
				
				//when all IoT requests have been set up the end time
				//it start calculate average provision time of a set of requests
				//the number of the set of requests can be numRequest = 10,20,30,40,50,....
//				numRequest =10;
//				numRequest =30;
				numRequest =50;
//				numRequest =70;
//				numRequest =90;
				lastBigReqID = Integer.parseInt(bigReqID);
				//total time to processing n simultaneous input requests
				if((lastBigReqID == listIoTBigReq.size())  && (listIoTBigReq.size() >numRequest) && (bigReqEntry.getProcessingTime()!=0)) {
					IoTBigReqEntry IoTReqEntry;
					System.out.println("lastBigReqID is " + lastBigReqID + " and processing time = " + bigReqEntry.getProcessingTime() + " milliseconds.");
					for(int j= listIoTBigReq.size()-1;j>=listIoTBigReq.size()- numRequest;j--) {
						IoTReqEntry = listIoTBigReq.get(j);
						entryProcessingTime = IoTReqEntry.getProcessingTime();
						totalProcessTimeForNReq = totalProcessTimeForNReq + entryProcessingTime;
//						System.out.println("entryProcessingTime : "+ entryProcessingTime + " milliseconds." + " j = "+ j);
					}
					//the time from a big IoT is generated until the time the first results for all required services have been achieved
					System.out.println("\n-------Total PROVISIONING time of " + numRequest + " requests is "+ totalProcessTimeForNReq + " milliseconds.\n");
				}
			}
			
		}
		
	}
	
	/**
	 * count number of sub-request per big IoT request
	 * @param bigReqEntry
	 * @param listSubReq
	 * @return
	 */
	public int countNumOfSdiotReqPerBigReq(String bigReqId,ArrayList<SdiotBigReqEntry> listSubReq ) {
		int count = 0;
		SdiotBigReqEntry subReqEntry = null;
		for(int j=0;j<listSubReq.size();j++) {
			subReqEntry = listSubReq.get(j);
			if(subReqEntry.getBigReqID().equalsIgnoreCase(bigReqId)) {
				count++;
			}
		}
		return count;
	}
	
	/**
	 * count number of sdiot sub-requests are completely done
	 * @param bigReqEntry
	 * @param listSubReq
	 * @return
	 */
	public int countNumOfSdiotReqIsCompletelyDone(String bigReqId,ArrayList<SdiotBigReqEntry> listSubReq) {
		int count = 0;
		SdiotBigReqEntry subReqEntry = null;

		for(int j=0;j<listSubReq.size();j++) {
			subReqEntry = listSubReq.get(j);
//			System.out.println("\nsubReqEntry = " + subReqEntry.toString());
			if((subReqEntry.getBigReqID().equalsIgnoreCase(bigReqId)) && (subReqEntry.getStatus().equalsIgnoreCase("Completely done"))) {
//				System.out.println("\nstatus = " + subReqEntry.getStatus());
				count++;
			}
		}
		return count;
	}
	
	/**
	 * count a number of sdiot sub-requests for an IoT request are all executed and send the first results to the desired application
	 * @param bigReqEntry
	 * @param listSubReq
	 * @return
	 */
	public int countNumOfSdiotReqForAnIoTReqIsExecuted(String bigReqId,ArrayList<SdiotBigReqEntry> listSubReq) {
		int count = 0;
		SdiotBigReqEntry subReqEntry = null;

		for(int j=0;j<listSubReq.size();j++) {
			subReqEntry = listSubReq.get(j);
//			System.out.println("\nsubReqEntry = " + subReqEntry.toString());
			if((subReqEntry.getBigReqID().equalsIgnoreCase(bigReqId)) && (subReqEntry.getExecuted().equalsIgnoreCase("YES"))) {
//				System.out.println("\nstatus = " + subReqEntry.getStatus());
				count++;
			}
		}
		return count;
	}
	
	
		
}