package mdt.instance;

import java.io.File;

import mdt.model.ServiceFactory;
import mdt.model.registry.AssetAdministrationShellRegistry;
import mdt.model.registry.SubmodelRegistry;

/**
 *
 * @author Kang-Woo Lee (ETRI)
 */
public abstract class AbstractMDTInstanceManagerBuilder<B extends MDTInstanceManagerBuilder<B,T>,
															T extends MDTInstanceManagerProvider>
																implements MDTInstanceManagerBuilder<B,T> {
	protected ServiceFactory m_serviceFact;
	protected AssetAdministrationShellRegistry m_aasRegistry;
	protected SubmodelRegistry m_submodelRegistry;
	protected String m_repositoryEndpointFormat;
	protected File m_workspaceDir;
	
	protected abstract T internalBuild();
	
	public T build() {
		return internalBuild();
	}

	@Override
	public ServiceFactory serviceFactory() {
		return m_serviceFact;
	}

	@Override
	public B serviceFactory(ServiceFactory fact) {
		m_serviceFact = fact;
		return self();
	}

	@Override
	public AssetAdministrationShellRegistry aasRegistry() {
		return m_aasRegistry;
	}

	@Override
	public B aasRegistry(AssetAdministrationShellRegistry aasRegistry) {
		m_aasRegistry = aasRegistry;
		return self();
	}

	@Override
	public SubmodelRegistry submodeRegistry() {
		return m_submodelRegistry;
	}

	@Override
	public B submodeRegistry(SubmodelRegistry submodelRegistry) {
		m_submodelRegistry = submodelRegistry;
		return self();
	}

	@Override
	public String repositoryEndpointFormat() {
		return m_repositoryEndpointFormat;
	}

	@Override
	public B repositoryEndpointFormat(String format) {
		m_repositoryEndpointFormat = format;
		return self();
	}

	@Override
	public File workspaceDir() {
		return m_workspaceDir;
	}

	@Override
	public B workspaceDir(File workspaceDir) {
		m_workspaceDir = workspaceDir;
		return self();
	}
	
	@SuppressWarnings("unchecked")
	private B self() {
		return (B)this;
	}
}