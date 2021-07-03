package net.floodlightcontroller.sdiot.web;

import java.io.IOException;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;

public class IoTBigReqEntrySerializer extends JsonSerializer<IoTBigReqEntry>{
	@Override
	public void serialize(IoTBigReqEntry entry, JsonGenerator jGen, SerializerProvider arg2)
			throws IOException, JsonProcessingException {
		
		jGen.writeStartObject();
        
        jGen.writeStringField("reqID", entry.getReqID());
        jGen.writeStringField("reqArea", entry.getReqArea());
        jGen.writeStringField("reqService", entry.getReqService());//add 4-8-2019
        jGen.writeStringField("startTime", entry.getStartTimeInString());//add 20-10-2019  time in milliseconds
        jGen.writeStringField("status", entry.getStatus());//add 24-8-2019

        jGen.writeEndObject();
	}
}
