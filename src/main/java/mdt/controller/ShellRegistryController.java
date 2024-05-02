package mdt.controller;

import java.util.List;

import org.eclipse.digitaltwin.aas4j.v3.dataformat.core.DeserializationException;
import org.eclipse.digitaltwin.aas4j.v3.dataformat.core.SerializationException;
import org.eclipse.digitaltwin.aas4j.v3.model.AssetAdministrationShellDescriptor;
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
import mdt.registry.CachingFileMDTAASRegistry;


/**
*
* @author Kang-Woo Lee (ETRI)
*/
@RestController
@RequestMapping("/registry/shell-descriptors")
public class ShellRegistryController extends MDTController<AssetAdministrationShellDescriptor>
										implements InitializingBean {
	private final Logger s_logger = LoggerFactory.getLogger(ShellRegistryController.class);
	
	@Autowired
    private CachingFileMDTAASRegistry m_registry;

	@Override
	public void afterPropertiesSet() throws Exception {
		if ( s_logger.isInfoEnabled() ) {
			s_logger.info("set AAS Registry store: {}", m_registry.getStoreDir().getAbsolutePath());
		}
	}

    @GetMapping({"", "/"})
    @ResponseStatus(HttpStatus.OK)
    public String getAllAssetAdministrationShellDescriptors(
    										@RequestParam(name="idShort", required=false) String idShort)
    	throws SerializationException {
		List<AssetAdministrationShellDescriptor> aasList
			= (idShort != null)
					? m_registry.getAllAssetAdministrationShellDescriptorsByIdShort(idShort)
					: m_registry.getAllAssetAdministrationShellDescriptors();
		return s_ser.writeList(aasList);
    }

    @GetMapping("/{aasId}")
    @ResponseStatus(HttpStatus.OK)
    public String getAssetAdministrationShellDescriptorById(@PathVariable("aasId") String aasId)
    	throws SerializationException {
		aasId = decodeBase64(aasId);
		return m_registry.getJsonAssetAdministrationShellDescriptorById(aasId);
    }

    @PostMapping({"", "/"})
    @ResponseStatus(HttpStatus.CREATED)
    public String addAssetAdministrationShellDescriptor(@RequestBody String aasJson)
    	throws SerializationException, DeserializationException {
		AssetAdministrationShellDescriptor aas = s_deser.read(aasJson,
															AssetAdministrationShellDescriptor.class);
		return s_ser.write(m_registry.addAssetAdministrationShellDescriptor(aas));
    }

    @DeleteMapping(value = "/{aasId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void removeAssetAdministrationShellDescriptor(@PathVariable("aasId") String aasId)
    	throws SerializationException {
		aasId = decodeBase64(aasId);
		m_registry.removeAssetAdministrationShellDescriptorById(aasId);
    }

    @PutMapping({""})
    @ResponseStatus(HttpStatus.CREATED)
    public String updateAssetAdministrationShellDescriptor(@RequestBody String aasJson)
    	throws SerializationException, DeserializationException {
		AssetAdministrationShellDescriptor aas = s_deser.read(aasJson,
																AssetAdministrationShellDescriptor.class);
		return s_ser.write(m_registry.updateAssetAdministrationShellDescriptorById(aas));
    }
}
