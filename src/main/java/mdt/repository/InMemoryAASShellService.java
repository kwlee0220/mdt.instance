package mdt.repository;

import java.util.List;

import org.eclipse.digitaltwin.aas4j.v3.model.AssetAdministrationShell;
import org.eclipse.digitaltwin.aas4j.v3.model.AssetInformation;
import org.eclipse.digitaltwin.aas4j.v3.model.Reference;

import utils.stream.FStream;

import mdt.model.repository.AssetAdministrationShellRepository;


/**
 *
 * @author Kang-Woo Lee (ETRI)
 */
public class InMemoryAASShellService implements AssetAdministrationShellServiceProvider {
	private final AssetAdministrationShellRepository m_repository;
	private AssetAdministrationShell m_shell;
	private boolean m_dirty = false;
	
	public InMemoryAASShellService(AssetAdministrationShellRepository repo, AssetAdministrationShell shell) {
		m_repository = repo;
		m_shell = shell;
	}

	@Override
	public void close() throws Exception {
		if ( m_dirty ) {
			m_repository.updateAssetAdministrationShellById(m_shell);
			m_dirty = false;
		}
	}

	@Override
	public AssetAdministrationShell getAssetAdministrationShell() {
		return m_shell;
	}

	@Override
	public AssetAdministrationShell updateAssetAdministrationShell(AssetAdministrationShell aas) {
		m_shell = aas;
		m_dirty = true;
		return aas;
	}

	@Override
	public List<Reference> getAllSubmodelReferences() {
		return m_shell.getSubmodels();
	}

	@Override
	public Reference addSubmodelReference(Reference ref) {
		m_shell.getSubmodels().add(ref);
		m_dirty = true;
		return ref;
	}

	@Override
	public void deleteSubmodelReference(String submodelId) {
		List<Reference> updatedRefs = FStream.from(m_shell.getSubmodels())
											.filter(ref -> !matches(ref, submodelId))
											.toList();
		m_shell.setSubmodels(updatedRefs);
		m_dirty = true;
	}

	@Override
	public AssetInformation getAssetInformation() {
		return m_shell.getAssetInformation();
	}

	@Override
	public AssetInformation updateAssetInformation(AssetInformation assetInfo) {
		m_shell.setAssetInformation(assetInfo);
		m_dirty = true;
		return assetInfo;
	}

	private static boolean matches(Reference ref, String id) {
		return FStream.from(ref.getKeys())
						.exists(key -> id.equals(key.getValue()));
	}
}
