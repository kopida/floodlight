package net.floodlightcontroller.sdiot.web;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;

@JsonSerialize(using = IoTBigReqEntrySerializer.class)
public class IoTBigReqEntry {
	private String reqID;
	private String reqService;
	private String reqArea;
	private String status; //execution status of all related sdiot sub-requests
	private long startTime; //the time when an IoT request is processed by the SDN controller
	private long endTime; //the time when all related sdiot requests of an IoT request are executed
	private long processingTime;
	
	public IoTBigReqEntry() {};
	
	public IoTBigReqEntry(String id, String requiredService, String areaID) {
		reqID = id;
		reqService = requiredService;
		reqArea = areaID;
		this.status = "OnGoing";
	}
	
	public IoTBigReqEntry(String id, String requiredService, String areaID,  long startTime, long endTime) {
		reqID = id;
		reqService = requiredService;
		reqArea = areaID;
		this.status = "OnGoing";
		this.startTime = startTime;
		this.endTime = endTime;
		processingTime = 0;
	}
	
	public String getReqID() {
		return reqID;
	}
	public void setReqID(String reqID) {
		this.reqID = reqID;
	}
	public String getReqService() {
		return reqService;
	}
	public void setReqService(String reqService) {
		this.reqService = reqService;
	}
	public String getReqArea() {
		return reqArea;
	}
	public void setReqArea(String reqArea) {
		this.reqArea = reqArea;
	}
	
	
	
	public String getStatus() {
		return status;
	}

	public void setStatus(String status) {
		this.status = status;
	}
	
	

	public long getStartTime() {
		return startTime;
	}
	
	public String getStartTimeInString() {
		String timeInStr;
		timeInStr = String.valueOf(getStartTime());
		return timeInStr;
	}

	public void setStartTime(long startTime) {
		this.startTime = startTime;
	}
	
	
	public long getEndTime() {
		return endTime;
	}

	public void setEndTime(long endTime) {
		this.endTime = endTime;
	}
	
	
	public long getProcessingTime() {
		return processingTime;
	}

	public void setProcessingTime(long processingTime) {
		this.processingTime = processingTime;
	}

	@Override
	public String toString() {
		return "IoTBigReqEntry [reqID=" + reqID + ", reqService=" + reqService + ", reqArea=" + reqArea + ", status="
				+ status + ", startTime=" + startTime + ", endTime=" + endTime + "]";
	}
	
	
	
	
}
