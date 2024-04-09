package mdt.exector.jar.model;

import java.io.File;
import java.io.IOException;
import java.lang.ProcessBuilder.Redirect;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;

import javax.annotation.Nullable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Value;

import com.google.common.collect.Maps;
import com.google.common.collect.Sets;

import utils.Polling;
import utils.Throwables;
import utils.async.Executions;
import utils.async.Guard;
import utils.func.CheckedSupplier;
import utils.func.KeyValue;
import utils.func.Unchecked;
import utils.io.LogTailer;
import utils.stream.FStream;

import mdt.client.InternalException;
import mdt.model.instance.JarExecutionArguments;
import mdt.model.instance.MDTInstanceStatus;
import mdt.model.instance.StatusResult;


/**
*
* @author Kang-Woo Lee (ETRI)
*/
public class JarInstanceExecutor implements InitializingBean {
	private static final Logger s_logger = LoggerFactory.getLogger(JarInstanceExecutor.class);
	
	@Value("file:${executor.workspaceDir}") private File m_workspaceDir;
	@Value("${executor.sampleInterval}") private Duration m_sampleInterval;
	@Value("${executor.timeout}") @Nullable private Duration m_timeout = null;
	
	@Value("${executor.repositoryEndpointFormat}") private String m_repositoryEndpointFormat;
	
	private final Guard m_guard = Guard.create();
	private final Map<String,ProcessDesc> m_runningInstances = Maps.newHashMap();
	private final Set<JarExecutionListener> m_listeners = Sets.newHashSet();

	private static class ProcessDesc {
		private final String m_id;
		private Process m_process;
		private MDTInstanceStatus m_status;
		private String m_serviceEndpoint;
		private final File m_stdoutLogFile;
		
		public ProcessDesc(String id, Process process, MDTInstanceStatus status,
							String serviceEndpoint, File stdoutLogFile) {
			this.m_id = id;
			this.m_process = process;
			this.m_status = status;
			this.m_serviceEndpoint = serviceEndpoint;
			this.m_stdoutLogFile = stdoutLogFile;
		}
		
		public StatusResult toStatusResult() {
			return new StatusResult(m_id, m_status, m_serviceEndpoint);
		}
		
		@Override
		public String toString() {
			return String.format("Process(id=%s, proc=%d, status=%s, endpoint=%s)",
									m_id, this.m_process.toHandle().pid(),
									this.m_status, this.m_serviceEndpoint);
		}
	}

	public JarInstanceExecutor() {
	}
	
	private JarInstanceExecutor(Builder builder) throws Exception {
		this.m_workspaceDir = builder.m_workspaceDir;
		this.m_sampleInterval = builder.m_sampleInterval;
		this.m_timeout = builder.m_timeout;
		this.m_repositoryEndpointFormat = builder.m_repositoryEndpointFormat;
		
		afterPropertiesSet();
	}

	@Override
	public void afterPropertiesSet() throws Exception {
		if ( this.m_repositoryEndpointFormat == null ) {
			try {
				String host = InetAddress.getLocalHost().getHostAddress();
				this.m_repositoryEndpointFormat = "https:" + host + ":%d/api/v3.0";
			}
			catch ( UnknownHostException e ) {
				throw new InternalException("" + e);
			}
		}
	}

	public StatusResult start(String id, String aasId, JarExecutionArguments args)
		throws MDTInstanceExecutorException {
    	ProcessDesc desc = m_runningInstances.get(id);
    	if ( desc != null ) {
	    	switch ( desc.m_status ) {
	    		case RUNNING:
	    		case STARTING:
	    			return desc.toStatusResult();
	    		default: break;
	    	}
    	}
    	
		ProcessBuilder builder = new ProcessBuilder("java", "-jar", args.getJarFile(),
													"-m", args.getModelFile(),
													"-c", args.getConfigFile());
		File logDir = new File("logs");
		if ( this.m_workspaceDir != null ) {
			builder = builder.directory(this.m_workspaceDir);
			logDir = new File(this.m_workspaceDir, "logs");
		}
		
		File stdoutLogFile = new File(logDir, id + "_stdout");
		builder.redirectOutput(Redirect.to(stdoutLogFile));
		builder.redirectError(Redirect.to(new File(logDir, id + "_stderr")));

		final ProcessDesc procDesc = new ProcessDesc(id, null, MDTInstanceStatus.STARTING,
														null, stdoutLogFile);
		m_guard.run(() -> m_runningInstances.put(id, procDesc));
		try {
			Process proc = builder.start();
			m_guard.run(() -> procDesc.m_process = proc);
			
			proc.onExit().whenCompleteAsync((completedProc, error) -> {
				if ( error == null ) {
					// m_runningInstances에 등록되지 않은 process들은
					// 모두 성공적으로 종료된 것으로 간주한다.
					m_guard.run(() -> {
						procDesc.m_status = MDTInstanceStatus.STOPPED;
						m_runningInstances.remove(id);
					});
			    	if ( s_logger.isInfoEnabled() ) {
			    		s_logger.info("stopped MDTInstance: {}", id);
			    	}
			    	notifyStatusChanged(procDesc);
				}
				else {
					m_guard.run(() -> procDesc.m_status = MDTInstanceStatus.FAILED);
			    	notifyStatusChanged(procDesc);
				}
			});

			Executions.runAsync(() -> waitWhileStarting(id, procDesc));
			return procDesc.toStatusResult();
		}
		catch ( IOException e ) {
			m_guard.run(() -> procDesc.m_status = MDTInstanceStatus.FAILED);
			return procDesc.toStatusResult();
		}
    }

    public StatusResult stop(final String instanceId) {
    	if ( s_logger.isInfoEnabled() ) {
    		s_logger.info("stopping MDTInstance: {}", instanceId);
    	}
    	
    	ProcessDesc procDesc = m_guard.get(() -> {
        	ProcessDesc desc = m_runningInstances.get(instanceId);
        	if ( desc != null ) {
        		switch ( desc.m_status ) {
        			case RUNNING:
        			case STARTING:
        				desc.m_serviceEndpoint = null;
                		desc.m_status = MDTInstanceStatus.STOPPING;

            			Executions.runAsync(() -> waitWhileStopping(instanceId, desc));
                		desc.m_process.destroy();
                		break;
                	default:
                		return null;
        		}
        	}
        	return desc;
    	});
    	
    	return new StatusResult(instanceId, procDesc.m_status, procDesc.m_serviceEndpoint);
    }

    public StatusResult getStatus(String instanceId) {
    	return m_guard.get(() -> {
    		ProcessDesc desc = m_runningInstances.get(instanceId);
        	if ( desc != null ) {
        		return desc.toStatusResult();
        	}
        	else {
        		return new StatusResult(instanceId, MDTInstanceStatus.STOPPED, null);
        	}
    	});
    }

	public List<StatusResult> getAllStatuses() throws MDTInstanceExecutorException {
    	return m_guard.get(() -> {
    		return FStream.from(m_runningInstances.values())
							.map(pdesc -> pdesc.toStatusResult())
							.toList();
    	});
	}
	
	public boolean addExecutionListener(JarExecutionListener listener) {
		return m_guard.get(() -> m_listeners.add(listener));
	}
	public boolean removeExecutionListener(JarExecutionListener listener) {
		return m_guard.get(() -> m_listeners.remove(listener));
	}
	
	public StatusResult waitWhileStarting(String instanceId) throws TimeoutException, InterruptedException {
		WhileStarting startingWaiter = new WhileStarting(instanceId);
		Polling polling = Polling.builder()
								.setPollingPredicate(startingWaiter)
								.setPollingInterval(Duration.ofSeconds(1))
								.setTimeout(Duration.ofMinutes(1))
								.build();
		try {
			polling.run();
			return startingWaiter.result;
		}
		catch ( ExecutionException e ) {
			Throwable cause = Throwables.unwrapThrowable(e);
			throw Throwables.toRuntimeException(cause);
		}
	}
	
	public StatusResult waitWhileStopping(String instanceId) throws TimeoutException, InterruptedException {
		WhileStopping stopWaiting = new WhileStopping(instanceId);
		Polling polling = Polling.builder()
								.setPollingPredicate(stopWaiting)
								.setPollingInterval(Duration.ofSeconds(1))
								.setTimeout(Duration.ofSeconds(30))
								.build();
		try {
			polling.run();
			return stopWaiting.result;
		}
		catch ( ExecutionException e ) {
			Throwable cause = Throwables.unwrapThrowable(e);
			throw Throwables.toRuntimeException(cause);
		}
	}
	
	private class WhileStarting implements CheckedSupplier<Boolean> {
		private final String instanceId;
		private StatusResult result;
		
		private WhileStarting(String instanceId) {
			this.instanceId = instanceId;
		}

		@Override
		public Boolean get() throws Throwable {
			ProcessDesc pdesc = m_runningInstances.get(this.instanceId);
			if ( pdesc == null ) {
				this.result = new StatusResult(this.instanceId, MDTInstanceStatus.STOPPED, null);
				return false;
			}
			else if ( pdesc.m_status == MDTInstanceStatus.STARTING ) {
				return true;
			}
			else {
				this.result = new StatusResult(this.instanceId, pdesc.m_status, pdesc.m_serviceEndpoint);
				return false;
			}
		}
	}
	
	private class WhileStopping implements CheckedSupplier<Boolean> {
		private final String instanceId;
		private StatusResult result;
		
		private WhileStopping(String instanceId) {
			this.instanceId = instanceId;
		}

		@Override
		public Boolean get() throws Throwable {
			ProcessDesc pdesc = m_runningInstances.get(this.instanceId);
			if ( pdesc == null ) {
				this.result = new StatusResult(this.instanceId, MDTInstanceStatus.STOPPED, null);
				return false;
			}
			else if ( pdesc.m_status == MDTInstanceStatus.STOPPING ) {
				return true;
			}
			else {
				this.result = new StatusResult(this.instanceId, pdesc.m_status, pdesc.m_serviceEndpoint);
				return false;
			}
		}
	}
	
	private StatusResult waitWhileStarting(final String instId, ProcessDesc procDesc) {
		LogTailer tailer = LogTailer.builder()
									.file(procDesc.m_stdoutLogFile)
									.startAtBeginning(false)
									.sampleInterval(this.m_sampleInterval)
									.timeout(this.m_timeout)
									.build();
		
		List<String> sentinels = Arrays.asList("HTTP endpoint available on port", "ERROR");
		SentinelFinder finder = new SentinelFinder(sentinels);
		tailer.addLogTailerListener(finder);
		
		try {
			tailer.run();
			
			final KeyValue<Integer,String> sentinel = finder.getSentinel();
			return m_guard.get(() -> {
				switch ( sentinel.key() ) {
					case 0:
						String[] parts = sentinel.value().split(" ");
						int repoPort = Integer.parseInt(parts[parts.length-1]);
				    	String svcEndpoint = (repoPort > 0)
				    						? String.format(this.m_repositoryEndpointFormat, repoPort) : null;
						procDesc.m_serviceEndpoint = svcEndpoint;
						procDesc.m_status = MDTInstanceStatus.RUNNING;
						
				    	if ( s_logger.isInfoEnabled() ) {
				    		s_logger.info("started MDTInstance: {}, port={}", instId, repoPort);
				    	}
				    	notifyStatusChanged(procDesc);
				    	
						return procDesc.toStatusResult();
					case 1:
				    	if ( s_logger.isInfoEnabled() ) {
				    		s_logger.info("failed to start an MDTInstance: {}", instId);
				    	}
						procDesc.m_status = MDTInstanceStatus.FAILED;

				    	notifyStatusChanged(procDesc);
				    	if ( s_logger.isInfoEnabled() ) {
				    		s_logger.info("failed to start MDTInstance: {}", instId);
				    	}
						return procDesc.toStatusResult();
					default:
						throw new AssertionError();
				}
			});
		}
		catch ( Exception e ) {
			m_guard.run(() -> procDesc.m_status = MDTInstanceStatus.FAILED);
			procDesc.m_process.destroyForcibly();
			
			StatusResult result = procDesc.toStatusResult();
	    	for ( JarExecutionListener listener: m_listeners ) {
	    		Unchecked.runOrIgnore(() -> listener.stausChanged(procDesc.m_id, procDesc.toStatusResult()));
	    	}
	    	if ( s_logger.isInfoEnabled() ) {
	    		s_logger.info("failed to start an MDTInstance: {}, cause={}", instId, e);
	    	}
	    	
			return result;
		}
	}
    
    private StatusResult waitWhileStopping(final String instId, ProcessDesc procDesc) {
		try {
			LogTailer tailer = LogTailer.builder()
										.file(procDesc.m_stdoutLogFile)
										.startAtBeginning(false)
										.sampleInterval(this.m_sampleInterval)
										.timeout(this.m_timeout)
										.build();
			
			List<String> sentinels = Arrays.asList("Goodbye!");
			SentinelFinder finder = new SentinelFinder(sentinels);
			tailer.addLogTailerListener(finder);

			try {
				tailer.run();
				
				finder.getSentinel();
		    	if ( s_logger.isInfoEnabled() ) {
		    		s_logger.info("stopped MDTInstance: {}", instId);
		    	}
				m_guard.run(() -> {
					procDesc.m_status = MDTInstanceStatus.STOPPED;
					m_runningInstances.remove(instId);
				});
				return new StatusResult(instId, MDTInstanceStatus.STOPPED, null);
			}
			catch ( Exception e ) {
				m_guard.run(() -> procDesc.m_status = MDTInstanceStatus.FAILED);
				procDesc.m_process.destroyForcibly();
		    	if ( s_logger.isInfoEnabled() ) {
		    		s_logger.info("failed to stop MDTInstance gracefully: id={}, cause={}", instId, e);
		    	}
				return new StatusResult(instId, MDTInstanceStatus.FAILED, null);
			}
		}
		catch ( Exception e ) {
			// 지정된 시간 내에 원하는 sentinel이 발견되지 못하거나 대기 중에 쓰레드가 종료된 경우.
			m_guard.run(() -> {
				procDesc.m_status = MDTInstanceStatus.STOPPED;
				m_runningInstances.remove(instId);
			});
			procDesc.m_process.destroyForcibly();
			return new StatusResult(instId, MDTInstanceStatus.STOPPED, null);
		}
    }
	
	public static Builder builder() {
		return new Builder();
	}
	public static class Builder {
		private File m_workspaceDir;
		private Duration m_sampleInterval;
		private Duration m_timeout;
		private String m_repositoryEndpointFormat;
		
		public JarInstanceExecutor build() throws Exception {
			return new JarInstanceExecutor(this);
		}
		
		public Builder workspaceDir(File dir) {
			this.m_workspaceDir = dir;
			return this;
		}
		
		public Builder sampleInterval(Duration interval) {
			this.m_sampleInterval = interval;
			return this;
		}
		
		public Builder timeout(@Nullable Duration timeout) {
			this.m_timeout = timeout;
			return this;
		}
		
		public Builder repositoryEndpointFormat(String format) {
			this.m_repositoryEndpointFormat = format;
			return this;
		}
	}
	
	private void notifyStatusChanged(ProcessDesc pdesc) {
    	for ( JarExecutionListener listener: m_listeners ) {
    		Unchecked.runOrIgnore(() -> listener.stausChanged(pdesc.m_id, pdesc.toStatusResult()));
    	}
	}
}
