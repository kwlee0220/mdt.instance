package mdt.registry;

import java.io.File;
import java.util.List;

import org.eclipse.digitaltwin.aas4j.v3.model.SubmodelDescriptor;

import mdt.model.registry.RegistryException;
import mdt.model.registry.ResourceAlreadyExistsException;
import mdt.model.registry.ResourceNotFoundException;
import mdt.model.registry.SubmodelRegistry;

/**
 *
 * @author Kang-Woo Lee (ETRI)
 */
public class CachingFileMDTSubmodelRegistry implements SubmodelRegistry {
    private final CachingFileBasedRegistry<SubmodelDescriptor> m_store;
    
    public CachingFileMDTSubmodelRegistry(File storeDir, int cacheSize) throws RegistryException {
    	m_store = new CachingFileBasedRegistry<>(storeDir, cacheSize, SubmodelDescriptor.class);
    }
    
    public File getStoreDir() {
    	return m_store.getStoreDir();
    }

	@Override
	public List<SubmodelDescriptor> getAllSubmodelDescriptors() throws RegistryException {
		return m_store.getAllDescriptors();
	}

	@Override
	public List<SubmodelDescriptor> getAllSubmodelDescriptorsByIdShort(String idShort) throws RegistryException {
		return m_store.getAllDescriptorsByShortId(idShort);
	}

	@Override
	public SubmodelDescriptor getSubmodelDescriptorById(String submodelId)
		throws ResourceNotFoundException, RegistryException {
		return m_store.getDescriptorById(submodelId);
	}

	@Override
	public SubmodelDescriptor addSubmodelDescriptor(SubmodelDescriptor descriptor)
		throws ResourceAlreadyExistsException, RegistryException {
		return m_store.addDescriptor(descriptor.getId(), descriptor);
	}

	@Override
	public SubmodelDescriptor updateSubmodelDescriptorById(SubmodelDescriptor descriptor)
		throws ResourceNotFoundException, RegistryException {
		return m_store.updateDescriptor(descriptor.getId(), descriptor);
	}

	@Override
	public void removeSubmodelDescriptorById(String submodelId)
		throws ResourceNotFoundException, RegistryException {
		m_store.removeDescriptor(submodelId);
	}
}
