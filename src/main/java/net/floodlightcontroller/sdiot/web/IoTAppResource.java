package net.floodlightcontroller.sdiot.web;

import java.io.IOException;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;

import org.restlet.resource.Get;
import org.restlet.resource.Post;
import org.restlet.resource.ServerResource;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.MappingJsonFactory;

import net.floodlightcontroller.sdiot.ISdiotService;

public class IoTAppResource extends ServerResource{
	
	@Get("json")
	public Object getIoTAppList() {
		ISdiotService sdiotService = (ISdiotService) getContext().getAttributes().get(
				ISdiotService.class.getCanonicalName());
		return sdiotService.getIoTAppList();
	}
	
	
	/**
	 * the input request enterred in GUI
	 * @param json
	 * @return
	 */
	@Post("json")
	public String postIoTAppEntry(String json) {
		ISdiotService sdiotService = (ISdiotService) getContext().getAttributes().get(
				ISdiotService.class.getCanonicalName());
		
		try {
//			System.out.println("\n--Run addIoTAppEntry: ------: " + json);
			sdiotService.addIoTAppEntry(jsonToBigRequestEntry(json));
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
//		System.out.println(entry.toString());
		return entry;
	}
}
