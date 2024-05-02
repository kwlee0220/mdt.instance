package mdt.instance;

import java.util.List;

import mdt.model.instance.MDTInstanceManagerException;
import mdt.model.registry.ResourceAlreadyExistsException;
import mdt.model.registry.ResourceNotFoundException;


/**
 *
 * @author Kang-Woo Lee (ETRI)
 */
public interface InstanceDescriptorManager {
	public void addInstanceDescriptor(InstanceDescriptor desc)
		throws MDTInstanceManagerException, ResourceAlreadyExistsException;
	public void removeInstanceDescriptor(String id) throws MDTInstanceManagerException;
	
	public InstanceDescriptor getInstanceDescriptor(String id) throws MDTInstanceManagerException,
																		ResourceNotFoundException;
	public InstanceDescriptor getInstanceDescriptorByAasId(String aasId) throws MDTInstanceManagerException,
																				ResourceNotFoundException;
	public List<InstanceDescriptor> getInstanceDescriptorAllByAasIdShort(String aasIdShort)
		throws MDTInstanceManagerException; 
	public InstanceDescriptor getInstanceDescriptorBySubmodelId(String submodelId)
			throws MDTInstanceManagerException;
	
	public List<InstanceDescriptor> getInstanceDescriptorAll() throws MDTInstanceManagerException; 
}
