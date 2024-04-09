package mdt.exector.jar.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import mdt.model.instance.JarExecutionArguments;

/**
 *
 * @author Kang-Woo Lee (ETRI)
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class JarExecutionCommand {
	private String instanceId;
	private String aasId;
	private JarExecutionArguments arguments;
}
