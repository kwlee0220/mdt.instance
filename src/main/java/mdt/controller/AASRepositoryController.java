package mdt.controller;

import java.io.File;
import java.nio.file.Files;
import java.util.List;

import org.eclipse.digitaltwin.aas4j.v3.dataformat.core.DeserializationException;
import org.eclipse.digitaltwin.aas4j.v3.dataformat.core.SerializationException;
import org.eclipse.digitaltwin.aas4j.v3.model.AssetAdministrationShell;
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

import utils.stream.FStream;

import mdt.MDTController;
import mdt.model.instance.MDTInstance;
import mdt.repository.AssetAdministrationShellRepositoryProvider;
import mdt.repository.FileBasedAASRepository;
import mdt.repository.ServiceIdentifier;


/**
*
* @author Kang-Woo Lee (ETRI)
*/
@RestController
@RequestMapping("/api/v3.0/shells")
public class AASRepositoryController extends MDTController<MDTInstance> implements InitializingBean {
	private final Logger s_logger = LoggerFactory.getLogger(AASRepositoryController.class);
	
	@Value("file:${repository.workspaceDir}")
	private File m_repositoryWorkspaceDir;
	private AssetAdministrationShellRepositoryProvider m_repo;
	@Value("${repository.endpoint}")
	private String m_endpoint;

	@Override
	public void afterPropertiesSet() throws Exception {
		File workspaceDir = new File(m_repositoryWorkspaceDir, "shells");
		if ( s_logger.isInfoEnabled() ) {
			s_logger.info("{} is ready to serve: workspace={}", getClass().getName(), workspaceDir);
		}
		Files.createDirectories(workspaceDir.toPath());
		
		m_repo = new FileBasedAASRepository(workspaceDir);
		m_endpoint = m_endpoint + "/shells";
	}

    @GetMapping("")
    @ResponseStatus(HttpStatus.OK)
    public List<String> getAllAssetAdministrationShells(@PathVariable("id") String id) throws Exception {
    	return FStream.from(m_repo.getAllAssetAdministrationShells())
		    			.map(this::toEndpoint)
		    			.toList();
    }

    @GetMapping("/{aasId}")
    @ResponseStatus(HttpStatus.OK)
    public String getAssetAdministrationShellById(@PathVariable("aasId") String aasId) throws Exception {
    	String decoded = decodeBase64(aasId);
    	return toEndpoint(m_repo.getAssetAdministrationShellById(decoded));
    }

    @GetMapping("?assetids={assetId}")
    @ResponseStatus(HttpStatus.OK)
    public List<String> getAllAssetAdministrationShellsByAssetId(@PathVariable("assetId") String assetId)
    	throws Exception {
    	return FStream.from(m_repo.getAllAssetAdministrationShellsByAssetId(assetId))
		    			.map(this::toEndpoint)
		    			.toList();
    }

    @GetMapping("?idShort={assetId}")
    @ResponseStatus(HttpStatus.OK)
    public List<String> getAllAssetAdministrationShellsByIdShort(@PathVariable("idShort") String idShort) throws Exception {
    	return FStream.from(m_repo.getAssetAdministrationShellByIdShort(idShort))
    					.map(this::toEndpoint)
		    			.toList();
    }

    @PostMapping({""})
    @ResponseStatus(HttpStatus.CREATED)
    public String addAssetAdministrationShell(@RequestBody String aasJson) throws DeserializationException {
    	AssetAdministrationShell aas = s_deser.read(aasJson, AssetAdministrationShell.class);
    	return toEndpoint(m_repo.addAssetAdministrationShell(aas));
    }

    @PutMapping({"/{aasId}"})
    @ResponseStatus(HttpStatus.CREATED)
    public String updateAssetAdministrationShellById(@RequestBody String aasJson)
    	throws SerializationException, DeserializationException {
    	AssetAdministrationShell aas = s_deser.read(aasJson, AssetAdministrationShell.class);
    	return toEndpoint(m_repo.updateAssetAdministrationShellById(aas));
    }
    
    @DeleteMapping("/{aasId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void removeAssetAdministrationShellById(@PathVariable("aasId") String id)
		throws SerializationException {
		m_repo.removeAssetAdministrationShellById(id);
    }
	
	private String toEndpoint(ServiceIdentifier svcId) {
		return String.format("%s/%s", m_endpoint, svcId.getId());
	}
}
