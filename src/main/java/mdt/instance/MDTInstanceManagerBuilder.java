package mdt.instance;

import java.io.File;

import mdt.model.ServiceFactory;
import mdt.model.instance.MDTInstanceManager;
import mdt.model.registry.AssetAdministrationShellRegistry;
import mdt.model.registry.SubmodelRegistry;


/**
 *
 * @author Kang-Woo Lee (ETRI)
 */
public interface MDTInstanceManagerBuilder<B extends MDTInstanceManagerBuilder<B,T>,
											T extends MDTInstanceManager> {
	public ServiceFactory serviceFactory();
	public B serviceFactory(ServiceFactory fact);
	
	public AssetAdministrationShellRegistry aasRegistry();
	public B aasRegistry(AssetAdministrationShellRegistry aasRegistry);

	public SubmodelRegistry submodeRegistry();
	public B submodeRegistry(SubmodelRegistry submodelRegistry);
	
	public String repositoryEndpointFormat();
	public B repositoryEndpointFormat(String format);
	
	public File workspaceDir();
	public B workspaceDir(File workspaceDir);
	
	public T build();
}