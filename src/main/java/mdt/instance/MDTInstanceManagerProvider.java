package mdt.instance;

import java.util.List;

import org.eclipse.digitaltwin.aas4j.v3.model.Endpoint;
import org.eclipse.digitaltwin.aas4j.v3.model.Environment;

import mdt.client.registry.RegistryModelConverter;
import mdt.model.ServiceFactory;
import mdt.model.instance.MDTInstance;
import mdt.model.instance.MDTInstanceManager;
import mdt.model.instance.MDTInstanceManagerException;
import mdt.model.registry.RegistryException;
import mdt.model.registry.ResourceNotFoundException;
import mdt.model.registry.ResourceNotReadyException;
import mdt.model.service.AssetAdministrationShellService;
import mdt.model.service.SubmodelService;

/**
 *
 * @author Kang-Woo Lee (ETRI)
 */
public interface MDTInstanceManagerProvider extends MDTInstanceManager {
	public ServiceFactory getServiceFactory();
	
	public MDTInstance addInstance(String id, Environment env, String arguments)
		throws MDTInstanceManagerException;

	@Override
	public default AssetAdministrationShellService getAssetAdministrationShellService(String aasId)
		throws ResourceNotFoundException, ResourceNotReadyException, RegistryException {
		List<Endpoint> eps = getAssetAdministrationShellRegistry()
								.getAssetAdministrationShellDescriptorById(aasId)
								.getEndpoints();
		String endpoint = RegistryModelConverter.getEndpointString(eps);
		if ( endpoint == null ) {
			throw new ResourceNotReadyException("AssetAdministrationShell", aasId);
		}
		
		return getServiceFactory().getAssetAdministrationShellService(endpoint);
	}

	@Override
	public default SubmodelService getSubmodelService(String submodelId)
		throws ResourceNotFoundException, ResourceNotReadyException, RegistryException {
		List<Endpoint> eps = getSubmodelRegistry()
								.getSubmodelDescriptorById(submodelId)
								.getEndpoints();
		String endpoint = RegistryModelConverter.getEndpointString(eps);
		if ( endpoint == null ) {
			throw new ResourceNotReadyException("Submodel", submodelId);
		}
		
		return getServiceFactory().getSubmodelService(endpoint);
	}
}
