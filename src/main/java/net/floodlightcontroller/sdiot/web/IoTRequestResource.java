package net.floodlightcontroller.sdiot.web;

import org.restlet.resource.Get;
import org.restlet.resource.ServerResource;

import net.floodlightcontroller.sdiot.ISdiotService;

public class IoTRequestResource extends ServerResource{
	
	@Get("json")
	public Object showIoTRequestList() {
		ISdiotService sdiotService = (ISdiotService) getContext().getAttributes().get(
				ISdiotService.class.getCanonicalName());
//		System.out.println("\nSize of big IoT Request list in GET REQUEST = "+ sdiotService.getBigReqList().size());

		return sdiotService.getBigReqList();
	}
}
