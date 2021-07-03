package net.floodlightcontroller.sdiot.web;

import org.restlet.Context;
import org.restlet.Restlet;
import org.restlet.routing.Router;

import net.floodlightcontroller.devicemanager.web.DeviceEntityResource;
import net.floodlightcontroller.devicemanager.web.DeviceResource;
import net.floodlightcontroller.restserver.RestletRoutable;

public class SdiotWebRoutable implements RestletRoutable {
	
	public SdiotWebRoutable() {
		super();
		System.out.println("\nInit SdiotWebRoutable\n");
	}

	@Override
    public Restlet getRestlet(Context context) {
        Router router = new Router(context);
        router.attach("/", SdiotResource.class);
        router.attach("/iotrequest", IoTRequestResource.class);
        router.attach("/iotappstatus", IoTAppResource.class);
        router.attach("/updateExecStatus", SdiotResourceUpdateExeStatus.class);
        router.attach("/updateStatus", SdiotResourceUpdateStatus.class);//24-Aug-2019
        router.attach("/sdiotCluster", SdiotClusterResource.class);
        router.attach("/test", SdiotResource.class);
        return router;
    }

	@Override
    public String basePath() {
        return "/wm/sdiot";
    }

}
