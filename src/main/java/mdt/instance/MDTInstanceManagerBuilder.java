package mdt.instance;

import java.io.File;

import mdt.model.ServiceFactory;
import mdt.model.instance.MDTInstanceManager;
import mdt.registry.AssetAdministrationShellRegistryProvider;
import mdt.registry.SubmodelRegistryProvider;


/**
 *
 * @author Kang-Woo Lee (ETRI)
 */
public interface MDTInstanceManagerBuilder<B extends MDTInstanceManagerBuilder<B,T>,
											T extends MDTInstanceManager> {
	public ServiceFactory serviceFactory();
	public B serviceFactory(ServiceFactory fact);
	
	public AssetAdministrationShellRegistryProvider aasRegistry();
	public B aasRegistry(AssetAdministrationShellRegistryProvider aasRegistry);

	public SubmodelRegistryProvider submodeRegistry();
	public B submodeRegistry(SubmodelRegistryProvider submodelRegistry);
	
	public String repositoryEndpointFormat();
	public B repositoryEndpointFormat(String format);
	
	public File workspaceDir();
	public B workspaceDir(File workspaceDir);
	
	public InstanceDescriptorManager instanceDescriptorManager();
	public B instanceDescriptorManager(InstanceDescriptorManager mgr);
	
	public T build();
}