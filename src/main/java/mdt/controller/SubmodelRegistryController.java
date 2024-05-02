package mdt.controller;

import java.util.List;

import org.eclipse.digitaltwin.aas4j.v3.dataformat.core.DeserializationException;
import org.eclipse.digitaltwin.aas4j.v3.dataformat.core.SerializationException;
import org.eclipse.digitaltwin.aas4j.v3.model.SubmodelDescriptor;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import mdt.MDTController;
import mdt.registry.CachingFileMDTSubmodelRegistry;


/**
*
* @author Kang-Woo Lee (ETRI)
*/
@RestController
@RequestMapping("/registry/submodel-descriptors")
public class SubmodelRegistryController extends MDTController<SubmodelDescriptor>
											implements InitializingBean {
	private final Logger s_logger = LoggerFactory.getLogger(SubmodelRegistryController.class);
	
	@Autowired CachingFileMDTSubmodelRegistry m_registry;

	@Override
	public void afterPropertiesSet() throws Exception {
		if ( s_logger.isInfoEnabled() ) {
			s_logger.info("set Submodel Registry store: {}", m_registry.getStoreDir().getAbsolutePath());
		}
	}

    @GetMapping({"", "/"})
    @ResponseStatus(HttpStatus.OK)
    public String getAllSubmodelDescriptors(@RequestParam(name="idShort", required=false) String idShort)
    	throws SerializationException {
		List<SubmodelDescriptor> submodelList
			= (idShort != null)
					? m_registry.getAllSubmodelDescriptorsByIdShort(idShort)
					: m_registry.getAllSubmodelDescriptors();
		return s_ser.writeList(submodelList);
    }

    @GetMapping(value = "/{submodelId}")
    @ResponseStatus(HttpStatus.OK)
    public String getSubmodelDescriptorById(@PathVariable("submodelId") String submodelId)
    	throws SerializationException {
		submodelId = decodeBase64(submodelId);
		return m_registry.getJsonSubmodelDescriptorById(submodelId);
    }

    @PostMapping({"", "/"})
    @ResponseStatus(HttpStatus.CREATED)
    public String addSubmodelDescriptor(@RequestBody String submodelJson)
    	throws SerializationException, DeserializationException {
		SubmodelDescriptor aas = s_deser.read(submodelJson, SubmodelDescriptor.class);
		return s_ser.write(m_registry.addSubmodelDescriptor(aas));
    }

    @DeleteMapping(value = "/{submodelId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void removeSubmodelDescriptor(@PathVariable("submodelId") String submodelId)
    	throws SerializationException {
    	submodelId = decodeBase64(submodelId);
		m_registry.removeSubmodelDescriptorById(submodelId);
    }

    @PutMapping(value = "/{submodelId}")
    @ResponseStatus(HttpStatus.CREATED)
    public String updateSubmodelDescriptor(@PathVariable("submodelId") String submodelId,
    														@RequestBody String submodelJson)
    	throws SerializationException, DeserializationException {

    	SubmodelDescriptor aas = s_deser.read(submodelJson, SubmodelDescriptor.class);
		return s_ser.write(m_registry.updateSubmodelDescriptorById(aas));
    }
}
