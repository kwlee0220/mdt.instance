package mdt.instance.jar;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import mdt.instance.InstanceDescriptor;
import mdt.model.instance.JarExecutionArguments;


/**
 *
 * @author Kang-Woo Lee (ETRI)
 */
@Getter
@Setter
@NoArgsConstructor
@JsonInclude(Include.NON_NULL)
public class JarInstanceDescriptor extends InstanceDescriptor {
	private JarExecutionArguments arguments;
	
	public JarInstanceDescriptor(String id, String aasId, String aasIdShort, JarExecutionArguments args) {
		super(id, aasId, aasIdShort);
		this.arguments = args;
	}
}
