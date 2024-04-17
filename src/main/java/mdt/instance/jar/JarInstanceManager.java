package mdt.instance.jar;

import java.io.File;
import java.io.IOException;

import org.eclipse.digitaltwin.aas4j.v3.model.AssetAdministrationShell;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Preconditions;
import com.google.common.io.Files;

import mdt.exector.jar.JarExecutionListener;
import mdt.exector.jar.JarInstanceExecutor;
import mdt.instance.AbstractMDTInstanceManagerBuilder;
import mdt.instance.FileBasedInstanceManager;
import mdt.model.instance.JarExecutionArguments;
import mdt.model.instance.MDTInstanceManagerException;
import mdt.model.instance.MDTInstanceStatus;


/**
 *
 * @author Kang-Woo Lee (ETRI)
 */
public class JarInstanceManager extends FileBasedInstanceManager<JarInstanceDescriptor> {
	private static final Logger s_logger = LoggerFactory.getLogger(JarInstanceManager.class);
	
	private final JarInstanceExecutor m_executor;
	
	public JarInstanceManager(JarInstanceManagerBuilder builder) {
		super(builder);
		setLogger(s_logger);
		
		Preconditions.checkNotNull(builder.m_exector, "JarInstanceExecutor was null");
		
		m_executor = builder.m_exector;
		m_executor.addExecutionListener(m_execListener);
	}
	
	public JarInstanceExecutor getInstanceExecutor() {
		return m_executor;
	}
	
	@Override
	protected JarInstanceDescriptor buildDescriptor(String id, AssetAdministrationShell aas,
													Object arguments) {
		if ( arguments instanceof JarExecutionArguments jargs ) {
			return new JarInstanceDescriptor(id, aas.getId(), aas.getIdShort(), jargs);
		}
		else {
			throw new IllegalArgumentException("Invalid ExecutionArguments type: type=" + arguments.getClass());
		}
	}

	@Override
	protected JarInstanceDescriptor readDescriptor(File descFile) throws MDTInstanceManagerException {
		try {
			return m_mapper.readValue(descFile, JarInstanceDescriptor.class);
		}
		catch ( Exception e ) {
			throw new MDTInstanceManagerException("Failed to read InstanceDescriptor file: " + descFile
													+ ", cause=" + e);
		}
	}
	
	@Override
	protected JarInstance toInstance(JarInstanceDescriptor descriptor) {
		if ( descriptor instanceof JarInstanceDescriptor jdesc ) {
			return new JarInstance(this, jdesc);
		}
		else {
			throw new MDTInstanceManagerException("Invalid JarInstanceDescriptor: desc=" + descriptor);
		}
	}
	
	@Override
	protected JarInstanceDescriptor buildInstance(File instanceDir, JarInstanceDescriptor descriptor) {
		if ( descriptor instanceof JarInstanceDescriptor jdesc ) {
			try {
				File jarFile = new File(instanceDir, "fa3st-repository.jar");
				File modelFile = new File(instanceDir, "model.json");
				File confFile = new File(instanceDir, "conf.json");
				
				JarExecutionArguments args = jdesc.getArguments();
				copyFileIfNotSame(new File(args.getJarFile()), jarFile);
				copyFileIfNotSame(new File(args.getModelFile()), modelFile);
				copyFileIfNotSame(new File(args.getConfigFile()), confFile);
				args = JarExecutionArguments.builder()
											.jarFile(jarFile.getAbsolutePath())
											.modelFile(modelFile.getAbsolutePath())
											.configFile(confFile.getAbsolutePath())
											.build();
				jdesc.setArguments(args);
				
				return jdesc;
			}
			catch ( IOException e ) {
				throw new MDTInstanceManagerException("Failed to create MDTInstance: args="
														+ descriptor + ", cause=" + e);
			}
		}
		else {
			throw new MDTInstanceManagerException("Invalid JarInstanceDescriptor: desc=" + descriptor);
		}
	}
	
	private void copyFileIfNotSame(File src, File dest) throws IOException {
		if ( !src.getAbsolutePath().equals(dest.getAbsolutePath()) ) {
			Files.copy(src, dest);
		}
	}
	
	private final JarExecutionListener m_execListener = new JarExecutionListener() {
		@Override
		public void stausChanged(String id, MDTInstanceStatus status, int repoPort) {
			try {
				JarInstance instance = (JarInstance)getInstance(id);
				switch ( status ) {
					case RUNNING:
						String ep = toServiceEndpoint(repoPort);
						setServiceEndpoint(instance.getAASId(), ep);
						if ( s_logger.isInfoEnabled() ) {
							s_logger.info("JarInstance is now running: " + instance);
						}
						break;
					case STOPPED:
						unsetServiceEndpoint(instance.getAASId());
						if ( s_logger.isInfoEnabled() ) {
							s_logger.info("JarInstance is now stopped: " + instance);
						}
						break;
					case FAILED:
						unsetServiceEndpoint(instance.getAASId());
						if ( s_logger.isInfoEnabled() ) {
							s_logger.info("JarInstance is now failed: " + instance);
						}
						break;
					default: break;
				}
			}
			catch ( Exception expected ) { }
		}
		
		@Override
		public void timeoutExpired() { }
	};
	
	public static JarInstanceManagerBuilder builder() {
		return new JarInstanceManagerBuilder();
	}
	public static class JarInstanceManagerBuilder
		extends AbstractMDTInstanceManagerBuilder<JarInstanceManagerBuilder, JarInstanceManager> {
		private JarInstanceExecutor m_exector;
		
		public JarInstanceExecutor executor() {
			return m_exector;
		}
		
		public JarInstanceManagerBuilder executor(JarInstanceExecutor exector) {
			m_exector = exector;
			return this;
		}
		
		@Override
		protected JarInstanceManager internalBuild() {
			return new JarInstanceManager(this);
		}
	}
}
