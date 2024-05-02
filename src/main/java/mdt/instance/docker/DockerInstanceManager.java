package mdt.instance.docker;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import org.mandas.docker.client.DockerClient;
import org.mandas.docker.client.DockerClient.ListContainersFilterParam;
import org.mandas.docker.client.DockerClient.ListContainersParam;
import org.mandas.docker.client.DockerClient.RemoveContainerParam;
import org.mandas.docker.client.builder.jersey.JerseyDockerClientBuilder;
import org.mandas.docker.client.exceptions.ConflictException;
import org.mandas.docker.client.exceptions.ContainerNotFoundException;
import org.mandas.docker.client.exceptions.DockerException;
import org.mandas.docker.client.messages.Container;
import org.mandas.docker.client.messages.ContainerConfig;
import org.mandas.docker.client.messages.HostConfig;
import org.mandas.docker.client.messages.HostConfig.Bind;
import org.mandas.docker.client.messages.PortBinding;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.google.common.collect.Maps;
import com.google.common.io.Files;

import utils.func.Try;

import mdt.instance.AbstractInstanceManager;
import mdt.instance.AbstractMDTInstanceManagerBuilder;
import mdt.instance.InstanceDescriptor;
import mdt.model.InternalException;
import mdt.model.instance.DockerExecutionArguments;
import mdt.model.instance.MDTInstanceManagerException;
import mdt.model.registry.ResourceAlreadyExistsException;


/**
 *
 * @author Kang-Woo Lee (ETRI)
 */
public class DockerInstanceManager extends AbstractInstanceManager {
	private static final Logger s_logger = LoggerFactory.getLogger(DockerInstanceManager.class);

	private String m_dockerHost;
	private final JsonMapper m_mapper;
	private final String m_mountPrefix;
	
	public DockerInstanceManager(DockerInstanceManagerBuilder builder) {
		super(builder);
		
		m_dockerHost = builder.m_dockerHost;
		m_mountPrefix = (builder.m_mountPrefix != null) ? builder.m_mountPrefix
														: getWorkspaceDir().getAbsolutePath();
		
		m_mapper = JsonMapper.builder().build();
		
		setLogger(s_logger);
	}
	
	/**
	 * RESTful 인터페이스 기반 Docker 접속을 위한 client를 생성한다.
	 * 
	 * @return	{@link DockerClient} 객체.
	 */
	DockerClient newDockerClient() {
		return new JerseyDockerClientBuilder().uri(m_dockerHost).build();
	}
	
	public DockerExecutionArguments parseExecutionArguments(String argsJson) {
		try {
			return m_mapper.readValue(argsJson, DockerExecutionArguments.class);
		}
		catch ( JsonProcessingException e ) {
			throw new InternalException("Failed to parse DockerExecutionArguments string, cause=" + e);
		}
	}
	
	public String toExecutionArgumentsString(DockerExecutionArguments args) {
		try {
			return m_mapper.writeValueAsString(args);
		}
		catch ( JsonProcessingException e ) {
			throw new InternalException("Failed to write DockerExecutionArguments string, cause=" + e);
		}
	}

	@Override
	protected DockerInstance toInstance(InstanceDescriptor descriptor) throws MDTInstanceManagerException {
		try ( DockerClient docker = newDockerClient() ) {
			Container container = findContainerByInstanceId(docker, descriptor.getId());
			return new DockerInstance(this, descriptor, container);
		}
		catch ( ContainerNotFoundException e ) {
			throw new MDTInstanceManagerException("Cannot find Docker container: id=" + descriptor.getId());
		}
		catch ( Exception e ) {
			throw new MDTInstanceManagerException("Failed to get DockerInstance: id=" + descriptor.getId());
		}
	}
	
	private String getHostMountPath(String instanceId, String path) {
		return String.format("%s/%s/%s", m_mountPrefix, instanceId, path);
	}

	@Override
	protected InstanceDescriptor initializeInstance(InstanceDescriptor desc) {
		File instanceWorkspaceDir = getInstanceWorkspaceDir(desc.getId());
		
		try ( DockerClient docker = newDockerClient() ) {
			DockerExecutionArguments args = parseExecutionArguments(desc.getArguments());
			
			// model 파일 복사 후 root 디렉토리로 bind mount 생성
			File modelFile = new File(instanceWorkspaceDir, "model.json");
			copyFileIfNotSame(new File(args.getModelFile()), modelFile);
			args.setModelFile("model.json");
			Bind modelBinding = Bind.builder()
									.from(getHostMountPath(desc.getId(), "model.json"))
									.to("/model.json")
									.readOnly(true).build();

			// configuration 파일 복사 후 root 디렉토리로 bind mount 생성
			File confFile = new File(instanceWorkspaceDir, "conf.json");
			copyFileIfNotSame(new File(args.getConfigFile()), confFile);
			args.setConfigFile("conf.json");
			Bind confBinding = Bind.builder()
									.from(getHostMountPath(desc.getId(), "conf.json"))
									.to("/conf.json")
									.readOnly(true).build();

			// 443 port binding
			Map<String,List<PortBinding>> portBindings = Maps.newHashMap();
			portBindings.put("443/tcp", Arrays.asList(PortBinding.randomPort("0.0.0.0")));
			
			HostConfig hostConf = HostConfig.builder()
											.portBindings(portBindings)
											.binds(modelBinding)
											.binds(confBinding)
											.build();
			Map<String,String> labels = Maps.newHashMap();
			labels.put("mdt-id", desc.getId());
			labels.put("mdt-aas-id", desc.getAasId());
			if ( desc.getAasIdShort() != null ) {
				labels.put("mdt-aas-id-short", desc.getAasIdShort());
			}
			ContainerConfig containerConf = ContainerConfig.builder()
															.hostConfig(hostConf)
															.image(args.getImageId())
															.labels(labels)
															.build();
			docker.createContainer(containerConf, desc.getId());
			
			return desc;
		}
		catch ( ConflictException e ) {
			throw new ResourceAlreadyExistsException("DockerContainer", desc.getId());
		}
		catch ( Exception e ) {
			throw new MDTInstanceManagerException("Failed to create an MDTInstance: id=" + desc.getId()
													+ ", cause=" + e);
		}
	}

	@Override
	public void removeInstanceAll() throws MDTInstanceManagerException {
		m_rwLock.writeLock().lock();
		try ( DockerClient docker = newDockerClient() ) {
			Try.run(() -> super.removeInstanceAll());
			
			// Remove all dangling MDTInstance docker containers
			for ( Container container: docker.listContainers(ListContainersParam.allContainers()) ) {
				if ( container.labels().containsKey("mdt-id") ) {
					Try.run(() -> docker.removeContainer(container.id(), RemoveContainerParam.forceKill()));
				}
			}
		}
		catch ( Exception ignored ) { }
		finally {
			m_rwLock.writeLock().unlock();
		}
	}

	private Container findContainerByInstanceId(DockerClient docker, String instanceId)
		throws DockerException, InterruptedException, ContainerNotFoundException {
		List<Container> containers = docker.listContainers(ListContainersParam.allContainers(),
												ListContainersFilterParam.filter("name", instanceId));
		if ( containers.size() == 0 ) {
			throw new ContainerNotFoundException(instanceId);
		}
		else if ( containers.size() == 1 ) {
			return containers.get(0);
		}
		else {
			throw new MDTInstanceManagerException("Duplicate DockerInstances: id=" + instanceId);
		}
	}

	@SuppressWarnings("unused")
	private DockerInstance toInstance(Container container) throws MDTInstanceManagerException {
		String id = container.names().get(0).substring(1);
		
		Map<String,String> labels = container.labels();
		String aasId = labels.get("mdt-aas-id");
		String aasIdShort = labels.get("mdt-aas-id-short");
		
		String modelFilePath = new File(new File(getWorkspaceDir(), id), "model.json").getAbsolutePath();
		DockerExecutionArguments args = DockerExecutionArguments.builder()
													.imageId(container.image())
													.modelFile(modelFilePath)
													.build();
		try {
			String argsJson = m_mapper.writeValueAsString(args);
			InstanceDescriptor desc = new InstanceDescriptor(id, aasId, aasIdShort, null, argsJson);
			
			return new DockerInstance(this, desc, container);
		}
		catch ( JsonProcessingException e ) {
			throw new MDTInstanceManagerException("" + e);
		}
	}
	
	private void copyFileIfNotSame(File src, File dest) throws IOException {
		if ( !src.getAbsolutePath().equals(dest.getAbsolutePath()) ) {
			Files.copy(src, dest);
		}
	}

	public static DockerInstanceManagerBuilder builder() {
		return new DockerInstanceManagerBuilder();
	}
	public static class DockerInstanceManagerBuilder
		extends AbstractMDTInstanceManagerBuilder<DockerInstanceManagerBuilder, DockerInstanceManager> {
		private String m_dockerHost;
		private String m_mountPrefix = null;
		
		public DockerInstanceManagerBuilder dockerHost(String host) {
			m_dockerHost = host;
			return this;
		}
		
		public DockerInstanceManagerBuilder mountPrefix(String prefix) {
			m_mountPrefix = prefix;
			return this;
		}
		
		@Override
		protected DockerInstanceManager internalBuild() {
			return new DockerInstanceManager(this);
		}
	}
}
