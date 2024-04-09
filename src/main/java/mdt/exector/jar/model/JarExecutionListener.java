package mdt.exector.jar.model;

import mdt.model.instance.StatusResult;

/**
 *
 * @author Kang-Woo Lee (ETRI)
 */
public interface JarExecutionListener {
	public void stausChanged(String id, StatusResult status);
	public void timeoutExpired();
}
