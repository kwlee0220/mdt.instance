package mdt.instance;

import org.eclipse.digitaltwin.aas4j.v3.model.Environment;

import mdt.model.ServiceFactory;
import mdt.model.instance.MDTInstance;
import mdt.model.instance.MDTInstanceManager;
import mdt.model.instance.MDTInstanceManagerException;

/**
 *
 * @author Kang-Woo Lee (ETRI)
 */
public interface MDTInstanceManagerProvider extends MDTInstanceManager {
	public ServiceFactory getServiceFactory();
	
	public MDTInstance addInstance(String id, Environment env, Object arguments)
		throws MDTInstanceManagerException;
}
