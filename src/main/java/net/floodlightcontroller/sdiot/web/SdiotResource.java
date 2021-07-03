package net.floodlightcontroller.sdiot.web;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;

import org.projectfloodlight.openflow.protocol.match.MatchFields;
import org.restlet.resource.Get;
import org.restlet.resource.Post;
import org.restlet.resource.Put;
import org.restlet.resource.ServerResource;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.MappingJsonFactory;

import net.floodlightcontroller.accesscontrollist.IACLService;
import net.floodlightcontroller.sdiot.ISdiotService;
import net.floodlightcontroller.sdiot.SdiotModule;

public class SdiotResource extends ServerResource {
	
	/*public SdiotService sdiotService;
	
	public SdiotModule sdiotModule;
	
	public SdiotResource() {
		super();
		this.sdiotService = new SdiotService();
	}
	
	public void SdiotModule() {
		this.sdiotModule = new SdiotModule();
	}*/
	
	@Get("json")
	public Object handleRequest() {
		ISdiotService sdiotService = (ISdiotService) getContext().getAttributes().get(
				ISdiotService.class.getCanonicalName());
		/*System.out.println("\nSize of bigRequest list in GET REQUEST = "+ sdiotService.getListSubReqForBigReq().size());
		for(int i=0;i<sdiotService.getListSubReqForBigReq().size();i++) {
			System.out.println("big req entry = "+ sdiotService.getListSubReqForBigReq().get(i).toString());
		}*///print forever

		return sdiotService.getListSubReqForBigReq();
	}
	
	
	/**
	 * this functions for changing the cluster response
	 * 
	 * 1.parse json to SdiotBigReqEntry class
	 * 2.call SdiotService get list SdiotBigReqEntry
	 * 3.find Item in the list
	 * 4.Update the item by information from json
	 * @param json
	 * @return
	 */
	@Put("json")
	public String updateSdiotBigReqEntry(String json) {
		ISdiotService sdiotService = (ISdiotService) getContext().getAttributes().get(
				ISdiotService.class.getCanonicalName());
		
		try {
//			System.out.println("\nRun updateSdiotBigReqEntry: " + json);
//			System.out.println("\n list before update: ---------------- : " + sdiotService.getListBigReq());
//			sdiotService.updateBigRequestEntry(jsonToBigRequestEntry(json));
//			sdiotService.updateBigRequestEntryV2(jsonToBigRequestEntry(json));//updateClusterResponse
			sdiotService.updateClusterResponse(jsonToBigRequestEntry(json));
			
//			System.out.println("\nList after updateSdiotBigReqEntry: ---------------- : \n" + sdiotService.getListBigReq());
			
		} catch (Exception e) {
			e.printStackTrace();
			return e.getMessage();
			
		}
		return "{'status': 'success'}";
	}
	
	/**
	 * the input request enterred in GUI
	 * @param json
	 * @return
	 */
	@Post("json")
	public String addBigIoTReqEntry(String json) {
		ISdiotService sdiotService = (ISdiotService) getContext().getAttributes().get(
				ISdiotService.class.getCanonicalName());
		
		try {
//			System.out.println("\nRun addBigIoTReqEntry: ---------------- : " + json);
			sdiotService.addBigIoTReq(jsonToBigRequestEntry(json));
//			System.out.println("\nList after addSdiotClusterEntry: ---------------- : \n" + sdiotService.getSdiotClusterServices());
			
		} catch (Exception e) {
			e.printStackTrace();
			return e.getMessage();
			
		}
		return "{'status': 'success'}";
	}
	
	/**
	 * mapping json values to a BigRequestEntry's values
	 * @param json
	 * @return
	 * @throws IOException
	 */
	public Map<String, Object>  jsonToBigRequestEntry(String json) throws IOException {
		MappingJsonFactory f = new MappingJsonFactory();
		Map<String, Object> entry = new HashMap<String, Object>();
		JsonParser jp;
		
		try {
			jp = f.createParser(json);
		} catch (JsonParseException e) {
			throw new IOException(e);
		}

		jp.nextToken();
		if (jp.getCurrentToken() != JsonToken.START_OBJECT) {
			throw new IOException("Expected START_OBJECT");
		}

		while (jp.nextToken() != JsonToken.END_OBJECT) {
			if (jp.getCurrentToken() != JsonToken.FIELD_NAME) {
				throw new IOException("Expected FIELD_NAME");
			}
			String propName = jp.getCurrentName().trim();
			jp.nextToken();
			String propValue = jp.getText();
//			System.out.println("\n---jsonToBigRequestEntry()---");
//			System.out.println("Checking " + propName + ":" + propValue);
			
			entry.put(propName, propValue);
		}
		
		Iterator<Entry<String, Object>> it = entry.entrySet().iterator();
		while(it.hasNext()) {
			Map.Entry<String, Object> pair = (Map.Entry<String, Object>)it.next();
//	        System.out.println(pair.getKey() + " = " + pair.getValue());
		}
		/* Why cannot create object and assign value to the object as well as cannot create variable and assign value for variable 
		 * System.out.println("bigReqID: " + bigReqID);
		System.out.println("sdiotClusterName: " + sdiotClusterName);
		System.out.println("responseFromSdiotCluster: " + responseFromSdiotCluster);
		System.out.println("requiredServices: " + requiredServices);
		SdiotBigReqEntry sdiotBigReqEntry = new SdiotBigReqEntry(bigReqID, sdiotClusterName, responseFromSdiotCluster, requiredServices);
		
		System.out.println(sdiotBigReqEntry.toString());*/
//		System.out.println(entry.toString());
		return entry;
	}

}
