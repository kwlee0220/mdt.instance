package mdt.instance.jar;

import java.io.File;
import java.io.IOException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.google.common.base.Preconditions;
import com.google.common.io.Files;

import mdt.Globals;
import mdt.exector.jar.JarExecutionListener;
import mdt.exector.jar.JarInstanceExecutor;
import mdt.instance.AbstractInstanceManager;
import mdt.instance.AbstractMDTInstanceManagerBuilder;
import mdt.instance.InstanceDescriptor;
import mdt.instance.InstanceStatusChangeEvent;
import mdt.model.InternalException;
import mdt.model.instance.JarExecutionArguments;
import mdt.model.instance.MDTInstanceManagerException;
import mdt.model.instance.MDTInstanceStatus;


/**
 *
 * @author Kang-Woo Lee (ETRI)
 */
public class JarInstanceManager extends AbstractInstanceManager {
	private static final Logger s_logger = LoggerFactory.getLogger(JarInstanceManager.class);
	
	private final JarInstanceExecutor m_executor;
	private final JsonMapper m_mapper;
	
	public JarInstanceManager(JarInstanceManagerBuilder builder) {
		super(builder);
		setLogger(s_logger);
		
		Preconditions.checkNotNull(builder.m_exector, "JarInstanceExecutor was null");

		m_mapper = JsonMapper.builder().build();
		m_executor = builder.m_exector;
		m_executor.addExecutionListener(m_execListener);
	}
	
	public JarInstanceExecutor getInstanceExecutor() {
		return m_executor;
	}
	
	public JarExecutionArguments parseExecutionArguments(String argsJson) {
		try {
			return m_mapper.readValue(argsJson, JarExecutionArguments.class);
		}
		catch ( JsonProcessingException e ) {
			throw new InternalException("Failed to parse JarExecutionArguments string, cause=" + e);
		}
	}
	
	public String toExecutionArgumentsString(JarExecutionArguments args) {
		try {
			return m_mapper.writeValueAsString(args);
		}
		catch ( JsonProcessingException e ) {
			throw new InternalException("Failed to write JarExecutionArguments string, cause=" + e);
		}
	}
	
	@Override
	protected JarInstance toInstance(InstanceDescriptor descriptor) throws MDTInstanceManagerException {
		return new JarInstance(this, descriptor);
	}
	
	@Override
	protected InstanceDescriptor initializeInstance(InstanceDescriptor desc) {
		File instanceDir = getInstanceWorkspaceDir(desc.getId());
		try {
			File jarFile = new File(instanceDir, "fa3st-repository.jar");
			Files.createParentDirs(jarFile);
			
			File modelFile = new File(instanceDir, "model.json");
			File confFile = new File(instanceDir, "conf.json");
			
			JarExecutionArguments jargs = m_mapper.readValue(desc.getArguments(),
																	JarExecutionArguments.class);
			copyFileIfNotSame(new File(jargs.getJarFile()), jarFile);
			copyFileIfNotSame(new File(jargs.getModelFile()), modelFile);
			copyFileIfNotSame(new File(jargs.getConfigFile()), confFile);
			jargs = JarExecutionArguments.builder()
										.jarFile("fa3st-repository.jar")
										.modelFile("model.json")
										.configFile("conf.json")
										.build();
			desc.setArguments(m_mapper.writeValueAsString(jargs));
			
			return desc;
		}
		catch ( IOException e ) {
			throw new MDTInstanceManagerException("Failed to initialize MDTInstance: descriptor="
													+ desc + ", cause=" + e);
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
			switch ( status ) {
				case RUNNING:
					String svcEp = toServiceEndpoint(repoPort);
					Globals.EVENT_BUS.post(InstanceStatusChangeEvent.RUNNING(id, svcEp));
					break;
				case STOPPED:
					Globals.EVENT_BUS.post(InstanceStatusChangeEvent.STOPPED(id));
					break;
				case FAILED:
					Globals.EVENT_BUS.post(InstanceStatusChangeEvent.FAILED(id));
					break;
				case STARTING:
					Globals.EVENT_BUS.post(InstanceStatusChangeEvent.STARTING(id));
					break;
				case STOPPING:
					Globals.EVENT_BUS.post(InstanceStatusChangeEvent.STOPPING(id));
					break;
				default:
					throw new InternalException("JarExecutor throws an unknown status: " + status);
			}
		}
		
		@Override
		public void timeoutExpired() {
		}
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
