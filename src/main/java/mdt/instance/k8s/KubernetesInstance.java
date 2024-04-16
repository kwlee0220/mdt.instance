package mdt.instance.k8s;

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Random;

import utils.func.Unchecked;
import utils.stream.FStream;

import io.fabric8.kubernetes.api.model.Node;
import io.fabric8.kubernetes.api.model.NodeAddress;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.Service;
import io.fabric8.kubernetes.api.model.ServiceBuilder;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.fabric8.kubernetes.api.model.apps.DeploymentBuilder;
import mdt.instance.AbstractMDTInstance;
import mdt.model.instance.MDTInstance;
import mdt.model.instance.MDTInstanceManagerException;
import mdt.model.instance.MDTInstanceStatus;
import mdt.model.instance.StatusResult;
import mdt.model.registry.RegistryException;


/**
 *
 * @author Kang-Woo Lee (ETRI)
 */
public class KubernetesInstance extends AbstractMDTInstance implements MDTInstance {
	public static final String NAMESPACE = "mdt-instance";
	
	private final KubernetesRemote m_kube;
	private String m_workerHostname = null;
	
	KubernetesInstance(KubernetesInstanceManager manager, KubernetesInstanceDescriptor desc) {
		super(manager, desc);
		
		m_kube = manager.getKubernetesRemote();
	}

	@Override
	public String getServiceEndpoint() {
		Service service = m_kube.getService(NAMESPACE, getId());
		if ( service != null ) {
			int port = service.getSpec().getPorts().get(0).getNodePort();
			return String.format("http://%s:%d", m_workerHostname, port);
		}
		else {
			return null;
		}
	}

	@Override
	public MDTInstanceStatus getStatus() {
		Pod pod = m_kube.getPod(NAMESPACE, getId());
		if ( pod == null ) {
			return MDTInstanceStatus.STOPPED;
		}
		
		String phase = pod.getStatus().getPhase();
		switch ( phase ) {
			case "Pending":
				return MDTInstanceStatus.STARTING;
			case "Running":
				return MDTInstanceStatus.RUNNING;
			case "Succeeded":
				return MDTInstanceStatus.STOPPED;
			case "Failed":
				return MDTInstanceStatus.FAILED;
			case "Unknown":
				return MDTInstanceStatus.FAILED;
			default:
				throw new AssertionError();
		}
	}

	@Override
	public StatusResult start() {
		KubernetesInstanceDescriptor desc = getInstanceDescriptor();
		
		try ( KubernetesRemote k8s = m_kube; ) {
			Deployment deployment = buildDeploymentResource(desc.getArguments().getImageId());
			deployment = k8s.createDeployment(NAMESPACE, deployment);
			
			try {
				Service svc = buildServiceResource();
				
				int svcPort = k8s.createService(NAMESPACE, svc);
				m_workerHostname = selectWorkerHostname();
				String endpoint = String.format("http://%s:%d", m_workerHostname, svcPort);
				
				return new StatusResult(getId(), MDTInstanceStatus.RUNNING, endpoint);
			}
			catch ( RegistryException e ) {
				Unchecked.runOrIgnore(() -> k8s.deleteService(NAMESPACE, toServiceName(getId())));
				Unchecked.runOrIgnore(() -> k8s.deleteDeployment(NAMESPACE, toDeploymentName(getId())));
				
				throw new MDTInstanceManagerException("fails to update MDTInstance status, cause=" + e);
			}
			catch ( Exception e ) {
				Unchecked.acceptOrIgnore(k8s::deleteDeployment, deployment);
				throw e;
			}
		}
		catch ( IOException e ) {
			throw new MDTInstanceManagerException("fails to connect to Kubernetes, cause=" + e);
		}
	}

	@Override
	public StatusResult stop() {
		try ( KubernetesRemote k8s = m_kube ) {
			Unchecked.runOrIgnore(() -> k8s.deleteService(NAMESPACE, toServiceName(getId())));
			Unchecked.runOrIgnore(() -> k8s.deleteDeployment(NAMESPACE, toDeploymentName(getId())));
			
			return new StatusResult(getId(), MDTInstanceStatus.STOPPED, null);
		}
		catch ( IOException e ) {
			throw new MDTInstanceManagerException("fails to connect to Kubernetes, cause=" + e);
		}
	}

	@Override
	protected void remove() {
		// nothing to do
	}
	
	private String selectWorkerHostname() {
		List<Node> workers = m_kube.getWorkerNodeAll();
		int idx = new Random().nextInt(workers.size());
		
		List<NodeAddress> addresses = workers.get(idx).getStatus().getAddresses();
		return FStream.from(addresses)
						.findFirst(addr -> addr.getType().equals("Hostname"))
						.getOrElse(addresses.get(addresses.size()-1))
						.getAddress();
	}
	
	private static String toDeploymentName(String instanceId) {
		return instanceId;
	}
	
	private static String toServiceName(String instanceId) {
		return instanceId;
	}
	
	private static String toPodName(String instanceId) {
		return instanceId;
	}
	
	private static String toContainerName(String instanceId) {
		return String.format("container-%s", instanceId);
	}
	
	private Deployment buildDeploymentResource(String imageId) {
        return new DeploymentBuilder()
						.withNewMetadata()
							.withName(toDeploymentName(getId()))
						.endMetadata()
						.withNewSpec()
							.withReplicas(1)
							.withNewSelector()
								.addToMatchLabels("mdt-type", "instance")
								.addToMatchLabels("mdt-instance-id", getId())
							.endSelector()
							.withNewTemplate()
								.withNewMetadata()
									.withName(toPodName(getId()))
									.addToLabels("mdt-type", "instance")
									.addToLabels("mdt-instance-id", getId())
								.endMetadata()
								.withNewSpec()
									.addNewContainer()
										.withName(toContainerName(getId()))
										.withImage(imageId)
										.addNewPort()
											.withContainerPort(80)
										.endPort()
									.endContainer()
								.endSpec()
							.endTemplate()
						.endSpec()
					.build();
	}
	
	private Service buildServiceResource() {
		return new ServiceBuilder()
					.withNewMetadata()
						.withName(toServiceName(getId()))
					.endMetadata()
					.withNewSpec()
						.withType("NodePort")
						.withSelector(Collections.singletonMap("mdt-instance-id", getId()))
						.addNewPort()
							.withName("service-port")
							.withProtocol("TCP")
							.withPort(80)
						.endPort()
				    .endSpec()
			    .build();
	}
}
