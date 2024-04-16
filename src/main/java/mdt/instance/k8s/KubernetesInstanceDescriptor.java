package mdt.instance.k8s;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import mdt.instance.InstanceDescriptor;
import mdt.model.instance.KubernetesExecutionArguments;


/**
 *
 * @author Kang-Woo Lee (ETRI)
 */
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class KubernetesInstanceDescriptor extends InstanceDescriptor {
	private KubernetesExecutionArguments arguments;
	
	public KubernetesInstanceDescriptor(String id, String aasId, String aasIdShort,
										KubernetesExecutionArguments args) {
		super(id, aasId, aasIdShort);
		this.arguments = args;
	}
}
