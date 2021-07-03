package net.floodlightcontroller.sdiot.web;

import java.io.IOException;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;


public class SdiotBigReqEntrySerializer extends JsonSerializer<SdiotBigReqEntry> {

	@Override
	public void serialize(SdiotBigReqEntry entry, JsonGenerator jGen, SerializerProvider arg2)
			throws IOException, JsonProcessingException {

		jGen.writeStartObject();
        
        jGen.writeStringField("bigReqID", entry.getBigReqID());
        jGen.writeStringField("requiredService", entry.getRequiredServices());
        jGen.writeStringField("subReq", entry.getSubReq());//add 4-8-2019
        jGen.writeStringField("clusterResponse", entry.getClusterResponse());
        jGen.writeStringField("executed", entry.getExecuted());
        jGen.writeStringField("clusterName", entry.getClusterName());
        jGen.writeStringField("status", entry.getStatus());

        jGen.writeEndObject();
	}

}
