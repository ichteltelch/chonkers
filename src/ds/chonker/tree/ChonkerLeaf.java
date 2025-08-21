package ds.chonker.tree;


public abstract class ChonkerLeaf<M extends ChonkersMonoidData<M>> implements ChonkerNode<M>{
	@Override
	public int layerTag() {
		return 0;
	}
	@Override
	public long getRawDiffbit(long selfOffset, long otherOffset, ChonkerNode<?> other) {
		if(selfOffset>=weight())
			return -1;
		if(other instanceof ChonkerLeaf) {
			ChonkerLeaf<?> otherLeaf = (ChonkerLeaf<?>) other;
			assert selfOffset < Integer.MAX_VALUE;
			assert otherOffset < Integer.MAX_VALUE;
			return getLeafDiffBit((int)selfOffset, (int)otherOffset, otherLeaf);
		}
		return other.getRawDiffbit(otherOffset, selfOffset, this)^1;
	}
	@Override
	public long getReverseDiffbit(long selfOffset, long otherOffset, ChonkerNode<?> other) {
		if(selfOffset>=weight())
			return -1;
		if(other instanceof ChonkerLeaf) {
			ChonkerLeaf<?> otherLeaf = (ChonkerLeaf<?>) other;
			assert selfOffset < Integer.MAX_VALUE;
			assert otherOffset < Integer.MAX_VALUE;
			return getReverseLeafDiffbit((int)selfOffset, (int)otherOffset, otherLeaf);
		}
		return other.getReverseDiffbit(otherOffset, selfOffset, this)^1;
	}
	protected abstract int getLeafDiffBit(int selfOffset, int otherOffset, ChonkerLeaf<?> otherLeaf);
	protected abstract int getReverseLeafDiffbit(int selfOffset, int otherOffset, ChonkerLeaf<?> otherLeaf);

	@Override
	public ChonkerNode<M> getChild(long i) {
		throw new UnsupportedOperationException("Leaf nodes have no children");
	}
	@Override
	public long numChildren() {
		return 0;
	}
	boolean canonical;
	public boolean isCanonical() {
		return canonical;
	}
	public void setCanonical(boolean canonical) {
        this.canonical = canonical;
    }
	public static class ByteLeaf extends ChonkerLeaf<ChonkersMonoidData.Minimal> implements ChonkersMonoidData.Minimal{
		final int value;
		public ByteLeaf(int value) {
			this.value = 0xFF & value;
		}
		@Override
		public ByteLeaf content() {
			return this;
		}
		@Override
		protected int getLeafDiffBit(int selfOffset, int otherOffset, ChonkerLeaf<?> otherLeaf) {
			if(otherLeaf instanceof ByteLeaf) {
				ByteLeaf l = (ByteLeaf) otherLeaf;
				int sValue = value>>>selfOffset;
				int oValue = l.value>>>otherOffset;
				int diff = sValue ^ oValue;
				if(diff==0)
					return -1;
				int index = Integer.bitCount(Integer.lowestOneBit(diff)-1);
				if(index + selfOffset >= weight())
					return -1;
				boolean up = (sValue&(1<<index))==0;
				return (index << 1) + (up?1:0);
			}
			throw new IllegalArgumentException();
		}
		@Override
		protected int getReverseLeafDiffbit(int selfOffset, int otherOffset, ChonkerLeaf<?> otherLeaf) {
			if(otherLeaf instanceof ByteLeaf) {
				ByteLeaf l = (ByteLeaf) otherLeaf;
				int sValue = value>>>selfOffset;
				int oValue = l.value>>>otherOffset;
				int diff = sValue ^ oValue;
				if(diff==0)
					return -1;
				int index = (int)weight() - 1 - Integer.bitCount(Integer.highestOneBit(diff)-1);
				if(index + selfOffset >= weight())
					return -1;
				boolean up = (sValue&(1<<index))==0;
				return (index << 1) + (up?1:0);
			}
			throw new IllegalArgumentException();
		}

		@Override
		public long weight() {
			return 8;
		}

		@Override
		public boolean getBit(long bitIndex) {
			if(bitIndex < 0)
				throw new IndexOutOfBoundsException("Bit index out of range");
			if(bitIndex >= weight())
				throw new IndexOutOfBoundsException("Bit index out of range");

			return ((value>>bitIndex)&1)==1;
		}

		@Override
		public int augHash() {
			int ret=0;
			int w = (int)weight();
			for(int i=0; i<w; ++i) {
				if((value&(1<<i))!=0)
					ret += 1;
				ret *= 31;
			}
			return ret;
		}
		@Override
		public String toString() {
			StringBuilder sb = new StringBuilder(8);
			String r = Integer.toString(value, 2);
			sb.append(r);
			sb.reverse();
			while(sb.length()<2)
				sb.append("0");
			return sb.toString();
		}
		@Override
		public int defaultHashCode() {
			// TODO Auto-generated method stub
			return super.defaultHashCode();
		}
		@Override
		public boolean defaultEquals(Object o) {
			// TODO Auto-generated method stub
			return super.defaultEquals(o);
		}
		@Override
		public ByteLeaf getMonoidData() {
			return this;
		}
		@Override
		public void setContent(ChonkerNode<Minimal> caterpillar) {
			
		}

	}
	
	public static class CharLeaf extends ChonkerLeaf<ChonkersMonoidData.WithUserMonoids> implements ChonkersMonoidData.WithUserMonoids{
		final char value;
		public CharLeaf(char value) {
			this.value = value;
		}
		@Override
        public CharLeaf content() {
            return this;
        }
		@Override
		public ChonkersMonoidData.WithUserMonoids getMonoidData() {
			return this;
		}
		@Override
		protected int getLeafDiffBit(int selfOffset, int otherOffset, ChonkerLeaf<?> otherLeaf) {
			if(otherLeaf instanceof CharLeaf) {
				CharLeaf l = (CharLeaf) otherLeaf;
				int sValue = value>>>selfOffset;
				int oValue = l.value>>>otherOffset;
				int diff = sValue ^ oValue;
				if(diff==0)
					return -1;
				int index = Integer.bitCount(Integer.lowestOneBit(diff)-1);
				if(index + selfOffset >= weight())
					return -1;
				boolean up = (sValue&(1<<index))==0;
				return (index << 1) + (up?1:0);
			}
			throw new IllegalArgumentException();
		}
		@Override
		protected int getReverseLeafDiffbit(int selfOffset, int otherOffset, ChonkerLeaf<?> otherLeaf) {
			if(otherLeaf instanceof CharLeaf) {
				CharLeaf l = (CharLeaf) otherLeaf;
				int sValue = value>>>selfOffset;
				int oValue = l.value>>>otherOffset;
				int diff = sValue ^ oValue;
				if(diff==0)
					return -1;
				int index = (int)weight() - 1 - Integer.bitCount(Integer.highestOneBit(diff)-1);
				if(index + selfOffset >= weight())
					return -1;
				boolean up = (sValue&(1<<index))==0;
				return (index << 1) + (up?1:0);
			}
			throw new IllegalArgumentException();
		}
		@Override
		public long weight() {
			return 32;
		}

		@Override
		public boolean getBit(long bitIndex) {
			if(bitIndex < 0)
				throw new IndexOutOfBoundsException("Bit index out of range");
			if(bitIndex >= weight())
				throw new IndexOutOfBoundsException("Bit index out of range");

			return ((value>>bitIndex)&1)==1;
		}

		@Override
		public int augHash() {
			int ret=0;
			int w = (int)weight();
			for(int i=0; i<w; ++i) {
				if((value&(1<<i))!=0)
					ret += 1;
				ret *= 31;
			}
			return ret;
		}
		@Override
		public String toString() {
			StringBuilder sb = new StringBuilder(8);
			String r = Integer.toString(value, 2);
			sb.append(r);
			sb.reverse();
			while(sb.length()<2)
				sb.append("0");
			return sb.toString();
		}

		@Override
		public <M, U extends UserMonoid<M>> M get(U monoid) {
			return monoid.inject(this);
		}
		@Override
		public int defaultHashCode() {
			// TODO Auto-generated method stub
			return super.defaultHashCode();
		}
		@Override
		public boolean defaultEquals(Object o) {
			// TODO Auto-generated method stub
			return super.defaultEquals(o);
		}
		@Override
		public void setContent(ChonkerNode<WithUserMonoids> caterpillar) {
			
		}
	}
	@Override
	public ChonkerTreeZipper<M> zipTo(ChonkerTreeZipper<M> zipperToMe, long bitIndex) {
		zipperToMe = ChonkerNode._zipTo_commonChecks(this, zipperToMe, bitIndex);
		return zipperToMe;
	}
	@Override
	public ChonkerNode<M> dropPrefixChildren(long i, ChonkerConfig<M> c) {
		if(i!=0)
			throw new IndexOutOfBoundsException();
		return this;
	}
	@Override
	public ChonkerNode<M> dropSuffixChildren(long i, ChonkerConfig<M> c) {
		if(i!=0)
			throw new IndexOutOfBoundsException();
		return this;
	}
	@Override
	public ChonkerNode<M> substChild(long i, ChonkerNode<M> subst, ChonkerConfig<M> c) {
		throw new IndexOutOfBoundsException();
	}

	@Override
	public boolean equals(Object obj) {
		return defaultEquals(obj);
	}
	@Override
    public int hashCode() {
        return defaultHashCode();
    }


}
