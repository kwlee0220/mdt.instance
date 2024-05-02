package mdt.instance.k8s;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.json.JsonMapper;

import mdt.instance.AbstractInstanceManager;
import mdt.instance.AbstractMDTInstanceManagerBuilder;
import mdt.instance.InstanceDescriptor;
import mdt.model.InternalException;
import mdt.model.instance.KubernetesExecutionArguments;
import mdt.model.instance.MDTInstanceManagerException;


/**
 *
 * @author Kang-Woo Lee (ETRI)
 */
public class KubernetesInstanceManager extends AbstractInstanceManager {
	private static final Logger s_logger = LoggerFactory.getLogger(KubernetesInstanceManager.class);
	
	private final JsonMapper m_mapper;
	
	public KubernetesInstanceManager(KubernetesInstanceManagerBuilder builder) {
		super(builder);
		
		m_mapper = JsonMapper.builder().build();
		setLogger(s_logger);
	}
	
	public KubernetesExecutionArguments parseExecutionArguments(String argsJson) {
		try {
			return m_mapper.readValue(argsJson, KubernetesExecutionArguments.class);
		}
		catch ( JsonProcessingException e ) {
			throw new InternalException("Failed to parse KubernetesExecutionArguments string, cause=" + e);
		}
	}
	
	public String toExecutionArgumentsString(KubernetesExecutionArguments args) {
		try {
			return m_mapper.writeValueAsString(args);
		}
		catch ( JsonProcessingException e ) {
			throw new InternalException("Failed to write KubernetesExecutionArguments string, cause=" + e);
		}
	}
	
	@Override
	protected KubernetesInstance toInstance(InstanceDescriptor descriptor) throws MDTInstanceManagerException {
		return new KubernetesInstance(this, descriptor);
	}
	
	@Override
	protected InstanceDescriptor initializeInstance(InstanceDescriptor desc) {
		return desc;
	}

	KubernetesRemote newKubernetesRemote() {
		return KubernetesRemote.connect();
	}

	public static KubernetesInstanceManagerBuilder builder() {
		return new KubernetesInstanceManagerBuilder();
	}
	public static class KubernetesInstanceManagerBuilder
		extends AbstractMDTInstanceManagerBuilder<KubernetesInstanceManagerBuilder, KubernetesInstanceManager> {
		@Override
		protected KubernetesInstanceManager internalBuild() {
			return new KubernetesInstanceManager(this);
		}
	}
}
