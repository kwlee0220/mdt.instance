package mdt.instance.jar;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Preconditions;

import utils.func.Tuple;

import mdt.exector.jar.JarInstanceExecutor;
import mdt.instance.FileBasedInstance;
import mdt.model.instance.MDTInstance;
import mdt.model.instance.MDTInstanceManagerException;
import mdt.model.instance.MDTInstanceStatus;
import mdt.model.instance.StatusResult;


/**
 *
 * @author Kang-Woo Lee (ETRI)
 */
public class JarInstance extends FileBasedInstance<JarInstanceDescriptor> implements MDTInstance {
	private static final Logger s_logger = LoggerFactory.getLogger(JarInstance.class);

	JarInstance(JarInstanceManager manager, JarInstanceDescriptor desc) {
		super(manager, desc);
		Preconditions.checkArgument(desc instanceof JarInstanceDescriptor);
	}

	@Override
	public MDTInstanceStatus getStatus() {
		return getExecutor().getStatus(getId())._1;
	}

	@Override
	public String getServiceEndpoint() {
		Tuple<MDTInstanceStatus,Integer> result = getExecutor().getStatus(getId());
		return toServiceEndpoint(result._2);
	}

	@Override
	public StatusResult start() throws MDTInstanceManagerException {
		JarInstanceDescriptor desc = getInstanceDescriptor();

		Tuple<MDTInstanceStatus,Integer> result = getExecutor().start(getId(), getAASId(), desc.getArguments());
		if ( s_logger.isInfoEnabled() ) {
			s_logger.info("starting...: " + this + ", status=" + result._1);
		}
		return new StatusResult(getId(), result._1, toServiceEndpoint(result._2));
	}

	@Override
	public StatusResult stop() {
		Tuple<MDTInstanceStatus,Integer> result =  getExecutor().stop(getId());
		if ( s_logger.isInfoEnabled() ) {
			s_logger.info("stopping...: " + this + ", status=" + result._1);
		}
		return new StatusResult(getId(), result._1, toServiceEndpoint(result._2));
	}

	@Override
	protected void remove() {
		// nothing to do!
	}
	
	@Override
	public String toString() {
		return String.format("JarInstance(id=%s, aas_id=%s)", getId(), getAASId());
	}
	
	private JarInstanceExecutor getExecutor() {
		JarInstanceManager mgr = getInstanceManager();
		return mgr.getInstanceExecutor();
	}
	
	private String toServiceEndpoint(int repoPort) {
		if ( repoPort > 0 ) {
			return String.format(getInstanceManager().getRepositoryEndpointFormat(), repoPort);
		}
		else {
			return null;
		}
	}
}