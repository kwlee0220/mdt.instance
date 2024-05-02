package mdt.instance.k8s;

import java.io.Closeable;
import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Random;

import utils.Throwables;
import utils.func.Lazy;
import utils.func.Unchecked;
import utils.stream.FStream;

import io.fabric8.kubernetes.api.model.Node;
import io.fabric8.kubernetes.api.model.NodeAddress;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.Service;
import io.fabric8.kubernetes.api.model.ServiceBuilder;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.fabric8.kubernetes.api.model.apps.DeploymentBuilder;
import mdt.Globals;
import mdt.instance.AbstractInstance;
import mdt.instance.InstanceDescriptor;
import mdt.instance.InstanceStatusChangeEvent;
import mdt.model.instance.KubernetesExecutionArguments;
import mdt.model.instance.MDTInstance;
import mdt.model.instance.MDTInstanceManagerException;
import mdt.model.instance.MDTInstanceStatus;
import mdt.model.instance.StartResult;
import mdt.model.registry.RegistryException;


/**
 *
 * @author Kang-Woo Lee (ETRI)
 */
public class KubernetesInstance extends AbstractInstance implements MDTInstance, Closeable {
	public static final String NAMESPACE = "mdt-instance";
	
	private final Lazy<KubernetesRemote> m_kube = Lazy.of(this::newKubernetesRemote);
	private String m_workerHostname = null;
	
	KubernetesInstance(KubernetesInstanceManager manager, InstanceDescriptor desc) {
		super(manager, desc);
	}
	
	private KubernetesRemote newKubernetesRemote() {
		return ((KubernetesInstanceManager)m_manager).newKubernetesRemote();
	}

	@Override
	public void close() throws IOException {
		m_kube.ifLoadedOrThrow(KubernetesRemote::close);
	}

	@Override
	public String getServiceEndpoint() {
		Service service = m_kube.get().getService(NAMESPACE, getId());
		if ( service != null ) {
			int port = service.getSpec().getPorts().get(0).getNodePort();
			return toServiceEndpoint(port);
		}
		else {
			return null;
		}
	}

	@Override
	public MDTInstanceStatus getStatus() {
		Pod pod = m_kube.get().getPod(NAMESPACE, getId());
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
	public StartResult start() {
		InstanceDescriptor desc = getInstanceDescriptor();

		KubernetesRemote k8s = m_kube.get();
		Deployment deployment = null;
		try {
			KubernetesInstanceManager mgr = getInstanceManager();
			KubernetesExecutionArguments args = mgr.parseExecutionArguments(desc.getArguments());
			
			Globals.EVENT_BUS.post(InstanceStatusChangeEvent.STARTING(desc.getId()));
			
			deployment = buildDeploymentResource(args.getImageId());
			deployment = k8s.createDeployment(NAMESPACE, deployment);
			
			Service svc = buildServiceResource();

			m_workerHostname = selectWorkerHostname();
			int svcPort = k8s.createService(NAMESPACE, svc);
			String endpoint = toServiceEndpoint(svcPort);
			Globals.EVENT_BUS.post(InstanceStatusChangeEvent.RUNNING(desc.getId(), endpoint));
			
			return new StartResult(MDTInstanceStatus.RUNNING, endpoint);
		}
		catch ( RegistryException e ) {
			Unchecked.runOrIgnore(() -> k8s.deleteService(NAMESPACE, toServiceName(getId())));
			Unchecked.runOrIgnore(() -> k8s.deleteDeployment(NAMESPACE, toDeploymentName(getId())));
			Globals.EVENT_BUS.post(InstanceStatusChangeEvent.STOPPED(desc.getId()));
			
			throw new MDTInstanceManagerException("fails to update MDTInstance status, cause=" + e);
		}
		catch ( Exception e ) {
			Globals.EVENT_BUS.post(InstanceStatusChangeEvent.STOPPED(desc.getId()));
			
			Unchecked.acceptOrIgnore(k8s::deleteDeployment, deployment);
			Throwables.throwIfInstanceOf(e, MDTInstanceManagerException.class);
			throw new MDTInstanceManagerException("Failed to start MDTInstance: id=" + getId() + ", cause=" + e);
		}
	}

	@Override
	public void stop() {
		KubernetesRemote k8s = m_kube.get();

		Globals.EVENT_BUS.post(InstanceStatusChangeEvent.STOPPING(getId()));
		
		Unchecked.runOrIgnore(() -> k8s.deleteService(NAMESPACE, toServiceName(getId())));
		Unchecked.runOrIgnore(() -> k8s.deleteDeployment(NAMESPACE, toDeploymentName(getId())));
		m_workerHostname = null;

		Globals.EVENT_BUS.post(InstanceStatusChangeEvent.STOPPED(getId()));
	}

	@Override
	protected void remove() {
		// nothing to do
	}
	
	private String toServiceEndpoint(int svcPort) {
		if ( m_workerHostname == null ) {
			m_workerHostname = selectWorkerHostname();
		}
		return String.format("https://%s:%d/api/v3.0", m_workerHostname, svcPort);
	}
	
	private String selectWorkerHostname() {
		List<Node> workers = m_kube.get().getWorkerNodeAll();
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
											.withContainerPort(443)
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
							.withPort(443)
						.endPort()
				    .endSpec()
			    .build();
	}
}
