package mdt.instance.docker;

import java.io.File;
import java.util.List;
import java.util.Map;

import org.eclipse.digitaltwin.aas4j.v3.model.AssetAdministrationShell;
import org.mandas.docker.client.DockerClient;
import org.mandas.docker.client.DockerClient.ListContainersFilterParam;
import org.mandas.docker.client.DockerClient.ListContainersParam;
import org.mandas.docker.client.builder.jersey.JerseyDockerClientBuilder;
import org.mandas.docker.client.exceptions.ConflictException;
import org.mandas.docker.client.exceptions.ContainerNotFoundException;
import org.mandas.docker.client.exceptions.DockerException;
import org.mandas.docker.client.messages.Container;
import org.mandas.docker.client.messages.ContainerConfig;
import org.mandas.docker.client.messages.ContainerCreation;
import org.mandas.docker.client.messages.HostConfig;
import org.mandas.docker.client.messages.HostConfig.Bind;
import org.mandas.docker.client.messages.PortBinding;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;

import utils.stream.FStream;

import mdt.instance.AbstractMDTInstanceManager;
import mdt.instance.AbstractMDTInstanceManagerBuilder;
import mdt.instance.InstanceDescriptor;
import mdt.model.instance.DockerExecutionArguments;
import mdt.model.instance.MDTInstance;
import mdt.model.instance.MDTInstanceManagerException;
import mdt.model.registry.ResourceAlreadyExistsException;


/**
 *
 * @author Kang-Woo Lee (ETRI)
 */
public class DockerInstanceManager extends AbstractMDTInstanceManager {
	private static final Logger s_logger = LoggerFactory.getLogger(DockerInstanceManager.class);
	@SuppressWarnings("unused")
	private static final String FA3ST_IMAGE_PATH = "mdt/faaast-service";

	private String m_dockerHost;
	
	public DockerInstanceManager(DockerInstanceManagerBuilder builder) {
		super(builder);
		
		m_dockerHost = builder.m_dockerHost;
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

//	@Override
//	public List<MDTInstance> getInstanceAll() throws MDTInstanceManagerException {
//		try ( DockerClient docker = newDockerClient() ) {
//			List<Container> containers = docker.listContainers(ListContainersParam.withLabel("mdt-aas-id"));
//			return FStream.from(containers)
//							.map(this::toInstance)
//							.cast(MDTInstance.class)
//							.toList();
//		}
//		catch ( DockerException e ) {
//			e.printStackTrace();
//		}
//		catch ( InterruptedException e ) {
//			e.printStackTrace();
//		}
//		return null;
//	}

//	@Override
//	public DockerInstance getInstance(String id) throws MDTInstanceManagerException {
//		try ( DockerClient docker = newDockerClient() ) {
//			Container container = findContainerByInstanceId(docker, id);
//			return toInstance(container);
//		}
//		catch ( ContainerNotFoundException e ) {
//			throw new ResourceNotFoundException("DockerInstance: id=" + id);
//		}
//		catch ( DockerException | InterruptedException e ) {
//			throw new MDTInstanceManagerException("Failed to get MDTInstance: id=" + id);
//		}
//	}

//	@Override
//	public DockerInstance getInstanceByAasId(String aasId) throws ResourceNotFoundException {
//		try ( DockerClient docker = newDockerClient() ) {
//			List<Container> containers = docker.listContainers(ListContainersParam.withLabel("mdt-aas-id", aasId));
//			if ( containers.size() == 0 ) {
//				throw new ContainerNotFoundException("aas-id=" + aasId);
//			}
//			else if ( containers.size() == 1 ) {
//				return toInstance(containers.get(0));
//			}
//			else {
//				throw new MDTInstanceManagerException("Duplicate DockerInstances: aas-id=" + aasId);
//			}
//		}
//		catch ( DockerException | InterruptedException e ) {
//			throw new MDTInstanceManagerException("Failed to get MDTInstance: aas-id=" + aasId);
//		}
//	}

//	@Override
//	public List<MDTInstance> getInstanceAllByIdShort(String aasIdShort) throws MDTInstanceManagerException {
//		try ( DockerClient docker = newDockerClient() ) {
//			List<Container> containers
//					= docker.listContainers(ListContainersParam.withLabel("mdt-aas-id-short", aasIdShort));
//			return FStream.from(containers)
//							.map(this::toInstance)
//							.cast(MDTInstance.class)
//							.toList();
//		}
//		catch ( DockerException | InterruptedException e ) {
//			throw new MDTInstanceManagerException("Failed to get MDTInstance: aas-id-short=" + aasIdShort);
//		}
//	}
	
	@Override
	protected DockerInstanceDescriptor buildDescriptor(String id, AssetAdministrationShell aas,
														Object arguments) {
		if ( arguments instanceof DockerExecutionArguments jargs ) {
			return new DockerInstanceDescriptor(id, aas.getId(), aas.getIdShort(), jargs);
		}
		else {
			throw new IllegalArgumentException("Invalid ExecutionArguments type: type=" + arguments.getClass());
		}
	}

	@Override
	protected DockerInstanceDescriptor readDescriptor(File descFile) throws MDTInstanceManagerException {
		try {
			return m_mapper.readValue(descFile, DockerInstanceDescriptor.class);
		}
		catch ( Exception e ) {
			throw new MDTInstanceManagerException("Failed to read InstanceDescriptor file: " + descFile
													+ ", cause=" + e);
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
		DockerInstanceDescriptor desc = new DockerInstanceDescriptor(id, aasId, aasIdShort, args);
		desc.setContainerId(container.id());
		
		return new DockerInstance(this, desc);
	}
	
	@Override
	protected DockerInstance toInstance(InstanceDescriptor descriptor) {
		if ( descriptor instanceof DockerInstanceDescriptor ddesc ) {
			return new DockerInstance(this, ddesc);
		}
		else {
			throw new MDTInstanceManagerException("Invalid DockerInstanceDescriptor: desc=" + descriptor);
		}
	}
	
	@Override
	protected DockerInstanceDescriptor buildInstance(AbstractMDTInstanceManager manager, File instanceDir,
													InstanceDescriptor desc) {
		Preconditions.checkArgument(desc instanceof DockerInstanceDescriptor);
		
		DockerInstanceDescriptor ddesc = (DockerInstanceDescriptor)desc;
		DockerExecutionArguments args = ddesc.getArguments();
		try ( DockerClient docker = newDockerClient() ) {
			Bind modelBinding = Bind.builder()
									.from(args.getModelFile())
									.to("/model.json")
									.readOnly(true)
									.build();

			Map<String,List<PortBinding>> portBindings = Maps.newHashMap();
			List<PortBinding> randomPort = Lists.newArrayList();
			randomPort.add(PortBinding.randomPort("0.0.0.0"));
			portBindings.put("443/tcp", randomPort);
			
			HostConfig hostConf = HostConfig.builder()
											.portBindings(portBindings)
											.binds(modelBinding)
											.publishAllPorts(true)
											.build();
			Map<String,String> labels = Maps.newHashMap();
			labels.put("mdt-aas-id", desc.getAasId());
			if ( desc.getAasIdShort() != null ) {
				labels.put("mdt-aas-id-short", desc.getAasIdShort());
			}
			ContainerConfig containerConf = ContainerConfig.builder()
															.hostConfig(hostConf)
															.image(args.getImageId())
															.exposedPorts("443")
															.env("faaast.model=/model.json")
															.labels(labels)
															.build();
			ContainerCreation creation = docker.createContainer(containerConf, desc.getId());
			ddesc.setContainerId(creation.id());
			
			return ddesc;
		}
		catch ( ConflictException e ) {
			throw new ResourceAlreadyExistsException("instance: id=" + desc.getId());
		}
		catch ( Exception e ) {
			throw new MDTInstanceManagerException("Failed to create an MDTInstance: id=" + desc.getId()
													+ ", cause=" + e);
		}
	}

	public static DockerInstanceManagerBuilder builder() {
		return new DockerInstanceManagerBuilder();
	}
	public static class DockerInstanceManagerBuilder
		extends AbstractMDTInstanceManagerBuilder<DockerInstanceManagerBuilder, DockerInstanceManager> {
		private String m_dockerHost;
		
		public DockerInstanceManagerBuilder dockerHost(String host) {
			m_dockerHost = host;
			return this;
		}
		
		@Override
		protected DockerInstanceManager internalBuild() {
			return new DockerInstanceManager(this);
		}
	}
}
