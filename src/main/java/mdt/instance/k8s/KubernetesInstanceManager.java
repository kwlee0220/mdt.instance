package mdt.instance.k8s;

import java.io.File;

import org.eclipse.digitaltwin.aas4j.v3.model.AssetAdministrationShell;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import mdt.instance.AbstractMDTInstanceManager;
import mdt.instance.AbstractMDTInstanceManagerBuilder;
import mdt.instance.InstanceDescriptor;
import mdt.model.instance.KubernetesExecutionArguments;
import mdt.model.instance.MDTInstanceManagerException;

/**
 *
 * @author Kang-Woo Lee (ETRI)
 */
public class KubernetesInstanceManager extends AbstractMDTInstanceManager {
	private static final Logger s_logger = LoggerFactory.getLogger(KubernetesInstanceManager.class);
	
	private final KubernetesRemote m_k8s;
	
	public KubernetesInstanceManager(KubernetesInstanceManagerBuilder builder) {
		super(builder);
		
		setLogger(s_logger);
		m_k8s = KubernetesRemote.connect();
	}
	
	@Override
	protected KubernetesInstanceDescriptor buildDescriptor(String id, AssetAdministrationShell aas,
															Object arguments) {
		if ( arguments instanceof KubernetesExecutionArguments jargs ) {
			return new KubernetesInstanceDescriptor(id, aas.getId(), aas.getIdShort(), jargs);
		}
		else {
			throw new IllegalArgumentException("Invalid ExecutionArguments type: type=" + arguments.getClass());
		}
	}

	@Override
	protected KubernetesInstanceDescriptor readDescriptor(File descFile) throws MDTInstanceManagerException {
		try {
			return m_mapper.readValue(descFile, KubernetesInstanceDescriptor.class);
		}
		catch ( Exception e ) {
			throw new MDTInstanceManagerException("Failed to read InstanceDescriptor file: " + descFile
													+ ", cause=" + e);
		}
	}
	
	@Override
	protected KubernetesInstance toInstance(InstanceDescriptor descriptor) {
		if ( descriptor instanceof KubernetesInstanceDescriptor jdesc ) {
			return new KubernetesInstance(this, jdesc);
		}
		else {
			throw new MDTInstanceManagerException("Invalid KubernetesInstanceDescriptor: desc=" + descriptor);
		}
	}
	
	@Override
	protected KubernetesInstanceDescriptor buildInstance(AbstractMDTInstanceManager manager, File instanceDir,
														InstanceDescriptor descriptor) {
		if ( descriptor instanceof KubernetesInstanceDescriptor kdesc ) {
			return kdesc;
		}
		else {
			throw new MDTInstanceManagerException("Invalid JarInstanceDescriptor: desc=" + descriptor);
		}
	}

	KubernetesRemote getKubernetesRemote() {
		return m_k8s;
	}

	public static KubernetesInstanceManagerBuilder builder() {
		return new KubernetesInstanceManagerBuilder();
	}
	public static class KubernetesInstanceManagerBuilder
		extends AbstractMDTInstanceManagerBuilder<KubernetesInstanceManagerBuilder, KubernetesInstanceManager> {
		@Override
		protected KubernetesInstanceManager internalBuild() {
			return new KubernetesInstanceManager(this);
		}
	}
}
