package mdt.instance;

import java.io.File;
import java.io.IOException;
import java.util.List;

import org.eclipse.digitaltwin.aas4j.v3.model.AssetAdministrationShellDescriptor;
import org.eclipse.digitaltwin.aas4j.v3.model.SubmodelDescriptor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Objects;

import mdt.model.registry.AssetAdministrationShellRegistry;
import mdt.model.repository.AssetAdministrationShellRepository;
import mdt.model.repository.SubmodelRepository;


/**
 *
 * @author Kang-Woo Lee (ETRI)
 */
public abstract class AbstractInstance implements MDTInstanceProvider {
	@SuppressWarnings("unused")
	private static final Logger s_logger = LoggerFactory.getLogger(AbstractInstance.class);
	
	protected final AbstractInstanceManager m_manager;
	protected final InstanceDescriptor m_desc;
	
	protected abstract void remove();
	
	protected AbstractInstance(AbstractInstanceManager manager, InstanceDescriptor desc) {
		m_manager = manager;
		m_desc = desc;
	}
	
	public InstanceDescriptor getInstanceDescriptor() {
		return m_desc;
	}
	
	public File getWorkspaceDir() {
		return m_manager.getInstanceWorkspaceDir(getId());
	}

	@Override
	public void close() throws IOException { }

	@Override
	public String getId() {
		return m_desc.getId();
	}

	@Override
	public String getAASId() {
		return m_desc.getAasId();
	}

	@Override
	public String getAASIdShort() {
		return m_desc.getAasIdShort();
	}

	@Override
	public String getExecutionArguments() {
		return m_desc.getArguments();
	}

	@Override
	public AssetAdministrationShellDescriptor getAssetAdministrationShellDescriptor() {
		AssetAdministrationShellRegistry registry = m_manager.getAssetAdministrationShellRegistry();
		return registry.getAssetAdministrationShellDescriptorById(getAASId());
	}

	@Override
	public List<SubmodelDescriptor> getAllSubmodelDescriptors() {
		return getAssetAdministrationShellDescriptor().getSubmodelDescriptors();
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
	
	@Override
	public boolean equals(Object obj) {
		if ( this == obj ) {
			return true;
		}
		else if ( this == null || getClass() != obj.getClass() ) {
			return false;
		}
		
		AbstractInstance other = (AbstractInstance)obj;
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
	
	@SuppressWarnings("unchecked")
	protected <T extends AbstractInstanceManager> T getInstanceManager() {
		return (T)m_manager;
	}
}
