package mdt.instance.controller;

import java.nio.file.AccessDeniedException;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import jakarta.servlet.http.HttpServletRequest;
import mdt.model.MDTExceptionEntity;
import mdt.model.registry.ResourceNotFoundException;

/**
 *
 * @author Kang-Woo Lee (ETRI)
 */
@RestControllerAdvice
public class MDTExceptionAdvice {
	@ExceptionHandler({IllegalArgumentException.class})
	public ResponseEntity<MDTExceptionEntity> exceptionHandler(HttpServletRequest request,
																IllegalArgumentException e) {
		return ResponseEntity
				.status(HttpStatus.BAD_REQUEST)
				.body(MDTExceptionEntity.from(e));
	}
	
	@ExceptionHandler({ResourceNotFoundException.class})
	public ResponseEntity<MDTExceptionEntity> exceptionHandler(HttpServletRequest request,
																ResourceNotFoundException e) {
		return ResponseEntity
				.status(HttpStatus.NOT_FOUND)
				.body(MDTExceptionEntity.from(e));
	}
	
	@ExceptionHandler({NoResourceFoundException.class})
	public ResponseEntity<MDTExceptionEntity> exceptionHandler(HttpServletRequest request,
																	NoResourceFoundException e) {
		return ResponseEntity
				.status(HttpStatus.BAD_REQUEST)
				.body(new MDTExceptionEntity("BAD_REQUEST", request.getRequestURL().toString()));
	}

	@ExceptionHandler({Exception.class})
	public ResponseEntity<MDTExceptionEntity> exceptionHandler(HttpServletRequest request, Exception e) {
		return ResponseEntity
				.status(HttpStatus.INTERNAL_SERVER_ERROR)
				.body(MDTExceptionEntity.from(e));
	}

	@ExceptionHandler({AccessDeniedException.class})
	public ResponseEntity<MDTExceptionEntity> exceptionHandler(HttpServletRequest request,
																	AccessDeniedException e) {
		return ResponseEntity
				.status(HttpStatus.METHOD_NOT_ALLOWED)
				.body(MDTExceptionEntity.from(e));
	}
}
