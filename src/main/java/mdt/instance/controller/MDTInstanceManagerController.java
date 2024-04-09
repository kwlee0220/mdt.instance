package mdt.instance.controller;

import java.util.List;
import java.util.stream.Collectors;

import org.eclipse.digitaltwin.aas4j.v3.dataformat.core.DeserializationException;
import org.eclipse.digitaltwin.aas4j.v3.dataformat.core.SerializationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import mdt.MDTController;
import mdt.instance.model.MDTInstanceManagerImpl;
import mdt.model.instance.AddMDTInstancePayload;
import mdt.model.instance.MDTInstance;
import mdt.model.instance.MDTInstanceManagerException;
import mdt.model.instance.MDTInstancePayload;
import mdt.model.instance.MDTInstanceStatus;


/**
*
* @author Kang-Woo Lee (ETRI)
*/
@RestController
@RequestMapping("/instance_manager")
public class MDTInstanceManagerController extends MDTController<MDTInstance> implements InitializingBean {
	private final Logger s_logger = LoggerFactory.getLogger(MDTInstanceManagerController.class);
	
    @Autowired MDTInstanceManagerImpl m_registry;	

	@Override
	public void afterPropertiesSet() throws Exception {
		if ( s_logger.isInfoEnabled() ) {
			s_logger.info("MDTInstanceManagerController is ready to serve");
		}
	}

    @GetMapping("/id/{id}")
    @ResponseStatus(HttpStatus.OK)
    public MDTInstancePayload getMDTInstance(@PathVariable("id") String id) throws SerializationException {
    	return toPayload(m_registry.getInstance(id));
    }

    @GetMapping("/aasId/{aasId}")
    @ResponseStatus(HttpStatus.OK)
    public MDTInstancePayload getMDTInstanceByAasId(@PathVariable("aasId") String aasId)
    	throws SerializationException {
    	String decoded = decodeBase64(aasId);
		return toPayload(m_registry.getInstanceByAasId(decoded));
    }

    @GetMapping({"/aasIdShort/{aasIdShort}"})
    @ResponseStatus(HttpStatus.OK)
    public List<MDTInstancePayload> getAllMDTInstancesByAasIdShort(@PathVariable("aasIdShort") String aasIdShort)
    	throws MDTInstanceManagerException, SerializationException {
		return m_registry.getAllInstancesByIdShort(aasIdShort).stream()
							.map(this::toPayload)
							.collect(Collectors.toList());
    }
    
    @GetMapping({"/all"})
    @ResponseStatus(HttpStatus.OK)
    public List<MDTInstancePayload> getAllMDTInstaces()
    	throws MDTInstanceManagerException, SerializationException {
    	List<MDTInstance> instances = m_registry.getAllInstances();
		return instances.stream()
						.map(this::toPayload)
						.collect(Collectors.toList());
    }
    
    @PutMapping({"/status"})
    @ResponseStatus(HttpStatus.OK)
    public List<MDTInstancePayload> getAllMDTInstacesOfStatus(@RequestBody MDTInstanceStatus status)
    	throws MDTInstanceManagerException, SerializationException {
    	List<MDTInstance> instances = m_registry.getAllInstancesOfStatus(status);
		return instances.stream()
						.map(this::toPayload)
						.collect(Collectors.toList());
    }

    @PostMapping({""})
    @ResponseStatus(HttpStatus.CREATED)
    public MDTInstancePayload addMDTInstance(@RequestBody String payloadJson) throws DeserializationException {
    	AddMDTInstancePayload payload = s_deser.read(payloadJson, AddMDTInstancePayload.class);
    	MDTInstance instance = m_registry.addInstance(payload.getId(), payload.getEnvironment(),
    													payload.getArguments());
    	return toPayload(instance);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void removeMDTInstance(@PathVariable("id") String id) throws SerializationException {
		m_registry.removeInstance(id);
    }

    @DeleteMapping("")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void removeAllMDTInstance() throws SerializationException {
		m_registry.removeInstanceAll();
    }
    
    private MDTInstancePayload toPayload(MDTInstance instance) {
    	return new MDTInstancePayload(instance.getId(), instance.getAASId(), instance.getAASIdShort());
    }
}
