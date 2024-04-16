package mdt.controller;

import org.springframework.http.HttpStatus;

/**
 *
 * @author Kang-Woo Lee (ETRI)
 */
public enum StatusCode {
	Success(HttpStatus.OK),
	SuccessCreated(HttpStatus.CREATED),
	SuccessNoContent(HttpStatus.NO_CONTENT),
	ClientForbidden(HttpStatus.FORBIDDEN),
	ClientErrorBadRequest(HttpStatus.BAD_REQUEST),
	ClientMethodNotAllowed(HttpStatus.METHOD_NOT_ALLOWED),
	ClientErrorResourceNotFound(HttpStatus.NOT_FOUND),
	ServerInternalError(HttpStatus.INTERNAL_SERVER_ERROR),
	ServerErrorBadGateway(HttpStatus.BAD_GATEWAY);
	
	private final HttpStatus m_status;
	private final String m_message;
	
	StatusCode(HttpStatus status, String message) {
		m_status = status;
		m_message = message;
	}
	
	StatusCode(HttpStatus status) {
		this(status, null);
	}
	
	public HttpStatus getStatus() {
		return m_status;
	}
	
	public String getMessage() {
		return m_message;
	}
}
