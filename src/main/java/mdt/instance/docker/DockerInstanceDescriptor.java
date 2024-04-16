package mdt.instance.docker;

import javax.annotation.Nullable;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import mdt.instance.InstanceDescriptor;
import mdt.model.instance.DockerExecutionArguments;


/**
 *
 * @author Kang-Woo Lee (ETRI)
 */
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@JsonInclude(Include.NON_NULL)
public class DockerInstanceDescriptor extends InstanceDescriptor {
	@Nullable private String containerId;
	private DockerExecutionArguments arguments;
	
	public DockerInstanceDescriptor(String id, String aasId, String aasIdShort, DockerExecutionArguments args) {
		super(id, aasId, aasIdShort);
		this.arguments = args;
	}
}
