package net.floodlightcontroller.sdiot.web;

import java.io.IOException;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;

public class IoTAppEntrySerializer extends JsonSerializer<IoTAppEntry>{

	@Override
	public void serialize(IoTAppEntry entry, JsonGenerator jGen, SerializerProvider arg2)
			throws IOException, JsonProcessingException {
		jGen.writeStartObject();
        
		jGen.writeStringField("dest", entry.getDest());
        jGen.writeStringField("sender", entry.getSender());
        jGen.writeStringField("result", entry.getResult());

        jGen.writeEndObject();
		
	}
	

}
