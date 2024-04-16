package mdt.controller;

import java.io.File;

import lombok.Data;


/**
 *
 * @author Kang-Woo Lee (ETRI)
 */
@Data
public class MDTInstanceManagerConfiguration {
	private String type;
	private String repositoryEndpointFormat;
	private File workspaceDir;
}
