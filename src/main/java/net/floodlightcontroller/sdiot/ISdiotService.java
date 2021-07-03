package net.floodlightcontroller.sdiot;

import java.util.ArrayList;
import java.util.Map;

import net.floodlightcontroller.core.module.IFloodlightService;
import net.floodlightcontroller.sdiot.web.IoTAppEntry;
import net.floodlightcontroller.sdiot.web.IoTBigReqEntry;
import net.floodlightcontroller.sdiot.web.SdiotBigReqEntry;
import net.floodlightcontroller.sdiot.web.SdiotCluster;

public interface ISdiotService extends IFloodlightService{
	
	
	/**
	 * get a list of all sub-request for big IoT Requests
	 * @return
	 */
	public ArrayList<SdiotBigReqEntry> getListSubReqForBigReq() ;
	
	/**
	 * get list of sdiot clusters and their provided services, location, area
	 * @return
	 */
	public ArrayList<SdiotCluster> getSdiotClusterServices();
	
	/**
	 * get list of big IoT requests
	 * @return
	 */
	public ArrayList<IoTBigReqEntry> getBigReqList();
	
	/**
	 * get list of IoT Application destination which receiving results from sdiots
	 * @return
	 */
	public ArrayList<IoTAppEntry> getIoTAppList();
	
	/**
	 * get a list of all sdiot clusters serving a specific big IoT request
	 * @param reqID
	 * @return
	 */
	public ArrayList<String> getAllClusterName(int reqID);
	
	/**
	 * update the response of a sdiot cluster to a big iot request
	 * @param clusterName
	 * @param bigReqID
	 * @param clusterRes
	 */
	public boolean updateClusterResponse(Map<String, Object> entry); 
	
	
	/**
	 * create a a list of big IoT requests
	 * @return
	 */
	public ArrayList<SdiotBigReqEntry> createDummyBigRequestList();
	
	/**
	 * update the Executed status of a sub-request that has been executed by a sdiot cluster
	 * the sdiot cluster also sent results to the desired destination
	 * @param entry
	 * @return
	 */
	public boolean updateExecStatus(Map<String, Object> entry);
	
	/**
	 * update the status of a completion of an sdiot request
	 * the sdiot cluster announces the sdn controller when it removes a completed sdiot request
	 * it means that it completely achieved required services for an sdiot request
	 * @param entry
	 * @return
	 */
	public boolean updateStatus(Map<String, Object> entry);
	
	/**
	 * 
	 * @param entry
	 * @return
	 */
	public boolean postSdiotClusterResources(Map<String, Object> entry);
	
	/**
	 * 
	 * @param entry
	 * @return
	 */
	public boolean updateSdiotClusterResources(Map<String, Object> entry);
	
	/**
	 * get input request from GUI
	 * @param inputReq
	 * @return
	 * @throws  
	 */
	public boolean addBigIoTReq(Map<String, Object> entry) ;
	
	/**
	 * receiving info from destination of iot application via POST message
	 * adding IoT results to API
	 * add update status of iot application dest host which receive results from sdiot systems
	 * it is for showing results received at desired destionation -> proving routing configuration of sdn controller
	 * @param inputReq
	 * @return
	 * @throws  
	 */
	public boolean addIoTAppEntry(Map<String, Object> entry) ;

}
