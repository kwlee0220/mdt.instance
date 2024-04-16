package mdt.instance;

import javax.annotation.Nullable;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;


/**
 *
 * @author Kang-Woo Lee (ETRI)
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class InstanceDescriptor {
	private String id;
	private String aasId;
	@Nullable private String aasIdShort;
}