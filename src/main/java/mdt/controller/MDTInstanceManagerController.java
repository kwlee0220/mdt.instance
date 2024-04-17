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

import utils.func.Unchecked;
import utils.io.IOUtils;

import mdt.MDTController;
import mdt.instance.FileBasedInstanceManager;
import mdt.instance.InstanceDescriptor;
import mdt.instance.docker.DockerInstanceManager;
import mdt.instance.jar.JarInstanceManager;
import mdt.model.instance.DockerExecutionArguments;
import mdt.model.instance.JarExecutionArguments;
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
	
	@Autowired FileBasedInstanceManager<? extends InstanceDescriptor> m_instance_manager;	
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
    public MDTInstancePayload getMDTInstance(@PathVariable("id") String id) throws SerializationException {
    	return toPayload(m_instance_manager.getInstance(id));
    }

    @GetMapping("/aasId/{aasId}")
    @ResponseStatus(HttpStatus.OK)
    public MDTInstancePayload getMDTInstanceByAasId(@PathVariable("aasId") String aasId)
    	throws SerializationException {
    	String decoded = decodeBase64(aasId);
		return toPayload(m_instance_manager.getInstanceByAasId(decoded));
    }

    @GetMapping({"/aasIdShort/{aasIdShort}"})
    @ResponseStatus(HttpStatus.OK)
    public List<MDTInstancePayload> getAllMDTInstancesByAasIdShort(@PathVariable("aasIdShort") String aasIdShort)
    	throws MDTInstanceManagerException, SerializationException {
		return m_instance_manager.getInstanceAllByIdShort(aasIdShort).stream()
							.map(this::toPayload)
							.collect(Collectors.toList());
    }
    
    @GetMapping({"/all"})
    @ResponseStatus(HttpStatus.OK)
    public List<MDTInstancePayload> getAllMDTInstaces()
    	throws MDTInstanceManagerException, SerializationException {
    	List<MDTInstance> instances = m_instance_manager.getInstanceAll();
		return instances.stream()
						.map(this::toPayload)
						.collect(Collectors.toList());
    }
    
    @PutMapping({"/status"})
    @ResponseStatus(HttpStatus.OK)
    public List<MDTInstancePayload> getAllMDTInstacesOfStatus(@RequestBody MDTInstanceStatus status)
    	throws MDTInstanceManagerException, SerializationException {
    	List<MDTInstance> instances = m_instance_manager.getAllInstancesOfStatus(status);
		return instances.stream()
						.map(this::toPayload)
						.collect(Collectors.toList());
    }

    @PostMapping({"/jar"})
    @ResponseStatus(HttpStatus.CREATED)
    public MDTInstancePayload addJarInstance(@RequestParam("id") String id,
				    							@RequestParam("jar") MultipartFile mpfJar,
				    							@RequestParam("model") MultipartFile mpfModel,
				    							@RequestParam("conf") MultipartFile mpfConf) {
    	Preconditions.checkState(m_instance_manager instanceof JarInstanceManager,
									"Incompatible MDTInstanceManager: Not JarInstanceManager");
    	
    	File topDir = new File(m_workspaceDir, id);
    	try {
			Files.createDirectories(topDir.toPath());

			File jarFile = uploadFile(topDir, "fa3st-repository.jar", mpfJar);
			File modelFile = uploadFile(topDir, "model.json", mpfModel);
			File confFile = uploadFile(topDir, "conf.json", mpfConf);
			
			JarExecutionArguments args = JarExecutionArguments.builder()
																.jarFile(jarFile.getAbsolutePath())
																.modelFile(modelFile.getAbsolutePath())
																.configFile(confFile.getAbsolutePath())
																.build();
	    	MDTInstance instance = m_instance_manager.addInstance(id, modelFile, args);
	    	return toPayload(instance);
		}
		catch ( IOException e ) {
			throw new MDTInstanceManagerException("" + e);
		}
    }

    @PostMapping({"/docker"})
    @ResponseStatus(HttpStatus.CREATED)
    public MDTInstancePayload addDockerInstance(@RequestParam("id") String id,
		    							@RequestParam("imageId") String imageId,
		    							@RequestParam("model") MultipartFile mpfModel,
		    							@RequestParam(name="conf", required=false) MultipartFile mpfConf) {
    	Preconditions.checkState(m_instance_manager instanceof DockerInstanceManager,
    							"Incompatible MDTInstanceManager: Not DockerInstanceManager");
    	
    	File topDir = new File(m_workspaceDir, id);
    	try {
			Files.createDirectories(topDir.toPath());

			File modelFile = uploadFile(topDir, "model.json", mpfModel);
//			File confFile = (mpfConf != null) ? uploadFile(topDir, "conf.json", mpfConf) : null;
			
			DockerExecutionArguments args = DockerExecutionArguments.builder()
																.imageId(imageId)
																.modelFile(modelFile.getAbsolutePath())
																.build();
	    	MDTInstance instance = m_instance_manager.addInstance(id, modelFile, args);
	    	return toPayload(instance);
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
    	List<String> instanceIdList = null;
		try {
			instanceIdList = m_instance_manager.getInstanceAll().stream()
										.map(inst -> inst.getId())
										.collect(Collectors.toList());
			m_instance_manager.removeInstanceAll();
		}
		finally {
			for ( String id: instanceIdList ) {
		    	File topDir = new File(m_workspaceDir, id);
				Unchecked.runOrIgnore(() -> FileSystemUtils.deleteRecursively(topDir));
			}
		}
    }
    
    private File uploadFile(File topDir, String fileName, MultipartFile mpf) throws IOException {
		File file = new File(topDir, fileName);
		IOUtils.toFile(mpf.getInputStream(), file);
		return file;
    }
    
    private MDTInstancePayload toPayload(MDTInstance instance) {
    	return new MDTInstancePayload(instance.getId(), instance.getAASId(), instance.getAASIdShort());
    }
}
