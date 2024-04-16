package mdt;

import javax.annotation.Nullable;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;

import mdt.controller.StatusCode;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

/**
 *
 * @author Kang-Woo Lee (ETRI)
 */
@JsonPropertyOrder({"status", "result"})
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(Include.NON_NULL)
public final class ResponsePayload {
	@JsonProperty("status") 
	private StatusCode m_statusCode;
	@Nullable @JsonInclude(Include.NON_NULL) @JsonProperty("result") 
	private Object m_result;
	
	public static ResponsePayload CREATED(Object result) {
		return new ResponsePayload(StatusCode.SuccessCreated, result);
	}
	public static ResponsePayload SUCCESS(Object result) {
		return new ResponsePayload(StatusCode.Success, result);
	}
	public static ResponsePayload NO_CONTENT() {
		return new ResponsePayload(StatusCode.SuccessNoContent, null);
	}
	
	public ResponsePayload() {
		m_statusCode = StatusCode.Success;
		m_result = null;
	}
	
	public ResponsePayload(StatusCode statusCode, Object result) {
		m_statusCode = statusCode;
		m_result = result;
	}

	@Nullable @JsonInclude(Include.NON_NULL) @JsonProperty("status") 
	public StatusCode getStatusCode() {
		return m_statusCode;
	}
	@Nullable @JsonInclude(Include.NON_NULL) @JsonProperty("status")
	public void setStatusCode(StatusCode statusCode) {
		m_statusCode = statusCode;
	}
	
	@Nullable @JsonInclude(Include.NON_NULL) @JsonProperty("result") 
	public Object getResult() {
		return m_result;
	}
	@Nullable @JsonInclude(Include.NON_NULL) @JsonProperty("result")
	public void setResult(Object result) {
		m_result = result;
	}
}
