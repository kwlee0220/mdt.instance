package mdt.instance.model;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

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
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;

import utils.async.Guard;
import utils.func.Tuple;
import utils.func.Unchecked;
import utils.stream.FStream;

import mdt.client.Utils;
import mdt.client.registry.RegistryModelConverter;
import mdt.exector.jar.model.JarExecutionListener;
import mdt.exector.jar.model.JarInstanceExecutor;
import mdt.model.ServiceFactory;
import mdt.model.instance.MDTInstance;
import mdt.model.instance.MDTInstanceManager;
import mdt.model.instance.MDTInstanceManagerException;
import mdt.model.instance.MDTInstanceNotFoundException;
import mdt.model.instance.StatusResult;
import mdt.model.registry.AssetAdministrationShellRegistry;
import mdt.model.registry.ResourceAlreadyExistsException;
import mdt.model.registry.ResourceNotFoundException;
import mdt.model.registry.SubmodelRegistry;

/**
 *
 * @author Kang-Woo Lee (ETRI)
 */
@Component
public class MDTInstanceManagerImpl implements MDTInstanceManager, InitializingBean {
	private static final Logger s_logger = LoggerFactory.getLogger(MDTInstanceManagerImpl.class);
	
	@Autowired private ServiceFactory m_serviceFact;
	@Autowired private AssetAdministrationShellRegistry m_aasRegistry;
	@Autowired private SubmodelRegistry m_submodelRegistry;
	@Autowired private MDTInstanceStore m_instStore;
	@Autowired private JarInstanceExecutor m_executor;
	
	final JsonMapper m_mapper;
	
	private final Guard m_guard = Guard.create();
	private final Map<String,MDTJarInstance> m_instances = Maps.newHashMap();
	
	public MDTInstanceManagerImpl() {
		m_mapper = JsonMapper.builder().build();
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
	
	public MDTInstanceStore getMDTInstanceStore() {
		return m_instStore;
	}

	@Override
	public MDTJarInstance getInstance(String instanceId) throws MDTInstanceManagerException {
		Objects.requireNonNull(instanceId);
		
		return m_guard.getOrThrow(() -> {
			MDTJarInstance instance = m_instances.get(instanceId);
			if ( instance == null ) {
				throw new MDTInstanceNotFoundException(instanceId); 
			}
			
			return instance;
		});
	}

	@Override
	public MDTInstance getInstanceByAasId(String aasId) throws MDTInstanceManagerException {
		Objects.requireNonNull(aasId);

		return m_guard.getOrThrow(() -> {
			return FStream.from(m_instances.values())
						.findFirst(inst -> inst.getAASId().equals(aasId))
						.getOrThrow(() -> new MDTInstanceNotFoundException("aas-id=" + aasId));
		});
	}

	@Override
	public List<MDTInstance> getAllInstances() throws MDTInstanceManagerException {
		return m_guard.getOrThrow(() -> {
			return m_instances.values().stream().collect(Collectors.toList());
		});
	}

	@Override
	public List<MDTInstance> getAllInstancesByIdShort(String aasIdShort) throws MDTInstanceManagerException {
		return m_guard.getOrThrow(() -> {
			return m_instances.values().stream()
								.filter(inst -> aasIdShort.equals(inst.getAASIdShort()))
								.collect(Collectors.toList());
		});
	}

	public MDTInstance addInstance(String id, Environment env, Object arguments)
		throws MDTInstanceManagerException {
		try {
			// AAS Environment 정의 파일을 읽어서 AAS Registry에 등록한다.
			registerEnvironment(env);
			
			String argsJson = m_mapper.writeValueAsString(arguments);
			
			// AAS 정보와 이미지 식별자를 instance database에 저장한다.
			AssetAdministrationShell aas = env.getAssetAdministrationShells().get(0);
			MDTInstanceRecord rec = MDTInstanceRecord.builder()
													.instanceId(id)
													.aasId(aas.getId())
													.aasIdShort(aas.getIdShort())
													.arguments(argsJson)
													.build();
			m_instStore.addRecord(rec);
			
			MDTJarInstance instance = new MDTJarInstance(this, rec);
			m_guard.runOrThrow(() -> m_instances.put(id, instance));
			
			return instance;
		}
		catch ( SQLException | JsonProcessingException e ) {
			String params = String.format("%s: (%s)", id, arguments);
			throw new MDTInstanceManagerException("failed to register an instance: " + params + ", cause=" + e);
		}
	}

	@Override
	public MDTInstance addInstance(String id, File aasFile, Object arguments) throws MDTInstanceManagerException {
		try {
			// AAS Environment 정의 파일을 읽어서 AAS Registry에 등록한다.
			Environment env = readEnvironment(aasFile);
			registerEnvironment(env);
			
			String argssJson = m_mapper.writeValueAsString(arguments);
			
			// AAS 정보와 이미지 식별자를 instance database에 저장한다.
			AssetAdministrationShell aas = env.getAssetAdministrationShells().get(0);
			MDTInstanceRecord rec = MDTInstanceRecord.builder()
													.instanceId(id)
													.aasId(aas.getId())
													.aasIdShort(aas.getIdShort())
													.arguments(argssJson)
													.build();
			m_instStore.addRecord(rec);
			
			MDTJarInstance instance = new MDTJarInstance(this, rec);
			m_guard.runOrThrow(() -> m_instances.put(id, instance));
			
			return instance;
		}
		catch ( IOException | SQLException e ) {
			String params = String.format("%s: (%s)", id, arguments);
			throw new MDTInstanceManagerException("failed to register an instance: " + params + ", cause=" + e);
		}
	}

	@Override
	public void removeInstance(String id) {
		MDTInstance inst = getInstance(id);
		
		unregisterEnvironment(inst.getAASDescriptor());
		Unchecked.runOrIgnore(() -> m_instStore.deleteRecord(id));
		m_guard.runOrThrow(() -> m_instances.remove(id));
	}

	@Override
	public void removeInstanceAll() {
		for ( MDTInstance inst: getAllInstances() ) {
			removeInstance(inst.getId());
		}
	}
	
	public Optional<MDTInstance> getMDTInstanceBySubmodelId(String submodelId) {
		for ( AssetAdministrationShellDescriptor desc:
											m_aasRegistry.getAllAssetAdministrationShellDescriptors() ) {
			for ( SubmodelDescriptor smDesc: desc.getSubmodelDescriptors() ) {
				if ( smDesc.getId().equals(submodelId) ) {
					return Optional.of(getInstanceByAasId(desc.getId()));
				}
			}
		}
		
		return Optional.empty();
	}
	
	public List<Tuple<MDTInstance,String>> getAllMDTInstancesBySubmodelIdShort(String submodelIdShort) {
		List<Tuple<MDTInstance,String>> instList = Lists.newArrayList();
		for ( AssetAdministrationShellDescriptor desc:
											m_aasRegistry.getAllAssetAdministrationShellDescriptors() ) {
			for ( SubmodelDescriptor smDesc: desc.getSubmodelDescriptors() ) {
				if ( submodelIdShort.equals(smDesc.getIdShort()) ) {
					Tuple<MDTInstance,String> tup = Tuple.of(getInstanceByAasId(desc.getId()),
															smDesc.getIdShort());
					instList.add(tup);
					break;
				}
			}
		}
		
		return instList;
	}

	JarInstanceExecutor getInstanceExecutor() throws MDTInstanceManagerException {
		return m_executor;
	}
	
	MDTInstanceRecord readInstanceRecord(String instanceId) throws SQLException {
		return m_instStore.getRecordByInstanceId(instanceId);
	}
	
	MDTInstanceRecord readInstanceRecordByAasId(String aasId) throws SQLException {
		return m_instStore.getRecordByAASId(aasId);
	}

	@Override
	public void afterPropertiesSet() throws Exception {
		m_executor.addExecutionListener(m_execListener);
		m_guard.run(() -> {
			try {
				m_instStore.getRecordAll().stream()
							.map(rec -> new MDTJarInstance(this, rec))
							.forEach(inst -> m_instances.put(inst.getId(), inst));
			}
			catch ( SQLException e ) {
				throw new MDTInstanceManagerException("failed to build up registered MDTInstances");
			}
		});
		
		if ( s_logger.isInfoEnabled() ) {
			s_logger.info("MDTInstanceManager is now ready to serve");
		}
	}
	
	private final JarExecutionListener m_execListener = new JarExecutionListener() {
		@Override
		public void stausChanged(String id, StatusResult status) {
			try {
				MDTJarInstance instance = getInstance(id);
				switch ( status.getStatus() ) {
					case RUNNING:
						setServiceEndpoint(instance.getAASId(), status.getServiceEndpoint());
						break;
					case STOPPED:
					case FAILED:
						unsetServiceEndpoint(instance.getAASId());
						break;
					default: break;
				}
			}
			catch ( Exception expected ) { }
		}
		
		@Override
		public void timeoutExpired() { }
	};
	
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
		Map<String,DefaultSubmodelDescriptor> submodelMaps = Maps.newHashMap();
		for ( Submodel submodel: env.getSubmodels() ) {
			DefaultSubmodelDescriptor smDesc = RegistryModelConverter.createSubmodelDescriptor(submodel, null);
			submodelMaps.put(smDesc.getId(), smDesc);
		}

		for ( DefaultSubmodelDescriptor smDesc: submodelMaps.values() ) {
			m_submodelRegistry.addSubmodelDescriptor(smDesc);
		}
		
		AssetAdministrationShell aas = env.getAssetAdministrationShells().get(0);
		AssetAdministrationShellDescriptor aasDesc
							= RegistryModelConverter.createAssetAdministrationShellDescriptor(aas, null);
		List<SubmodelDescriptor> submodels = FStream.from(aas.getSubmodels())
													.flatMapIterable(ref -> ref.getKeys())
													.map(k -> k.getValue())
													.flatMapNullable(smId -> submodelMaps.get(smId))
													.cast(SubmodelDescriptor.class)
													.toList();
		aasDesc.setSubmodelDescriptors(submodels);
		return m_aasRegistry.addAssetAdministrationShellDescriptor(aasDesc);
	}
	
	private void unregisterEnvironment(AssetAdministrationShellDescriptor aasDesc) {
		for ( SubmodelDescriptor smDesc:aasDesc.getSubmodelDescriptors() ) {
			try {
				m_submodelRegistry.removeSubmodelDescriptorById(smDesc.getId());
			}
			catch ( Throwable e ) { }
		}
		try {
			m_aasRegistry.removeAssetAdministrationShellDescriptorById(aasDesc.getId());
		}
		catch ( Throwable e ) { }
	}
	
	private void setServiceEndpoint(String aasId, String svcEndpoint) {
		AssetAdministrationShellDescriptor aasDesc
											= m_aasRegistry.getAssetAdministrationShellDescriptorById(aasId);
		
		String encodedAssId = Utils.encodeBase64(aasId);
		Endpoint aasEp = RegistryModelConverter.createEndpoint(svcEndpoint + "/shells/" + encodedAssId, "AAS-3.0");
		aasDesc.setEndpoints(Arrays.asList(aasEp));
		
		if ( s_logger.isInfoEnabled() ) {
			s_logger.info("updated Endpoints in the AssetAdministrationShell Registry: aas={}", aasId);
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
		
		if ( s_logger.isInfoEnabled() ) {
			s_logger.info("updated Endpoints in the Submodel Registry: aas={}", aasId);
		}
	}
	
	private void unsetServiceEndpoint(String aasId) {
		AssetAdministrationShellDescriptor aasDesc
											= m_aasRegistry.getAssetAdministrationShellDescriptorById(aasId);
		Endpoint aasEp = RegistryModelConverter.createEndpoint("", "AAS-3.0");
		aasDesc.setEndpoints(Arrays.asList(aasEp));
		
		if ( s_logger.isInfoEnabled() ) {
			s_logger.info("Remove Endpoints in the AssetAdministrationShell Registry: aas={}", aasId);
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
		
		if ( s_logger.isInfoEnabled() ) {
			s_logger.info("Remove Endpoints in the Submodel Registry: aas={}", aasId);
		}
	}
}
