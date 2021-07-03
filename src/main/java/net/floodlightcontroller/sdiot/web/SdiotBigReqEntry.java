package net.floodlightcontroller.sdiot.web;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;

@JsonSerialize(using = SdiotBigReqEntrySerializer.class)
public class SdiotBigReqEntry {
	private String bigReqID;
	private String clusterName;
	private String clusterResponse;
	private String requiredServices; //requiredServices
	private String subReq;
	private String executed;
	private String status;
	
	

	public SdiotBigReqEntry() {};
	
	public SdiotBigReqEntry(String id, String sdiotName, String clusterRes, String reqService) {
		bigReqID = id;
		clusterName = sdiotName;
		clusterResponse = clusterRes;
		requiredServices = reqService;
	}
	
	public SdiotBigReqEntry(String id, String sdiotName, String clusterRes, String reqService, String subRequest) {
		bigReqID = id;
		clusterName = sdiotName;
		clusterResponse = clusterRes;
		requiredServices = reqService;
		subReq = subRequest;
	}
	
	public SdiotBigReqEntry(String id, String sdiotName, String clusterRes, String reqService, String subRequest, String exec) {
		bigReqID = id;
		clusterName = sdiotName;
		clusterResponse = clusterRes;
		requiredServices = reqService;
		subReq = subRequest;
		executed = exec;
	}
	
	public SdiotBigReqEntry(String id, String sdiotName, String clusterRes, String reqService, String subRequest, String exec, String status) {
		bigReqID = id;
		clusterName = sdiotName;
		clusterResponse = clusterRes;
		requiredServices = reqService;
		subReq = subRequest;
		executed = exec;
		this.status = status;
	}


	
	
	@Override
	public String toString() {
		return "SdiotBigReqEntry [bigReqID=" + bigReqID + ", clusterName=" + clusterName + ", clusterResponse="
				+ clusterResponse + ", requiredServices=" + requiredServices + ", subReq=" + subReq + ", executed="
				+ executed + ", status=" + status + "]";
	}

	public String getBigReqID() {
		return bigReqID;
	}


	public void setBigReqID(String bigReqID) {
		this.bigReqID = bigReqID;
	}

	public String getClusterName() {
		return clusterName;
	}

	public void setClusterName(String clusterName) {
		this.clusterName = clusterName;
	}

	public String getClusterResponse() {
		return clusterResponse;
	}

	public void setClusterResponse(String clusterResponse) {
		this.clusterResponse = clusterResponse;
	}

	public String getRequiredServices() {
		return requiredServices;
	}

	public void setRequiredServices(String requiredServices) {
		this.requiredServices = requiredServices;
	}
	

	public String getSubReq() {
		return subReq;
	}

	public void setSubReq(String subReq) {
		this.subReq = subReq;
	}

	public String getExecuted() {
		return executed;
	}

	public void setExecuted(String executed) {
		this.executed = executed;
	}

	public String getStatus() {
		return status;
	}

	public void setStatus(String status) {
		this.status = status;
	}
	
	

}
