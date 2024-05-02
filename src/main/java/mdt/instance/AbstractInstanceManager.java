package mdt.instance;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import org.apache.commons.io.FileUtils;
import org.eclipse.digitaltwin.aas4j.v3.dataformat.core.DeserializationException;
import org.eclipse.digitaltwin.aas4j.v3.dataformat.json.JsonDeserializer;
import org.eclipse.digitaltwin.aas4j.v3.model.AssetAdministrationShell;
import org.eclipse.digitaltwin.aas4j.v3.model.AssetAdministrationShellDescriptor;
import org.eclipse.digitaltwin.aas4j.v3.model.Endpoint;
import org.eclipse.digitaltwin.aas4j.v3.model.Environment;
import org.eclipse.digitaltwin.aas4j.v3.model.Reference;
import org.eclipse.digitaltwin.aas4j.v3.model.Submodel;
import org.eclipse.digitaltwin.aas4j.v3.model.SubmodelDescriptor;
import org.eclipse.digitaltwin.aas4j.v3.model.impl.DefaultSubmodelDescriptor;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttClientPersistence;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.FileSystemUtils;

import com.fasterxml.jackson.databind.json.JsonMapper;
import com.google.common.base.Preconditions;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.google.common.eventbus.Subscribe;

import utils.LoggerSettable;
import utils.func.Try;
import utils.stream.FStream;

import mdt.Globals;
import mdt.client.Utils;
import mdt.client.registry.RegistryModelConverter;
import mdt.model.ServiceFactory;
import mdt.model.instance.MDTInstance;
import mdt.model.instance.MDTInstanceManagerException;
import mdt.model.registry.AssetAdministrationShellRegistry;
import mdt.model.registry.ResourceAlreadyExistsException;
import mdt.model.registry.ResourceNotFoundException;
import mdt.model.registry.SubmodelRegistry;

/**
 *
 * @author Kang-Woo Lee (ETRI)
 */
public abstract class AbstractInstanceManager implements MDTInstanceManagerProvider, LoggerSettable {
	private static final Logger s_logger = LoggerFactory.getLogger(AbstractInstanceManager.class);
	
	private final ServiceFactory m_serviceFact;
	private final AssetAdministrationShellRegistry m_aasRegistry;
	private final SubmodelRegistry m_submodelRegistry;
	private final String m_repositoryEndpointFormat;
	private final File m_workspaceDir;
	private final InstanceDescriptorManager m_descriptorManager;
	private Logger m_logger;
	protected final ReadWriteLock m_rwLock = new ReentrantReadWriteLock();
	private final MqttClient m_mqttClient;
	
	abstract protected InstanceDescriptor initializeInstance(InstanceDescriptor desc);
	abstract protected AbstractInstance toInstance(InstanceDescriptor descriptor)
		throws MDTInstanceManagerException;
	
	protected AbstractInstanceManager(MDTInstanceManagerBuilder<?,?> builder) throws MDTInstanceManagerException {
		m_serviceFact = builder.serviceFactory();
		m_aasRegistry = builder.aasRegistry();
		m_submodelRegistry = builder.submodeRegistry();
		m_workspaceDir = builder.workspaceDir();
		m_descriptorManager = builder.instanceDescriptorManager();
		
		String epFormat = builder.repositoryEndpointFormat();
		if ( epFormat == null ) {
			try {
				String host = InetAddress.getLocalHost().getHostAddress();
				epFormat = "https:" + host + ":%d/api/v3.0";
			}
			catch ( UnknownHostException e ) {
				throw new MDTInstanceManagerException("" + e);
			}
		}
		m_repositoryEndpointFormat = epFormat;
		
		try {
			MqttClientPersistence persist = new MemoryPersistence();
			m_mqttClient = new MqttClient("tcp://localhost:1883", "MDTInstanceManager", persist);
			m_mqttClient.connect();
			
			Globals.EVENT_BUS.register(this);
		}
		catch ( MqttException e ) {
			throw new MDTInstanceManagerException("Failed to initialize MQTT client, cause=" + e);
		}
		
		setLogger(s_logger);
	}

	public ServiceFactory getServiceFactory() {
		return m_serviceFact;
	}
	
	@Override
	public AssetAdministrationShellRegistry getAssetAdministrationShellRegistry() {
		return m_aasRegistry;
	}
	
	@Override
	public SubmodelRegistry getSubmodelRegistry() {
		return m_submodelRegistry;
	}
	
	public File getWorkspaceDir() {
		return m_workspaceDir;
	}
	
	public File getInstanceWorkspaceDir(String id) {
		return new File(m_workspaceDir, id);
	}

	@Override
	public AbstractInstance getInstance(String id) throws ResourceNotFoundException {
		Preconditions.checkNotNull(id);
		
		m_rwLock.readLock().lock();
		try {
			InstanceDescriptor descriptor = m_descriptorManager.getInstanceDescriptor(id);
			return toInstance(descriptor);
		}
		finally {
			m_rwLock.readLock().unlock();
		}
	}

	@Override
	public AbstractInstance getInstanceByAasId(String aasId) throws ResourceNotFoundException {
		Preconditions.checkNotNull(aasId);

		m_rwLock.readLock().lock();
		try {
			InstanceDescriptor desc = m_descriptorManager.getInstanceDescriptorByAasId(aasId);
			return toInstance(desc);
		}
		finally {
			m_rwLock.readLock().unlock();
		}
	}

	@Override
	public List<MDTInstance> getInstanceAll() throws MDTInstanceManagerException {
		m_rwLock.readLock().lock();
		try {
			return FStream.from(m_descriptorManager.getInstanceDescriptorAll())
							.mapOrIgnore(this::toInstance)
							.cast(MDTInstance.class)
							.toList();
		}
		finally {
			m_rwLock.readLock().unlock();
		}
	}

	@Override
	public List<MDTInstance> getInstanceAllByIdShort(String aasIdShort) throws MDTInstanceManagerException {
		Preconditions.checkNotNull(aasIdShort);

		m_rwLock.readLock().lock();
		try {
			return FStream.from(m_descriptorManager.getInstanceDescriptorAllByAasIdShort(aasIdShort))
							.mapOrIgnore(this::toInstance)
							.cast(MDTInstance.class)
							.toList();
		}
		finally {
			m_rwLock.readLock().unlock();
		}
	}

	@Override
	public AbstractInstance addInstance(String id, Environment env, String arguments)
		throws MDTInstanceManagerException {
		m_rwLock.writeLock().lock();
		try {
			// AAS Environment 정의 파일을 읽어서 AAS Registry에 등록한다.
			registerEnvironment(env);
	
			AssetAdministrationShell aas = env.getAssetAdministrationShells().get(0);
			try {
				// AAS 정보와 이미지 식별자를 instance descriptor에 저장한다.
				InstanceDescriptor desc = new InstanceDescriptor(id, aas.getId(), aas.getIdShort(), null, arguments);
				desc = initializeInstance(desc);
				
				List<InstanceSubmodelDescriptor> smDescList
							= FStream.from(env.getSubmodels())
									.map(sm -> new InstanceSubmodelDescriptor(id, sm.getId(), sm.getIdShort()))
									.toList();
				desc.setSubmodels(smDescList);
				m_descriptorManager.addInstanceDescriptor(desc);
				AbstractInstance instance = toInstance(desc);
				
				Globals.EVENT_BUS.post(InstanceStatusChangeEvent.ADDED(id));
				
				return instance;
			}
			catch ( MDTInstanceManagerException e ) {
				Try.run(() -> unregisterEnvironment(aas.getId()));
				throw e;
			}
		}
		finally {
			m_rwLock.writeLock().unlock();
		}
	}

	@Override
	public AbstractInstance addInstance(String id, File aasFile, String arguments)
		throws MDTInstanceManagerException {
		m_rwLock.writeLock().lock();
		try {
			Environment env = null;
			try {
				// AAS Environment 정의 파일을 읽어서 AAS Registry에 등록한다.
				env = readEnvironment(aasFile);
			}
			catch ( MDTInstanceManagerException e ) {
				throw e;
			}
			catch ( Exception e ) {
				throw new MDTInstanceManagerException("" + e);
			}
			
			return addInstance(id, env, arguments);
		}
		finally {
			m_rwLock.writeLock().unlock();
		}
	}

	@Override
	public void removeInstance(String id) throws MDTInstanceManagerException {
		m_rwLock.writeLock().lock();
		try ( AbstractInstance instance = getInstance(id) ) {
			switch ( instance.getStatus() ) {
				case STARTING:
				case RUNNING:
					throw new IllegalStateException("Cannot remove the running instance: id=" + instance.getId());
				default: break;
			}
			
			Try.run(() -> unregisterEnvironment(instance.getAASId()));
			Try.run(() -> m_descriptorManager.removeInstanceDescriptor(id));
			Try.run(instance::remove);

	    	publishStatusChangeEvent(InstanceStatusChangeEvent.REMOVED(id));
			if ( getLogger().isInfoEnabled() ) {
				getLogger().info("removed: " + instance);
			}
		}
		catch ( IOException e ) { }
		finally {
	    	File topDir = new File(getWorkspaceDir(), id);
	    	FileSystemUtils.deleteRecursively(topDir);
	    	
			m_rwLock.writeLock().unlock();
		}
	}

	@Override
	public void removeInstanceAll() throws MDTInstanceManagerException {
		m_rwLock.writeLock().lock();
		try {
			List<InstanceDescriptor> descs = m_descriptorManager.getInstanceDescriptorAll();
			for ( InstanceDescriptor desc: descs ) {
				Try.run(() -> removeInstance(desc.getId()));
				m_descriptorManager.removeInstanceDescriptor(desc.getId());
			}
			
			// dangling registry를 삭제한다.
			for ( SubmodelDescriptor desc: m_submodelRegistry.getAllSubmodelDescriptors() ) {
				m_submodelRegistry.removeSubmodelDescriptorById(desc.getId());
			}
			for ( AssetAdministrationShellDescriptor desc: m_aasRegistry.getAllAssetAdministrationShellDescriptors() ) {
				m_aasRegistry.removeAssetAdministrationShellDescriptorById(desc.getId());
			}
			
			// dangling directory를 삭제한다.
			FStream.of(m_workspaceDir.listFiles(File::isDirectory))
					.forEachOrIgnore(FileUtils::deleteDirectory);
		}
		finally {
			m_rwLock.writeLock().unlock();
		}
	}
	
	public String toServiceEndpoint(int repoPort) {
		return String.format(m_repositoryEndpointFormat, repoPort);
	}
	
	public String getRepositoryEndpointFormat() {
		return m_repositoryEndpointFormat;
	}
	
	@Subscribe
	public void updateServiceEndpoint(InstanceStatusChangeEvent ev) {
		m_rwLock.readLock().lock();
		try {
			MDTInstance inst = getInstance(ev.getId());
			switch ( ev.getStatus() ) {
				case RUNNING:
					setServiceEndpoint(inst.getAASId(), ev.getServiceEndpoint());
					break;
				case STOPPED:
				case FAILED:
					unsetServiceEndpoint(inst.getAASId());
					break;
				default: break;
			}
		}
		catch ( Exception ignored ) { }
		finally {
			m_rwLock.readLock().unlock();
		}
	}
	
	private static JsonMapper s_mapper = JsonMapper.builder().build();
	private static final String TOPIC_STATUS_CHANGES = "mdt/manager";
	private static final int MQTT_QOS = 2;
	private static final MqttConnectOptions MQTT_OPTIONS;
	static {
		MQTT_OPTIONS = new MqttConnectOptions();
		MQTT_OPTIONS.setCleanSession(true);
	}
	
	@Subscribe
	public void publishStatusChangeEvent(InstanceStatusChangeEvent ev) {
		try {
			String jsonStr = s_mapper.writeValueAsString(new JsonEvent<>(ev));
			MqttMessage message = new MqttMessage(jsonStr.getBytes());
			message.setQos(MQTT_QOS);
			m_mqttClient.publish(TOPIC_STATUS_CHANGES, message);
		}
		catch ( Exception e ) {
			s_logger.error("Failed to publish event, cause=" + e);
		}
	}
	
	@Override
	public Logger getLogger() {
		return m_logger;
	}

	@Override
	public void setLogger(Logger logger) {
		m_logger = (logger != null) ? logger : s_logger;
	}
	
	private Environment readEnvironment(File aasEnvFile)
		throws IOException, ResourceAlreadyExistsException, ResourceNotFoundException {
		JsonDeserializer deser = new JsonDeserializer();
		
		try ( FileInputStream fis = new FileInputStream(aasEnvFile) ) {
			Environment env = deser.read(fis, Environment.class);
			if ( env.getAssetAdministrationShells().size() > 1
				|| env.getAssetAdministrationShells().size() == 0 ) {
				throw new MDTInstanceManagerException("Not supported: Multiple AAS descriptors in the Environment");
			}
			
			Set<String> submodelIds = Sets.newHashSet();
			for ( Submodel submodel: env.getSubmodels() ) {
				if ( submodelIds.contains(submodel.getId()) ) {
					throw new ResourceAlreadyExistsException("Submodel", submodel.getId());
				}
				submodelIds.add(submodel.getId());
			}
			
			AssetAdministrationShell aas = env.getAssetAdministrationShells().get(0);
			for ( Reference ref: aas.getSubmodels() ) {
				String refId = ref.getKeys().get(0).getValue();
				if ( !submodelIds.contains(refId) ) {
					throw new ResourceNotFoundException("Submodel", refId);
				}
			}
			
			return env;
		}
		catch ( DeserializationException e ) {
			throw new MDTInstanceManagerException("failed to parse Environment: file=" + aasEnvFile);
		}
	}

	private AssetAdministrationShellDescriptor registerEnvironment(Environment env) {
		// 주어진 Environment에 정의된 모든 Submodel들로 부터 SubmodelDescriptor 객체를 생성한다.
		Map<String,DefaultSubmodelDescriptor> smDescMap = Maps.newHashMap();
		for ( Submodel submodel: env.getSubmodels() ) {
			DefaultSubmodelDescriptor smDesc
								= RegistryModelConverter.createSubmodelDescriptor(submodel, null);
			smDescMap.put(smDesc.getId(), smDesc);
		}

		// Submodel Registry에 생성된 SubmodelDescriptor들을 등록시킨다.
		for ( DefaultSubmodelDescriptor smDesc: smDescMap.values() ) {
			m_submodelRegistry.addSubmodelDescriptor(smDesc);
		}
		
		AssetAdministrationShell aas = env.getAssetAdministrationShells().get(0);
		AssetAdministrationShellDescriptor aasDesc
						= RegistryModelConverter.createAssetAdministrationShellDescriptor(aas, null);
		List<SubmodelDescriptor> submodels = FStream.from(aas.getSubmodels())
													.flatMapIterable(ref -> ref.getKeys())
													.map(k -> k.getValue())
													.flatMapNullable(smId -> smDescMap.get(smId))
													.cast(SubmodelDescriptor.class)
													.toList();
		aasDesc.setSubmodelDescriptors(submodels);
		
		return m_aasRegistry.addAssetAdministrationShellDescriptor(aasDesc);
	}
	
	private void unregisterEnvironment(String aasId) {
		AssetAdministrationShellDescriptor aasDesc
									= m_aasRegistry.getAssetAdministrationShellDescriptorById(aasId);
		for ( SubmodelDescriptor smDesc:aasDesc.getSubmodelDescriptors() ) {
			String smId = smDesc.getId();
			Try.run(() -> m_submodelRegistry.removeSubmodelDescriptorById(smId));
		}
		Try.run(() -> m_aasRegistry.removeAssetAdministrationShellDescriptorById(aasDesc.getId()));
	}
	
	protected void setServiceEndpoint(String aasId, String svcEndpoint) {
		AssetAdministrationShellDescriptor aasDesc
											= m_aasRegistry.getAssetAdministrationShellDescriptorById(aasId);
		
		String encodedAssId = Utils.encodeBase64(aasId);
		Endpoint aasEp = RegistryModelConverter.createEndpoint(svcEndpoint + "/shells/"
																+ encodedAssId, "AAS-3.0");
		aasDesc.setEndpoints(Arrays.asList(aasEp));
		
		if ( getLogger().isInfoEnabled() ) {
			getLogger().info("updated Endpoints in the AssetAdministrationShell Registry: aas={}", aasId);
		}
		
		String prefix = svcEndpoint + "/submodels";
		List<SubmodelDescriptor> smDescList
				= FStream.from(aasDesc.getSubmodelDescriptors())
						.map(smDesc -> {
							String url = prefix + "/" + Utils.encodeBase64(smDesc.getId());
							List<Endpoint> eps = RegistryModelConverter.createEndpoints(url, "SUBMODEL-3.0");
							smDesc.setEndpoints(eps);
							return smDesc;
						})
						.toList();
		m_aasRegistry.updateAssetAdministrationShellDescriptorById(aasDesc);
		
		for ( SubmodelDescriptor smDesc: smDescList ) {
			m_submodelRegistry.updateSubmodelDescriptorById(smDesc);
		}
		
		if ( getLogger().isInfoEnabled() ) {
			getLogger().info("updated Endpoints in the Submodel Registry: aas={}", aasId);
		}
	}
	
	protected void unsetServiceEndpoint(String aasId) {
		AssetAdministrationShellDescriptor aasDesc
											= m_aasRegistry.getAssetAdministrationShellDescriptorById(aasId);
		Endpoint aasEp = RegistryModelConverter.createEndpoint("", "AAS-3.0");
		aasDesc.setEndpoints(Arrays.asList(aasEp));
		
		if ( getLogger().isInfoEnabled() ) {
			getLogger().info("Remove Endpoints in the AssetAdministrationShell Registry: aas={}", aasId);
		}

		List<SubmodelDescriptor> smDescList
				= FStream.from(aasDesc.getSubmodelDescriptors())
						.map(smDesc -> {
							List<Endpoint> eps = RegistryModelConverter.createEndpoints("", "SUBMODEL-3.0");
							smDesc.setEndpoints(eps);
							return smDesc;
						})
						.toList();
		m_aasRegistry.updateAssetAdministrationShellDescriptorById(aasDesc);

		for ( SubmodelDescriptor smDesc: smDescList ) {
			m_submodelRegistry.updateSubmodelDescriptorById(smDesc);
		}
		
		if ( getLogger().isInfoEnabled() ) {
			getLogger().info("Remove Endpoints in the Submodel Registry: aas={}", aasId);
		}
	}
}
