package mdt.instance;

import java.util.List;

import org.eclipse.digitaltwin.aas4j.v3.model.AssetAdministrationShellDescriptor;
import org.eclipse.digitaltwin.aas4j.v3.model.SubmodelDescriptor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Objects;

import mdt.model.instance.MDTInstance;
import mdt.model.registry.AssetAdministrationShellRegistry;
import mdt.model.repository.AssetAdministrationShellRepository;
import mdt.model.repository.SubmodelRepository;


/**
 *
 * @author Kang-Woo Lee (ETRI)
 */
public abstract class FileBasedInstance<D extends InstanceDescriptor> implements MDTInstance {
	@SuppressWarnings("unused")
	private static final Logger s_logger = LoggerFactory.getLogger(FileBasedInstance.class);
	
	protected final FileBasedInstanceManager<D> m_manager;
	protected final D m_desc;
	
	protected abstract void remove();
	
	protected FileBasedInstance(FileBasedInstanceManager<D> manager, D desc) {
		m_manager = manager;
		m_desc = desc;
	}

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
	public AssetAdministrationShellDescriptor getAASDescriptor() {
		AssetAdministrationShellRegistry registry = m_manager.getAssetAdministrationShellRegistry();
		return registry.getAssetAdministrationShellDescriptorById(getAASId());
	}

	@Override
	public List<SubmodelDescriptor> getAllSubmodelDescriptors() {
		return getAASDescriptor().getSubmodelDescriptors();
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
	
	protected D getInstanceDescriptor() {
		return m_desc;
	}
	
	@Override
	public boolean equals(Object obj) {
		if ( this == obj ) {
			return true;
		}
		else if ( this == null || getClass() != obj.getClass() ) {
			return false;
		}
		
		MDTInstance other = (MDTInstance)obj;
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
	protected <T extends FileBasedInstanceManager<D>> T getInstanceManager() {
		return (T)m_manager;
	}
}
