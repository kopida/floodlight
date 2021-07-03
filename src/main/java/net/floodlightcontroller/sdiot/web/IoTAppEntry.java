package net.floodlightcontroller.sdiot.web;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;

//IoTAppEntrySerializer
@JsonSerialize(using = IoTAppEntrySerializer.class)
public class IoTAppEntry {
	
	private String dest;
	private String sender;
	private String result;
	
	
	public IoTAppEntry() {};
	
	public IoTAppEntry(String destinationApp, String sdiotsender, String result) {
		this.dest = destinationApp;
		this.sender = sdiotsender;
		this.result = result;
	}
	
	public String getDest() {
		return dest;
	}

	public void setDest(String dest) {
		this.dest = dest;
	}

	public String getSender() {
		return sender;
	}

	public void setSender(String sender) {
		this.sender = sender;
	}

	public String getResult() {
		return result;
	}

	public void setResult(String result) {
		this.result = result;
	}

	@Override
	public String toString() {
		return "IoTAppEntry [dest=" + dest + ", sender=" + sender + ", result=" + result + "]";
	}
	
	
	
}
