package mdt.controller;

import java.io.File;
import java.nio.file.Files;
import java.util.List;

import org.eclipse.digitaltwin.aas4j.v3.dataformat.core.DeserializationException;
import org.eclipse.digitaltwin.aas4j.v3.dataformat.core.SerializationException;
import org.eclipse.digitaltwin.aas4j.v3.model.Submodel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Value;
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
import mdt.model.instance.MDTInstance;
import mdt.repository.FileBasedSubmodelRepository;
import mdt.repository.ServiceIdentifier;
import mdt.repository.SubmodelRepositoryProvider;


/**
*
* @author Kang-Woo Lee (ETRI)
*/
@RestController
@RequestMapping("/api/v3.0/submodels")
public class SubmodelRepositoryController extends MDTController<MDTInstance> implements InitializingBean {
	private final Logger s_logger = LoggerFactory.getLogger(SubmodelRepositoryController.class);

	@Value("file:${repository.workspaceDir}")
	private File m_repositoryWorkspaceDir;
	private SubmodelRepositoryProvider m_repository;
	@Value("${repository.endpoint}")
	private String m_endpoint;

	@Override
	public void afterPropertiesSet() throws Exception {
		File workspaceDir = new File(m_repositoryWorkspaceDir, "submodels");
		if ( s_logger.isInfoEnabled() ) {
			s_logger.info("{} is ready to serve: workspace={}", getClass().getName(), workspaceDir);
		}
		Files.createDirectories(workspaceDir.toPath());
		
		m_repository = new FileBasedSubmodelRepository(workspaceDir);
		m_endpoint = m_endpoint + "/submodels";
	}

    @GetMapping("")
    @ResponseStatus(HttpStatus.OK)
    public List<String> getAllSubmodels() throws Exception {
    	return m_repository.getAllSubmodels().stream()
    						.map(this::toEndpoint)
    						.toList();
    }

    @GetMapping("?semanticId={semanticId}")
    @ResponseStatus(HttpStatus.OK)
    public List<String> getAllSubmodelsBySemanticId(@PathVariable("semanticId") String semanticId)
    	throws Exception {
    	return m_repository.getAllSubmodelBySemanticId(semanticId).stream()
							.map(this::toEndpoint)
							.toList();
    }

    @GetMapping("?idShort={idShort}")
    @ResponseStatus(HttpStatus.OK)
    public List<String> getAllSubmodelsByIdShort(@PathVariable("idShort") String idShort)
    	throws Exception {
    	return m_repository.getAllSubmodelsByIdShort( idShort).stream()
							.map(this::toEndpoint)
							.toList();
    }

    @GetMapping("/{aasId}")
    @ResponseStatus(HttpStatus.OK)
    public String getSubmodelById(@PathVariable("aasId") String idShort) throws Exception {
    	return toEndpoint(m_repository.getSubmodelById( idShort));
    }

    @PostMapping({""})
    @ResponseStatus(HttpStatus.CREATED)
    public String addSubmodel(@RequestBody String submodelJson) throws Exception {
    	Submodel submodel = s_deser.read(submodelJson, Submodel.class);
    	return toEndpoint(m_repository.addSubmodel(submodel));
    }

    @PutMapping({""})
    @ResponseStatus(HttpStatus.CREATED)
    public String updateSubmodelById(@RequestBody String submodelJson)
    	throws SerializationException, DeserializationException {
    	Submodel submodel = s_deser.read(submodelJson, Submodel.class);
    	return toEndpoint(m_repository.updateSubmodelById(submodel));
    }
    
    @DeleteMapping("/{submodelId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void removeSubmodelById(@PathVariable("submodelId") String id) throws SerializationException {
		m_repository.removeSubmodelById(id);
    }
	
	private String toEndpoint(ServiceIdentifier svcId) {
		return String.format("%s/%s", m_endpoint, svcId.getId());
	}
}
