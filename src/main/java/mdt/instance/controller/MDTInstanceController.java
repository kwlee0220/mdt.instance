package mdt.instance.controller;

import java.util.List;

import org.eclipse.digitaltwin.aas4j.v3.dataformat.core.SerializationException;
import org.eclipse.digitaltwin.aas4j.v3.model.AssetAdministrationShellDescriptor;
import org.eclipse.digitaltwin.aas4j.v3.model.SubmodelDescriptor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import mdt.MDTController;
import mdt.instance.model.MDTJarInstance;
import mdt.instance.model.MDTInstanceManagerImpl;
import mdt.model.instance.MDTInstance;
import mdt.model.instance.MDTInstanceStatus;
import mdt.model.instance.StatusResult;


/**
*
* @author Kang-Woo Lee (ETRI)
*/
@RestController
@RequestMapping("/instances")
public class MDTInstanceController extends MDTController<MDTInstance> implements InitializingBean {
	private final Logger s_logger = LoggerFactory.getLogger(MDTInstanceController.class);
	
    @Autowired MDTInstanceManagerImpl m_registry;

	@Override
	public void afterPropertiesSet() throws Exception {
		if ( s_logger.isInfoEnabled() ) {
			s_logger.info("MDTInstanceController is ready to serve");
		}
	}

    @GetMapping("/status/{id}")
    @ResponseStatus(HttpStatus.OK)
    public MDTInstanceStatus getStatus(@PathVariable("id") String id) {
    	MDTJarInstance inst = m_registry.getInstance(id);
    	return inst.getStatus();
    }

    @GetMapping("/endpoint/{id}")
    @ResponseStatus(HttpStatus.OK)
    public String gerServiceEndpoint(@PathVariable("id") String id) {
    	MDTJarInstance inst = m_registry.getInstance(id);
    	return inst.getServiceEndpoint();
    }

    @PostMapping({"/start/{id}"})
    @ResponseStatus(HttpStatus.CREATED)
    public StatusResult start(@PathVariable("id") String id) throws SerializationException {
    	MDTInstance inst = m_registry.getInstance(id);
    	return inst.start();
    }

    @PostMapping({"/stop/{id}"})
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public StatusResult stop(@PathVariable("id") String id) throws SerializationException {
    	MDTInstance inst = m_registry.getInstance(id);
    	return inst.stop();
    }

    @GetMapping("/aas_descriptor/{id}")
    @ResponseStatus(HttpStatus.OK)
    public String getAssetAdministrationShellDescriptor(@PathVariable("id") String id)
    	throws SerializationException {
    	MDTJarInstance inst = m_registry.getInstance(id);
    	AssetAdministrationShellDescriptor desc = inst.getAASDescriptor();
		return s_ser.write(desc);
    }

    @GetMapping("/submodel_descriptors/{id}")
    @ResponseStatus(HttpStatus.OK)
    public String getAllSubmodelDescriptors(@PathVariable("id") String id) throws SerializationException {
    	MDTJarInstance inst = m_registry.getInstance(id);
    	List<SubmodelDescriptor> descList = inst.getAllSubmodelDescriptors();
		return s_ser.write(descList);
    }
}
