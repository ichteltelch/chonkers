package ds.chonker.tree;

public class ChonkerBranch<M extends ChonkersMonoidData<M>> implements ChonkerNode<M> {
	final int layerTag;
	final ChonkerNode<M> left;
	final ChonkerNode<M> right;
	M monoidData;
	ChonkerBranch(ChonkerConfig<M> c, int layerTag, ChonkerNode<M> left, ChonkerNode<M> right) {
		if(layerTag<left.layerTag())
			throw new IllegalArgumentException("Inconsistent levels");
		if(layerTag<right.layerTag())
			throw new IllegalArgumentException("Inconsistent levels");
		if(left.equalContent(right))
			throw new IllegalArgumentException("Should have been a caterpillar");
		this.layerTag = layerTag;
		this.left = left;
		this.right = right;
		assert left == c.canonical(left);
		assert right == c.canonical(right);
		this.monoidData = c.computeMonoidData(this, left, right);
		this.monoidData = c.canonicalMonoidData(this.monoidData);
		if(weight()<0)
			throw new IllegalStateException("Negative weight. Overflow?");
	}
	@Override
	public int layerTag() {
		return layerTag;
	}

	@Override
	public long weight() {
		return monoidData==null?left.weight()+right.weight():monoidData.weight();
	}

	@Override
	public boolean getBit(long bitIndex) {
		if(bitIndex < 0)
			throw new IndexOutOfBoundsException("Bit index out of range");
		if(bitIndex >= weight())
			throw new IndexOutOfBoundsException("Bit index out of range");
		return bitIndex < left.weight()? left.getBit(bitIndex) : right.getBit(bitIndex - left.weight());
	}

	@Override
	public int augHash() {
		return monoidData.augHash();
	}

	@Override
	public long getRawDiffbit(long selfOffset, long otherOffset, ChonkerNode<?> other) {
		
		if(selfOffset==otherOffset && (getMonoidData() == other.getMonoidData() 
				//for canonical chunks with equal content, the above check will have succeeded.
				//for non.canonical chunks, we cannot call equalContent() because it is defined
				//terms of getRawffbit(). We want to avoid calling equalStructure unnecessarily,
				//so we first compare hashes and weights.
				|| !(!isCanonical()  && other.isCanonical()) 
				&& weight() == other.weight() && augHash()==other.augHash() && equalStructure(other) ))
			return -1;
		if(selfOffset >= weight())
			return -1;
		if(selfOffset>=left.weight()) {
			if(right.weight()<other.weight()) {
				return other.getRawDiffbit(otherOffset, selfOffset-left.weight(), right)^1;
			}else {
				return right.getRawDiffbit(selfOffset-left.weight(), otherOffset, other);

			}
		}
		long diff ;
		if(left.weight()<other.weight()) {
			diff = other.getRawDiffbit(otherOffset, selfOffset, left);
			if(diff>=0)
				return diff^1;
		}else {
			diff = left.getRawDiffbit(selfOffset, otherOffset, other);
			if(diff>=0)
				return diff;
		}
		diff = other.getRawDiffbit(otherOffset+left.weight() - selfOffset, 0, right);
		if(diff>=0) {
			return (diff^1) + ((left.weight() - selfOffset)<<1);
		}
		return -1;
	}

	@Override
	public long getReverseDiffbit(long selfOffset, long otherOffset, ChonkerNode<?> other) {
		if(selfOffset==otherOffset && (getMonoidData() == other.getMonoidData() 
				//for canonical chunks with equal content, the above check will have succeeded.
				//for non.canonical chunks, this method should not be called, but if it is called,
				//we can safely call equalContent() because it is defined
				//terms of the forwards version of getRawffbit().
				|| !(!isCanonical()  && other.isCanonical()) 

				&& equalContent(other) ))
			return -1;
		if(selfOffset >= weight())
			return -1;
		if(selfOffset>=right.weight()) {
			if(left.weight()<other.weight()) {
				return other.getReverseDiffbit(otherOffset, selfOffset-right.weight(), left)^1;
			}else {
				return left.getReverseDiffbit(selfOffset-right.weight(), otherOffset, other);

			}
		}
		long diff ;
		if(weight()<other.weight()) {
			diff = other.getReverseDiffbit(otherOffset, selfOffset, right);
			if(diff>=0)
				return diff^1;
		}else {
			//Give the recursion a chance to discover that the chunks are actually identical
			diff = right.getReverseDiffbit(selfOffset, otherOffset, other);
			if(diff>=0)
				return diff;
		}
		diff = other.getReverseDiffbit(otherOffset+right.weight() - selfOffset, 0, left);
		if(diff>=0) {
			return (diff^1) + ((right.weight() - selfOffset)<<1);
		}
		return -1;
	}

	@Override
	public ChonkerNode<M> getChild(long i) {
		return i==0?left:right;
	}
	@Override
	public long numChildren() {
		return 2;
	}

	@Override
	public String toString() {
		return "("+left.augHash()+"["+level()+":"+phase()+":"+prio()+"]"+right.augHash()+")";
//		return "["+level()+":"+phase()+":"+prio()+"]:"+
//		Rechonker.weights(Rechonker.give(this, layerTag-(3*16), new ArrayList<>()));
	}

	@Override
	public ChonkerTreeZipper<M> zipTo(ChonkerTreeZipper<M> zipperToMe, long bitIndex) {
		zipperToMe = ChonkerNode._zipTo_commonChecks(this, zipperToMe, bitIndex);
		long leftWeight = left.weight();
		if(bitIndex < leftWeight) {
			return new ChonkerTreeZipper<M>(left, zipperToMe, 0).zipTo(bitIndex);
		}else {
			return new ChonkerTreeZipper<M>(right, zipperToMe, 1).zipTo(bitIndex-leftWeight);
		}
	}
	@Override
	public ChonkerNode<M> dropPrefixChildren(long i, ChonkerConfig<M> c) {
		if(i==0)
			return this;
		if(i==1)
			return right;
		throw new IndexOutOfBoundsException();
	}
	@Override
	public ChonkerNode<M> dropSuffixChildren(long i, ChonkerConfig<M> c) {
		if(i==0)
			return this;
		if(i==1)
			return left;
		throw new IndexOutOfBoundsException();
	}
	@Override
	public ChonkerNode<M> substChild(long i, ChonkerNode<M> subst, ChonkerConfig<M> c) {
		if(i==0)
			return c.branch(layerTag, subst, right);
		if(i==1)
			return c.branch(layerTag, left, subst);
		throw new IndexOutOfBoundsException();
	}

	@Override
	public int hashCode() {
		return defaultHashCode();
	}
	@Override
	public boolean equals(Object obj) {
		return defaultEquals(obj);
	}
	@Override
	public M getMonoidData() {
		if(isCanonical())
			monoidData.setContent(this);
		return monoidData;
	}
	boolean canonical;
	public boolean isCanonical() {
		return canonical;
	}
	public void setCanonical(boolean canonical) {
        this.canonical = canonical;
    }

}
