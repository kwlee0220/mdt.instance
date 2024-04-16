package mdt.controller;

import java.nio.file.AccessDeniedException;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import jakarta.servlet.http.HttpServletRequest;
import mdt.model.registry.RegistryExceptionEntity;
import mdt.model.registry.ResourceNotFoundException;

/**
 *
 * @author Kang-Woo Lee (ETRI)
 */
@RestControllerAdvice
public class RegistryExceptionAdvice {
	@ExceptionHandler({IllegalArgumentException.class})
	public ResponseEntity<RegistryExceptionEntity> exceptionHandler(HttpServletRequest request,
																	IllegalArgumentException e) {
		return ResponseEntity
				.status(StatusCode.ClientErrorBadRequest.getStatus())
				.body(RegistryExceptionEntity.from(e));
	}
	
	@ExceptionHandler({ResourceNotFoundException.class})
	public ResponseEntity<RegistryExceptionEntity> exceptionHandler(HttpServletRequest request,
																	ResourceNotFoundException e) {
		return ResponseEntity
				.status(StatusCode.ClientErrorResourceNotFound.getStatus())
				.body(RegistryExceptionEntity.from(e));
	}

	@ExceptionHandler({Exception.class})
	public ResponseEntity<RegistryExceptionEntity> exceptionHandler(HttpServletRequest request, Exception e) {
		return ResponseEntity
				.status(StatusCode.ServerInternalError.getStatus())
				.body(RegistryExceptionEntity.from(e));
	}

	@ExceptionHandler({AccessDeniedException.class})
	public ResponseEntity<RegistryExceptionEntity> exceptionHandler(HttpServletRequest request,
																	AccessDeniedException e) {
		return ResponseEntity
				.status(StatusCode.ClientMethodNotAllowed.getStatus())
				.body(RegistryExceptionEntity.from(e));
	}
}
