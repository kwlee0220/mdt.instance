package mdt.registry;

import mdt.model.registry.AssetAdministrationShellRegistry;
import mdt.model.registry.RegistryException;
import mdt.model.registry.ResourceNotFoundException;


/**
 *
 * @author Kang-Woo Lee (ETRI)
 */
public interface AASRegistryProvider extends AssetAdministrationShellRegistry {
	public String getJsonAssetAdministrationShellDescriptorById(String aasId)
		throws ResourceNotFoundException, RegistryException;
}
