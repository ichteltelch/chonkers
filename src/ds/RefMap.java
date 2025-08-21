package ds;



import java.lang.ref.Reference;
import java.lang.ref.ReferenceQueue;
import java.lang.ref.SoftReference;
import java.lang.ref.WeakReference;
import java.lang.reflect.Array;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.function.Supplier;



public class RefMap<K, V, M extends Map<RefMap.WrappedKey<K>, V>> implements Map<K, V>{

	public static final Supplier<?> newHashMap = HashMap::new;
	public static final Supplier<?> newLinkedHashMap = LinkedHashMap::new;
	public static final Supplier<?> newConcurrentHashMap = ConcurrentHashMap::new;

	final private M map;
	final private ReferenceQueue<K> garbage = new ReferenceQueue<>();

	@SuppressWarnings("unchecked")
	public static <K, V> RefMap<K, V, ?> forHashMap(){
		return make((Supplier<? extends Map<WrappedKey<K>, V>>) newHashMap);
	}
	@SuppressWarnings("unchecked")
	public static <K, V> RefMap<K, V, ?> forLinkedHashMap(){
		return make((Supplier<? extends Map<WrappedKey<K>, V>>) newLinkedHashMap);
	}
	@SuppressWarnings("unchecked")
	public static <K, V> RefMap<K, V, ?> forConcurrentHashMap(){
		return make((Supplier<? extends Map<WrappedKey<K>, V>>) newConcurrentHashMap);
	}
	public static <K, V> RefMap<K, V, ?> make(
			Supplier<? extends Map<WrappedKey<K>, V>> make){
		return new ForHashMap<>(make.get());
	}
	


	
	public static class ForHashMap<K, V> extends RefMap<K, V, Map<WrappedKey<K>, V>> {
		protected ForHashMap(Map<WrappedKey<K>, V> map) {
			super(map);
		}
	}
	
	public enum Strength{
		STRONG, WEAK, SOFT;
	}
	Strength defaultStrength = Strength.WEAK;
	public static interface WrappedKey<K>{
		K get();
		default public boolean defaultEquals(Object o) {
			if (o == this)
				return true;
			if (o == null)
				return false;
			if (o instanceof RefMap.WrappedKey) {
				@SuppressWarnings("unchecked")
				RefMap.WrappedKey<K> a = (RefMap.WrappedKey<K>) o;
				if(a.hashCode()!=hashCode())
					return false;
				K value = get();
				if(value==null) return false;
				K value2 = a.get();
				if(value2==null) return false;

				return Objects.equals(value, value2);
			}
			return false;
		}
		public void cancel();
		public boolean isCancelled();
	}
	protected static class StrongKey<K> implements WrappedKey<K> {
		K key;
		int hash;
		public void cancel() {
			hash=0;
		}
		public boolean isCancelled() {
			return hash==0;
		}
		public StrongKey(K key, ReferenceQueue<K> queue) {
			this.key = key;
			this.hash = Objects.hashCode(key);
			if(hash==0)
				hash = 1;
		}
		@Override
		public K get() {
			return key;
		}
		@Override
		public int hashCode() {
			return hash;
		}
		@Override
		public boolean equals(Object obj) {
			return defaultEquals(obj);
		}

	}

	protected static class WeakKey<K> extends WeakReference<K> implements WrappedKey<K> {
		int hashCode;
		public void cancel() {
			hashCode=0;
		}
		public boolean isCancelled() {
			return hashCode==0;
		}
		public WeakKey(K key, ReferenceQueue<K> queue) {
			super(key, queue);
			this.hashCode = Objects.hashCode(key);
			if(hashCode==0)
				hashCode = 1;		
		}
		@Override
		public int hashCode() {
			return hashCode;
		}
		@Override
		public boolean equals(Object obj) {
			return defaultEquals(obj);
		}

	}
	protected static class SoftKey<K> extends SoftReference<K> implements WrappedKey<K> {
		int hashCode;
		public void cancel() {
			hashCode=0;
		}
		public boolean isCancelled() {
			return hashCode==0;
		}
		public SoftKey(K key, ReferenceQueue<K> queue) {
			super(key, queue);
			this.hashCode = Objects.hashCode(key);
			if(hashCode==0)
				hashCode = 1;		
		}
		@Override
		public int hashCode() {
			return hashCode;
		}
		@Override
		public boolean equals(Object obj) {
			return defaultEquals(obj);
		}


	}

	protected RefMap(M map) {
		this.map = map;
	}

	AtomicInteger amort=new AtomicInteger();
	public void gc(int max) {
		int a = amort.incrementAndGet();
		if(a<max)
			return;
		amort.addAndGet(-Math.min(max, a));
		synchronized (this) {
			for(int i=2*max; i>=0; --i) {
				Reference<? extends K> key = garbage.poll();
				if(key==null) break;
				if(key instanceof WrappedKey) {
					@SuppressWarnings("unchecked")
					WrappedKey<K> cast = (WrappedKey<K>) key;
					if(!cast.isCancelled())
						map.remove(cast);
				}
			}
		}
	}
	public synchronized void gc() {
		for(;;) {
			amort.set(0);
			Reference<? extends K> key = garbage.poll();
			if(key==null) break;
			if(key instanceof WrappedKey) {
				@SuppressWarnings("unchecked")
				WrappedKey<K> cast = (WrappedKey<K>) key;
				if(!cast.isCancelled())
					map.remove(cast);
			}
		}
	}

	@Override
	public int size() {
		gc();
		return map.size();
	}

	@Override
	public boolean isEmpty() {
		gc();
		return map.isEmpty();
	}

	@SuppressWarnings("unchecked")
	@Override
	public boolean containsKey(Object key) {
		if(key==null) return false;
		WrappedKey<K> wk;
		try {
			wk = new StrongKey<K>((K)key, garbage);
		}catch(ClassCastException ex) {
			return false;
		}
		return map.containsKey(wk);
	}

	@Override
	public boolean containsValue(Object value) {
		gc();
		return map.containsValue(value);
	}

	@SuppressWarnings("unchecked")
	@Override
	public V get(Object key) {
		gc(100);
		StrongKey<K> ck;
		try {
			ck = new StrongKey<K>((K)key, garbage);
		}catch(ClassCastException ex) {
			return null;
		}
		V ret = map.get(ck);
		return ret;
	}

	@Override
	public V computeIfAbsent(K key, Function<? super K, ? extends V> mappingFunction) {
		return computeIfAbsent(key, mappingFunction, defaultStrength);
	}
    public V computeIfAbsent(K key, Function<? super K,? extends V> mappingFunction, Strength strength) {
		switch(strength) {
		case WEAK:
			return computeIfAbsentWeak(key, mappingFunction);
		case SOFT:
			return computeIfAbsentSoft(key, mappingFunction);
		case STRONG:
			return computeIfAbsentStrong(key, mappingFunction);
		default: 
			assert false;
			return null;
		}
	}
	
	public V computeFromWrappedKeyIfAbsent(K key, Function<? super WrappedKey<K>, ? extends V> mappingFunction) {
		return computeFromWrappedKeyIfAbsent(key, mappingFunction, defaultStrength);
	}
    public V computeFromWrappedKeyIfAbsent(K key, Function<? super WrappedKey<K>,? extends V> mappingFunction, Strength strength) {
		switch(strength) {
		case WEAK:
			return computeFromWrappedKeyIfAbsentWeak(key, mappingFunction);
		case SOFT:
			return computeFromWrappedKeyIfAbsentSoft(key, mappingFunction);
		case STRONG:
			return computeFromWrappedKeyIfAbsentStrong(key, mappingFunction);
		default: 
			assert false;
			return null;
		}
	}
	@Override
	public V put(K key, V value) {
		return put(key, value, defaultStrength);
	}
	public V put(K key, V value, Strength strength) {
		switch(strength) {
		case WEAK:
			return putWeak(key, value);
		case SOFT:
			return putSoft(key, value);
		case STRONG:
			return putStrong(key, value);
		default: 
			assert false;
			return null;
		}
	}
	@Override
	public V putIfAbsent(K key, V value) {
		return putIfAbsent(key, value, defaultStrength);
	}
	public V putIfAbsent(K key, V value, Strength strength) {
		switch(strength) {
		case WEAK:
			return putIfAbsentWeak(key, value);
		case SOFT:
			return putIfAbsentSoft(key, value);
		case STRONG:
			return putIfAbsentStrong(key, value);
		default: 
			assert false;
			return null;
		}
	}
	public V putStrong(K key, V value) {
		gc(100);
		StrongKey<K> wk = new StrongKey<K>(key, garbage);
		V ret = map.put(wk, value);
		if(ret!=null)
			wk.cancel();
		return ret;
	}
	public V putWeak(K key, V value) {
		gc(100);
		WeakKey<K> wk = new WeakKey<K>(key, garbage);
		V ret = map.put(wk, value);
		if(ret!=null)
			wk.cancel();
		return ret;	}
	public V putSoft(K key, V value) {
		gc(100);
		SoftKey<K> wk = new SoftKey<K>(key, garbage);
		V ret = map.put(wk, value);
		if(ret!=null)
			wk.cancel();
		return ret;	}
	public V putIfAbsentStrong(K key, V value) {
		gc(100);
		StrongKey<K> wk = new StrongKey<K>(key, garbage);
		V ret = map.putIfAbsent(wk, value);
		if(ret!=null)
			wk.cancel();
		return ret;
	}
	public V putIfAbsentWeak(K key, V value) {
		gc(100);
		WeakKey<K> wk = new WeakKey<K>(key, garbage);
		V ret = map.putIfAbsent(wk, value);
		if(ret!=null)
			wk.cancel();
		return ret;
	}
	public V putIfAbsentSoft(K key, V value) {
		gc(100);
		SoftKey<K> wk = new SoftKey<K>(key, garbage);
		V ret = map.putIfAbsent(wk, value);
		if(ret!=null)
			wk.cancel();
		return ret;
	}
	static class MappingFunction<K, V> implements Function<WrappedKey<K>, V>{
		final Function<? super K, ? extends V> back;
		boolean wasInvoked;
		public MappingFunction(Function<? super K, ? extends V> back) {
			this.back=back;
		}
		@Override
		public V apply(WrappedKey<K> t) {
			wasInvoked = true;
			return back.apply(t.get());
		}
	}
	public V computeIfAbsentStrong(K key, Function<? super K, ? extends V> mappingFunction) {
		gc(100);
		StrongKey<K> wk = new StrongKey<K>(key, garbage);
		MappingFunction<K, V> f = new MappingFunction<>(mappingFunction);
		V ret = map.computeIfAbsent(wk, f);
		if(!f.wasInvoked)
			wk.cancel();
		return ret;
	}
	public V computeIfAbsentWeak(K key, Function<? super K, ? extends V> mappingFunction) {
		gc(100);
		WeakKey<K> wk = new WeakKey<K>(key, garbage);
		MappingFunction<K, V> f = new MappingFunction<>(mappingFunction);
		V ret = map.computeIfAbsent(wk, f);
		if(!f.wasInvoked)
			wk.cancel();
		return ret;
	}
	public V computeIfAbsentSoft(K key, Function<? super K, ? extends V> mappingFunction) {
		gc(100);
		SoftKey<K> wk = new SoftKey<K>(key, garbage);
		MappingFunction<K, V> f = new MappingFunction<>(mappingFunction);
		V ret = map.computeIfAbsent(wk, f);
		if(!f.wasInvoked)
			wk.cancel();
		return ret;
	}
	static class MappingFunctionFromWrappedKey<K, V> implements Function<WrappedKey<K>, V>{
		final Function<? super WrappedKey<K>, ? extends V> back;
		boolean wasInvoked;
		public MappingFunctionFromWrappedKey(Function<? super WrappedKey<K>, ? extends V> back) {
			this.back=back;
		}
		@Override
		public V apply(WrappedKey<K> t) {
			wasInvoked = true;
			return back.apply(t);
		}
	}
	public V computeFromWrappedKeyIfAbsentStrong(K key, Function<? super WrappedKey<K>, ? extends V> mappingFunction) {
		gc(100);
		StrongKey<K> wk = new StrongKey<K>(key, garbage);
		MappingFunctionFromWrappedKey<K, V> f = new MappingFunctionFromWrappedKey<>(mappingFunction);
		V ret = map.computeIfAbsent(wk, f);
		if(!f.wasInvoked)
			wk.cancel();
		return ret;	
	}
	public V computeFromWrappedKeyIfAbsentWeak(K key, Function<? super WrappedKey<K>, ? extends V> mappingFunction) {
		gc(100);
		WeakKey<K> wk = new WeakKey<K>(key, garbage);
		MappingFunctionFromWrappedKey<K, V> f = new MappingFunctionFromWrappedKey<>(mappingFunction);
		V ret = map.computeIfAbsent(wk, f);
		if(!f.wasInvoked)
			wk.cancel();
		return ret;	
	}
	public V computeFromWrappedKeyIfAbsentSoft(K key, Function<? super WrappedKey<K>, ? extends V> mappingFunction) {
		gc(100);
		SoftKey<K> wk = new SoftKey<K>(key, garbage);
		MappingFunctionFromWrappedKey<K, V> f = new MappingFunctionFromWrappedKey<>(mappingFunction);
		V ret = map.computeIfAbsent(wk, f);
		if(!f.wasInvoked)
			wk.cancel();
		return ret;	
	}

	@SuppressWarnings("unchecked")
	@Override
	public V remove(Object key) {
		gc(100);
		StrongKey<K> ck;
		try {
			ck = new StrongKey<K>((K)key, garbage);
		}catch(ClassCastException ex) {
			return null;
		}
		return map.remove(ck);
	}
	@SuppressWarnings("unchecked")
	protected boolean remove_rb(Object key) {
		gc(100);
		StrongKey<K> ck;
		try {
			ck = new StrongKey<K>((K)key, garbage);
		}catch(ClassCastException ex) {
			return false;
		}
		if(!map.containsKey(ck)) return false;
		map.remove(ck);
		return true;
	}

	@Override
	public void putAll(Map<? extends K, ? extends V> m) {
		for(Map.Entry<? extends K,? extends V> entry : m.entrySet())
			put(entry.getKey(), entry.getValue(), defaultStrength);
	}

	@Override
	public void clear() {
		map.clear();
		gc();
	}

	@Override
	public int hashCode() {
		gc();
		return map.hashCode();
	}
	@Override
	public boolean equals(Object o) {
		if (o == this)
			return true;
		if (o == null)
			return false;
		if (o instanceof Map<?,?>) {
			Map<?,?> a = (Map<?,?>) o;
			return entrySet.equals(a.entrySet());
		}
		return false;
	}
	volatile WrappedKeySet<K, M> keySet;

	@Override
	public Set<K> keySet() {
		WrappedKeySet<K, M> local = keySet;
		if(local==null) {
			synchronized(this) {
				local = keySet;
				if(local==null) {
					local = makeKeySet();
					keySet = local;
				}
			}
		}
		return local;
	}

	protected WrappedKeySet<K, M> makeKeySet() {
		return new WrappedKeySet<K, M>(this);
	}

	volatile WrappedValues<K, V, M> values;

	@Override
	public Collection<V> values() {
		WrappedValues<K, V, M> local = values;
		if(local==null) {
			synchronized(this) {
				local = values;
				if(local==null) {
					local = makeValues();
					values = local;
				}
			}
		}
		return local;
	}

	protected WrappedValues<K, V, M> makeValues() {
		return new WrappedValues<K, V, M>(this);
	}
	volatile WrappedEntrySet<K, V, M> entrySet;
	@Override
	public Set<Entry<K, V>> entrySet() {
		WrappedEntrySet<K, V, M> local = entrySet;
		if(local==null) {
			synchronized(this) {
				local = entrySet;
				if(local==null) {
					local = makeEntrySet();
					entrySet = local;
				}
			}
		}
		return local;
	}

	protected WrappedEntrySet<K, V, M> makeEntrySet() {
		return new WrappedEntrySet<K, V, M>(this);
	}
	protected static class WrappedEntry<K, V> implements Map.Entry<K, V> {
		final Map.Entry<WrappedKey<K>, V> back;
		final K keyStrong;
		public WrappedEntry(Map.Entry<WrappedKey<K>, V> back, K keyStrong) {
			this.back = back;
			this.keyStrong = back.getKey().get();
			assert keyStrong!=null;
			assert keyStrong == back.getKey().get();
		}
		@Override
		public K getKey() {
			return keyStrong;
		}

		@Override
		public V getValue() {
			return back.getValue();
		}

		@Override
		public V setValue(V value) {
			return back.setValue(value);
		}
		@Override
		public boolean equals(Object o) {
			if (o == this)
				return true;
			if (o == null)
				return false;
			if (o instanceof Map.Entry<?, ?>) {
				Map.Entry<?, ?> a = (Map.Entry<?, ?>) o;
				if(!Objects.equals(back.getValue(), a.getValue()))
					return false;
				try {
					return Objects.equals(keyStrong, a.getKey());
				}catch(ClassCastException ex) {
					return false;
				}
			}
			return false;
		}
		@Override
		public int hashCode() {
			return (back.getKey()==null   ? 0 : back.getKey().hashCode()) ^
					(back.getValue()==null ? 0 : back.getValue().hashCode());
		}

	}
	protected static class WrappedValues<K, V, M extends Map<WrappedKey<K>, V>> implements Collection<V> {
		final RefMap<K, V, M> belong;
		public WrappedValues(RefMap<K, V, M> belong) {
			this.belong = belong;
		}
		@Override
		public int size() {
			return belong.size();
		}
		@Override
		public boolean isEmpty() {
			return belong.isEmpty();
		}
		@Override
		public boolean contains(Object o) {
			return belong.containsValue(o);
		}
		@Override
		public boolean add(V e) {
			throw new UnsupportedOperationException();
		}
		@Override
		public boolean remove(Object o) {
			for(Iterator<Entry<WrappedKey<K>, V>> it = belong.map.entrySet().iterator(); it.hasNext(); ) {
				Entry<WrappedKey<K>, V> e = it.next();
				K keyStrong = e.getKey().get();
				if(keyStrong==null) {
					it.remove();
					continue;
				}
				if(Objects.equals(e.getValue(), o)) {
					it.remove();
					return true;
				}
			}
			return false;
		}
		@Override
		public boolean addAll(Collection<? extends V> c) {
			throw new UnsupportedOperationException();
		}
		@Override
		public boolean removeAll(Collection<?> c) {
			boolean changed = false;
			for(Object o : c)
				changed |= remove(o);            
			return changed;  
		}
		@Override
		public void clear() {
			belong.clear();
		}
		@Override
		public boolean containsAll(Collection<?> c) {
			for(Object o : c)
				if(!contains(o))
                    return false;
            return true;
		}
		@Override
		public Object[] toArray() {
			return toArray(new Object[belong.size()]);
		}
		@SuppressWarnings("unchecked")
		@Override
		public <T> T[] toArray(T[] a) {
			if(a.length<size())
				a = (T[]) Array.newInstance(a.getClass().getComponentType(), size());
			int i=0;
			for(V e : this)
				a[i++] = (T) e;
			return a;
		}
		@Override
		public boolean retainAll(Collection<?> c) {
			boolean changed = false;
			for(Iterator<V> it = iterator(); it.hasNext(); ) {
				V e = it.next();
                if(!c.contains(e)) {
                    it.remove();
                    changed = true;
                }
			}
			return changed;
		}
		@Override
        public Iterator<V> iterator() {
			return new Iterator<V>() {
				Iterator<Entry<WrappedKey<K>, V>> back=belong.map.entrySet().iterator();
				K nextKey;
				V nextValue;
				boolean nextComputed;
				{
					forward();
				}
				void forward(){
					nextKey = null;
					nextValue = null;
					nextComputed = false;
					while(back.hasNext()) {
						Entry<WrappedKey<K>, V> e = back.next();
						K keyStrong = e.getKey().get();
						if(keyStrong!=null) {
							nextKey = keyStrong;
							nextValue = e.getValue();
							nextComputed = true;
							break;
						}
						back.remove();
					}

				}
				@Override
				public boolean hasNext() {
					if(!nextComputed) 
						forward();
					return nextKey!=null;
				}
				@Override
				public V next() {
					if(!nextComputed) 
						forward();
					if(nextKey==null) 
						throw new NoSuchElementException();
					V ret = nextValue;
					nextComputed = false;
					return ret;
				}
				@Override
				public void remove() {
					if(nextComputed) {
						throw new IllegalStateException("Cannot remove() last element after call to hasNext()");
					}
					back.remove();
				}

			};

		}


	}
	protected static class WrappedKeySet<K, M extends Map<WrappedKey<K>, ?>> implements Set<K> {
		final RefMap<K, ?, M> belong;
		public WrappedKeySet(RefMap<K, ?, M> belong) {
			this.belong = belong;
		}
		@Override
		public int size() {
			return belong.size();
		}
		@Override
		public boolean isEmpty() {
			return belong.isEmpty();
		}
		@Override
		public boolean contains(Object o) {
			return belong.containsKey(o);
		}
		@Override
		public Iterator<K> iterator() {
			return new Iterator<K>() {
				Iterator<WrappedKey<K>> back=belong.map.keySet().iterator();
				K nextKey;
				boolean nextComputed;
				{
					forward();
				}
				void forward(){
					nextKey = null;
					nextComputed = false;
					while(back.hasNext()) {
						WrappedKey<K> e = back.next();
						K keyStrong = e.get();
						if(keyStrong!=null) {
							nextKey = keyStrong;
							nextComputed = true;
							break;
						}
						back.remove();
					}

				}
				@Override
				public boolean hasNext() {
					if(!nextComputed) 
						forward();
					return nextKey!=null;
				}
				@Override
				public K next() {
					if(!nextComputed) 
						forward();
					if(nextKey==null) 
						throw new NoSuchElementException();
					K ret = nextKey;
					nextComputed = false;
					return ret;
				}
				@Override
				public void remove() {
					if(nextComputed) {
						throw new IllegalStateException("Cannot remove() last element after call to hasNext()");
					}
					back.remove();
				}

			};
		}
		@Override
		public Object[] toArray() {
			return toArray(new Object[belong.size()]);
		}
		@SuppressWarnings("unchecked")
		@Override
		public <T> T[] toArray(T[] a) {
			if(a.length<size())
				a = (T[]) Array.newInstance(a.getClass().getComponentType(), size());
			int i=0;
			for(K e : this)
				a[i++] = (T) e;
			return a;
		}
		@Override
		public boolean add(K e) {
			throw new UnsupportedOperationException();
		}
		@Override
		public boolean remove(Object o) {
			return belong.remove_rb(o);
		}
		@Override
		public boolean containsAll(Collection<?> c) {
			for(Object e : c)
				if(!belong.containsKey(e))
					return false;
			return true;
		}
		@Override
		public boolean addAll(Collection<? extends K> c) {
			throw new UnsupportedOperationException();
		}
		@Override
		public boolean retainAll(Collection<?> c) {
			boolean change = false;
			for(Iterator<K> e = iterator(); e.hasNext();) {
				if(!c.contains(e.next())) {
					change = true;
					e.remove();
				}
			}
			return change;
		}
		@Override
		public boolean removeAll(Collection<?> c) {
			boolean change = false;
			for(Object e : c)
				change |= belong.remove_rb(e);
			return change;

		}
		@Override
		public void clear() {
			belong.clear();
		}
		@Override
		public int hashCode() {
			return belong.keySet.hashCode();
		}
		@Override
		public boolean equals(Object o) {
			if (o == this)
				return true;
			if (o == null)
				return false;
			if (o instanceof Set<?>) {
				Set<?> a = (Set<?>) o;
				if(belong.size()!=a.size())
					return false;
                return containsAll(a) && a.containsAll(this);
			}
			return false;
		}

	}
	protected static class WrappedEntrySet<K, V, M extends Map<WrappedKey<K>, V>> implements Set<Map.Entry<K, V>> {
		final RefMap<K, V, M> belong;
		public WrappedEntrySet(RefMap<K, V, M> belong) {
			this.belong = belong;
		}
		@Override
		public int size() {
			return belong.size();
		}
		@Override
		public boolean add(Entry<K, V> e) {
			throw new UnsupportedOperationException();
		}
		@Override
		public boolean remove(Object o) {
			if(o instanceof Map.Entry<?,?>) {
				Map.Entry<?,?> a = (Map.Entry<?,?>) o;
				return belong.remove(a.getKey(), a.getValue());
			}
			return false;
		}
		@Override
		public boolean addAll(Collection<? extends Entry<K, V>> c) {
			throw new UnsupportedOperationException();
		}
		@Override
		public boolean removeAll(Collection<?> c) {
			boolean changed = false;
			for(Object o : c)
				changed |= remove(o);
			return changed;
		}
		@Override
		public void clear() {
			belong.clear();
		}
		@Override
		public boolean contains(Object o) {
			if(o instanceof Map.Entry<?,?>) {
				Map.Entry<?,?> a = (Map.Entry<?,?>) o;
				V value = belong.get(a.getKey());
				if(value==null) {
					if(!belong.containsKey(a.getKey()))
						return false;
				}
				return Objects.equals(a.getValue(), value);
			}
			return false;
		}
		@Override
		public boolean containsAll(Collection<?> c) {
			for(Object o : c)
				if(!contains(o))
					return false;
			return true;
		}
		@Override
		public boolean equals(Object o) {
			if (o == this)
				return true;
			if (o == null)
				return false;
			if (o instanceof Map<?, ?>) {
				Map<?, ?> a = (Map<?, ?>) o;
				if(a.size()!=size())
					return false;
				return containsAll(a.entrySet()) && a.entrySet().containsAll(this);
			}
			return false;
		}
		@Override
		public int hashCode() {
			return belong.hashCode();
		}
		@Override
		public boolean isEmpty() {
			return belong.isEmpty();
		}
		@Override
		public boolean retainAll(Collection<?> c) {
			boolean changed = false;
			for(Iterator<Map.Entry<K, V>> i = iterator(); i.hasNext(); ) {
				if(!c.contains(i.next())) {
					changed = true;
					i.remove();
				}
			}
			return changed;
		}
		@Override
		public Object[] toArray() {
			return toArray(new Object[size()]);
		}
		@SuppressWarnings("unchecked")
		@Override
		public <T> T[] toArray(T[] a) {
			if(a.length<size())
				a = (T[]) Array.newInstance(a.getClass().getComponentType(), size());
			int i=0;
			for(Map.Entry<K, V> e : this)
				a[i++] = (T) e;
			return a;
		}
		@Override
		public Iterator<Entry<K, V>> iterator() {
			return new Iterator<Entry<K, V>>() {
				Iterator<Map.Entry<WrappedKey<K>, V>> back=belong.map.entrySet().iterator();
				WrappedEntry<K, V> nextEntry;
				boolean nextComputed;
				{
					forward();
				}
				void forward(){
					nextEntry = null;
					nextComputed = false;
					while(back.hasNext()) {
						Map.Entry<WrappedKey<K>, V> e = back.next();
						K keyStrong = e.getKey().get();
						if(keyStrong!=null) {
							nextEntry = new WrappedEntry<K, V>(e, keyStrong);
							nextComputed = true;
							break;
						}
						back.remove();
					}

				}
				@Override
				public boolean hasNext() {
					if(!nextComputed) 
						forward();
					return nextEntry!=null;
				}
				@Override
				public Entry<K, V> next() {
					if(!nextComputed) 
						forward();
					if(nextEntry==null) 
						throw new NoSuchElementException();
					WrappedEntry<K, V> ret = nextEntry;
					nextComputed = false;
					nextEntry = null;
					return ret;
				}
				@Override
				public void remove() {
					if(nextComputed) {
						throw new IllegalStateException("Cannot remove() last element after call to hasNext()");
					}
					back.remove();
				}

			};
		}

	}
	public static final Function<Object, Object> ID = o -> o;
	@SuppressWarnings("unchecked")
	public static <T> Function<? super T, ? extends T> id(){
		return (Function<T, T>) ID;
	}
	@Override
	public String toString() {
		StringBuilder sb = new StringBuilder();
		sb.append('[');
		for(Map.Entry<K, V> e: entrySet()) {
			if(sb.length()>1)
				sb.append(",");
			sb.append(e.getKey()).append('=').append(e.getValue());
		}
		sb.append(']');
		return sb.toString();
	}

}
