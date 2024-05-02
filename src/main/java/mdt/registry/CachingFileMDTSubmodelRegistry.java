package mdt.registry;

import java.io.File;
import java.util.List;
import java.util.function.Function;

import org.eclipse.digitaltwin.aas4j.v3.dataformat.core.DeserializationException;
import org.eclipse.digitaltwin.aas4j.v3.dataformat.core.SerializationException;
import org.eclipse.digitaltwin.aas4j.v3.dataformat.json.JsonDeserializer;
import org.eclipse.digitaltwin.aas4j.v3.dataformat.json.JsonSerializer;
import org.eclipse.digitaltwin.aas4j.v3.model.SubmodelDescriptor;

import utils.stream.FStream;

import mdt.model.InternalException;
import mdt.model.registry.RegistryException;
import mdt.model.registry.ResourceAlreadyExistsException;
import mdt.model.registry.ResourceNotFoundException;


/**
 *
 * @author Kang-Woo Lee (ETRI)
 */
public class CachingFileMDTSubmodelRegistry implements SubmodelRegistryProvider {
    private final CachingFileBasedRegistry<SubmodelDescriptor> m_store;
    private final JsonSerializer m_jsonSer = new JsonSerializer();
	private final JsonDeserializer m_jsonDeser = new JsonDeserializer();
    
    @SuppressWarnings({ "rawtypes", "unchecked" })
	public CachingFileMDTSubmodelRegistry(File storeDir, int cacheSize) throws RegistryException {
    	m_store = new CachingFileBasedRegistry(storeDir, cacheSize, SubmodelDescriptor.class, m_deser);
    }
    
    public File getStoreDir() {
    	return m_store.getStoreDir();
    }

	@Override
	public SubmodelDescriptor getSubmodelDescriptorById(String submodelId) throws ResourceNotFoundException,
																					RegistryException {
		return m_store.getDescriptorById(submodelId).get();
	}

	@Override
	public String getJsonSubmodelDescriptorById(String submodelId) throws ResourceNotFoundException,
																			RegistryException {
		return m_store.getDescriptorById(submodelId).getJson();
	}

	@Override
	public List<SubmodelDescriptor> getAllSubmodelDescriptors() throws RegistryException {
		return FStream.from(m_store.getAllDescriptors())
						.map(LazyDescriptor::get)
						.toList();
	}

	@Override
	public List<SubmodelDescriptor> getAllSubmodelDescriptorsByIdShort(String idShort) throws RegistryException {
		return FStream.from(m_store.getAllDescriptorsByShortId(idShort))
						.map(LazyDescriptor::get)
						.toList();
	}

	@Override
	public SubmodelDescriptor addSubmodelDescriptor(SubmodelDescriptor descriptor)
		throws ResourceAlreadyExistsException, RegistryException {
		try {
			String json = m_jsonSer.write(descriptor);
			m_store.addDescriptor(descriptor.getId(), new LazyDescriptor<>(descriptor, json));
			return descriptor;
		}
		catch ( SerializationException e ) {
			throw new RegistryException("Failed to add SubmodelDescriptor: id=" + descriptor.getId()
										+ ", cause=" + e);
		}
	}

	@Override
	public SubmodelDescriptor updateSubmodelDescriptorById(SubmodelDescriptor descriptor)
		throws ResourceNotFoundException, RegistryException {
		try {
			String json = m_jsonSer.write(descriptor);
			m_store.updateDescriptor(descriptor.getId(), new LazyDescriptor<>(descriptor, json));
			return descriptor;
		}
		catch ( SerializationException e ) {
			throw new RegistryException("Failed to update SubmodelDescriptor: id=" + descriptor.getId()
										+ ", cause=" + e);
		}
	}

	@Override
	public void removeSubmodelDescriptorById(String submodelId) throws ResourceNotFoundException, RegistryException {
		m_store.removeDescriptor(submodelId);
	}
    
    private final Function<String,SubmodelDescriptor> m_deser = new Function<>() {
		@Override
		public SubmodelDescriptor apply(String json) {
			try {
				return m_jsonDeser.read(json, SubmodelDescriptor.class);
			}
			catch ( DeserializationException e ) {
				throw new InternalException("" + e);
			}
		}
    };
}
