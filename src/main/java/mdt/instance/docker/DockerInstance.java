package mdt.instance.docker;

import java.util.List;

import org.mandas.docker.client.DockerClient;
import org.mandas.docker.client.DockerClient.RemoveContainerParam;
import org.mandas.docker.client.exceptions.ContainerNotFoundException;
import org.mandas.docker.client.exceptions.DockerException;
import org.mandas.docker.client.messages.ContainerInfo;
import org.mandas.docker.client.messages.PortBinding;

import com.google.common.base.Preconditions;

import mdt.client.InternalException;
import mdt.instance.FileBasedInstance;
import mdt.model.instance.MDTInstanceManagerException;
import mdt.model.instance.MDTInstanceStatus;
import mdt.model.instance.StatusResult;

/**
 *
 * @author Kang-Woo Lee (ETRI)
 */
public class DockerInstance extends FileBasedInstance<DockerInstanceDescriptor> {
	@SuppressWarnings("unused")
	private static final String FA3ST_IMAGE_PATH = "mdt/faaast-service";
	private static final int SECONDS_TO_WAIT_BEFORE_KILLING = 30;
	
	DockerInstance(DockerInstanceManager manager, DockerInstanceDescriptor desc) {
		super(manager, desc);
	}
	
	@Override
	public MDTInstanceStatus getStatus() {
		DockerInstanceDescriptor desc = getInstanceDescriptor();
		if ( desc.getContainerId() == null ) {
			return MDTInstanceStatus.STOPPED;
		}
		
		try ( DockerClient docker = newDockerClient() ) {
			ContainerInfo info = docker.inspectContainer(desc.getContainerId());
			if ( info.state().running() ) {
				return MDTInstanceStatus.RUNNING;
			}
			else if ( info.state().error().length() > 0 ) {
				return MDTInstanceStatus.FAILED;
			}
			else {
				return MDTInstanceStatus.STOPPED;
			}
		}
		catch ( ContainerNotFoundException e ) {
			return MDTInstanceStatus.STOPPED;
		}
		catch ( InterruptedException | DockerException e ) {
			throw new MDTInstanceManagerException("" + e);
		}
	}

	@Override
	public String getServiceEndpoint() {
		DockerInstanceDescriptor desc = getInstanceDescriptor();
		if ( desc.getContainerId() == null ) {
			return null;
		}
		
		try ( DockerClient docker = newDockerClient() ) {
			ContainerInfo info = docker.inspectContainer(desc.getContainerId());
			if ( info.state().running() ) {
				int repoPort = getRepositoryPort(info);
				return getInstanceManager().toServiceEndpoint(repoPort);
			}
			else {
				return null;
			}
		}
		catch ( ContainerNotFoundException e ) {
			return null;
		}
		catch ( InterruptedException | DockerException e ) {
			throw new MDTInstanceManagerException("" + e);
		}
	}

	@Override
	public StatusResult start() {
		DockerInstanceDescriptor desc = getInstanceDescriptor();
		Preconditions.checkState(desc.getContainerId() != null, "No Docker Container has been assigned: id=" + getId());
		
		try ( DockerClient docker = newDockerClient() ) {
			docker.startContainer(desc.getContainerId());
			
			ContainerInfo info = docker.inspectContainer(desc.getContainerId());
			int repoPort = getRepositoryPort(info);
			
			String svcEndpoint = getInstanceManager().toServiceEndpoint(repoPort);
			StatusResult result = new StatusResult(getId(), MDTInstanceStatus.RUNNING, svcEndpoint);
			getInstanceManager().instanceStatusChanged(result);
			
			return result;
		}
		catch ( Exception e ) {
			throw new MDTInstanceManagerException("Failed to start MDTInstance: id=" + getId() + ", cause=" + e);
		}
	}
	
	@Override
	public StatusResult stop() {
		DockerInstanceDescriptor desc = getInstanceDescriptor();
		if ( desc.getContainerId() == null ) {
			return new StatusResult(getId(), MDTInstanceStatus.STOPPED, null);
		}
		
		try ( DockerClient docker = newDockerClient() ) {
			docker.stopContainer(desc.getContainerId(), SECONDS_TO_WAIT_BEFORE_KILLING);

			StatusResult result = new StatusResult(getId(), MDTInstanceStatus.STOPPED, null);
			getInstanceManager().instanceStatusChanged(result);
			
			return result;
		}
		catch ( ContainerNotFoundException e ) {
			return new StatusResult(getId(), MDTInstanceStatus.STOPPED, null);
		}
		catch ( InterruptedException | DockerException e ) {
			throw new MDTInstanceManagerException("Failed to stop the MDTInstance: id=" + getId()
													+ ", cause=" + e);
		}
	}
	
	protected void remove() {
		DockerInstanceDescriptor desc = getInstanceDescriptor();
		if ( desc.getContainerId() == null ) {
			return;
		}
		
		try ( DockerClient docker = newDockerClient() ) {
			docker.removeContainer(desc.getContainerId(), RemoveContainerParam.forceKill());
		}
		catch ( ContainerNotFoundException expected ) { }
		catch ( Exception e ) {
			throw new MDTInstanceManagerException("" + e);
		}
	}
	
	private DockerClient newDockerClient() {
		DockerInstanceManager mgr = getInstanceManager();
		return mgr.newDockerClient();
	}
	
	private int getRepositoryPort(ContainerInfo info) {
		List<PortBinding> hostPorts = info.networkSettings().ports().get("443/tcp");
		if ( hostPorts == null || hostPorts.size() == 0 ) {
			throw new InternalException("Cannot find external port");
		}
		return Integer.parseInt(hostPorts.get(0).hostPort());
	}
}
