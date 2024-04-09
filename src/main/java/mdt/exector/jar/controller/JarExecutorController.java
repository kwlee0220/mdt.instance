package mdt.exector.jar.controller;

import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.fasterxml.jackson.databind.json.JsonMapper;

import mdt.client.HttpServiceFactory;
import mdt.exector.jar.model.JarExecutionCommand;
import mdt.exector.jar.model.JarInstanceExecutor;
import mdt.exector.jar.model.MDTInstanceExecutorException;
import mdt.model.instance.StatusResult;


/**
*
* @author Kang-Woo Lee (ETRI)
*/
@RestController
@RequestMapping("/executor/jar")
public class JarExecutorController implements InitializingBean {
	private static final Logger s_logger = LoggerFactory.getLogger(JarExecutorController.class);
	
	@Autowired JarInstanceExecutor m_exector;

	private final HttpServiceFactory m_svcFact;
	private final JsonMapper m_mapper = JsonMapper.builder().build();
	
	public JarExecutorController() throws KeyManagementException, NoSuchAlgorithmException {
		m_svcFact = new HttpServiceFactory();
	}
	
	public void setJarExecutor(JarInstanceExecutor exector) {
		m_exector = exector;
	}
	
	@Override
	public void afterPropertiesSet() throws Exception {
	}
	
    @PostMapping({""})
    @ResponseStatus(HttpStatus.CREATED)
    public StatusResult start(@RequestBody JarExecutionCommand cmd) {
    	String instId = cmd.getInstanceId();
		
		StatusResult result = m_exector.start(instId, cmd.getAasId(), cmd.getArguments());
		return new StatusResult(instId, result.getStatus(), result.getServiceEndpoint());
    }

    @DeleteMapping("/{instanceId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void stop(@PathVariable("instanceId") String instanceId) {
    	if ( s_logger.isInfoEnabled() ) {
    		s_logger.info("stopping MDTInstance: {}", instanceId);
    	}
    	
    	m_exector.stop(instanceId);
    }

    @GetMapping("/{instanceId}")
    @ResponseStatus(HttpStatus.OK)
    public StatusResult getStatus(@PathVariable("instanceId") String instanceId) {
    	return m_exector.getStatus(instanceId);
    }

    @GetMapping({""})
    @ResponseStatus(HttpStatus.OK)
	public List<StatusResult> getAllStatuses() throws MDTInstanceExecutorException {
    	return m_exector.getAllStatuses();
	}
}
