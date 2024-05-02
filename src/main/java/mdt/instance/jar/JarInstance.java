package mdt.instance.jar;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import utils.func.Tuple;

import mdt.exector.jar.JarInstanceExecutor;
import mdt.instance.AbstractInstance;
import mdt.instance.InstanceDescriptor;
import mdt.model.instance.JarExecutionArguments;
import mdt.model.instance.MDTInstance;
import mdt.model.instance.MDTInstanceManagerException;
import mdt.model.instance.MDTInstanceStatus;
import mdt.model.instance.StartResult;


/**
 *
 * @author Kang-Woo Lee (ETRI)
 */
public class JarInstance extends AbstractInstance implements MDTInstance {
	private static final Logger s_logger = LoggerFactory.getLogger(JarInstance.class);

	JarInstance(JarInstanceManager manager, InstanceDescriptor desc) {
		super(manager, desc);
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
	public StartResult start() throws MDTInstanceManagerException {
		InstanceDescriptor desc = getInstanceDescriptor();
		
		JarInstanceManager mgr = getInstanceManager();
		JarExecutionArguments jargs = mgr.parseExecutionArguments(desc.getArguments());

		JarInstanceExecutor exector = getExecutor();
		if ( s_logger.isInfoEnabled() ) {
			s_logger.info("starting...: " + this);
		}
		Tuple<MDTInstanceStatus,Integer> result = exector.start(getId(), getAASId(), jargs);
		String svcEp = ( result._1 == MDTInstanceStatus.RUNNING ) ? toServiceEndpoint(result._2) : null;
		return new StartResult(result._1, svcEp);
	}

	@Override
	public void stop() {
		getExecutor().stop(getId());
	}
	
	@Override
	protected void remove() {
		stop();
	}
	
	@Override
	public String toString() {
		return String.format("JarInstance[id=%s, aas_id=%s, path=%s]", getId(), getAASId(), getWorkspaceDir());
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