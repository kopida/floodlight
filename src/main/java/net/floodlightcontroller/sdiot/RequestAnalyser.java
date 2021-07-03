package net.floodlightcontroller.sdiot;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

import com.mysql.jdbc.Connection;

import net.floodlightcontroller.sdiot.ConnectDatabase;
import net.floodlightcontroller.sdiot.web.SdiotBigReqEntry;
import net.floodlightcontroller.sdiot.web.SdiotCluster;

/**
 * this class decomposes the IoT request from floodlight into sub-requests to be sent to sdiot controller
 * @author chau
 *
 */
public class RequestAnalyser {
	
	public static String composeSubReqToSdiotController(String inputReq) {
		
		String subReq = null;
		String newReqLoc = "";

		String[] req = inputReq.split("\\|");

		//get required action
		String[] reqActions = req[0].split("\\,");
		String reqActiontType = reqActions[0];

		///get ReqServices elements used for controller resource allocation agorithm1
		String[] reqServices = req[1].split("\\,");

		///get ReqCondition elements
		String[] reqConditions = req[2].split("\\,");
		String reqLoc = reqConditions[0];
		String reqFreq = reqConditions[1];
		String reqPeriod = reqConditions[2];
		String reqDstAddr = reqConditions[3];
		

		///get stats
		String strReqID = req[3]; //it is the number of simultaneous requests
		int reqID = Integer.parseInt(strReqID);
		
		//in case: in a reqLoc, there are many sdiot names that may provide the required SID
		//to make it simple, need to make the newReqLoc always is LOC01, to choose between the LOC01 and LOC02, need to consider the state
		subReq = reqActiontType + "|" + reqServices[0] + "|" + newReqLoc + "," + reqFreq + "," + reqPeriod + "," + reqDstAddr + "|" + reqID;

		return subReq;
	}
	
	/**
	 * to decompose the input request: GET|SID01,SID02,SID07|Area1,10,10,121|1|dest6|1
	 * into sub-requests as : GET|SID01,SID02,SID03|LOC01,10,10,121|1
	 * 
	 * currently we allow requests with only one action(GET or SET_ON or SET_OFF) not combined action (develop later)
	 *
	 * @param request
	 * @return
	 */
	public static ArrayList<String> decomposeBigRequestIntoSubReqList(String request) {
		
		String[] req = request.split("\\|");
		
		//get required action
		String[] reqActions = req[0].split("\\,");
		String reqActiontType = reqActions[0];

		///get ReqServices elements used for controller resource allocation agorithm1
		String[] reqServices = req[1].split("\\,");

		///get ReqCondition elements
		String[] reqConditions = req[2].split("\\,");
		String reqLoc = reqConditions[0]; //reqLoc = Area1, area1 may have several sdiots, each sdiot may have different SID
		String reqFreq = reqConditions[1];
		String reqPeriod = reqConditions[2];
		String reqDstAddr = reqConditions[3];
		//reqLoc = Area1, area1 may have several sdiot, each sdiot may have different SID,
		//what will happen if an Area does not provide the required SID, how to make it simple, but can be easily developed when it becomes complex
		//how : reqLoc=Area1 -> newReqLoc=LOC01 or LOC02

		///get stats
		String strReqID = req[3]; //it is the number of simultaneous requests
		int reqID = Integer.parseInt(strReqID);
		
		//reqDstHost is the destination of the required services
		String reqDstHost = req[4];
		
		String strBigReqID = req[5]; //it is the number of simultaneous requests
        int bigReqID = Integer.parseInt(strBigReqID);

		/**
		 * compose sub-requests from information of the big IoT request
		 */
		String newReqLoc = "LOC01";//supposed that the sub-request is for LOC01, we can write a function to choose between LOC01 and LOC02 later on
		String subReq = null;
		ArrayList<String> listPairsOfSdiotNameAndsubRequest = new ArrayList<String>();//list requests for sdiot
		ArrayList<String> sdiotListByArea = new ArrayList<String>();
		
		sdiotListByArea = getIoTClusterListByLocIdFrDatabase(reqLoc); //list of sdiot clusters in the required area, e.g. Area1
		
//		ArrayList<String> sdiotIpAddressListByArea = new ArrayList<String>();
//		sdiotIpAddressListByArea = getIoTClusterIpAddrListByLocId(reqLoc);
		
		for(int j = 0; j< sdiotListByArea.size();j++) {
			ArrayList<String> reqSerForEachSdiot = new ArrayList<String>();
			String strReqServices ="";
			ArrayList<String> sidListOfEachSdiot = new ArrayList<String>();
			for(int i=0;i<reqServices.length;i++) {
//				System.out.println("sdiotListByArea.get(j): "+sdiotListByArea.get(j));
				sidListOfEachSdiot = getSidListByClusterNameFrDatabase(sdiotListByArea.get(j));
				for(int k=0; k<sidListOfEachSdiot.size();k++) {
					if(reqServices[i].equalsIgnoreCase(sidListOfEachSdiot.get(k))) {
						reqSerForEachSdiot.add(reqServices[i]);
						break;
					}
				}
			}
			if(reqSerForEachSdiot.size()>=1) {
				for(int m = 0;m<reqSerForEachSdiot.size();m++) {
					if(m==0){
						strReqServices = strReqServices + reqSerForEachSdiot.get(m);
					}else {
						strReqServices = strReqServices +"," + reqSerForEachSdiot.get(m);
					}
					
				}
				/**
				 * build
				 * ing sub-requests for the sdiot system
				 */
//				subReq = reqActiontType+"|"+strReqServices+"|"+newReqLoc+","+reqFreq+","+reqPeriod+","+reqDstAddr+"|"+reqID+"|"+reqDstHost; //original without bigReqID
				subReq = reqActiontType+"|"+strReqServices+"|"+newReqLoc+","+reqFreq+","+reqPeriod+","+reqDstAddr+"|"+reqID+"|"+reqDstHost+"|"+bigReqID ;
//				System.out.println("Sub-request is " + subReq);
				listPairsOfSdiotNameAndsubRequest.add(sdiotListByArea.get(j) + ":" + subReq);
//				System.out.println("\nRequestAnalyser: "+ sdiotListByArea.get(j) + ":" + subReq);
			}
		}
		return listPairsOfSdiotNameAndsubRequest;
	}
	
	/**
	 * is used by exeIoTBigRequestFrGUIWithoutSmartOrch()
	 * 
	 * using the approach, all sdiot systems in the required area are considered to provide the required services
	 * 
	 * the approach produces a list of pair [sdiotName : required services that it can achieve]
	 * 
	 * @param request
	 * @param bigReqID
	 * @return
	 */
	public static ArrayList<String> decomposeBigReqIntoSubReqListWithouSmart(String request, int bigReqID) {
		
		String[] req = request.split("\\|");
		
		//get required action
		String[] reqActions = req[0].split("\\,");
		String reqActiontType = reqActions[0];

		///get ReqServices elements used for controller resource allocation agorithm1
		String[] reqServices = req[1].split("\\,");

		///get ReqCondition elements
		String[] reqConditions = req[2].split("\\,");
		String reqLoc = reqConditions[0]; //reqLoc = Area1, area1 may have several sdiots, each sdiot may have different SID
		String reqFreq = reqConditions[1];
		String reqPeriod = reqConditions[2];
		String reqDstAddr = reqConditions[3];
		//reqLoc = Area1, area1 may have several sdiot, each sdiot may have different SID,
		//what will happen if an Area does not provide the required SID, how to make it simple, but can be easily developed when it becomes complex
		//how : reqLoc=Area1 -> newReqLoc=LOC01 or LOC02

		///get stats
		String strReqID = req[3]; //it is the number of simultaneous requests
		int reqID = Integer.parseInt(strReqID);
		
		//reqDstHost is the destination of the required services
		String reqDstHost = req[4];
		
		/**
		 * compose sub-requests from information of the big IoT request
		 */
		String newReqLoc = "LOC01";//supposed that the sub-request is for LOC01, we can write a function to choose between LOC01 and LOC02 later on
		String subReq = null;
		ArrayList<String> listPairsOfSdiotNameAndsubRequest = new ArrayList<String>();//list requests for sdiot
		ArrayList<String> sdiotListByArea = new ArrayList<String>();
		
		/**
		 * get list of sdiot clusters in the required area, e.g. Area1
		 */
		sdiotListByArea = getIoTClusterListByLocIdFrDatabase(reqLoc);
		
		for(int j = 0; j< sdiotListByArea.size();j++) {
			ArrayList<String> reqSerForEachSdiot = new ArrayList<String>();
			String strReqServices ="";
			ArrayList<String> sidListOfEachSdiot = new ArrayList<String>();
			
			/**
			 * check if a sdiot can provide which service among all required services
			 * the produce a list of pair[sdiotName and required services that it can provide]
			 * 
			 */
			for(int i=0;i<reqServices.length;i++) {
//				System.out.println("sdiotListByArea.get(j): " + sdiotListByArea.get(j));
				sidListOfEachSdiot = getSidListByClusterNameFrDatabase(sdiotListByArea.get(j));
				for(int k=0; k<sidListOfEachSdiot.size();k++) {
					if(reqServices[i].equalsIgnoreCase(sidListOfEachSdiot.get(k))) {
						reqSerForEachSdiot.add(reqServices[i]);
						break;
					}
				}
			}
			if(reqSerForEachSdiot.size()>=1) {
				for(int m = 0;m<reqSerForEachSdiot.size();m++) {
					if(m==0){
						strReqServices = strReqServices + reqSerForEachSdiot.get(m);
					}else {
						strReqServices = strReqServices +"," + reqSerForEachSdiot.get(m);
					}
					
				}
				/**
				 * building sub-requests for the sdiot system
				 */
//				subReq = reqActiontType+"|"+strReqServices+"|"+newReqLoc+","+reqFreq+","+reqPeriod+","+reqDstAddr+"|"+reqID+"|"+reqDstHost; //original without bigReqID
				subReq = reqActiontType+"|"+strReqServices+"|"+newReqLoc+","+reqFreq+","+reqPeriod+","+reqDstAddr+"|"+reqID+"|"+reqDstHost+"|"+bigReqID ;
//				System.out.println("Sub-request is " + subReq);
				listPairsOfSdiotNameAndsubRequest.add(sdiotListByArea.get(j) + ":" + subReq);
//				System.out.println("RequestAnalyser: "+ sdiotListByArea.get(j) + ":" + subReq);
			}
		}
		return listPairsOfSdiotNameAndsubRequest;
	}
	
	/**
	 * orignial version 
	 * 
	 * using the approach, all sdiot systems in the required area are considered to provide the required services
	 * check if a sdiot can provide which service among all required services
     * the produce a list of pair[sdiotName and required serivces that it can provide
	 * 
	 * the approach produces a list of pair [sdiotName : required services that it can achieve]
	 * 
	 * if we use this approach, we can not show that feature about re-utilization of historical data of a required SID that can be provided for multiple applications
	 * 
	 * @param request
	 * @param bigReqID
	 * @return
	 */
	public static ArrayList<String> decomposeBigReqIntoSubReqList(String request, int bigReqID) {
		
		String[] req = request.split("\\|");
		
		//get required action
		String[] reqActions = req[0].split("\\,");
		String reqActiontType = reqActions[0];

		///get ReqServices elements used for controller resource allocation agorithm1
		String[] reqServices = req[1].split("\\,");

		///get ReqCondition elements
		String[] reqConditions = req[2].split("\\,");
		String reqLoc = reqConditions[0]; //reqLoc = Area1, area1 may have several sdiots, each sdiot may have different SID
		String reqFreq = reqConditions[1];
		String reqPeriod = reqConditions[2];
		String reqDstAddr = reqConditions[3];
		//reqLoc = Area1, area1 may have several sdiot, each sdiot may have different SID,
		//what will happen if an Area does not provide the required SID, how to make it simple, but can be easily developed when it becomes complex
		//how : reqLoc=Area1 -> newReqLoc=LOC01 or LOC02

		///get stats
		String strReqID = req[3]; //it is the number of simultaneous requests
		int reqID = Integer.parseInt(strReqID);
		
		//reqDstHost is the destination of the required services
		String reqDstHost = req[4];
		
		/**
		 * compose sub-requests from information of the big IoT request
		 */
		String newReqLoc = "LOC01";//supposed that the sub-request is for LOC01, we can write a function to choose between LOC01 and LOC02 later on
		String subReq = null;
		ArrayList<String> listPairsOfSdiotNameAndsubRequest = new ArrayList<String>();//list requests for sdiot
		ArrayList<String> sdiotListByArea = new ArrayList<String>();
		
		/**
		 * get list of sdiot clusters in the required area, e.g. Area1
		 */
		sdiotListByArea = getIoTClusterListByLocIdFrDatabase(reqLoc);
		
//		ArrayList<String> sdiotIpAddressListByArea = new ArrayList<String>();
//		sdiotIpAddressListByArea = getIoTClusterIpAddrListByLocId(reqLoc);
		
		for(int j = 0; j< sdiotListByArea.size();j++) {
			ArrayList<String> reqSerForEachSdiot = new ArrayList<String>();
			String strReqServices ="";
			ArrayList<String> sidListOfEachSdiot = new ArrayList<String>();
			
			/**
			 * check if a sdiot can provide which service among all required services
			 * the produce a list of pair[sdiotName and required services that it can provide]
			 * 
			 */
			for(int i=0;i<reqServices.length;i++) {
//				System.out.println("sdiotListByArea.get(j): " + sdiotListByArea.get(j));
				sidListOfEachSdiot = getSidListByClusterNameFrDatabase(sdiotListByArea.get(j));
				for(int k=0; k<sidListOfEachSdiot.size();k++) {
					if(reqServices[i].equalsIgnoreCase(sidListOfEachSdiot.get(k))) {
						reqSerForEachSdiot.add(reqServices[i]);
						break;
					}
				}
			}
			if(reqSerForEachSdiot.size()>=1) {
				for(int m = 0;m<reqSerForEachSdiot.size();m++) {
					if(m==0){
						strReqServices = strReqServices + reqSerForEachSdiot.get(m);
					}else {
						strReqServices = strReqServices +"," + reqSerForEachSdiot.get(m);
					}
					
				}
				/**
				 * building sub-requests for the sdiot system
				 */
//				subReq = reqActiontType+"|"+strReqServices+"|"+newReqLoc+","+reqFreq+","+reqPeriod+","+reqDstAddr+"|"+reqID+"|"+reqDstHost; //original without bigReqID
				subReq = reqActiontType+"|"+strReqServices+"|"+newReqLoc+","+reqFreq+","+reqPeriod+","+reqDstAddr+"|"+reqID+"|"+reqDstHost+"|"+bigReqID ;
//				System.out.println("Sub-request is " + subReq);
				listPairsOfSdiotNameAndsubRequest.add(sdiotListByArea.get(j) + ":" + subReq);
//				System.out.println("RequestAnalyser: "+ sdiotListByArea.get(j) + ":" + subReq);
			}
		}
		return listPairsOfSdiotNameAndsubRequest;
	}
	
	/**
	 * using the approach, only one sdiot is orchestrated to provide a required services
	 * 
	 * this function works out the list of potential sdiots that can provide a kind of SID
	 * pick the best sdiot for a SID
	 * then produces a list of the best sdiot for each required SID
	 * then compose sub-requests for these sdiots
	 * 
	 * @param request
	 * @param bigReqID
	 * @return
	 */
	public static ArrayList<String> decomposeBigRequestWithBigReqIDIntoSubReqListBySIDUsingDatabase(String request, int bigReqID) {
		
		String[] req = request.split("\\|");
		
		//get required action
		String[] reqActions = req[0].split("\\,");
		String reqActiontType = reqActions[0];

		///get ReqServices elements used for controller resource allocation agorithm1
		String[] reqServices = req[1].split("\\,");

		///get ReqCondition elements
		String[] reqConditions = req[2].split("\\,");
		String reqLoc = reqConditions[0]; //reqLoc = Area1, area1 may have several sdiots, each sdiot may have different SID
		String reqFreq = reqConditions[1];
		String reqPeriod = reqConditions[2];
		String reqDstAddr = reqConditions[3];
		//reqLoc = Area1, area1 may have several sdiot, each sdiot may have different SID,
		//what will happen if an Area does not provide the required SID, how to make it simple, but can be easily developed when it becomes complex
		//how : reqLoc=Area1 -> newReqLoc=LOC01 or LOC02

		///get stats
		String strReqID = req[3]; //it is the number of simultaneous requests
		int reqID = Integer.parseInt(strReqID);
		
		//reqDstHost is the destination of the required services
		String reqDstHost = req[4];
		
		/**
		 * compose sub-requests from information of the big IoT request
		 */
		String newReqLoc = "LOC01";//supposed that the sub-request is for LOC01, we can write a function to choose between LOC01 and LOC02 later on
		String subReq = null;
		ArrayList<String> listPairsOfSdiotNameAndsubRequest = new ArrayList<String>();//list requests for sdiot
//		ArrayList<String> sdiotListByArea = new ArrayList<String>();
		ArrayList<String> sdiotListByAreaAndSid = new ArrayList<String>();
		String bestSdiotNameForASID = null;
		ArrayList<SdiotCluster> sdiotClusterList;
				
//		sdiotListByArea = getIoTClusterListByLocIdFrDatabase(reqLoc); //list of sdiot clusters in the required area, e.g. Area1
		
		for(String sID: reqServices) {
			sdiotListByAreaAndSid = getListSdiotNameByAreaIdAndSidFrDatabase(reqLoc, sID);
			
			bestSdiotNameForASID = pickBestSdiotFromListSdiotSID(reqLoc, sID, sdiotListByAreaAndSid); //String tmpLocation, String tmpServiceID, ArrayList<String> listSdiotForEachSID
			
			/**
			 * building sub-requests for the sdiot system
			 */
			subReq = reqActiontType+"|"+sID+"|"+newReqLoc+","+reqFreq+","+reqPeriod+","+reqDstAddr+"|"+reqID+"|"+reqDstHost+"|"+bigReqID ;
//			System.out.println("Sub-request is " + subReq);
			listPairsOfSdiotNameAndsubRequest.add(bestSdiotNameForASID + ":" + subReq);
//			System.out.println("\nDatabase-based RequestAnalyser: "+ bestSdiotNameForASID + ":" + subReq);
		}
		
/*//		for(int j=0; j< sdiotListByArea.size();j++) {
			for(int i=0;i<listPairsOfSdiotNameAndsubRequest.size();i++) {
				String newStrReqServices = null;
				String[] temp1 = listPairsOfSdiotNameAndsubRequest.get(i).split("\\:");
				String[] temp2 = listPairsOfSdiotNameAndsubRequest.get(i+1).split("\\:");
				if(temp1[0].equalsIgnoreCase(temp2[0]));
			}
//		}
*/		
		return listPairsOfSdiotNameAndsubRequest;
	}
	
	//getSdiotClusterAndSidWithGET
	public static ArrayList<String> decomposeBigReqIntoSubReqListUsingDatabaseForCaseGET(ArrayList<SdiotBigReqEntry> listSdiotReq,String request, int bigReqID) {
		
		String[] req = request.split("\\|");
		
		//get required action
		String[] reqActions = req[0].split("\\,");
		String reqActiontType = reqActions[0];

		///get ReqServices elements used for controller resource allocation agorithm1
		String[] reqServices = req[1].split("\\,");

		///get ReqCondition elements
		String[] reqConditions = req[2].split("\\,");
		String reqLoc = reqConditions[0]; //reqLoc = Area1, area1 may have several sdiots, each sdiot may have different SID
		String reqFreq = reqConditions[1];
		String reqPeriod = reqConditions[2];
		String reqDstAddr = reqConditions[3];
		//reqLoc = Area1, area1 may have several sdiot, each sdiot may have different SID,
		//what will happen if an Area does not provide the required SID, how to make it simple, but can be easily developed when it becomes complex
		//how : reqLoc=Area1 -> newReqLoc=LOC01 or LOC02

		///get stats
		String strReqID = req[3]; //it is the number of simultaneous requests
		int reqID = Integer.parseInt(strReqID);
		
		//reqDstHost is the destination of the required services
		String reqDstHost = req[4];
		
		/**
		 * compose sub-requests from information of the big IoT request
		 */
		String newReqLoc = "LOC01";//supposed that the sub-request is for LOC01, we can write a function to choose between LOC01 and LOC02 later on
		String subReq = null;
		ArrayList<String> listPairsOfSdiotNameAndsubRequest = new ArrayList<String>();//list requests for sdiot
//		ArrayList<String> sdiotListByArea = new ArrayList<String>();
		ArrayList<String> sdiotListByAreaAndSid = new ArrayList<String>();
		String bestSdiotNameForASID = null;
		ArrayList<SdiotCluster> sdiotClusterList;
		ArrayList<String> curSdiotAndSidWGET = new ArrayList<>();
		curSdiotAndSidWGET = getSdiotClusterAndSidWithGET(listSdiotReq);
				
//		sdiotListByArea = getIoTClusterListByLocIdFrDatabase(reqLoc); //list of sdiot clusters in the required area, e.g. Area1
		
		for(String sID: reqServices) {
			for(int i=0; i< curSdiotAndSidWGET.size();i++) {
				String[] entryArr = curSdiotAndSidWGET.get(i).split("\\:");
				if(entryArr[1].equalsIgnoreCase(sID)) {
					bestSdiotNameForASID = entryArr[0];
//					subReq = reqActiontType+"|"+sID+"|"+newReqLoc+","+reqFreq+","+reqPeriod+","+reqDstAddr+"|"+reqID+"|"+reqDstHost+"|"+bigReqID ;
////					System.out.println("Sub-request is " + subReq);
//					listPairsOfSdiotNameAndsubRequest.add(bestSdiotNameForASID + ":" + subReq);
				}
/*				else {
					sdiotListByAreaAndSid = getListSdiotNameByAreaIdAndSidFrDatabase(reqLoc, sID);
					bestSdiotNameForASID = pickBestSdiotFromListSdiotSID(reqLoc, sID, sdiotListByAreaAndSid); //String tmpLocation, String tmpServiceID, ArrayList<String> listSdiotForEachSID
				}*/

			}
			sdiotListByAreaAndSid = getListSdiotNameByAreaIdAndSidFrDatabase(reqLoc, sID);
			bestSdiotNameForASID = pickBestSdiotFromListSdiotSID(reqLoc, sID, sdiotListByAreaAndSid); //String tmpLocation, String tmpServiceID, ArrayList<String> listSdiotForEachSID
		
			
			
			
			/**
			 * building sub-requests for the sdiot system
			 */
			subReq = reqActiontType+"|"+sID+"|"+newReqLoc+","+reqFreq+","+reqPeriod+","+reqDstAddr+"|"+reqID+"|"+reqDstHost+"|"+bigReqID ;
//			System.out.println("Sub-request is " + subReq);
			listPairsOfSdiotNameAndsubRequest.add(bestSdiotNameForASID + ":" + subReq);
//			System.out.println("\nDatabase-based RequestAnalyser: "+ bestSdiotNameForASID + ":" + subReq);
		}
		
/*//		for(int j=0; j< sdiotListByArea.size();j++) {
			for(int i=0;i<listPairsOfSdiotNameAndsubRequest.size();i++) {
				String newStrReqServices = null;
				String[] temp1 = listPairsOfSdiotNameAndsubRequest.get(i).split("\\:");
				String[] temp2 = listPairsOfSdiotNameAndsubRequest.get(i+1).split("\\:");
				if(temp1[0].equalsIgnoreCase(temp2[0]));
			}
//		}
*/		
		return listPairsOfSdiotNameAndsubRequest;
	}
	
	
	
	
	/**
	 * decompose the big IoT request into sdiot requests for all sdiot cluster
	 * produce a list of all sdiot clusters and their corresponding sdiot requests
	 * @param request
	 * @param bigReqID
	 * @return
	 */
	public static ArrayList<String> decomposeBigReqIntoSubReqListUsingDatabaseForCaseSET(String request, int bigReqID) {
		
		String[] req = request.split("\\|");
		
		//get required action
		String[] reqActions = req[0].split("\\,");
		String reqActiontType = reqActions[0];

		///get ReqServices elements used for controller resource allocation agorithm1
		String[] reqServices = req[1].split("\\,");

		///get ReqCondition elements
		String[] reqConditions = req[2].split("\\,");
		String reqLoc = reqConditions[0]; //reqLoc = Area1, area1 may have several sdiots, each sdiot may have different SID
		String reqFreq = reqConditions[1];
		String reqPeriod = reqConditions[2];
		String reqDstAddr = reqConditions[3];
		//reqLoc = Area1, area1 may have several sdiot, each sdiot may have different SID,
		//what will happen if an Area does not provide the required SID, how to make it simple, but can be easily developed when it becomes complex
		//how : reqLoc=Area1 -> newReqLoc=LOC01 or LOC02

		///get stats
		String strReqID = req[3]; //it is the number of simultaneous requests
		int reqID = Integer.parseInt(strReqID);
		
		//reqDstHost is the destination of the required services
		String reqDstHost = req[4];
		
		/**
		 * compose sub-requests from information of the big IoT request
		 */
		String newReqLoc = "LOC01";//supposed that the sub-request is for LOC01, we can write a function to choose between LOC01 and LOC02 later on
		String subReq = null;
		ArrayList<String> listPairsOfSdiotNameAndsubRequest = new ArrayList<String>();//list requests for sdiot
//		ArrayList<String> sdiotListByArea = new ArrayList<String>();
		ArrayList<String> sdiotListByAreaAndSid = new ArrayList<String>();
		String bestSdiotNameForASID = null;
//		ArrayList<SdiotCluster> sdiotClusterList;
				

		ArrayList<String> clusterNameList = getIoTClusterListByLocIdFrDatabase(reqLoc);		
		for(int i=0;i< clusterNameList.size();i++) {
			/**
			 * building sub-requests for the sdiot system
			 */
			subReq = reqActiontType+"|"+req[1]+"|"+newReqLoc+","+reqFreq+","+reqPeriod+","+reqDstAddr+"|"+reqID+"|"+reqDstHost+"|"+bigReqID ;
//			System.out.println("Sub-request is " + subReq);
			listPairsOfSdiotNameAndsubRequest.add(bestSdiotNameForASID + ":" + subReq);
//			System.out.println("\nDatabase-based RequestAnalyser: "+ bestSdiotNameForASID + ":" + subReq);
		}
		
/*//		for(int j=0; j< sdiotListByArea.size();j++) {
			for(int i=0;i<listPairsOfSdiotNameAndsubRequest.size();i++) {
				String newStrReqServices = null;
				String[] temp1 = listPairsOfSdiotNameAndsubRequest.get(i).split("\\:");
				String[] temp2 = listPairsOfSdiotNameAndsubRequest.get(i+1).split("\\:");
				if(temp1[0].equalsIgnoreCase(temp2[0]));
			}
//		}
*/		
		return listPairsOfSdiotNameAndsubRequest;
	}
	
	/**
	 * decomposing task is based on the available sdiot cluster resources not based on the pre-defined data in mysql
	 * ->the orchestration will vary according the availability of current sdiot cluster resources
	 * @param clusterList
	 * @param request
	 * @param bigReqID
	 * @return
	 */
	public static ArrayList<String> decomposeBigRequestWithBigReqIDIntoSubReqListBySIDUsingAvailableSdiotResources(ArrayList<SdiotCluster> clusterList, String request, int bigReqID) {
		
		String[] req = request.split("\\|");
		
		//get required action
		String[] reqActions = req[0].split("\\,");
		String reqActiontType = reqActions[0];

		///get ReqServices elements used for controller resource allocation agorithm1
		String[] reqServices = req[1].split("\\,");

		///get ReqCondition elements
		String[] reqConditions = req[2].split("\\,");
		String reqLoc = reqConditions[0]; //reqLoc = Area1, area1 may have several sdiots, each sdiot may have different SID
		String reqFreq = reqConditions[1];
		String reqPeriod = reqConditions[2];
		String reqDstAddr = reqConditions[3];
		//reqLoc = Area1, area1 may have several sdiot, each sdiot may have different SID,
		//what will happen if an Area does not provide the required SID, how to make it simple, but can be easily developed when it becomes complex
		//how : reqLoc=Area1 -> newReqLoc=LOC01 or LOC02

		///get stats
		String strReqID = req[3]; //it is the number of simultaneous requests
		int reqID = Integer.parseInt(strReqID);
		
		//reqDstHost is the destination of the required services
		String reqDstHost = req[4];
		
		/**
		 * compose sub-requests from information of the big IoT request
		 */
		String newReqLoc = "LOC01";//supposed that the sub-request is for LOC01, we can write a function to choose between LOC01 and LOC02 later on
		String subReq = null;
		ArrayList<String> listPairsOfSdiotNameAndsubRequest = new ArrayList<String>();//list requests for sdiot
		ArrayList<String> sdiotListByAreaAndSid = new ArrayList<String>();
		String bestSdiotNameForASID = null;
		
		ArrayList<SdiotCluster> sdiotClusterList = clusterList;
		
		for(String sID: reqServices) {
			sdiotListByAreaAndSid = getListSdiotNameByAreaIdAndSidFrSdiotClusterResource(sdiotClusterList,reqLoc, sID);
			bestSdiotNameForASID = pickBestSdiotFromListSdiotSID(reqLoc, sID, sdiotListByAreaAndSid); //String tmpLocation, String tmpServiceID, ArrayList<String> listSdiotForEachSID
			
			/**
			 * building sub-requests for the sdiot system
			 */
			subReq = reqActiontType+"|"+sID+"|"+newReqLoc+","+reqFreq+","+reqPeriod+","+reqDstAddr+"|"+reqID+"|"+reqDstHost+"|"+bigReqID ;
//			System.out.println("Sub-request is " + subReq);
			listPairsOfSdiotNameAndsubRequest.add(bestSdiotNameForASID + ":" + subReq);
//			System.out.println("\nAvailableResource-based RequestAnalyser: "+ bestSdiotNameForASID + ":" + subReq);
		}
		
/*//		for(int j=0; j< sdiotListByArea.size();j++) {
			for(int i=0;i<listPairsOfSdiotNameAndsubRequest.size();i++) {
				String newStrReqServices = null;
				String[] temp1 = listPairsOfSdiotNameAndsubRequest.get(i).split("\\:");
				String[] temp2 = listPairsOfSdiotNameAndsubRequest.get(i+1).split("\\:");
				if(temp1[0].equalsIgnoreCase(temp2[0]));
			}
//		}
*/		
		return listPairsOfSdiotNameAndsubRequest;
	}
	
	/**
	 * used by exeIoTBigRequestFrGUIWithoutSmartOrch()
	 * 
	 * decomposing task is based on the available sdiot cluster resources not based on the pre-defined data in mysql
	 * ->the orchestration will vary according the availability of current sdiot cluster resources
	 * @param clusterList
	 * @param request
	 * @param bigReqID
	 * @return
	 */
	public static ArrayList<String> decomposeBigReqIntoSubReqListBySIDUseAvailSdiotResources(ArrayList<SdiotCluster> clusterList, String request, int bigReqID) {
		
		String[] req = request.split("\\|");
		
		//get required action
		String[] reqActions = req[0].split("\\,");
		String reqActiontType = reqActions[0];

		///get ReqServices elements used for controller resource allocation agorithm1
		String[] reqServices = req[1].split("\\,");

		///get ReqCondition elements
		String[] reqConditions = req[2].split("\\,");
		String reqLoc = reqConditions[0]; //reqLoc = Area1, area1 may have several sdiots, each sdiot may have different SID
		String reqFreq = reqConditions[1];
		String reqPeriod = reqConditions[2];
		String reqDstAddr = reqConditions[3];
		//reqLoc = Area1, area1 may have several sdiot, each sdiot may have different SID,
		//what will happen if an Area does not provide the required SID, how to make it simple, but can be easily developed when it becomes complex
		//how : reqLoc=Area1 -> newReqLoc=LOC01 or LOC02

		///get stats
		String strReqID = req[3]; //it is the number of simultaneous requests
		int reqID = Integer.parseInt(strReqID);
		
		//reqDstHost is the destination of the required services
		String reqDstHost = req[4];
		
		/**
		 * compose sub-requests from information of the big IoT request
		 */
		String newReqLoc = "LOC01";//supposed that the sub-request is for LOC01, we can write a function to choose between LOC01 and LOC02 later on
		String subReq = null;
		ArrayList<String> listPairsOfSdiotNameAndsubRequest = new ArrayList<String>();//list requests for sdiot
		ArrayList<String> sdiotListByAreaAndSid = new ArrayList<String>();
		String bestSdiotNameForASID = null;
		
		ArrayList<SdiotCluster> sdiotClusterList = clusterList;
		
		/**
		 * select the best sdiot cluster for each required SID
		 * the sdiot with least state value would be the best candidate
		 */
		for(String sID: reqServices) {
			sdiotListByAreaAndSid = getListSdiotNameByAreaIdAndSidFrSdiotClusterResource(sdiotClusterList,reqLoc, sID);
			bestSdiotNameForASID = pickBestSdiotFromListSdiotSID(reqLoc, sID, sdiotListByAreaAndSid); //String tmpLocation, String tmpServiceID, ArrayList<String> listSdiotForEachSID
			
			/**
			 * building sub-requests for the sdiot system
			 */
			subReq = reqActiontType+"|"+sID+"|"+newReqLoc+","+reqFreq+","+reqPeriod+","+reqDstAddr+"|"+reqID+"|"+reqDstHost+"|"+bigReqID ;
//			System.out.println("Sub-request is " + subReq);
			listPairsOfSdiotNameAndsubRequest.add(bestSdiotNameForASID + ":" + subReq);
//			System.out.println("\nAvailableResource-based RequestAnalyser: "+ bestSdiotNameForASID + ":" + subReq);
		}
		
/*//		for(int j=0; j< sdiotListByArea.size();j++) {
			for(int i=0;i<listPairsOfSdiotNameAndsubRequest.size();i++) {
				String newStrReqServices = null;
				String[] temp1 = listPairsOfSdiotNameAndsubRequest.get(i).split("\\:");
				String[] temp2 = listPairsOfSdiotNameAndsubRequest.get(i+1).split("\\:");
				if(temp1[0].equalsIgnoreCase(temp2[0]));
			}
//		}
*/		
		return listPairsOfSdiotNameAndsubRequest;
	}
	
	/**
	 * it is currently used by exeIoTBigRequestFrGUIWithSmartOrch()
	 * 
	 * decomposing task is based on available sdiot cluster resources not based on the pre-defined data in mysql
	 * ->the orchestration will vary according the availability of current sdiot cluster resources
	 * 
	 * additional feature of this function is that
	 * it considers required action type of the incoming request
	 * GET: it sees if any sdiot cluster has the same "SID" with the incoming request and
	 * is with "OnGoing" "GET" "IsExecuted" and has least "state" among all possible sdiot candidates
	 * 
	 * @param clusterList
	 * @param request
	 * @param bigReqID
	 * @return
	 * @throws IOException 
	 * @throws UnknownHostException 
	 * @throws SocketException 
	 */
	public static ArrayList<String> decomposeBigReqIntoSubReqListBySIDUseAvailSdiotResourcesForSmartOrch(ArrayList<SdiotCluster> clusterList, ArrayList<SdiotBigReqEntry> sdiotReqList,String request, int bigReqID) throws SocketException, UnknownHostException, IOException {
		
		String[] req = request.split("\\|");
		
		//get required action
		String[] reqActions = req[0].split("\\,");
		String reqActiontType = reqActions[0];

		///get ReqServices elements used for controller resource allocation agorithm1
		String[] reqServices = req[1].split("\\,");

		///get ReqCondition elements
		String[] reqConditions = req[2].split("\\,");
		String reqLoc = reqConditions[0]; //reqLoc = Area1, area1 may have several sdiots, each sdiot may have different SID
		String reqFreq = reqConditions[1];
		String reqPeriod = reqConditions[2];
		String reqDstAddr = reqConditions[3];
		//reqLoc = Area1, area1 may have several sdiot, each sdiot may have different SID,
		//what will happen if an Area does not provide the required SID, how to make it simple, but can be easily developed when it becomes complex
		//how : reqLoc=Area1 -> newReqLoc=LOC01 or LOC02

		///get stats
		String strReqID = req[3]; //it is the number of simultaneous requests
		int reqID = Integer.parseInt(strReqID);
		
		//reqDstHost is the destination of the required services
		String reqDstHost = req[4];
		String strIpDstHost = getIpAddrDstHost(reqDstHost);
		
		/**
		 * compose sub-requests from information of the big IoT request
		 */
		String newReqLoc = "LOC01";//supposed that the sub-request is for LOC01, we can write a function to choose between LOC01 and LOC02 later on
		String subReq = null;
		ArrayList<String> listPairsOfSdiotNameAndsubRequest = new ArrayList<String>();//list requests for sdiot with execution status
		ArrayList<String> sdiotListByAreaAndSid = new ArrayList<String>();
		String bestSdiotNameForASID = null;
		ArrayList<String> sdiotNameWithGETList = new ArrayList<String>();
		
		ArrayList<SdiotCluster> sdiotClusterList = clusterList;
		ArrayList<SdiotBigReqEntry> sdiotReqListWitGetAct = new ArrayList<>();
		String exeStatus = "NO";
		//code for psuedo-code of resource orchestration mechanism
		switch(reqActiontType) {
		case "GET":{
			
			for(String sID: reqServices) {
				sdiotReqListWitGetAct = getSdiotReqWithGET(sdiotReqList,sID,"GET");
				//size() == 0 means there is currently no IoT request having similar interest with the incoming request
				if(sdiotReqListWitGetAct.size() == 0) {
					sdiotListByAreaAndSid = getListSdiotNameByAreaIdAndSidFrSdiotClusterResource(sdiotClusterList,reqLoc, sID);
					bestSdiotNameForASID = pickBestSdiotFromListSdiotSID(reqLoc, sID, sdiotListByAreaAndSid); //String tmpLocation, String tmpServiceID, ArrayList<String> listSdiotForEachSID
					
				}
				//size() == 1 means there is currently one IoT request having similar interest with the incoming request
				//also means that only one sdiot cluster associated with one required SID -> only one choice and no need for pickBestSdiotClusterNameWithLeastState()
				else if(sdiotReqListWitGetAct.size() == 1){
					sdiotNameWithGETList = getListSdiotNameWithGET(sdiotReqListWitGetAct);
					for(int j=0;j<sdiotNameWithGETList.size();j++) {
						bestSdiotNameForASID = sdiotNameWithGETList.get(j);
						System.out.println("smart orchestration: send result to " + reqDstHost.toUpperCase());
//						sendResultToMininetHost(reqActiontType, sID, reqDstHost, reqID);
						exeStatus = "YES";//need a function that retrieve historical data and send to required destination
					}
				}
				else{
					sdiotNameWithGETList = getListSdiotNameWithGET(sdiotReqListWitGetAct);
					String sdiotNameFrDB = pickBestSdiotClusterNameWithLeastState(sdiotNameWithGETList);
					for(int j=0;j<sdiotNameWithGETList.size();j++) {
						if(sdiotNameWithGETList.get(j).equalsIgnoreCase(sdiotNameFrDB)) {
							bestSdiotNameForASID = sdiotNameWithGETList.get(j);
							System.out.println("smart orchestration: send result to " + reqDstHost.toUpperCase());
//							sendResultToMininetHost(reqActiontType, sID, reqDstHost, reqID);
							exeStatus = "YES";
						}
						
					}
					
				}
				/**
				 * building sub-requests for the sdiot system
				 */
				subReq = reqActiontType+"|"+sID+"|"+newReqLoc+","+reqFreq+","+reqPeriod+","+reqDstAddr+"|"+reqID+"|"+reqDstHost+"|"+bigReqID ;
				listPairsOfSdiotNameAndsubRequest.add(bestSdiotNameForASID + ":" + subReq + ":" + exeStatus);
				
			}
		}
		break;
		case "SET_ON":{
			for(String sID: reqServices) {
				sdiotListByAreaAndSid = getListSdiotNameByAreaIdAndSidFrSdiotClusterResource(sdiotClusterList,reqLoc, sID);
				bestSdiotNameForASID = pickBestSdiotFromListSdiotSID(reqLoc, sID, sdiotListByAreaAndSid); //String tmpLocation, String tmpServiceID, ArrayList<String> listSdiotForEachSID
				
				/**
				 * building sub-requests for the sdiot system
				 */
				subReq = reqActiontType+"|"+sID+"|"+newReqLoc+","+reqFreq+","+reqPeriod+","+reqDstAddr+"|"+reqID+"|"+reqDstHost+"|"+bigReqID ;
				listPairsOfSdiotNameAndsubRequest.add(bestSdiotNameForASID + ":" + subReq + ":" + exeStatus);
			}
		}
		break;
		case "SET_OFF":{
			for(String sID: reqServices) {
				sdiotListByAreaAndSid = getListSdiotNameByAreaIdAndSidFrSdiotClusterResource(sdiotClusterList,reqLoc, sID);
				bestSdiotNameForASID = pickBestSdiotFromListSdiotSID(reqLoc, sID, sdiotListByAreaAndSid); //String tmpLocation, String tmpServiceID, ArrayList<String> listSdiotForEachSID
				
				/**
				 * building sub-requests for the sdiot system
				 */
				subReq = reqActiontType+"|"+sID+"|"+newReqLoc+","+reqFreq+","+reqPeriod+","+reqDstAddr+"|"+reqID+"|"+reqDstHost+"|"+bigReqID ;
				listPairsOfSdiotNameAndsubRequest.add(bestSdiotNameForASID + ":" + subReq + ":" + exeStatus);
			}
		}
		break;
		default:
		break;
		}
		
		return listPairsOfSdiotNameAndsubRequest;
	}
	
	
	/**
	 * original version
	 * 
	 * decomposing task is based on available sdiot cluster resources not based on the pre-defined data in mysql
	 * ->the orchestration will vary according the availability of current sdiot cluster resources
	 * 
	 * additional feature of this function is that
	 * it considers required action type of the incoming request
	 * GET: it sees if any sdiot cluster has the same "SID" with the incoming request and
	 * is with "OnGoing" "GET" "IsExecuted" and has least "state" among all possible sdiot candidates
	 * 
	 * @param clusterList
	 * @param request
	 * @param bigReqID
	 * @return
	 */
	public static ArrayList<String> decomposeBigReqIntoSubReqListBySIDUsingAvailableSdiotResources(ArrayList<SdiotCluster> clusterList, ArrayList<SdiotBigReqEntry> sdiotReqList,String request, int bigReqID) {
		
		String[] req = request.split("\\|");
		
		//get required action
		String[] reqActions = req[0].split("\\,");
		String reqActiontType = reqActions[0];

		///get ReqServices elements used for controller resource allocation agorithm1
		String[] reqServices = req[1].split("\\,");

		///get ReqCondition elements
		String[] reqConditions = req[2].split("\\,");
		String reqLoc = reqConditions[0]; //reqLoc = Area1, area1 may have several sdiots, each sdiot may have different SID
		String reqFreq = reqConditions[1];
		String reqPeriod = reqConditions[2];
		String reqDstAddr = reqConditions[3];
		//reqLoc = Area1, area1 may have several sdiot, each sdiot may have different SID,
		//what will happen if an Area does not provide the required SID, how to make it simple, but can be easily developed when it becomes complex
		//how : reqLoc=Area1 -> newReqLoc=LOC01 or LOC02

		///get stats
		String strReqID = req[3]; //it is the number of simultaneous requests
		int reqID = Integer.parseInt(strReqID);
		
		//reqDstHost is the destination of the required services
		String reqDstHost = req[4];
		
		/**
		 * compose sub-requests from information of the big IoT request
		 */
		String newReqLoc = "LOC01";//supposed that the sub-request is for LOC01, we can write a function to choose between LOC01 and LOC02 later on
		String subReq = null;
		ArrayList<String> listPairsOfSdiotNameAndsubRequest = new ArrayList<String>();//list requests for sdiot with execution status
		ArrayList<String> sdiotListByAreaAndSid = new ArrayList<String>();
		String bestSdiotNameForASID = null;
		ArrayList<String> sdiotNameWithGETList = new ArrayList<String>();
		
		ArrayList<SdiotCluster> sdiotClusterList = clusterList;
		ArrayList<SdiotBigReqEntry> sdiotReqListWitGetAct = new ArrayList<>();
		
		switch(reqActiontType) {
		case "GET":{
			
			for(String sID: reqServices) {
				sdiotReqListWitGetAct = getSdiotReqWithGET(sdiotReqList,sID,"GET");
				//size() == 0 means there is currently no IoT request having similar interest with the incoming request
				if(sdiotReqListWitGetAct.size() == 0) {
					sdiotListByAreaAndSid = getListSdiotNameByAreaIdAndSidFrSdiotClusterResource(sdiotClusterList,reqLoc, sID);
					bestSdiotNameForASID = pickBestSdiotFromListSdiotSID(reqLoc, sID, sdiotListByAreaAndSid); //String tmpLocation, String tmpServiceID, ArrayList<String> listSdiotForEachSID
					
				}
				//size() == 1 means there is currently one IoT request having similar interest with the incoming request
				//also means that only one sdiot cluster associated with one required SID -> only one choice and no need for pickBestSdiotClusterNameWithLeastState()
				else if(sdiotReqListWitGetAct.size() == 1){
					sdiotNameWithGETList = getListSdiotNameWithGET(sdiotReqListWitGetAct);
					for(int j=0;j<sdiotNameWithGETList.size();j++) {
						bestSdiotNameForASID = sdiotNameWithGETList.get(j);
					}
				}
				else{
					sdiotNameWithGETList = getListSdiotNameWithGET(sdiotReqListWitGetAct);
					String sdiotNameFrDB = pickBestSdiotClusterNameWithLeastState(sdiotNameWithGETList);
					for(int j=0;j<sdiotNameWithGETList.size();j++) {
						if(sdiotNameWithGETList.get(j).equalsIgnoreCase(sdiotNameFrDB)) {
							bestSdiotNameForASID = sdiotNameWithGETList.get(j);
						}
						
					}
					
				}
				/**
				 * building sub-requests for the sdiot system
				 */
				subReq = reqActiontType+"|"+sID+"|"+newReqLoc+","+reqFreq+","+reqPeriod+","+reqDstAddr+"|"+reqID+"|"+reqDstHost+"|"+bigReqID ;
				listPairsOfSdiotNameAndsubRequest.add(bestSdiotNameForASID + ":" + subReq);
				
			}
		}
		break;
		case "SET_ON":{
			for(String sID: reqServices) {
				sdiotListByAreaAndSid = getListSdiotNameByAreaIdAndSidFrSdiotClusterResource(sdiotClusterList,reqLoc, sID);
				bestSdiotNameForASID = pickBestSdiotFromListSdiotSID(reqLoc, sID, sdiotListByAreaAndSid); //String tmpLocation, String tmpServiceID, ArrayList<String> listSdiotForEachSID
				
				/**
				 * building sub-requests for the sdiot system
				 */
				subReq = reqActiontType+"|"+sID+"|"+newReqLoc+","+reqFreq+","+reqPeriod+","+reqDstAddr+"|"+reqID+"|"+reqDstHost+"|"+bigReqID ;
				listPairsOfSdiotNameAndsubRequest.add(bestSdiotNameForASID + ":" + subReq);
			}
		}
		break;
		case "SET_OFF":{
			for(String sID: reqServices) {
				sdiotListByAreaAndSid = getListSdiotNameByAreaIdAndSidFrSdiotClusterResource(sdiotClusterList,reqLoc, sID);
				bestSdiotNameForASID = pickBestSdiotFromListSdiotSID(reqLoc, sID, sdiotListByAreaAndSid); //String tmpLocation, String tmpServiceID, ArrayList<String> listSdiotForEachSID
				
				/**
				 * building sub-requests for the sdiot system
				 */
				subReq = reqActiontType+"|"+sID+"|"+newReqLoc+","+reqFreq+","+reqPeriod+","+reqDstAddr+"|"+reqID+"|"+reqDstHost+"|"+bigReqID ;
				listPairsOfSdiotNameAndsubRequest.add(bestSdiotNameForASID + ":" + subReq);
			}
		}
		break;
		default:
		break;
		}
		
		return listPairsOfSdiotNameAndsubRequest;
	}
	
	/**
	 * get list of sdiot request with "GET" action, ExecutionStatus = "OnGoing", "IsExecuted = "YES",
	 * @param list
	 * @param reqSID
	 * @param reqAct
	 * @return
	 */
	public static ArrayList<SdiotBigReqEntry> getSdiotReqWithGET(ArrayList<SdiotBigReqEntry> list, String reqSID, String reqAct){
		ArrayList<SdiotBigReqEntry> tmpList = new ArrayList<>();
		SdiotBigReqEntry sdiotEntry = null;
		String[] sdiotReqArr;
		String action;
		for(int i=0;i<list.size();i++) {
			sdiotEntry = list.get(i);
			sdiotReqArr = sdiotEntry.getSubReq().split("\\|");
			action = sdiotReqArr[0];
			if(sdiotEntry.getStatus().equalsIgnoreCase("OnGoing") &&  sdiotEntry.getExecuted().equalsIgnoreCase("YES")
					&& sdiotEntry.getRequiredServices().equalsIgnoreCase(reqSID)
					&& action.equalsIgnoreCase(reqAct)
					) {
				tmpList.add(sdiotEntry);
//				System.out.println("\nsdiot entry with get: "+ sdiotEntry);
			}
		}
		return tmpList;
	}
	
	/**
	 * get sdiot name with least "state" from the db
	 * @param listSdiotReqWithGET
	 * @return
	 */
	public static String pickBestSdiotClusterNameWithLeastState(ArrayList<String> listSdiotNameWithGET) {
		String theBestSdiot = "";
		String strCondition = "( ";
		for (int i = 0; i < listSdiotNameWithGET.size(); i++) {

			/**
			 * build the condition of SQL Query
			 */
			strCondition += "cluster_name = '" + listSdiotNameWithGET.get(i) + "'";
			String currentSID = listSdiotNameWithGET.get(i);
			if (i + 1 < listSdiotNameWithGET.size()) {
				strCondition += " OR ";
			}
		}
		strCondition += ") ";
		String strSQL = "SELECT cluster_name, state FROM tab_iot_cluster WHERE "
				+ strCondition + "ORDER BY state ASC LIMIT 1;";

		try {
			ConnectDatabase c = new ConnectDatabase();
			Statement stmt = (Statement) c.getConnection().createStatement();
			ResultSet rs = stmt.executeQuery(strSQL);
			while (rs.next()) {
				if (rs.getString("cluster_name").length() > 0) {
					theBestSdiot = rs.getString("cluster_name");
				}
			}
		} catch (SQLException e) {
			System.out.println(e.getMessage());
		} 
		return theBestSdiot;
	}
	
	/**
	 * get list of sdiot name with GET action from a sdiot request list
	 * @param listSdiotReqWithGET
	 * @return
	 */
	public static ArrayList<String> getListSdiotNameWithGET(ArrayList<SdiotBigReqEntry> listSdiotReqWithGET){
		ArrayList<String> list = new ArrayList<>();
		String sdiotName ;
		for(int i=0;i<listSdiotReqWithGET.size();i++) {
			sdiotName = listSdiotReqWithGET.get(i).getClusterName();
			list.add(sdiotName);
		}
		return list;
	}
	
	/**
	 * get a list of SIDs provided by each sdiot
	 * @param sid
	 * @return
	 */
	public static ArrayList<String> getSidListByClusterNameFrDatabase(String clusterName){
		ArrayList<String> sidList = new ArrayList<String>();
		String strSQL = "SELECT service_id FROM tab_sdiot_services WHERE cluster_name = '" + clusterName + "';";
		
        try {
            ConnectDatabase c = new ConnectDatabase();
            Statement stmt = (Statement) c.getConnection().createStatement();
            ResultSet rs = stmt.executeQuery(strSQL);
            

            while (rs.next()) {
                if (rs.getString("service_id").length() > 0) {
//                	System.out.println("getSidListByClusterName: "+ rs.getString("service_id"));
                	sidList.add(rs.getString("service_id"));
                }
            }
        } catch (SQLException e) {
            System.out.println(e.getMessage());
        } 
		return sidList;
	}
	
	/**
	 * get a list of sdiot clusters that can provide the required SID
	 * @param sid
	 * @return
	 */
	public static ArrayList<String> getIoTClusterListBySID(String sid){
		ArrayList<String> sdiotList = new ArrayList<String>();
		String strSQL = "SELECT cluster_name FROM tab_sdiot_services WHERE service_id = '" + sid + "';";

        try {
            ConnectDatabase c = new ConnectDatabase();
            Statement stmt = (Statement) c.getConnection().createStatement();
            ResultSet rs = stmt.executeQuery(strSQL);

            while (rs.next()) {
                if (rs.getString("cluster_name").length() > 0) {
                	sdiotList.add(rs.getString("cluster_name"));
                }
            }
        } catch (SQLException e) {
            System.out.println(e.getMessage());
        } 
//        System.out.println("getIoTClusterListBySID: size sdiotList = " + sdiotList.size());
		return sdiotList;
	}
	
		
	/**
	 * get a list of sdiot cluster in an area from pre-defined database
	 * @param reqArea
	 * @return
	 */
	public static ArrayList<String> getIoTClusterListByLocIdFrDatabase(String reqArea){
		ArrayList<String> clusterList = new ArrayList<String>();
        String strSQL = "SELECT cluster_name FROM tab_iot_cluster WHERE location_id = '" + reqArea + "';";

        try {
            ConnectDatabase c = new ConnectDatabase();
            Statement stmt = (Statement) c.getConnection().createStatement();
            ResultSet rs = stmt.executeQuery(strSQL);

            while (rs.next()) {
                if (rs.getString("cluster_name").length() > 0) {
//                	System.out.println("getIoTClusterListByLocId "+ rs.getString("cluster_name"));
                	clusterList.add(rs.getString("cluster_name"));
                }
            }
        } catch (SQLException e) {
            System.out.println(e.getMessage());
        } 
		return clusterList;
	}
	
	
	public static ArrayList<String> getIoTClusterIpAddrListByLocIdFrDatabase(String reqArea){
		ArrayList<String> IpClusterList = new ArrayList<String>();
        String strSQL = "SELECT IP_Address FROM tab_iot_cluster WHERE location_id = '" + reqArea + "';";

        try {
            ConnectDatabase c = new ConnectDatabase();
            Statement stmt = (Statement) c.getConnection().createStatement();
            ResultSet rs = stmt.executeQuery(strSQL);

            while (rs.next()) {
                if (rs.getString("IP_Address").length() > 0) {
//                	System.out.println("getIoTClusterListByLocId "+ rs.getString("cluster_name"));
                	IpClusterList.add(rs.getString("IP_Address"));
                }
            }
        } catch (SQLException e) {
            System.out.println(e.getMessage());
        } 
		return IpClusterList;
	}
	
	public static String getIPaddrByClusterNameFrDatabase(String sdiotName) {
		String ipv4 ="";
		String strSQL = "SELECT IP_Address FROM tab_iot_cluster WHERE cluster_name = '" + sdiotName + "';";
		
		try {
            ConnectDatabase c = new ConnectDatabase();
            Statement stmt = (Statement) c.getConnection().createStatement();
            ResultSet rs = stmt.executeQuery(strSQL);

            while (rs.next()) {
                if (rs.getString("IP_Address").length() > 0) {
//                	System.out.println("getIoTClusterListByLocId "+ rs.getString("cluster_name"));
                	ipv4 = rs.getString("IP_Address");
                }
            }
        } catch (SQLException e) {
            System.out.println(e.getMessage());
        }
		
		return ipv4;
	}
	
	public static ArrayList<String> getPairsOfSdiotIpAddrAndSubRequest(ArrayList<String> pairSdiotNameAndSubReq){
		ArrayList<String> pair = new ArrayList<String>();
		String[] strArr = null;
		String ipv4 = "";
		String newPair = "";
		for(int i=0;i<pairSdiotNameAndSubReq.size();i++) {
			strArr = pairSdiotNameAndSubReq.get(i).split(":");
			ipv4 = getIPaddrByClusterNameFrDatabase(strArr[0]);
			newPair = ipv4 +":"+strArr[1];
			pair.add(newPair);
		}
		return pair;
	}
	
	/**
     * return a list of sdiots that can provide the same sensor service SID 
     * return the list of sdiots by (Area ID and Service ID) 
     * tmpServiceID = ServiceName = SID
     *
     * @param tmpLocation
     * @param tmpServiceID
     * @return
     */
    public static ArrayList<String> getListSdiotNameByAreaIdAndSidFrDatabase(String areaID, String serviceID) {

        ArrayList<String> temp = new ArrayList<>();
        String strSQL = "SELECT cluster_name FROM tab_sdiot_services WHERE location_id = '" 
                + areaID + "' AND service_id = '" + serviceID + "';";

        try {
            ConnectDatabase c = new ConnectDatabase();
            Statement stmt = (Statement) c.getConnection().createStatement();
            ResultSet rs = stmt.executeQuery(strSQL);

            while (rs.next()) {
                if (rs.getString("cluster_name").length() > 0) {
                    temp.add(rs.getString("cluster_name"));
                }
            }
        } catch (SQLException e) {
            System.out.println(e.getMessage());
        } 
        return temp;
    }
    
    
	/**
     * return a list of sdiots that can provide the same sensor service SID 
     * return the list of sdiots by (Area ID and Service ID) 
     * tmpServiceID = ServiceName = SID
     *
     * @param tmpLocation
     * @param tmpServiceID
     * @return
     */
    public static ArrayList<String> getListSdiotNameByAreaIdAndSidFrSdiotClusterResource(ArrayList<SdiotCluster> sdiotClusterList, String areaID, String serviceID) {
    	ArrayList<String> clusterList = new ArrayList<String>();
		SdiotCluster sdiotEntry;
		
		for(int i=0; i< sdiotClusterList.size();i++) {
			sdiotEntry = sdiotClusterList.get(i);
			String[] providedServices = sdiotEntry.getProvidedServices().split("\\,");
			
			if(sdiotEntry.getManagedArea().equalsIgnoreCase(areaID)) {
				
				for(int j=0; j<providedServices.length;j++) {
					
					if(providedServices[j].equalsIgnoreCase(serviceID)) {
						clusterList.add(sdiotEntry.getClusterName());
//						System.out.println("\n" + sdiotEntry.getClusterName());
					}
					}
			}
		}
		
		return clusterList;
    }
    
    /**
     * choose the best sdiot which has the least state value, 
     * it is the best candidate for providing the required SID 
     * get a list of sdvs and sort ascending, but just get 1 row (LIMIT 1) =>
     * the most free SDVS
     *
     * @param tmpLocation
     * @param tmpServiceID
     * @return
     */
    public static String pickBestSdiotFromListSdiotSID(String tmpLocation, String tmpServiceID, ArrayList<String> listSdiotForEachSID) {
        String theBestSdiot = "";
        String strCondition = "( ";
        int SizeOfListSdiots = listSdiotForEachSID.size();
        for (int i = 0; i < SizeOfListSdiots; i++) {
            
        	/**
        	 * build the condition of SQL Query
        	 */
            strCondition += "cluster_name = '" + listSdiotForEachSID.get(i) + "'";
            String currentSID = listSdiotForEachSID.get(i);
            if (i + 1 < SizeOfListSdiots) {
                strCondition += " OR ";
            }
            //System.out.println("You can use service[" + currentSID + "] on the device [" +strTheBestSDVS + "]");
        }
        strCondition += ") ";
        String strSQL = "SELECT cluster_name, state FROM tab_iot_cluster WHERE "
                + "location_id = '" + tmpLocation + "' AND " + strCondition
                + "ORDER BY state ASC LIMIT 1;";

        /* String strSQL = "SELECT sdvs_id, state FROM tab_sdvs_status WHERE location_id = '" 
                + tmpLocation
                + "' ORDER BY state ASC LIMIT 1;";*/
        try {
            ConnectDatabase c = new ConnectDatabase();
            Statement stmt = (Statement) c.getConnection().createStatement();
            ResultSet rs = stmt.executeQuery(strSQL);
            while (rs.next()) {
                if (rs.getString("cluster_name").length() > 0) {
                	theBestSdiot = rs.getString("cluster_name");
                }
            }
        } catch (SQLException e) {
            System.out.println(e.getMessage());
        } 
        return theBestSdiot;
    }
    
    /**
     * it is necessary to rebuild a big request from a subrequest when a sdiot send a response "CAN NOT ACHIEVE THE REQUIRED SERVICES" to the SDN controller
     * the SDN controller needs to reschedule other sdiot systems to handle the request
     * 
     * @param subRequest
     * @param clusterName
     * @return
     */
    public String produceBigRequestFromSubRequest(String subRequest) {
    	String newBigReq ="";
    	String areaID = "Area1";
    	
		String[] subReq = subRequest.split("\\|");
		
		//get required action
		String[] reqActions = subReq[0].split("\\,");
		String reqActiontType = reqActions[0];

		///get ReqServices elements used for controller resource allocation agorithm1
		String reqServices = subReq[1];
//		System.out.println("requireservices = subReq[1] : "+ subReq[1]);

		///get ReqCondition elements
		String[] reqConditions = subReq[2].split("\\,");
		String reqLoc = reqConditions[0]; //reqLoc = Area1, area1 may have several sdiots, each sdiot may have different SID
		String reqFreq = reqConditions[1];
		String reqPeriod = reqConditions[2];
		String reqDstAddr = reqConditions[3];

		///get stats
		String strReqID = subReq[3]; //it is the number of simultaneous requests
		int reqID = Integer.parseInt(strReqID);
		
		//reqDstHost is the destination of the required services
		String reqDstHost = subReq[4];
		
		String strBigReqID = subReq[5]; //it is the number of simultaneous requests
        int bigReqID = Integer.parseInt(strBigReqID);
		
		/**
		 * building sub-requests for the sdiot system
		 */
        newBigReq = reqActiontType+"|"+reqServices+"|"+areaID+","+reqFreq+","+reqPeriod+","+reqDstAddr+"|"+reqID+"|"+reqDstHost+"|"+bigReqID ;
		
		return newBigReq;
	}
    
    /**
     * the approach produce a list of pairs[sdiotName:subRequest]
     * 
     * @param request
     * @param clusterName is a name of the cluster that sends a CANNOT/PARTIALLY ACHIEVE response
     * @return
     */
//    public static ArrayList<String> rescheduleABigRequest(String request, String clusterName, ArrayList<SdiotBigReqEntry> bigReqList) {
    public static ArrayList<String> rescheduleABigRequestUsingDatabase(String request, String clusterName) {	
		String[] req = request.split("\\|");
		
		//get required action
		String[] reqActions = req[0].split("\\,");
		String reqActiontType = reqActions[0];

		///get ReqServices elements used for controller resource allocation agorithm1
		String[] reqServices = req[1].split("\\,");

		///get ReqCondition elements
		String[] reqConditions = req[2].split("\\,");
		String reqLoc = reqConditions[0]; //reqLoc = Area1, area1 may have several sdiots, each sdiot may have different SID
		String reqFreq = reqConditions[1];
		String reqPeriod = reqConditions[2];
		String reqDstAddr = reqConditions[3];

		///get stats
		String strReqID = req[3]; //it is the number of simultaneous requests
		int reqID = Integer.parseInt(strReqID);
		
		//reqDstHost is the destination of the required services
		String reqDstHost = req[4];
		
		//bigReqID
		String bigReqID = req[5];
		
		/**
		 * compose sub-requests from information of the big IoT request
		 */
		String newReqLoc = "LOC01";//supposed that the sub-request is for LOC01, we can write a function to choose between LOC01 and LOC02 later on
		String subReq = null;
		ArrayList<String> listPairsOfSdiotNameAndsubRequest = new ArrayList<String>();//list requests for sdiot
//		ArrayList<String> sdiotListByArea = new ArrayList<String>();
		ArrayList<String> sdiotListByAreaAndSid = new ArrayList<String>();
		ArrayList<String> sdiotListByAreaAndSidAfterRemoveClusterResponse = new ArrayList<String>();
		String bestSdiotNameForASID = null;
		
//		sdiotListByArea = getIoTClusterListByLocId(reqLoc); //list of sdiot clusters in the required area, e.g. Area1
		
		for(String sID: reqServices) {
			sdiotListByAreaAndSid = getListSdiotNameByAreaIdAndSidFrDatabase(reqLoc, sID);//list of sdiots that can provide the same SID
			sdiotListByAreaAndSidAfterRemoveClusterResponse = removeSdiotNameFromPotentialSdiotList(sdiotListByAreaAndSid, clusterName);
			bestSdiotNameForASID = pickBestSdiotFromListSdiotSID(reqLoc, sID, sdiotListByAreaAndSidAfterRemoveClusterResponse); //String tmpLocation, String tmpServiceID, ArrayList<String> listSdiotForEachSID
			
			/**
			 * building sub-requests for the sdiot system
			 */
			subReq = reqActiontType+"|"+sID+"|"+newReqLoc+","+reqFreq+","+reqPeriod+","+reqDstAddr+"|"+reqID+"|"+reqDstHost+"|"+bigReqID ;
//			System.out.println("Sub-request is " + subReq);
			listPairsOfSdiotNameAndsubRequest.add(bestSdiotNameForASID + ":" + subReq);
			System.out.println("\nRequestAnalyser reschedules: "+ bestSdiotNameForASID + ":" + subReq);
		}
		
		return listPairsOfSdiotNameAndsubRequest;
	}
    
    public static ArrayList<String> rescheduleABigRequestUsingAvailableSdiotResource(ArrayList<SdiotCluster> sdiotClusterList,String request, String clusterName) {	
		String[] req = request.split("\\|");
		
		//get required action
		String[] reqActions = req[0].split("\\,");
		String reqActiontType = reqActions[0];

		///get ReqServices elements used for controller resource allocation agorithm1
		String[] reqServices = req[1].split("\\,");

		///get ReqCondition elements
		String[] reqConditions = req[2].split("\\,");
		String reqLoc = reqConditions[0]; //reqLoc = Area1, area1 may have several sdiots, each sdiot may have different SID
		String reqFreq = reqConditions[1];
		String reqPeriod = reqConditions[2];
		String reqDstAddr = reqConditions[3];

		///get stats
		String strReqID = req[3]; //it is the number of simultaneous requests
		int reqID = Integer.parseInt(strReqID);
		
		//reqDstHost is the destination of the required services
		String reqDstHost = req[4];
		
		//bigReqID
		String bigReqID = req[5];
		
		/**
		 * compose sub-requests from information of the big IoT request
		 */
		String newReqLoc = "LOC01";//supposed that the sub-request is for LOC01, we can write a function to choose between LOC01 and LOC02 later on
		String subReq = null;
		ArrayList<String> listPairsOfSdiotNameAndsubRequest = new ArrayList<String>();//list requests for sdiot
//		ArrayList<String> sdiotListByArea = new ArrayList<String>();
		ArrayList<String> sdiotListByAreaAndSid = new ArrayList<String>();
		ArrayList<String> sdiotListByAreaAndSidAfterRemoveClusterResponse = new ArrayList<String>();
		String bestSdiotNameForASID = null;
		
//		sdiotListByArea = getIoTClusterListByLocId(reqLoc); //list of sdiot clusters in the required area, e.g. Area1
		
		for(String sID: reqServices) {
			sdiotListByAreaAndSid = getListSdiotNameByAreaIdAndSidFrSdiotClusterResource(sdiotClusterList,reqLoc, sID);//list of sdiots that can provide the same SID
			sdiotListByAreaAndSidAfterRemoveClusterResponse = removeSdiotNameFromPotentialSdiotList(sdiotListByAreaAndSid, clusterName);
			bestSdiotNameForASID = pickBestSdiotFromListSdiotSID(reqLoc, sID, sdiotListByAreaAndSidAfterRemoveClusterResponse); //String tmpLocation, String tmpServiceID, ArrayList<String> listSdiotForEachSID
			
			/**
			 * building sub-requests for the sdiot system
			 */
			subReq = reqActiontType+"|"+sID+"|"+newReqLoc+","+reqFreq+","+reqPeriod+","+reqDstAddr+"|"+reqID+"|"+reqDstHost+"|"+bigReqID ;
//			System.out.println("Sub-request is " + subReq);
			listPairsOfSdiotNameAndsubRequest.add(bestSdiotNameForASID + ":" + subReq);
//			System.out.println("\nrescheduleABigRequestUsingAvailableSdiotResource: RequestAnalyser reschedules: "+ bestSdiotNameForASID + ":" + subReq);
		}
		
		return listPairsOfSdiotNameAndsubRequest;
	}
    
    /**
     * with the old big request(the one has been processed and produce corresponding sub-request)
     * when we re-orchestrate sdiot resources, we have to remove the sdiot that cannot provide the required services from the potential candidates list
     * 
     * @param sdiotListByAreaAndSid
     * @param clusterName
     * @return
     */
    public static ArrayList<String> removeSdiotNameFromPotentialSdiotList(ArrayList<String> sdiotListByAreaAndSid, String clusterName){
    	ArrayList<String> list = new ArrayList<>();
//    	System.out.println("--size before removing sdiot name---"+ sdiotListByAreaAndSid.size());
    	
    	for(int i=0; i<sdiotListByAreaAndSid.size();i++) {
    		String tmp = sdiotListByAreaAndSid.get(i); //.split("\\:");
    		
    		if(tmp.equalsIgnoreCase(clusterName)) {
    			sdiotListByAreaAndSid.remove(i);
    		}
    	}
    	list = sdiotListByAreaAndSid;
//    	System.out.println("--size after removing sdiot name---"+ list.size());
    	return list;
    }
    
    /**
     * we have to remove the sdiot cluster name from the big request list when the sdiot cluster responds that it cannot provide the required services
     * @param bigReqList
     * @param clusterName
     * @return
     */
    public static ArrayList<SdiotBigReqEntry> removeClusterWithResponseCANNOTFrBigReqList(ArrayList<SdiotBigReqEntry> bigReqList, String clusterName, String subReq){
    	ArrayList<SdiotBigReqEntry> newList = new ArrayList<>();
    	for(int i = 0; i< bigReqList.size();i++) {
    		if(bigReqList.get(i).getClusterName().equalsIgnoreCase(clusterName)
    				&& bigReqList.get(i).getSubReq().equalsIgnoreCase(subReq)) {
//    			System.out.println("bigreqlist.remove " + bigReqList.get(i).toString());
    			bigReqList.remove(i);
    		}
    	}
    	newList = bigReqList;
    	return newList;
    }
    
    /**
     * we have to add the new sdiot big request entry for the sdiot cluster that is selected for rescheduling old big request
     * the entry is added to the big request list
     * @param bigReqList
     * @param clusterName
     * @return
     */
    public static ArrayList<SdiotBigReqEntry> addNewSubReqToBigReqList(ArrayList<SdiotBigReqEntry> bigReqList, String clusterName, ArrayList<String> pairSdiotNameAndSubRequest){
    	ArrayList<SdiotBigReqEntry> newList = new ArrayList<>();
    	String[] aPair = null;
    	String sdiotName = "", subReq = "", bigReqId = "", clusterRes="", reqService = "", isExecuted = "NO";
    	String[] subReqArr = null;
    	for(int i=0; i< pairSdiotNameAndSubRequest.size();i++) {
    		aPair = pairSdiotNameAndSubRequest.get(i).split("\\:");
    		sdiotName = aPair[0];
    		subReq = aPair[1];
    		subReqArr = subReq.split("\\|");
    		reqService = subReqArr[1];
    		bigReqId = subReqArr[5];
    		clusterRes = "New replacement of " + clusterName + " for IoT_Req_ID " + bigReqId;
    		SdiotBigReqEntry newEntry = new SdiotBigReqEntry(bigReqId, sdiotName, clusterRes, reqService, subReq, isExecuted,"OnGoing");
//    		System.out.println("\nAdd bigReqList : "+ newEntry.toString());
        	bigReqList.add(newEntry);
    	}
    	
    	newList = bigReqList;
    	return newList;
    }
    
    /**
	 * get a list of sdiot cluster in an area
	 * @param reqArea
	 * @return
	 */
	public static ArrayList<String> getIoTClusterListByLocIdFrSdiotClusterResource(ArrayList<SdiotCluster> sdiotClusterList, String areaID){
		ArrayList<String> clusterList = new ArrayList<String>();
		SdiotCluster sdiotEntry;
		for(int i=0; i< sdiotClusterList.size();i++) {
			sdiotEntry = sdiotClusterList.get(i);
			if(sdiotEntry.getManagedArea().equalsIgnoreCase(areaID)) {
				clusterList.add(sdiotEntry.getClusterName());
//				System.out.println("\n" + sdiotEntry.getClusterName());
			}
			
		}
		
		return clusterList;
	}
	
	/**
	 * get a list of sdiot and sid with GET action in the list of sdiot requests
	 * @return
	 */
	public static ArrayList<String> getSdiotClusterAndSidWithGET(ArrayList<SdiotBigReqEntry> listSdiotReq){
		ArrayList<String> listWGET = new ArrayList<>();
//		ArrayList<SdiotBigReqEntry> listSdiotReq = this.getListSubReqForBigReq();
		SdiotBigReqEntry entry = null;
		String subReq = "";
		String[] subReqArr = null;
		String reqAct = "";
		
		for(int i=0;i<listSdiotReq.size();i++) {
			entry = listSdiotReq.get(i);
			if(entry.getExecuted().equalsIgnoreCase("YES")) {
				subReq = entry.getSubReq();
				subReqArr = subReq.split("\\|");
				reqAct = subReqArr[0];
				if(reqAct.equalsIgnoreCase("GET")) {
					listWGET.add(entry.getClusterName().toString()+":"+entry.getRequiredServices().toString());
//					System.out.println("\ncluster with get = " + entry.getClusterName().toString());
				}
			}
			
		}
		
		return listWGET;
	}
	
	/**
     * send required services to the required mininet hosts
     * this function send sub-request to sdiot via UDP socket, not using packet out message
     *
     * @param reqServices
     * @param dst
     * @param bigReqId
     * @throws SocketException
     * @throws UnknownHostException
     * @throws IOException
     */
    public static void sendResultToMininetHost(String reqAct, String reqServices, String ipDstHost, int bigReqId) throws SocketException,UnknownHostException,IOException{
        System.out.println("\nsendResultToMininetHost() for Action = " + reqAct);
        DatagramSocket serverSocket = null;

        String result = "From SDN controller : results about serives " + reqServices + " for big request ID number " + bigReqId;
        
        byte[] sendData = new byte[65508];
        sendData = result.getBytes();
        int serverPort = 8000;
        int i = 1;
        while (i <= 1) {
            try {
                serverSocket = new DatagramSocket();
                serverSocket.setReuseAddress(true);
                serverSocket.setSoTimeout(50);
                InetAddress ipAddr = InetAddress.getByName(ipDstHost);
                DatagramPacket sendPacket = new DatagramPacket(sendData, sendData.length, ipAddr, serverPort);
                serverSocket.send(sendPacket);
                i++;
                serverSocket.close();
            } catch (SocketException ex) {
                System.err.println("Exception SocketException senddata " + Integer.toString(4002) + ex.getMessage());
            } catch (IOException ex) {
                System.err.println("Exception IOException senddata " + Integer.toString(4002) + ex.getMessage());
            } finally {
                serverSocket.close();
            }
        }
    }
    
    
    
    public static String getIpAddrDstHost(String strDstHost) {
    	String strIpAddr = null;
    	System.out.println("\nsendResultToMininetHost() dest = " + strDstHost.toUpperCase());
    	switch(strDstHost.toUpperCase()) {
    	case "DST6":
    		strIpAddr = "10.0.0.6";
    		break;
    	case "DST7":
    		strIpAddr = "10.0.0.7";
    		break;
    	default:
    		break;
    	}
    	return strIpAddr;
    }
    /**
     * used by the getPairsOfTheBestSdvsIDSID (String[] reqServices, String locID)
     */
    String theBestSdiotForEachSID = null;
    
    /**
     * Based on the Application input, the algorithm1 will figure out the most appropriate
     * sdiot handling a required service with the format of Command|SID list|Condition
     *
     * @param reqServices
     * @param locID
     * @return a list of Sdiot can provide the required SID
     */
    /*public ArrayList<String> getPairsOfTheBestSdiotNameAndSID(String[] reqServices, String locID) {
        ArrayList<String> listPairOfTheBestSdiotAndSID = new ArrayList<>();
        ArrayList<String> listTheBestSdiotForEachSID = new ArrayList<>();
        //listTheBestSDVSForEachSID.clear(); //error here?why?
        //listPairOfTheBestSDVSBySID.clear();
        
        //for each SID, there are possible Sdiot candidates, 
        //so, it is necessary to choose the best suitable SDVS for a required SID
        for (String aServiceId : reqServices) {
            //listSDVSForEachSID.clear(); //error here
            ArrayList<String> listSdiotForEachSID;
            listSdiotForEachSID = getListSdiotNameByAreaIdAndSid(locID, aServiceId);
            theBestSdiotForEachSID = pickBestSdiotFromListSdiotSID(locID, aServiceId, listSdiotForEachSID);

            if (theBestSdiotForEachSID.length() > 0) {
            	listPairOfTheBestSdiotAndSID.add(theBestSdiotForEachSID + ":" + aServiceId);
            	listTheBestSdiotForEachSID.add(theBestSdiotForEachSID);
            } else {
                System.out.println("Nonone supports the required SID");
                //listSDVSBySID.add(aServiceId + ":SDVS00");
            }
            //System.out.println("Result of Algorithm1: the best SDVS can provide the " +aServiceId);
        }
        return listPairOfTheBestSdiotAndSID;
        
        System.out.println("Result of Algorithm1: the list of the best SDVS can provide the required SID!");
        for (String aBestSDVS : listPairOfTheBestSDVSAndSID) {
            System.out.println(aBestSDVS);
        }
    }*/
	
	
}
