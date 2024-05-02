package mdt.controller;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.List;
import java.util.stream.Collectors;

import org.eclipse.digitaltwin.aas4j.v3.dataformat.core.SerializationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.util.FileSystemUtils;
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
import org.springframework.web.multipart.MultipartFile;

import com.google.common.base.Preconditions;

import utils.func.Try;
import utils.io.IOUtils;
import utils.stream.FStream;

import mdt.MDTController;
import mdt.instance.AbstractInstanceManager;
import mdt.instance.MDTInstanceProvider;
import mdt.instance.docker.DockerInstanceManager;
import mdt.instance.jar.JarInstanceManager;
import mdt.instance.k8s.KubernetesInstanceManager;
import mdt.model.instance.DockerExecutionArguments;
import mdt.model.instance.JarExecutionArguments;
import mdt.model.instance.KubernetesExecutionArguments;
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
	
	@Autowired AbstractInstanceManager m_instance_manager;	
	@Value("file:${instance-manager.workspaceDir}")
	private File m_workspaceDir;

	@Override
	public void afterPropertiesSet() throws Exception {
		if ( s_logger.isInfoEnabled() ) {
			s_logger.info("{} is ready to serve: workspace={}", getClass().getName(), m_workspaceDir);
		}
		
		Files.createDirectories(m_workspaceDir.toPath());
	}

    @GetMapping("/id/{id}")
    @ResponseStatus(HttpStatus.OK)
    public MDTInstancePayload getMDTInstance(@PathVariable("id") String id) throws Exception {
    	return toPayloadAndClose(m_instance_manager.getInstance(id));
    }

    @GetMapping("/aasId/{aasId}")
    @ResponseStatus(HttpStatus.OK)
    public MDTInstancePayload getMDTInstanceByAasId(@PathVariable("aasId") String aasId) throws Exception {
    	String decoded = decodeBase64(aasId);
		return toPayloadAndClose(m_instance_manager.getInstanceByAasId(decoded));
    }

    @GetMapping({"/aasIdShort/{aasIdShort}"})
    @ResponseStatus(HttpStatus.OK)
    public List<MDTInstancePayload> getAllMDTInstancesByAasIdShort(@PathVariable("aasIdShort") String aasIdShort)
    	throws MDTInstanceManagerException, SerializationException {
    	return FStream.from(m_instance_manager.getInstanceAllByIdShort(aasIdShort))
    					.map(this::toPayloadAndClose)
    					.toList();
    }
    
    @GetMapping({"/all"})
    @ResponseStatus(HttpStatus.OK)
    public List<MDTInstancePayload> getAllMDTInstaces() throws MDTInstanceManagerException, SerializationException {
		return m_instance_manager.getInstanceAll().stream()
								.map(this::toPayloadAndClose)
								.collect(Collectors.toList());
    }
    
    @PutMapping({"/status"})
    @ResponseStatus(HttpStatus.OK)
    public List<MDTInstancePayload> getAllMDTInstacesOfStatus(@RequestBody MDTInstanceStatus status)
    	throws MDTInstanceManagerException, SerializationException {
		return m_instance_manager.getAllInstancesOfStatus(status).stream()
								.map(this::toPayloadAndClose)
								.collect(Collectors.toList());
    }

    @PostMapping({""})
    @ResponseStatus(HttpStatus.CREATED)
    public MDTInstancePayload addInstance(@RequestParam("id") String id,
										@RequestParam(name="jar", required=false) MultipartFile mpfJar,
		    							@RequestParam(name="imageId", required=false) String imageId,
		    							@RequestParam(name="initialModel", required=false) MultipartFile mpfModel,
		    							@RequestParam(name="instanceConf", required=false) MultipartFile mpfConf) {
    	if ( m_instance_manager instanceof JarInstanceManager ) {
        	Preconditions.checkNotNull(mpfJar, "Jar file was null");
        	return addJarInstance(id, mpfJar, mpfModel, mpfConf);
    	}
    	else if ( m_instance_manager instanceof DockerInstanceManager ) {
        	Preconditions.checkNotNull(imageId, "ImageId was null");
        	return addDockerInstance(id, imageId, mpfModel, mpfConf);
    	}
    	else if ( m_instance_manager instanceof KubernetesInstanceManager ) {
        	Preconditions.checkNotNull(imageId, "ImageId was null");
        	return addKubernetesInstance(id, imageId, mpfModel);
    	}
    	else {
			throw new MDTInstanceManagerException("Unknown InstanceManager type: " + m_instance_manager);
    	}
    }

    private MDTInstancePayload addJarInstance(String id, MultipartFile mpfJar, MultipartFile mpfModel,
    											MultipartFile mpfConf) {
    	JarInstanceManager instMgr = (JarInstanceManager)m_instance_manager;
    	
    	File instDir = instMgr.getInstanceWorkspaceDir(id);
    	try {
			Files.createDirectories(instDir.toPath());

			File jarFile = uploadFile(instDir, "fa3st-repository.jar", mpfJar);
			File modelFile = uploadFile(instDir, "model.json", mpfModel);
			File confFile = uploadFile(instDir, "conf.json", mpfConf);
			
			JarExecutionArguments args = JarExecutionArguments.builder()
															.jarFile(jarFile.getAbsolutePath())
															.modelFile(modelFile.getAbsolutePath())
															.configFile(confFile.getAbsolutePath())
															.build();
			String argsJson = instMgr.toExecutionArgumentsString(args);
	    	MDTInstance instance = instMgr.addInstance(id, modelFile, argsJson);
	    	return toPayloadAndClose(instance);
		}
		catch ( IOException e ) {
			throw new MDTInstanceManagerException("" + e);
		}
    }

    private MDTInstancePayload addDockerInstance(String id, String imageId, MultipartFile mpfModel,
    											MultipartFile mpfConf) {
    	DockerInstanceManager instMgr = (DockerInstanceManager)m_instance_manager;
    	
    	File instDir = instMgr.getInstanceWorkspaceDir(id);
    	try {
			Files.createDirectories(instDir.toPath());

			File modelFile = uploadFile(instDir, "model.json", mpfModel);
			File confFile = uploadFile(instDir, "conf.json", mpfConf);
			
			DockerExecutionArguments args = DockerExecutionArguments.builder()
																.imageId(imageId)
																.modelFile(modelFile.getAbsolutePath())
																.configFile(confFile.getAbsolutePath())
																.build();
			String argsJson = instMgr.toExecutionArgumentsString(args);
	    	MDTInstance instance = instMgr.addInstance(id, modelFile, argsJson);
	    	return toPayloadAndClose(instance);
		}
		catch ( IOException e ) {
			throw new MDTInstanceManagerException("" + e);
		}
    }

    private MDTInstancePayload addKubernetesInstance(String id, String imageId,  MultipartFile mpfModel) {
    	KubernetesInstanceManager instMgr = (KubernetesInstanceManager)m_instance_manager;

    	File instDir = instMgr.getInstanceWorkspaceDir(id);
    	try {
			Files.createDirectories(instDir.toPath());
			
			File modelFile = uploadFile(instDir, "model.json", mpfModel);

			KubernetesExecutionArguments args = KubernetesExecutionArguments.builder()
																			.imageId(imageId)
																			.build();
			String argsJson = instMgr.toExecutionArgumentsString(args);
			MDTInstanceProvider instance = instMgr.addInstance(id, modelFile, argsJson);
	    	return toPayloadAndClose(instance);
		}
		catch ( IOException e ) {
			throw new MDTInstanceManagerException("" + e);
		}
    }
    
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void removeMDTInstance(@PathVariable("id") String id) throws SerializationException {
		try {
			m_instance_manager.removeInstance(id);
		}
		finally {
	    	File topDir = new File(m_workspaceDir, id);
	    	FileSystemUtils.deleteRecursively(topDir);
		}
    }

    @DeleteMapping("")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void removeAllMDTInstance() throws SerializationException {
    	Try.run(m_instance_manager::removeInstanceAll);
    }
    
    private File uploadFile(File topDir, String fileName, MultipartFile mpf) throws IOException {
		File file = new File(topDir, fileName);
		IOUtils.toFile(mpf.getInputStream(), file);
		return file;
    }
    
    private MDTInstancePayload toPayloadAndClose(MDTInstance instance) {
    	MDTInstancePayload payload = toPayload(instance);
    	IOUtils.closeQuietly(instance);
    	return payload;
    }
    
    private MDTInstancePayload toPayload(MDTInstance instance) {
    	return new MDTInstancePayload(instance.getId(), instance.getAASId(), instance.getAASIdShort());
    }
}
