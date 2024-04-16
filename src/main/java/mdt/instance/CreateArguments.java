package mdt.instance;

import lombok.Builder;
import lombok.Getter;

/**
 *
 * @author Kang-Woo Lee (ETRI)
 */
@Getter
@Builder
public class CreateArguments {
	private final String id;
	private final String aasId;
	private final String aasIdShort;
	private final String argsString;
}
