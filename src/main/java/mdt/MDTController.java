package mdt;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Base64;

import org.eclipse.digitaltwin.aas4j.v3.dataformat.core.SerializationException;
import org.eclipse.digitaltwin.aas4j.v3.dataformat.json.JsonDeserializer;
import org.eclipse.digitaltwin.aas4j.v3.dataformat.json.JsonSerializer;

import mdt.controller.StatusCode;
import mdt.model.registry.MessageTypeEnum;
import mdt.model.registry.RegistryExceptionEntity;


/**
*
* @author Kang-Woo Lee (ETRI)
*/
public class MDTController<T> {
	protected static final JsonSerializer s_ser = new JsonSerializer();
	protected static final JsonDeserializer s_deser = new JsonDeserializer();
	
	protected String decodeBase64(String encoded) {
		return new String(Base64.getDecoder().decode(encoded));
	}
	
	protected ResponsePayload toSuccessResult(StatusCode statusCode, Object message) {
		return new ResponsePayload(statusCode, message);
	}
	
	protected ResponsePayload getFailedOperationResult(StatusCode statusCode, Throwable cause) {
		ZonedDateTime zdt = Instant.now().atZone(ZoneOffset.systemDefault());
		String tsStr = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSXXX").format(zdt);
		
		RegistryExceptionEntity msg = new RegistryExceptionEntity();
		msg.setMessageType(MessageTypeEnum.Exception);
		msg.setText(cause.getClass().getSimpleName() + ": " + cause.getMessage());
		msg.setTimestamp(tsStr);
		
		return new ResponsePayload(statusCode, msg);
	}
	
	protected String getBadArgumentResult(String details) {
		ZonedDateTime zdt = Instant.now().atZone(ZoneOffset.systemDefault());
		String tsStr = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSXXX").format(zdt);
		
		RegistryExceptionEntity msg = new RegistryExceptionEntity();
		msg.setMessageType(MessageTypeEnum.Error);
		msg.setText(details);
		msg.setTimestamp(tsStr);
		try {
			return s_ser.write(msg);
		}
		catch ( SerializationException e1 ) {
			return details;
		}
	}
}
