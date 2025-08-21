package ds.chonker.tree;

import java.util.Map;

import ds.RefMap;

public interface ChonkersMonoidData<M extends ChonkersMonoidData<M>> {
	public long weight();
	public ChonkerNode<M> content();
	public int augHash();
	public static interface Minimal extends ChonkersMonoidData<Minimal> {
		
	}
	public static interface WithUserMonoids extends ChonkersMonoidData<WithUserMonoids> {
		public <M, U extends UserMonoid<M>> M get(U monoid);
	}
	public static class Abstract<M extends ChonkersMonoidData<M>> implements ChonkersMonoidData<M> {
		final long weight;
		ChonkerNode<M> content;
		final int augHash;
		public Abstract(long weight, ChonkerNode<M> content, int augHash) {
            this.weight = weight;
            this.content = content;
            this.augHash = augHash;
        }
		public void setContent(ChonkerNode<M> content) {
//			assert content==this.content || this.content.equalContent(content);
			this.content = content;
		}
		@Override
		public int hashCode() {
			return augHash;
		}
		@Override
		public boolean equals(Object o) {
			return defaultEquals(o);
		}
		@Override
		public int augHash() {
			return augHash;
		}
		@Override
        public long weight() {
            return weight;
        }
		@Override
        public ChonkerNode<M> content() {
            return content;
        }
	}
	public static class MinimalImpl extends Abstract<Minimal> implements Minimal {
		public MinimalImpl(long weight, ChonkerNode<Minimal> content, int augHash) {
			super(weight, content, augHash);
        }
	}
	public static class UserMonoidsImpl extends Abstract<WithUserMonoids> implements WithUserMonoids {
		public UserMonoidsImpl(long weight, ChonkerNode<WithUserMonoids> content, int augHash) {
			super(weight, content, augHash);
        }
		//TODO: Use a map that also has soft referenced values
		private volatile Map<Object, Object> cache;
		
		Map<Object, Object> cache(){
			Map<Object, Object> local = cache;
			if(local==null) {
				synchronized (this) {
					local = cache;
					if(local==null) {
						local = RefMap.forConcurrentHashMap();
						cache = local;
					}
				}
			}
			return local;
		}
		
		@Override
		public <M, U extends UserMonoid<M>> M get(U monoid) {
			Map<Object, Object> cache = cache();
			Object cached = cache.get(monoid);
			if(cached==null) {
				if(cache.containsKey(monoid))
					return null;
			}else {
				@SuppressWarnings("unchecked")
				M ret = (M)cached;
				return ret;
			}
			M computed;
			//Note: we need a local copy because the content reference might be 
			//replaced concurrently with a different node of the same content
			//due to the MRU strategy
			ChonkerNode<WithUserMonoids> localContent = content;
			if(localContent instanceof Caterpillar) {
				Caterpillar<WithUserMonoids> caterpillar = (Caterpillar<WithUserMonoids>)localContent;
                computed = monoid.power(caterpillar.getSegment().getMonoidData(), caterpillar.numChildren());
			}else if(localContent instanceof ChonkerBranch) {
				ChonkerBranch<WithUserMonoids> branch = (ChonkerBranch<WithUserMonoids>)localContent;
                computed = monoid.combine(branch.left.getMonoidData(), branch.right.getMonoidData());
			}else if(localContent instanceof ChonkerLeaf) {
				ChonkerLeaf<WithUserMonoids> leaf = (ChonkerLeaf<WithUserMonoids>)localContent;
                computed = monoid.inject(leaf);
			}else {
				throw new IllegalArgumentException("Unsupported ChonkersNode type: "+localContent.getClass().getName());
			}
			@SuppressWarnings("unchecked")
			M ret = (M)cache.putIfAbsent((UserMonoid<?>)monoid, (Object)computed);
			if(ret==null)
				ret=computed;
			return ret;
		}
	}
	
	default int defaultHashCode() {
		return augHash();
	}
	default boolean defaultEquals(Object o) {
		if (o == this)
			return true;
		if (o == null)
			return false;
		if (o instanceof ChonkersMonoidData) {
			ChonkersMonoidData<?> a = (ChonkersMonoidData<?>) o;
			if(a.weight()!=weight()) return false;
			if(a.augHash()!=augHash()) return false;
			if(!a.content().equalContent(content())) return false;
			return true;
		}
		return false;
	}
	/**
	 * Exchange the node that represents the content for another representant.
	 * The rationale for this is that this will over time ensure only nodes that
	 * are actually still in use will be used as content representants, saving memory.
	 * @param content
	 */
	public void setContent(ChonkerNode<M> content);
}
