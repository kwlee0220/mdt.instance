package mdt.exector.jar;

import java.io.File;
import java.io.IOException;
import java.lang.ProcessBuilder.Redirect;
import java.nio.file.Files;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.annotation.Nullable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Preconditions;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;

import utils.async.Executions;
import utils.async.Guard;
import utils.func.KeyValue;
import utils.func.Tuple;
import utils.func.Unchecked;
import utils.io.LogTailer;
import utils.stream.FStream;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import mdt.instance.InstanceStatusChangeEvent;
import mdt.model.instance.JarExecutionArguments;
import mdt.model.instance.MDTInstanceManagerException;
import mdt.model.instance.MDTInstanceStatus;


/**
*
* @author Kang-Woo Lee (ETRI)
*/
public class JarInstanceExecutor {
	private static final Logger s_logger = LoggerFactory.getLogger(JarInstanceExecutor.class);
	
	private final File m_workspaceDir;
	private final Duration m_sampleInterval;
	@Nullable private final Duration m_startTimeout;
	
	private final Guard m_guard = Guard.create();
	private final Map<String,ProcessDesc> m_runningInstances = Maps.newHashMap();
	private final Set<JarExecutionListener> m_listeners = Sets.newConcurrentHashSet();

	private static class ProcessDesc {
		private final String m_id;
		private Process m_process;
		private MDTInstanceStatus m_status;
		private int m_repoPort = -1;
		private final File m_stdoutLogFile;
		
		public ProcessDesc(String id, Process process, MDTInstanceStatus status,
							String serviceEndpoint, File stdoutLogFile) {
			this.m_id = id;
			this.m_process = process;
			this.m_status = status;
			this.m_stdoutLogFile = stdoutLogFile;
		}
		
		public Tuple<MDTInstanceStatus,Integer> toResult() {
			return Tuple.of(m_status, m_repoPort);
		}
		
		@Override
		public String toString() {
			return String.format("Process(id=%s, proc=%d, status=%s, repo_port=%s)",
									m_id, m_process.toHandle().pid(), m_status, m_repoPort);
		}
	}
	
	private JarInstanceExecutor(Builder builder) throws MDTInstanceManagerException {
		Preconditions.checkNotNull(builder.getWorkspaceDir());
		
		this.m_workspaceDir = builder.getWorkspaceDir();
		this.m_sampleInterval = builder.getSampleInterval();
		this.m_startTimeout = builder.getStartTimeout();
	}
	
	public Tuple<MDTInstanceStatus,Integer> start(String id, String aasId, JarExecutionArguments args)
		throws MDTInstanceExecutorException {
		return m_guard.get(() -> startInGuard(id, aasId, args));
	}
	
	private Tuple<MDTInstanceStatus,Integer> startInGuard(String id, String aasId, JarExecutionArguments args)
		throws MDTInstanceExecutorException {
		ProcessDesc desc = m_runningInstances.get(id);
    	if ( desc != null ) {
	    	switch ( desc.m_status ) {
	    		case RUNNING:
	    		case STARTING:
	    			return Tuple.of(desc.m_status, desc.m_repoPort);
	    		default: break;
	    	}
    	}
    	
    	File jobDir = new File(m_workspaceDir, id);
		File logDir = new File(jobDir, "logs");
    	
		ProcessBuilder builder = new ProcessBuilder("java", "-jar", args.getJarFile(),
													"-m", args.getModelFile(),
													"-c", args.getConfigFile());
		builder.directory(jobDir);
		
		File stdoutLogFile = new File(logDir, id + "_stdout");
		builder.redirectOutput(Redirect.to(stdoutLogFile));
		builder.redirectError(new File(logDir, id + "_stderr"));

		ProcessDesc procDesc = new ProcessDesc(id, null, MDTInstanceStatus.STARTING, null, stdoutLogFile);
		m_runningInstances.put(id, procDesc);
		
		try {
			Files.createDirectories(logDir.toPath());
			notifyStatusChanged(procDesc);
			
			procDesc.m_process = builder.start();
			procDesc.m_process.onExit()
								.whenCompleteAsync((proc, error) -> onProcessTerminated(procDesc, error));
			Executions.runAsync(() -> pollingServicePort(id, procDesc));
			
			return Tuple.of(procDesc.m_status, procDesc.m_repoPort);
		}
		catch ( IOException e ) {
			procDesc.m_status = MDTInstanceStatus.FAILED;
			if ( s_logger.isWarnEnabled() ) {
				s_logger.warn("failed to start jar application: cause=" + e);
			}
			notifyStatusChanged(procDesc);
			
			return Tuple.of(procDesc.m_status, procDesc.m_repoPort);
		}
	}
	
	public Tuple<MDTInstanceStatus,Integer> waitWhileStarting(String id) throws InterruptedException {
		return m_guard.getOrThrow(() -> {
			while ( true ) {
				ProcessDesc procDesc = m_runningInstances.get(id);
				if ( procDesc == null ) {
					return Tuple.of(MDTInstanceStatus.STOPPING, -1);
				}
				switch ( procDesc.m_status ) {
					case RUNNING:
					case STOPPED:
					case STOPPING:
					case REMOVED:
						return Tuple.of(procDesc.m_status, procDesc.m_repoPort);
					default:
						m_guard.await();
				}
			}
		});
	}
	
	private void onProcessTerminated(ProcessDesc procDesc, Throwable error) {
		if ( error == null ) {
			// m_runningInstances에 등록되지 않은 process들은
			// 모두 성공적으로 종료된 것으로 간주한다.
			m_guard.run(() -> {
				procDesc.m_status = MDTInstanceStatus.STOPPED;
				m_runningInstances.remove(procDesc.m_id);
			});
	    	if ( s_logger.isInfoEnabled() ) {
	    		s_logger.info("stopped MDTInstance: {}", procDesc.m_id);
	    	}
	    	notifyStatusChanged(procDesc);
		}
		else {
			m_guard.run(() -> procDesc.m_status = MDTInstanceStatus.FAILED);
	    	notifyStatusChanged(procDesc);
		}
	}

    public Tuple<MDTInstanceStatus,Integer> stop(final String instanceId) {
    	ProcessDesc procDesc = m_guard.get(() -> {
        	ProcessDesc desc = m_runningInstances.get(instanceId);
        	if ( desc != null ) {
        		switch ( desc.m_status ) {
        			case RUNNING:
        			case STARTING:
                    	if ( s_logger.isInfoEnabled() ) {
                    		s_logger.info("stopping MDTInstance: {}", instanceId);
                    	}
        				desc.m_repoPort = -1;
                		desc.m_status = MDTInstanceStatus.STOPPING;
                		notifyStatusChanged(desc);
                		
//            			Executions.runAsync(() -> waitWhileStopping(instanceId, desc));
                		desc.m_process.destroy();
                		break;
                	default:
                		return null;
        		}
        	}
        	return desc;
    	});
    	
    	return (procDesc != null) ? procDesc.toResult() : null;
    }

    public Tuple<MDTInstanceStatus,Integer> getStatus(String instanceId) {
    	return m_guard.get(() -> {
    		ProcessDesc desc = m_runningInstances.get(instanceId);
        	if ( desc != null ) {
        		return desc.toResult();
        	}
        	else {
        		return Tuple.of(MDTInstanceStatus.STOPPED, -1);
        	}
    	});
    }

	public List<Tuple<MDTInstanceStatus,Integer>> getAllStatuses() throws MDTInstanceExecutorException {
    	return m_guard.get(() -> {
    		return FStream.from(m_runningInstances.values())
							.map(pdesc -> pdesc.toResult())
							.toList();
    	});
	}
	
	public boolean addExecutionListener(JarExecutionListener listener) {
		return m_guard.get(() -> m_listeners.add(listener));
	}
	public boolean removeExecutionListener(JarExecutionListener listener) {
		return m_guard.get(() -> m_listeners.remove(listener));
	}
	
	private Tuple<MDTInstanceStatus,Integer> pollingServicePort(final String instId, ProcessDesc procDesc) {
		LogTailer tailer = LogTailer.builder()
									.file(procDesc.m_stdoutLogFile)
									.startAtBeginning(false)
									.sampleInterval(this.m_sampleInterval)
									.timeout(this.m_startTimeout)
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
						procDesc.m_repoPort = Integer.parseInt(parts[parts.length-1]);
						procDesc.m_status = MDTInstanceStatus.RUNNING;
						
				    	if ( s_logger.isInfoEnabled() ) {
				    		s_logger.info("started MDTInstance: {}, port={}", instId, procDesc.m_repoPort);
				    	}
				    	m_guard.signalAll();
				    	notifyStatusChanged(procDesc);
				    	
						return procDesc.toResult();
					case 1:
				    	if ( s_logger.isInfoEnabled() ) {
				    		s_logger.info("failed to start an MDTInstance: {}", instId);
				    	}
						procDesc.m_status = MDTInstanceStatus.FAILED;
				    	m_guard.signalAll();
				    	notifyStatusChanged(procDesc);
				    	
						return procDesc.toResult();
					default:
						throw new AssertionError();
				}
			});
		}
		catch ( Exception e ) {
			m_guard.run(() -> procDesc.m_status = MDTInstanceStatus.FAILED);
			procDesc.m_process.destroyForcibly();
			
	    	if ( s_logger.isInfoEnabled() ) {
	    		s_logger.info("failed to start an MDTInstance: {}, cause={}", instId, e);
	    	}
	    	notifyStatusChanged(procDesc);
	    	
			return Tuple.of(MDTInstanceStatus.FAILED, -1);
		}
	}
    
    private InstanceStatusChangeEvent waitWhileStopping(final String instId, ProcessDesc procDesc) {
		try {
			LogTailer tailer = LogTailer.builder()
										.file(procDesc.m_stdoutLogFile)
										.startAtBeginning(false)
										.sampleInterval(this.m_sampleInterval)
										.timeout(this.m_startTimeout)
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
				return InstanceStatusChangeEvent.STOPPED(instId);
			}
			catch ( Exception e ) {
				m_guard.run(() -> procDesc.m_status = MDTInstanceStatus.FAILED);
				procDesc.m_process.destroyForcibly();
		    	if ( s_logger.isInfoEnabled() ) {
		    		s_logger.info("failed to stop MDTInstance gracefully: id={}, cause={}", instId, e);
		    	}
				return InstanceStatusChangeEvent.FAILED(instId);
			}
		}
		catch ( Exception e ) {
			// 지정된 시간 내에 원하는 sentinel이 발견되지 못하거나 대기 중에 쓰레드가 종료된 경우.
			m_guard.run(() -> {
				procDesc.m_status = MDTInstanceStatus.STOPPED;
				m_runningInstances.remove(instId);
			});
			procDesc.m_process.destroyForcibly();
			return InstanceStatusChangeEvent.STOPPED(instId);
		}
    }
	
	private void notifyStatusChanged(ProcessDesc pdesc) {
		Tuple<MDTInstanceStatus, Integer> result = pdesc.toResult();
    	for ( JarExecutionListener listener: m_listeners ) {
    		Unchecked.runOrIgnore(() -> listener.stausChanged(pdesc.m_id, result._1, result._2));
    	}
	}
	
	public static Builder builder() {
		return new Builder();
	}
	@Data
	@lombok.Builder
	@NoArgsConstructor
	@AllArgsConstructor
	public static class Builder {
		private File workspaceDir;
		private Duration sampleInterval;
		private Duration startTimeout;
		
		public JarInstanceExecutor build() {
			return new JarInstanceExecutor(this);
		}
	}
}
