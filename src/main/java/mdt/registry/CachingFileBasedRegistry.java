package mdt.registry;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutionException;

import org.eclipse.digitaltwin.aas4j.v3.dataformat.core.DeserializationException;
import org.eclipse.digitaltwin.aas4j.v3.dataformat.core.SerializationException;
import org.eclipse.digitaltwin.aas4j.v3.dataformat.json.JsonDeserializer;
import org.eclipse.digitaltwin.aas4j.v3.dataformat.json.JsonSerializer;

import com.google.common.base.Preconditions;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.common.collect.Lists;

import utils.Throwables;
import utils.fostore.CachingFileObjectStore;
import utils.fostore.DefaultFileObjectStore;
import utils.fostore.FileObjectHandler;
import utils.fostore.FileObjectStore;
import utils.stream.FStream;

import mdt.model.registry.RegistryException;
import mdt.model.registry.ResourceAlreadyExistsException;
import mdt.model.registry.ResourceNotFoundException;


/**
 *
 * @author Kang-Woo Lee (ETRI)
 */
public class CachingFileBasedRegistry<T> {
	private final String m_resourceName;
	private final CachingFileObjectStore<String, T> m_store;
	private final Method m_getIdShort;
	private final int m_cacheSize;
	
	public CachingFileBasedRegistry(File storeDir, int cacheSize, Class<T> descCls)
		throws RegistryException {
		try {
			m_resourceName = descCls.getSimpleName();
			
			m_cacheSize = cacheSize;
			DescriptorHandler<T> descHandler = new DescriptorHandler<>(storeDir, descCls);
			FileObjectStore<String,T> baseStore = new DefaultFileObjectStore<>(storeDir, descHandler);
			LoadingCache<String, T> cache = CacheBuilder.newBuilder()
														.maximumSize(cacheSize)
														.build(new DescriptorCacheLoader<>(baseStore));
			m_store = new CachingFileObjectStore<>(baseStore, cache);
			if ( !storeDir.exists() ) {
				Files.createDirectories(storeDir.toPath());
			}
			
			Method method;
			try {
				method = descCls.getDeclaredMethod("getIdShort");
			}
			catch ( Exception expected ) {
				method = null;
			}
			m_getIdShort = method;
		}
		catch ( IOException e ) {
			throw new RegistryException("" + e);
		}
	}
	
	public File getStoreDir() {
		return m_store.getRootDir();
	}

    public List<String> getAllDescriptorIds() throws RegistryException {
    	try {
        	return Lists.newArrayList(m_store.getFileObjectKeyAll());
		}
		catch ( IOException e ) {
			throw new RegistryException("" + e);
		}
    }

	public List<T> getAllDescriptors() throws RegistryException {
		try {
			return m_store.getFileObjectAll();
		}
		catch ( IOException e ) {
			throw new RegistryException("" + e);
		}
		catch ( ExecutionException e ) {
			Throwable cause = Throwables.unwrapThrowable(e);
			throw new RegistryException("" + cause);
		}
	}

	public List<T> getAllDescriptorsByShortId(String idShort) throws RegistryException {
		Preconditions.checkNotNull(idShort, m_resourceName + " idShort");
		
		try {
			FStream<T> stream = FStream.from(m_store.getFileObjectAll());
			if ( idShort != null ) {
				stream = stream.filter(desc -> filterByShortId(desc, idShort));
			}
			return stream.toList();
		}
		catch ( IOException e ) {
			throw new RegistryException("" + e);
		}
		catch ( ExecutionException e ) {
			Throwable cause = Throwables.unwrapThrowable(e);
			throw new RegistryException("" + cause);
		}
	}

	public T getDescriptorById(String id) throws ResourceNotFoundException, RegistryException {
		Preconditions.checkNotNull(id, m_resourceName + " id");
		
		try {
			Optional<T> resource = m_store.get(id);
			if ( resource.isEmpty() ) {
				throw new ResourceNotFoundException(m_resourceName + " id: " + id);
			}
			
			return resource.get();
		}
		catch ( IOException e ) {
			throw new RegistryException("" + e);
		}
		catch ( ExecutionException e ) {
			Throwable cause = Throwables.unwrapThrowable(e);
			throw new RegistryException("" + cause);
		}
	}

	public T addDescriptor(String id, T descriptor) throws ResourceAlreadyExistsException, RegistryException {
		try {
			Preconditions.checkNotNull(descriptor);
			Preconditions.checkNotNull(id, m_resourceName + " id");
			
			Optional<File> file = m_store.insert(id, descriptor);
			if ( file.isEmpty() ) {
				throw new ResourceAlreadyExistsException(m_resourceName + " id: " + id);
			}
			return descriptor;
		}
		catch ( IOException e ) {
			throw new RegistryException("" + e);
		}
		catch ( ExecutionException e ) {
			Throwable cause = Throwables.unwrapThrowable(e);
			throw new RegistryException("" + cause);
		}
	}

	public void removeDescriptor(String id) throws ResourceNotFoundException, RegistryException {
		Preconditions.checkNotNull(id, m_resourceName + " id");
		
    	try {
    		boolean done = m_store.remove(id);
    		if ( !done ) {
				throw new ResourceNotFoundException(m_resourceName + " id: " + id);
    		}
		}
		catch ( IOException e ) {
			throw new RegistryException("" + e);
		}
	}

	public T updateDescriptor(String id, T descriptor) throws ResourceNotFoundException, RegistryException {
		removeDescriptor(id);
		return addDescriptor(id, descriptor);
	}
	
	@Override
	public String toString() {
		return String.format("resource=%s, store=%s, cache_size=%d",
								m_resourceName, m_store.getRootDir(), m_cacheSize);
	}
	
	private static final class DescriptorHandler<T> implements FileObjectHandler<String, T> {
		private final File m_rootDir;
		private final Class<T> m_descCls;
		private final JsonSerializer m_ser;
		private final JsonDeserializer m_deser;
		
		DescriptorHandler(File rootDir, Class<T> descCls) {
			m_rootDir = rootDir;
			m_descCls = descCls;
			m_ser = new JsonSerializer();
			m_deser = new JsonDeserializer();
		}
		
		@Override
		public T readFileObject(File file) throws IOException, ExecutionException {
    		try ( FileInputStream fis = new FileInputStream(file) ) {
    			return m_deser.read(fis, m_descCls);
    		}
			catch ( DeserializationException e ) {
				throw new ExecutionException(e);
			}
		}

		@Override
		public void writeFileObject(T obj, File file) throws IOException, ExecutionException {
			try ( FileOutputStream fos = new FileOutputStream(file) ) {
				m_ser.write(fos, obj);
			}
			catch ( SerializationException e ) {
				throw new ExecutionException(e);
			}
		}

		@Override
		public File toFile(String key) {
			String encodedName = Base64.getEncoder().encodeToString(key.getBytes());
			return new File(m_rootDir, encodedName);
		}

		@Override
		public String toFileObjectKey(File file) {
			return new String(Base64.getDecoder().decode(file.getName()));
		}

		@Override
		public boolean isVallidFile(File file) {
			return file.getParentFile().equals(m_rootDir);
		}
	}
    
    private static class DescriptorCacheLoader<T> extends CacheLoader<String, T> {
    	private final FileObjectStore<String,T> m_store;
    	
    	DescriptorCacheLoader(FileObjectStore<String,T> store) {
    		m_store = store;
    	}
    	
		@Override
		public T load(String key) throws Exception {
			return m_store.get(key).get();
		}
    }
	
	private boolean filterByShortId(T desc, String idShort) {
		try {
			Object ret = m_getIdShort.invoke(desc);
			return idShort.equals(ret);
		}
		catch ( Exception e ) {
			return false;
		}
	}
}
