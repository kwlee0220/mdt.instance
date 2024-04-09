package mdt.instance.model;

import javax.annotation.Nullable;

import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;


/**
 *
 * @author Kang-Woo Lee (ETRI)
 */
@Getter
@Builder
@ToString
@EqualsAndHashCode(of = {"instanceId"})
public class MDTInstanceRecord {
	private final String instanceId;
	private final String aasId;
	@Nullable private final String aasIdShort;
	private final String arguments;
}