package mdt.registry.controller;

import javax.annotation.Nullable;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;

import mdt.model.registry.RegistryExceptionEntity;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

/**
 *
 * @author Kang-Woo Lee (ETRI)
 */
@JsonPropertyOrder({"success", "message"})
@JsonInclude(Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public final class Result {
	@JsonProperty("success") 
	private boolean m_success;
	@Nullable @JsonInclude(Include.NON_NULL) @JsonProperty("message") 
	private RegistryExceptionEntity m_message;
	
	public Result() {
		m_success = false;
		m_message = null;
	}
	
	public Result(boolean success, RegistryExceptionEntity message) {
		m_success = success;
		m_message = message;
	}

	@Nullable @JsonInclude(Include.NON_NULL) @JsonProperty("success") 
	public boolean getSuccess() {
		return m_success;
	}
	@Nullable @JsonInclude(Include.NON_NULL) @JsonProperty("success")
	public void setSuccess(boolean success) {
		m_success = success;
	}
	
	@Nullable @JsonInclude(Include.NON_NULL) @JsonProperty("message") 
	public RegistryExceptionEntity getMessage() {
		return m_message;
	}
	@Nullable @JsonInclude(Include.NON_NULL) @JsonProperty("message")
	public void setMessage(RegistryExceptionEntity message) {
		m_message = message;
	}
}
