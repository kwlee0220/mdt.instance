package mdt.instance.model;

import java.util.List;

import org.eclipse.digitaltwin.aas4j.v3.model.AssetAdministrationShellDescriptor;
import org.eclipse.digitaltwin.aas4j.v3.model.Endpoint;
import org.eclipse.digitaltwin.aas4j.v3.model.SubmodelDescriptor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.google.common.base.Objects;
import com.google.common.collect.Lists;

import mdt.client.Utils;
import mdt.exector.jar.controller.JarExecutorController;
import mdt.exector.jar.model.JarInstanceExecutor;
import mdt.model.EndpointInterface;
import mdt.model.instance.JarExecutionArguments;
import mdt.model.instance.MDTInstance;
import mdt.model.instance.MDTInstanceManagerException;
import mdt.model.instance.MDTInstanceStatus;
import mdt.model.instance.StatusResult;
import mdt.model.registry.AssetAdministrationShellRegistry;
import mdt.model.registry.RegistryException;
import mdt.model.repository.AssetAdministrationShellRepository;
import mdt.model.repository.SubmodelRepository;


/**
 *
 * @author Kang-Woo Lee (ETRI)
 */
public class MDTJarInstance implements MDTInstance {
	private static final Logger s_logger = LoggerFactory.getLogger(JarExecutorController.class);
	
	private final MDTInstanceManagerImpl m_manager;
	private MDTInstanceRecord m_record;
	
	MDTJarInstance(MDTInstanceManagerImpl manager, MDTInstanceRecord record) {
		m_manager = manager;
		m_record = record;
	}

	@Override
	public String getId() {
		return m_record.getInstanceId();
	}

	@Override
	public String getAASId() {
		return m_record.getAasId();
	}

	@Override
	public String getAASIdShort() {
		return m_record.getAasIdShort();
	}

	@Override
	public String getServiceEndpoint() {
		StatusResult result = m_manager.getInstanceExecutor().getStatus(getId());
		return result.getServiceEndpoint();
	}
	
	public void setServiceEndpoint(String ep) throws RegistryException {
		AssetAdministrationShellDescriptor desc = getAASDescriptor();
		Endpoint aasRepoEp = Utils.newEndpoint(ep, EndpointInterface.AAS_REPOSITORY);
		desc.setEndpoints(Lists.newArrayList(aasRepoEp));
		m_manager.getAssetAdministrationShellRegistry()
					.updateAssetAdministrationShellDescriptorById(desc);
	}

	@Override
	public AssetAdministrationShellDescriptor getAASDescriptor() {
		AssetAdministrationShellRegistry registry = m_manager.getAssetAdministrationShellRegistry();
		return registry.getAssetAdministrationShellDescriptorById(getAASId());
	}

	@Override
	public List<SubmodelDescriptor> getAllSubmodelDescriptors() {
		return getAASDescriptor().getSubmodelDescriptors();
	}

	@Override
	public StatusResult start() throws MDTInstanceManagerException {
		JarExecutionArguments args;
		try {
			args = m_manager.m_mapper.readValue(m_record.getArguments(), JarExecutionArguments.class);
		}
		catch ( JsonProcessingException e ) {
			throw new MDTInstanceManagerException("invalid arguments: " + m_record.getArguments());
		}

		JarInstanceExecutor exector = m_manager.getInstanceExecutor();
		return exector.start(getId(), getAASId(), args);
	}

	@Override
	public StatusResult stop() {
		JarInstanceExecutor exector = m_manager.getInstanceExecutor();
		return exector.stop(getId());
	}

	@Override
	public MDTInstanceStatus getStatus() {
		return m_manager.getInstanceExecutor().getStatus(getId()).getStatus();
	}
	
	public String getArguments() {
		return m_record.getArguments();
	}

	@Override
	public AssetAdministrationShellRepository getAssetAdministrationShellRepository() {
		String url = getServiceEndpoint() + "/shells";
		return m_manager.getServiceFactory()
						.getAssetAdministrationShellRepository(url);
	}

	@Override
	public SubmodelRepository getSubmodelRepository() {
		String url = getServiceEndpoint() + "/submodels";
		return m_manager.getServiceFactory()
						.getSubmodelRepository(url);
	}
	
	public MDTInstanceRecord getMDTInstanceRecord() {
		return m_record;
	}
	
	@Override
	public boolean equals(Object obj) {
		if ( this == obj ) {
			return true;
		}
		else if ( this == null || getClass() != MDTJarInstance.class ) {
			return false;
		}
		
		MDTJarInstance other = (MDTJarInstance)obj;
		return Objects.equal(getId(), other.getId());
	}
	
	@Override
	public int hashCode() {
		return Objects.hashCode(getId());
	}
	
	@Override
	public String toString() {
		return String.format("id=%s, aas=%s, endpoint=%s, status=%s",
								getId(), getAASId(), getServiceEndpoint(), getStatus());
	}
}
