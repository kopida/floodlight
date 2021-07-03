package net.floodlightcontroller.sdiot.web;

import java.io.IOException;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;


public class SdiotClusterSerializer extends JsonSerializer<SdiotCluster> {

	@Override
	public void serialize(SdiotCluster entry, JsonGenerator jGen, SerializerProvider arg2)
			throws IOException, JsonProcessingException {

		jGen.writeStartObject(); //clusterName managedArea managedLocation providedServices
        
        jGen.writeStringField("clusterName", entry.getClusterName());
        jGen.writeStringField("managedArea", entry.getManagedArea());
        jGen.writeStringField("managedLocation", entry.getManagedLocation());
        jGen.writeStringField("providedServices", entry.getProvidedServices());

        jGen.writeEndObject();
	}


}
