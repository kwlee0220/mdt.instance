package mdt.registry;

import java.io.File;
import java.util.List;

import org.eclipse.digitaltwin.aas4j.v3.model.AssetAdministrationShellDescriptor;

import mdt.model.registry.AssetAdministrationShellRegistry;
import mdt.model.registry.RegistryException;
import mdt.model.registry.ResourceAlreadyExistsException;
import mdt.model.registry.ResourceNotFoundException;


/**
 *
 * @author Kang-Woo Lee (ETRI)
 */
public class CachingFileMDTAASRegistry implements AssetAdministrationShellRegistry {
    private final CachingFileBasedRegistry<AssetAdministrationShellDescriptor> m_store;
    
    public CachingFileMDTAASRegistry(File storeDir, int cacheSize) throws RegistryException {
		m_store = new CachingFileBasedRegistry<>(storeDir, cacheSize,
												AssetAdministrationShellDescriptor.class);
    }
    
    public File getStoreDir() {
    	return m_store.getStoreDir();
    }

	@Override
	public List<AssetAdministrationShellDescriptor> getAllAssetAdministrationShellDescriptors()
		throws RegistryException {
		return m_store.getAllDescriptors();
	}

	@Override
	public AssetAdministrationShellDescriptor getAssetAdministrationShellDescriptorById(String aasId)
														throws ResourceNotFoundException, RegistryException {
		return m_store.getDescriptorById(aasId);
	}

	@Override
	public List<AssetAdministrationShellDescriptor>
	getAllAssetAdministrationShellDescriptorsByIdShort(String idShort) throws RegistryException {
		return m_store.getAllDescriptorsByShortId(idShort);
	}

	@Override
	public AssetAdministrationShellDescriptor addAssetAdministrationShellDescriptor(
															AssetAdministrationShellDescriptor descriptor)
		throws ResourceAlreadyExistsException, RegistryException {
		return m_store.addDescriptor(descriptor.getId(), descriptor);
	}

	@Override
	public AssetAdministrationShellDescriptor updateAssetAdministrationShellDescriptorById(
															AssetAdministrationShellDescriptor descriptor)
		throws ResourceNotFoundException, RegistryException {
		return m_store.updateDescriptor(descriptor.getId(), descriptor);
	}

	@Override
	public void removeAssetAdministrationShellDescriptorById(String aasId)
		throws ResourceNotFoundException, RegistryException {
		m_store.removeDescriptor(aasId);
	}
}
