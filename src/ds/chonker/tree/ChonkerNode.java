package ds.chonker.tree;

import java.util.HashSet;
import java.util.Set;

public interface ChonkerNode<M extends ChonkersMonoidData<M>> {
	public static final boolean ASSUME_CHILDREN_CANONICAL = true;
	public static final int NO_MERGE_PRIORITY = 100;
	boolean isCanonical();
	void setCanonical(boolean canonical);
	abstract public int layerTag();
	default public int level() {
		return decodeLevel(layerTag());
	}
	default public int phase() {
		return decodePhase(layerTag());
	}
	default public int prio() {
		return decodePrio(layerTag());
	}

	abstract public long weight();

	default public int prioAtLevel(int atLevel, int atPhase) {
		return atLevel==level()&&atPhase==phase()?prio():NO_MERGE_PRIORITY;
	}



	abstract public boolean getBit(long bitIndex);
	default public boolean getAugBit(ChonkerConfig<M> c, int layer, long bitIndex) {
		if(bitIndex < 0)
			throw new IndexOutOfBoundsException("bitIndex < 0: " + bitIndex);
		if(bitIndex<c.augHashLength(layer))
			return getAugHashBit((int)bitIndex);
		bitIndex -= c.augHashLength(layer);
		if(bitIndex<c.weightBits(layer))
			return getBitLengthBit((int)bitIndex);
		bitIndex -= c.weightBits(layer);
		return getBit(bitIndex);
	}
	default boolean getBitLengthBit(int bitIndex) {
		if(bitIndex < 0)
			throw new IndexOutOfBoundsException("bitIndex < 0: " + bitIndex);
		return (weight()&(1<<bitIndex))!=0;
	}
	abstract public int augHash();
	default public boolean getAugHashBit(int bitIndex) {
		if(bitIndex < 0)
			throw new IndexOutOfBoundsException("bitIndex < 0: " + bitIndex);
		return (augHash()&(1<<bitIndex))!=0;
	}

	default public long getDiffbit(ChonkerConfig<M> c, ChonkerNode<M> other, int level) {
		int augHashLength = c.augHashLength(level);
		long idiff;
		long diff;
		if(augHashLength!=0) {
			int augHash1 = augHash();
			int augHash2 = other.augHash();
			idiff = (augHash1 ^ augHash2)  & ((1L<<augHashLength)-1);
			if(idiff!=0) {
				int index = Long.bitCount(Long.lowestOneBit(idiff)-1);
				boolean up = (augHash1 & (1L<<index)) == 0;
				return (index<<1) | (up?1:0);
			}
		}
		long bitLength1 = weight();
		long bitLength2 = other.weight();
		diff = (bitLength1 ^ bitLength2);
		if(diff!=0) {
			int index = Long.bitCount(Long.lowestOneBit(diff)-1);
			boolean up = (bitLength1 & (1<<index)) == 0;
			return ((augHashLength + index)<<1) | (up?1:0);
		}

		long  raw = getRawDiffbit(0, 0, other);
		if(raw<0) {
			throw new IllegalArgumentException("getDiffBit must not be called on equal chunks");
		}
		if(augHashLength>0)
			System.out.println("collision");
		int weightBits = c.weightBits(level);
		return ((augHashLength + weightBits)<<1) + raw;
	}

	/**
	 * Find the first index where the raw bit sequences differ, or a negative number
	 * if they are identical, or one chunk ends before a difference is found.
	 * This compares only up to as many bits as are contained in both chunks.
	 * @param selfOffset begin considering the bits of {@code this} here
	 * @param otherOffset begin considering the bits of {@code other} here
	 * @param other
	 * @return The index, relative to the offsets
	 */
	abstract long getRawDiffbit(long selfOffset, long otherOffset, ChonkerNode<?> other);
	abstract long getReverseDiffbit(long selfOffset, long otherOffset, ChonkerNode<?> other);

	abstract ChonkerNode<M> getChild(long i);
	abstract long numChildren();
	abstract ChonkerTreeZipper<M> zipTo(ChonkerTreeZipper<M> zipperToMe, long bitIndex);
	static <M extends ChonkersMonoidData<M>> ChonkerTreeZipper<M> _zipTo_commonChecks(
			ChonkerNode<M> self, ChonkerTreeZipper<M> zipperToMe, long bitIndex) {
		if(zipperToMe==null)
			zipperToMe = new ChonkerTreeZipper<M>(self);
		else if(self!=zipperToMe.node)
			throw new IllegalArgumentException("zipperToMe must be the same node as this");
		if(bitIndex<0 || bitIndex>=self.weight())
			throw new IndexOutOfBoundsException("bitIndex out of range");
		assert zipperToMe.node==self;
		return zipperToMe;
	}
	default public boolean equalContent(ChonkerNode<?> node) {
		if(this==node)
			return true;
		if(getMonoidData() == node.getMonoidData())
			return true;
//		if(isCanonical() && node.isCanonical())
//			return false;
		return weight()==node.weight() && augHash() == node.augHash() && getRawDiffbit(0, 0, node)<0;
	}
	default public boolean equalStructure(ChonkerNode<?> node) {
		if(node==this)
			return true;
		if(node.layerTag()!=layerTag())
			return false;
		if(node.numChildren()!=numChildren())
			return false;
		if(augHash()!=node.augHash())
			return false;
		if(numChildren()==0)
			return equalContent(node);
		for(int i=0; i<numChildren(); ++i)
			if(ASSUME_CHILDREN_CANONICAL) {
				if(getChild(i)!=node.getChild(i))
					return false;
			} else {
				if(!getChild(i).equalStructure(node.getChild(i)))
					return false;
			}
		assert toString().equals(node.toString());
		
		return true;
	}
	default public long tiles(ChonkerNode<M> bigger) {
		long tileCount = bigger.weight() / weight();
		long rem = bigger.weight() % weight();
		if(rem!=0) {
			return -1;
		}
		if(bigger instanceof Caterpillar) {
			Caterpillar<M> o = (Caterpillar<M>) bigger;
			if(o.data[0].tiles(this)<0)
				return -1;
		}
		for(int i=0; i<tileCount; ++i) {
			if(getRawDiffbit(0, weight()*i, bigger)>=0)
				return -1;
		}
		return tileCount;
	}
	default public boolean isConcat(ChonkerNode<M> l, ChonkerNode<M> r) {
		long lw = l.weight();
		return weight() == lw + r.weight()
		&& getRawDiffbit(0, 0, l) < 0
		&& getRawDiffbit(lw, 0, r) < 0;
	}
	default public int defaultHashCode() {
		int ret = Long.hashCode(numChildren());
		if(ret==0)
			return augHash();
		//The caterpillar case should be handled by the overidden method in the caterpillar class
		assert 2==numChildren();
		for(int i=0; i<numChildren(); ++i) {
			ret = 31 * ret + System.identityHashCode(getChild(i));
		}
		return ret;
	}
	M getMonoidData();
	default public boolean defaultEquals(Object o) {
		if (o == this)
			return true;
		if (o == null)
			return false;
		if (o instanceof ChonkerNode) {
			if(!o.getClass().equals(getClass()))
				return false;
			ChonkerNode<?> a = (ChonkerNode<?>) o;
			if(a.numChildren()!=numChildren())
				return false;
			//The caterpillar case should be handled by the overidden method in the caterpillar class
			assert numChildren()<=2;
			if(a.layerTag()!=layerTag())
				return false;
			if(!a.getClass().equals(getClass()))
				return false;
			if(a.augHash()!=augHash())
				return false;
			for(int i=0; i<numChildren(); ++i) {
				if(getChild(i)!=a.getChild(i))
					return false;
			}
			if(numChildren()==0)
				return equalContent(a);
			return true;
		}
		return false;
	}
	abstract ChonkerNode<M> dropPrefixChildren(long i, ChonkerConfig<M> c);
	abstract ChonkerNode<M> dropSuffixChildren(long i, ChonkerConfig<M> c);
	abstract ChonkerNode<M> substChild(long i, ChonkerNode<M> subst, ChonkerConfig<M> c);
	public static int encodeLayerTag(int level, int phase, int prio) {
		int pp;

		switch(phase) {
		case 0: 
			assert prio>=0;
			assert prio<2;
			pp = prio; 
			break;
		case 1: 
			assert prio==0;
			pp = 4; 
			break;
		case 2: 
			assert prio>=0;
			assert prio<6;
			pp = 5+prio; 
			break;
		default:
			throw new IllegalArgumentException("Invalid phase");
		}
		return (level<<4) | pp;
	}
	public static int decodeLevel(int layerTag) {
		return layerTag>>>4;
	}
	//	public static int decodePass(int levelTag) {
	//		int pp = levelTag & 7;
	//		int layer = levelTag>>>3;
	//        if(pp==0)
	//            return 3*layer;
	//        if(pp==1)
	//            return 3*layer + 1;
	//        if(pp>=2 && pp<8)
	//            return 3*layer + 2;
	//        throw new IllegalArgumentException("Invalid phase");
	//	}
	public static int decodePhase(int layerTag) {
		int pp = layerTag & 15;
		if(pp>=0 && pp<2) {
			return 0;
		}
		if(pp==4)
			return 1;
		if(pp>=5 && pp<11)
			return 2;
		throw new IllegalArgumentException("Invalid phase");
	}
	public static int decodePrio(int layerTag) {
		int pp = layerTag & 15;
		if(pp>=0 && pp<2)
			return pp;
		if(pp==4)
			return 0;
		if(pp>=5 && pp<11)
			return pp-5;
		throw new IllegalArgumentException("Invalid phase");
	}
	default public Set<ChonkerNode<M>> giveAllNodes(Set<ChonkerNode<M>> out){
		if(out == null)
			out = new HashSet<>();
		else if(out.contains(this))
			return out;
		out.add(this);
		getMonoidData().content().giveAllNodes(out);
		for(int i=0; i<numChildren(); ++i)
			getChild(i).giveAllNodes(out);
		return out;
	}
	default public Set<M> giveAllContents(Set<M> out){
		if(out == null)
			out = new HashSet<>();
		else if(out.contains(getMonoidData()))
			return out;
		out.add(getMonoidData());
//		getMonoidData().content().giveAllContents(out);
		for(int i=0; i<numChildren(); ++i)
			getChild(i).giveAllContents(out);
		return out;
	}
	default public int getStructureNodeCount() {
		return giveAllNodes(null).size();
	}
	default public int getContentNodeCount() {
		return giveAllContents(null).size();
	}
	default public int getSharedStructureNodeCount(ChonkerNode<M> other) {
		Set<ChonkerNode<M>> set = giveAllNodes(null);
		int myCount = set.size();
		int bothCount = other.giveAllNodes(set).size();
		set.clear();
		int otherCount = other.giveAllNodes(set).size();
		return myCount - bothCount + otherCount;
	}
	default public int getSharedContentNodeCount(ChonkerNode<M> other) {
		Set<M> set = giveAllContents(null);
		int myCount = set.size();
		int bothCount = other.giveAllContents(set).size();
		set.clear();
		int otherCount = other.giveAllContents(set).size();
		return myCount - bothCount + otherCount;
	}

}
