package mdt.controller;

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

import utils.io.IOUtils;

import mdt.MDTController;
import mdt.model.instance.MDTInstance;
import mdt.model.instance.MDTInstanceManager;
import mdt.model.instance.MDTInstanceStatus;
import mdt.model.instance.StartResult;


/**
*
* @author Kang-Woo Lee (ETRI)
*/
@RestController
@RequestMapping("/instances")
public class MDTInstanceController extends MDTController<MDTInstance> implements InitializingBean {
	private final Logger s_logger = LoggerFactory.getLogger(MDTInstanceController.class);
	
    @Autowired MDTInstanceManager m_instanceManager;

	@Override
	public void afterPropertiesSet() throws Exception {
		if ( s_logger.isInfoEnabled() ) {
			s_logger.info("MDTInstanceController is ready to serve");
		}
	}

    @GetMapping("/status/{id}")
    @ResponseStatus(HttpStatus.OK)
    public MDTInstanceStatus getStatus(@PathVariable("id") String id) {
    	MDTInstance inst = null;
    	try {
    		inst = m_instanceManager.getInstance(id);
    		return inst.getStatus();
    	}
    	finally {
        	IOUtils.closeQuietly(inst);
    	}
    }

    @GetMapping("/endpoint/{id}")
    @ResponseStatus(HttpStatus.OK)
    public String gerServiceEndpoint(@PathVariable("id") String id) {
    	MDTInstance inst = null;
    	try {
    		inst = m_instanceManager.getInstance(id);
    		return inst.getServiceEndpoint();
    	}
    	finally {
        	IOUtils.closeQuietly(inst);
    	}
    }

    @GetMapping("/arguments/{id}")
    @ResponseStatus(HttpStatus.OK)
    public String getExecutionArguments(@PathVariable("id") String id) {
    	MDTInstance inst = null;
    	try {
    		inst = m_instanceManager.getInstance(id);
    		return inst.getExecutionArguments();
    	}
    	finally {
        	IOUtils.closeQuietly(inst);
    	}
    }

    @PostMapping({"/start/{id}"})
    @ResponseStatus(HttpStatus.CREATED)
    public StartResult start(@PathVariable("id") String id) throws SerializationException {
    	MDTInstance inst = null;
    	try {
    		inst = m_instanceManager.getInstance(id);
        	return inst.start();
    	}
    	finally {
        	IOUtils.closeQuietly(inst);
    	}
    }

    @PostMapping({"/stop/{id}"})
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void stop(@PathVariable("id") String id) throws SerializationException {
    	MDTInstance inst = null;
    	try {
    		inst = m_instanceManager.getInstance(id);
        	inst.stop();
    	}
    	finally {
        	IOUtils.closeQuietly(inst);
    	}
    }

    @GetMapping("/aas_descriptor/{id}")
    @ResponseStatus(HttpStatus.OK)
    public String getAssetAdministrationShellDescriptor(@PathVariable("id") String id)
    	throws SerializationException {
    	MDTInstance inst = null;
    	try {
    		inst = m_instanceManager.getInstance(id);
        	AssetAdministrationShellDescriptor desc = inst.getAssetAdministrationShellDescriptor();
    		return s_ser.write(desc);
    	}
    	finally {
        	IOUtils.closeQuietly(inst);
    	}
    }

    @GetMapping("/submodel_descriptors/{id}")
    @ResponseStatus(HttpStatus.OK)
    public String getAllSubmodelDescriptors(@PathVariable("id") String id) throws SerializationException {
    	MDTInstance inst = null;
    	try {
    		inst = m_instanceManager.getInstance(id);
        	List<SubmodelDescriptor> descList = inst.getAllSubmodelDescriptors();
    		return s_ser.write(descList);
    	}
    	finally {
        	IOUtils.closeQuietly(inst);
    	}
    }
}
