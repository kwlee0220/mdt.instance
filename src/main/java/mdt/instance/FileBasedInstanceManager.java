package mdt.instance;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.FileSystemUtils;

import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;

import utils.LoggerSettable;
import utils.func.Try;
import utils.stream.FStream;

import mdt.client.Utils;
import mdt.client.registry.RegistryModelConverter;
import mdt.model.ServiceFactory;
import mdt.model.instance.MDTInstance;
import mdt.model.instance.MDTInstanceManagerException;
import mdt.model.instance.StatusResult;
import mdt.model.registry.AssetAdministrationShellRegistry;
import mdt.model.registry.ResourceAlreadyExistsException;
import mdt.model.registry.ResourceNotFoundException;
import mdt.model.registry.SubmodelRegistry;


/**
 *
 * @author Kang-Woo Lee (ETRI)
 */
public abstract class FileBasedInstanceManager<D extends InstanceDescriptor>
													implements MDTInstanceManagerProvider, LoggerSettable {
	private static final Logger s_logger = LoggerFactory.getLogger(FileBasedInstanceManager.class);
	
	private static final String DESCRIPTOR_FILE_NAME = "descriptor.json";
	
	private ServiceFactory m_serviceFact;
	private AssetAdministrationShellRegistry m_aasRegistry;
	private SubmodelRegistry m_submodelRegistry;
	private String m_repositoryEndpointFormat;
	private File m_workspaceDir;
	protected final JsonMapper m_mapper;
	private Logger m_logger;
	
	private final ReadWriteLock m_rwLock = new ReentrantReadWriteLock();

	abstract protected D buildDescriptor(String id, AssetAdministrationShell aas, Object arguments)
		throws MDTInstanceManagerException;
	abstract protected D readDescriptor(File descFile) throws MDTInstanceManagerException;
	abstract protected FileBasedInstance<D> toInstance(D descriptor) throws MDTInstanceManagerException;
	abstract protected D buildInstance(File instanceDir, D descriptor) throws MDTInstanceManagerException;
	
	public FileBasedInstanceManager(MDTInstanceManagerBuilder<?,?> builder) {
		m_serviceFact = builder.serviceFactory();
		m_aasRegistry = builder.aasRegistry();
		m_submodelRegistry = builder.submodeRegistry();
		m_repositoryEndpointFormat = builder.repositoryEndpointFormat();
		m_workspaceDir = builder.workspaceDir();
		
		m_mapper = JsonMapper.builder().enable(SerializationFeature.INDENT_OUTPUT).build();
		
		if ( m_repositoryEndpointFormat == null ) {
			try {
				String host = InetAddress.getLocalHost().getHostAddress();
				this.m_repositoryEndpointFormat = "https:" + host + ":%d/api/v3.0";
			}
			catch ( UnknownHostException e ) {
				throw new MDTInstanceManagerException("" + e);
			}
		}
		m_logger = s_logger;
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
	
	@Override
	public FileBasedInstance<D> getInstance(String id) throws ResourceNotFoundException {
		m_rwLock.readLock().lock();
		try {
			D desc = readDescriptor(id);
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
			return FStream.of(getWorkspaceDir().listFiles(File::isDirectory))
							.map(File::getName)
							.mapOrThrow(this::getInstance)
							.cast(MDTInstance.class)
							.toList();
		}
		finally {
			m_rwLock.readLock().unlock();
		}
	}
	
	@Override
	public FileBasedInstance<D> getInstanceByAasId(String aasId) throws ResourceNotFoundException {
		m_rwLock.readLock().lock();
		try {
			D desc = FStream.of(getWorkspaceDir().listFiles(File::isDirectory))
													.map(File::getName)
													.mapOrIgnore(this::readDescriptor)
													.filter(d -> d.getAasId().equals(aasId))
													.findFirst()
													.getOrNull();
			if ( desc != null ) {
				return toInstance(desc);
			}
			else {
				throw new ResourceNotFoundException("MDTInstance: aas-id=" + aasId);
			}
		}
		finally {
			m_rwLock.readLock().unlock();
		}
	}
	
	@Override
	public List<MDTInstance> getInstanceAllByIdShort(String aasIdShort) throws MDTInstanceManagerException {
		m_rwLock.readLock().lock();
		try {
			FStream<D> descriptors = FStream.of(getWorkspaceDir().listFiles(File::isDirectory))
																.map(File::getName)
																.mapOrIgnore(this::readDescriptor);
			if ( aasIdShort != null ) {
				descriptors = descriptors.filter(pl -> aasIdShort.equals(pl.getAasIdShort()));
			}
			else {
				descriptors = descriptors.filter(pl -> pl.getAasIdShort() == null);
			}
			return descriptors.map(this::toInstance)
							.cast(MDTInstance.class)
							.toList();
		}
		finally {
			m_rwLock.readLock().unlock();
		}
	}

	@Override
	public FileBasedInstance<D> addInstance(String id, Environment env, Object arguments)
		throws MDTInstanceManagerException {
		// AAS Environment 정의 파일을 읽어서 AAS Registry에 등록한다.
		registerEnvironment(env);
		
		// AAS 정보와 이미지 식별자를 instance descriptor에 저장한다.
		AssetAdministrationShell aas = env.getAssetAdministrationShells().get(0);
		D desc = buildDescriptor(id, aas, arguments);
		
		m_rwLock.writeLock().lock();
		try {
			FileBasedInstance<D> instance = createInstance(desc);
			if ( getLogger().isInfoEnabled() ) {
				getLogger().info("added: " + instance);
			}
			
			return instance;
		}
		catch ( MDTInstanceManagerException e ) {
			Try.run(() -> unregisterEnvironment(aas.getId()));
			throw e;
		}
		finally {
			m_rwLock.writeLock().unlock();
		}
	}

	@Override
	public FileBasedInstance<D> addInstance(String id, File aasFile, Object arguments)
		throws MDTInstanceManagerException {
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

	@Override
	public void removeInstance(String id) throws MDTInstanceManagerException {
		m_rwLock.writeLock().lock();
		try {
			FileBasedInstance<D> instance = getInstance(id);
			switch ( instance.getStatus() ) {
				case STARTING:
				case RUNNING:
					throw new IllegalStateException("Cannot remove the running instance: id=" + instance.getId());
				default: break;
			}
			
			Try.run(() -> unregisterEnvironment(instance.getAASId()));
			Try.run(instance::remove);
			
	    	File topDir = new File(getWorkspaceDir(), instance.getId());
	    	FileSystemUtils.deleteRecursively(topDir);
	    	
			if ( getLogger().isInfoEnabled() ) {
				getLogger().info("removed: " + instance);
			}
		}
		finally {
			m_rwLock.writeLock().unlock();
		}
	}
	
	@Override
	public void removeInstanceAll() throws MDTInstanceManagerException {
		m_rwLock.writeLock().lock();
		try {
			FStream.of(getWorkspaceDir().listFiles(File::isDirectory))
					.map(File::getName)
					.forEachOrIgnore(this::removeInstance);
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
	
	public File getWorkspaceDir() {
		return m_workspaceDir;
	}
	
	public JsonMapper getJsonMapper() {
		return m_mapper;
	}
	
	public void instanceStatusChanged(StatusResult result) {
		MDTInstance inst = getInstance(result.getId());
		switch ( result.getStatus() ) {
			case RUNNING:
				setServiceEndpoint(inst.getAASId(), result.getServiceEndpoint());
				break;
			case STOPPED:
			case FAILED:
				unsetServiceEndpoint(inst.getAASId());
				break;
			default: break;
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

	public FileBasedInstance<D> createInstance(D desc) {
		File instanceDir = new File(getWorkspaceDir(), desc.getId());
		try {
			Files.createDirectories(instanceDir.toPath());
			
			desc = buildInstance(instanceDir, desc);
			
			File descFile = new File(instanceDir, "descriptor.json");
			m_mapper.writeValue(descFile, desc);
			
			return toInstance(desc);
		}
		catch ( Exception e ) {
	    	Try.run(() -> FileSystemUtils.deleteRecursively(instanceDir));
			
			throw new MDTInstanceManagerException("Failed to create MDTInstance: desc=" + desc
												+ ", cause=" + e);
		}
	}
	
	private D readDescriptor(String id) throws ResourceNotFoundException,
															MDTInstanceManagerException {
		File instanceDir = new File(getWorkspaceDir(), id);
		if ( !instanceDir.isDirectory() ) {
			throw new ResourceNotFoundException("MDTInstance: id=" + id
												+ ", instanceDir does not exist dir=" + instanceDir);
		}
		File descFile = new File(instanceDir, DESCRIPTOR_FILE_NAME);
		return readDescriptor(descFile);
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
					throw new ResourceAlreadyExistsException("Duplicate Submodel: id=" + submodel.getId());
				}
				submodelIds.add(submodel.getId());
			}
			
			AssetAdministrationShell aas = env.getAssetAdministrationShells().get(0);
			for ( Reference ref: aas.getSubmodels() ) {
				String refId = ref.getKeys().get(0).getValue();
				if ( !submodelIds.contains(refId) ) {
					throw new ResourceNotFoundException("Dangling Submodel Ref: " + refId);
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
