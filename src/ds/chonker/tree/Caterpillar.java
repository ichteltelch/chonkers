package ds.chonker.tree;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

public class Caterpillar<M extends ChonkersMonoidData<M>> implements ChonkerNode<M>{
	final long[] offsets;
	final ChonkerNode<M>[] data;
	final int layerTag;
	M monoidData;

	@SuppressWarnings("unchecked")
	public static <M extends ChonkersMonoidData<M>> ChonkerNode<M> make(ChonkerConfig<M> c, int layerTag, ChonkerNode<M> data, long repetitions) {
		assert data.layerTag()<=layerTag;
		if(repetitions<2)
			throw new IllegalArgumentException("At least two repetitions are required");
		return c.newRleCaterpillar(layerTag, new ChonkerNode[] {c.canonical(data)}, new long[] {repetitions});
	}
	@SuppressWarnings("unchecked")
	public static <M extends ChonkersMonoidData<M>> ChonkerNode<M> make(ChonkerConfig<M> c, int layerTag, ChonkerNode<M> left, ChonkerNode<M> right) {
		assert left.layerTag()<=layerTag;
		assert right.layerTag()<=layerTag;
		assert left.equalContent(right);
		if(left.equalStructure(right))
			return make(c, layerTag, left, 2);
		return c.newRleCaterpillar(layerTag, new ChonkerNode[] {c.canonical(left), c.canonical(right)}, new long[] {1, 2});
	}
	public static <M extends ChonkersMonoidData<M>> ChonkerNode<M> concat(ChonkerConfig<M> c, Caterpillar<M> left, Caterpillar<M> right) {
		assert left.layerTag()==right.layerTag();
		boolean join = left.data[left.data.length-1].equalStructure(right.data[0]);
		if(join) {
			ChonkerNode<M>[] data = Arrays.copyOf(left.data, left.data.length+right.data.length-1);
			System.arraycopy(right.data, 1, data, left.data.length, right.data.length-1);
			long[] offsets = Arrays.copyOf(left.offsets, left.offsets.length+right.offsets.length-1);
			long shift = left.offsets[left.offsets.length-1];
			for(int i = 0; i<right.offsets.length; i++) {
				offsets[i+left.offsets.length-1] = right.offsets[i]+shift;
			}
			return c.newRleCaterpillar(left.layerTag, data, offsets);
		}else {
			ChonkerNode<M>[] data = Arrays.copyOf(left.data, left.data.length+right.data.length);
			System.arraycopy(right.data, 0, data, left.data.length, right.data.length);
			long[] offsets = Arrays.copyOf(left.offsets, left.offsets.length+right.offsets.length);
			long shift = left.offsets[left.offsets.length-1];
			for(int i = 0; i<right.offsets.length; i++) {
				offsets[i+left.offsets.length] = right.offsets[i]+shift;
			}
			return c.newRleCaterpillar(left.layerTag, data, offsets);
		}
	}
	public static <M extends ChonkersMonoidData<M>> ChonkerNode<M> appendSingle(ChonkerConfig<M> c, Caterpillar<M> left, ChonkerNode<M> right) {
		assert left.layerTag()>=right.layerTag();
		boolean join = left.data[left.data.length-1].equalStructure(right);
		if(join) {
			long[] offsets = left.offsets.clone();
			offsets[offsets.length-1] += 1;
			return c.newRleCaterpillar(left.layerTag, left.data, offsets);
		}else {
			long[] offsets = Arrays.copyOf(left.offsets, left.offsets.length+1);
			offsets[offsets.length-1] += offsets[offsets.length-2] + 1;
			ChonkerNode<M>[] data = Arrays.copyOf(left.data, left.data.length+1);
			data[data.length-1] = c.canonical(right);
			return c.newRleCaterpillar(left.layerTag, data, offsets);
		}
	}
	@SuppressWarnings("unchecked")
	public static <M extends ChonkersMonoidData<M>> ChonkerNode<M> prependSingle(ChonkerConfig<M> c, ChonkerNode<M> left, Caterpillar<M> right) {
		assert right.layerTag()>=left.layerTag();
		boolean join = left.equalStructure(right.data[0]);
		if(join) {
			long[] offsets = right.offsets.clone();
			for(int i = 0; i<offsets.length; i++) {
				offsets[i] += 1;
			}
			return c.newRleCaterpillar(right.layerTag, right.data, offsets);
		}else {
			long[] offsets = new long[right.offsets.length+1];
			for(int i = right.offsets.length; i>0; i--) {
				offsets[i] = right.offsets[i-1] + 1;
			}
			offsets[0] = 1;
			ChonkerNode<M>[] data = new ChonkerNode[right.data.length+1];
			data[0] = c.canonical(left);
			System.arraycopy(right.data, 0, data, 1, right.data.length);
			return c.newRleCaterpillar(right.layerTag, data, offsets);
		}
	}
	@SuppressWarnings("unchecked")
	@SafeVarargs
	public static <M extends ChonkersMonoidData<M>> ChonkerNode<M> make(ChonkerConfig<M> c, int layerTag, ChonkerNode<M>... data) {
		assert data.length>2;
		long[] offsetBuffer = new long[Math.min(data.length, 16)];
		ChonkerNode<M>[] dataBuffer = new ChonkerNode[offsetBuffer.length];


		int fill = 1;
		offsetBuffer[0] = 1;
		dataBuffer[0] = c.canonical(data[0]);
		for(int i = 1; i<data.length; i++) {
			if(data[i].equalStructure(dataBuffer[fill-1])) {
				offsetBuffer[fill-1]++;
			}else {
				assert dataBuffer[0].equalContent(data[i]);
				if(fill>=offsetBuffer.length) {
					offsetBuffer = Arrays.copyOf(offsetBuffer, offsetBuffer.length*2);
					dataBuffer = Arrays.copyOf(dataBuffer, dataBuffer.length*2);
				}
				dataBuffer[fill] = c.canonical(data[i]);
				offsetBuffer[fill] = offsetBuffer[fill-1] + 1;
				fill++;
			}
		}
		dataBuffer = Arrays.copyOf(dataBuffer, fill);
		offsetBuffer = Arrays.copyOf(offsetBuffer, fill);
		return c.newRleCaterpillar(layerTag, dataBuffer, offsetBuffer);
	}
	public ChonkerNode<M> appendSinlge(ChonkerConfig<M> c, ChonkerNode<M> other) {
		return appendSingle(c, this, other);
	}
	public ChonkerNode<M> prependSingle(ChonkerConfig<M> c, ChonkerNode<M> other) {
		return prependSingle(c, other, this);
	}

	Caterpillar(ChonkerConfig<M> c, int layerTag, ChonkerNode<M>[] data, long[] offsets) {
		assert offsets.length == data.length;
		assert offsets.length > 0;
		assert offsets[0] > 0;
		for(int i=offsets.length-1; i>0; i--) {
			assert offsets[i] > offsets[i-1];
		}
		for(ChonkerNode<M> n: data)
			assert n == c.canonical(n);
		assert offsets[offsets.length-1] >=2;
		this.offsets=offsets;
		this.data = data;
		this.layerTag = layerTag;
		this.monoidData = c.computeMonoidData(this, data[0], repetitions());
		this.monoidData = c.canonicalMonoidData(this.monoidData);

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
	@Override
	public int layerTag() {
		return layerTag;
	}

	@Override
	public long weight() {
		return monoidData==null?data[0].weight()*repetitions():monoidData.weight();
	}
	public long repetitions() {
		return offsets[offsets.length-1];
	}


	@Override
	public boolean getBit(long bitIndex) {
		if(bitIndex<0 || bitIndex>=weight())
			return false;
		return data[0].getBit(bitIndex%data[0].weight());
	}

	@Override
	public int augHash() {
		return monoidData.augHash();
	}

	@Override
	public long getRawDiffbit(long selfOffset, long otherOffset, ChonkerNode<?> other) {

		ChonkerNode<?> prototype = data[0];
		long dataWeight = prototype.weight();
		long repetitions = repetitions();
		if(selfOffset>= dataWeight*repetitions)
			return -1;
		if(other==this && selfOffset % dataWeight == otherOffset % dataWeight)
			return -1;
		
		

		
		long partial = selfOffset % dataWeight;
		
		if(partial == otherOffset % dataWeight) {
			if(other instanceof Caterpillar) {
				
				Caterpillar<?> otherCaterpillar = (Caterpillar<?>) other;
	            ChonkerNode<?> otherPrototype = otherCaterpillar.data[0];
	            long otherDataWeight = otherPrototype.weight();
	            long otherRepetitions = otherCaterpillar.repetitions();
				if(otherOffset >= otherDataWeight*otherRepetitions)
					return -1;
				if(dataWeight == otherDataWeight) {
					long diffbitPart1 = prototype.getRawDiffbit(partial, partial, otherPrototype);
					if(diffbitPart1>=0)
						return diffbitPart1;
					if(partial!=0) {
						long rep = 1 + selfOffset / dataWeight;
						if(rep>=repetitions)
							return -1;
						long otherRep = 1 + otherOffset / otherDataWeight;
						if(otherRep>=otherRepetitions)
							return -1;
						return prototype.getRawDiffbit(0, 0, otherPrototype);
					}else {
						return -1;
					}
	            }
			}
		}
		
		long rep;
		long originalOffset = otherOffset;
		if(partial!=0) {
			long ret = other.getRawDiffbit(otherOffset, partial, prototype);	
			if(ret>=0)
				return ret^1;
			rep = 1 + selfOffset / dataWeight;
			otherOffset += dataWeight - partial;
		} else {
			rep = selfOffset / dataWeight;
		}
		long otherWeight = other.weight();
		for(; rep<repetitions & otherOffset<otherWeight; ++rep, otherOffset+=dataWeight) {
			long ret = other.getRawDiffbit(otherOffset, 0, prototype);
			if(ret>=0)
				return (ret^1) + ((otherOffset - originalOffset)<<1);
		}

		return -1;
	}

	@Override
	public long getRawDiffbit_leafReverse(long selfOffset, long otherOffset, ChonkerNode<?> other) {

		ChonkerNode<?> prototype = data[0];
		long dataWeight = prototype.weight();
		long repetitions = repetitions();
		if(selfOffset>= dataWeight*repetitions)
			return -1;
		if(other==this && selfOffset % dataWeight == otherOffset % dataWeight)
			return -1;
		
		

		
		long partial = selfOffset % dataWeight;
		
		if(partial == otherOffset % dataWeight) {
			if(other instanceof Caterpillar) {
				
				Caterpillar<?> otherCaterpillar = (Caterpillar<?>) other;
	            ChonkerNode<?> otherPrototype = otherCaterpillar.data[0];
	            long otherDataWeight = otherPrototype.weight();
	            long otherRepetitions = otherCaterpillar.repetitions();
				if(otherOffset >= otherDataWeight*otherRepetitions)
					return -1;
				if(dataWeight == otherDataWeight) {
					long diffbitPart1 = prototype.getRawDiffbit_leafReverse(partial, partial, otherPrototype);
					if(diffbitPart1>=0)
						return diffbitPart1;
					if(partial!=0) {
						long rep = 1 + selfOffset / dataWeight;
						if(rep>=repetitions)
							return -1;
						long otherRep = 1 + otherOffset / otherDataWeight;
						if(otherRep>=otherRepetitions)
							return -1;
						return prototype.getRawDiffbit_leafReverse(0, 0, otherPrototype);
					}else {
						return -1;
					}
	            }
			}
		}
		
		long rep;
		long originalOffset = otherOffset;
		if(partial!=0) {
			long ret = other.getRawDiffbit_leafReverse(otherOffset, partial, prototype);	
			if(ret>=0)
				return ret^1;
			rep = 1 + selfOffset / dataWeight;
			otherOffset += dataWeight - partial;
		} else {
			rep = selfOffset / dataWeight;
		}
		long otherWeight = other.weight();
		for(; rep<repetitions & otherOffset<otherWeight; ++rep, otherOffset+=dataWeight) {
			long ret = other.getRawDiffbit_leafReverse(otherOffset, 0, prototype);
			if(ret>=0)
				return (ret^1) + ((otherOffset - originalOffset)<<1);
		}

		return -1;
	}

	
	@Override
	public long getReverseDiffbit(long selfOffset, long otherOffset, ChonkerNode<?> other) {

		ChonkerNode<?> prototype = data[0];
		long dataWeight = prototype.weight();
		long repetitions = repetitions();
		if(selfOffset>= dataWeight*repetitions)
			return -1;
		if(other==this && selfOffset % dataWeight == otherOffset % dataWeight)
			return -1;
		
		

		
		long partial = selfOffset % dataWeight;
		
		if(partial == otherOffset % dataWeight) {
			if(other instanceof Caterpillar) {
				
				Caterpillar<?> otherCaterpillar = (Caterpillar<?>) other;
	            ChonkerNode<?> otherPrototype = otherCaterpillar.data[0];
	            long otherDataWeight = otherPrototype.weight();
	            long otherRepetitions = otherCaterpillar.repetitions();
				if(otherOffset >= otherDataWeight*otherRepetitions)
					return -1;
				if(dataWeight == otherDataWeight) {
					long diffbitPart1 = prototype.getReverseDiffbit(partial, partial, otherPrototype);
					if(diffbitPart1>=0)
						return diffbitPart1;
					if(partial!=0) {
						long rep = 1 + selfOffset / dataWeight;
						if(rep>=repetitions)
							return -1;
						long otherRep = 1 + otherOffset / otherDataWeight;
						if(otherRep>=otherRepetitions)
							return -1;
						return prototype.getReverseDiffbit(0, 0, otherPrototype);
					}else {
						return -1;
					}
	            }
			}
		}
		
		long rep;
		long originalOffset = otherOffset;
		if(partial!=0) {
			long ret = other.getReverseDiffbit(otherOffset, partial, prototype);	
			if(ret>=0)
				return ret^1;
			rep = 1 + selfOffset / dataWeight;
			otherOffset += dataWeight - partial;
		} else {
			rep = selfOffset / dataWeight;
		}
		long otherWeight = other.weight();
		for(; rep<repetitions & otherOffset<otherWeight; ++rep, otherOffset+=dataWeight) {
			long ret = other.getReverseDiffbit(otherOffset, 0, prototype);
			if(ret>=0)
				return (ret^1) + ((otherOffset - originalOffset)<<1);
		}

		return -1;
	}


	@Override
	public ChonkerNode<M> getChild(long i) {
		if(i<0 || i>=offsets[offsets.length-1])
			throw new IndexOutOfBoundsException();
		int index = Arrays.binarySearch(offsets, i);
		if(index<0)
			index = -(index+1);
		else
			index = index+1;
		assert index>=0;
		assert index<data.length;
		return data[index];
	}
	@Override
	public long numChildren() {
		return repetitions();
	}
	@Override
	public String toString() {
		StringBuilder sb = new StringBuilder();
		sb.append("{")
		.append(level()).append("/")
		.append(phase()).append("/")
		.append(prio())
		.append(":");
		for(int i=0; i<data.length; ++i) {
			if(i>0)
				sb.append(";");
			long count = i==0?offsets[0]:(offsets[i]-offsets[i-1]);
			if(count!=1)
				sb.append(count).append("×");
			sb.append(data[i]);
		}
		sb.append("}");
		return sb.toString();
	}
	@Override
	public ChonkerTreeZipper<M> zipTo(ChonkerTreeZipper<M> zipperToMe, long bitIndex) {
		zipperToMe = ChonkerNode._zipTo_commonChecks(this, zipperToMe, bitIndex);
		long dataWeight = data[0].weight();
		long repetition = bitIndex / dataWeight;
		return new ChonkerTreeZipper<M>(getChild(repetition), zipperToMe, repetition).zipTo(bitIndex%dataWeight);
	}
	@Override
	public ChonkerNode<M> dropPrefixChildren(long i, ChonkerConfig<M> c) {
		long r = repetitions();
		if(i<0 || i>=r)
			throw new IllegalArgumentException("dropPrefixChildren: i "+i+" is out of range");
		if(i==0)
			return this;
		if(i==r-1)
			return data[data.length-1];

		int index = Arrays.binarySearch(offsets, i);
		if(index<0) {
			index = -(index+1) ;
		}else {
			index = index + 1;
		}
		long[] newOffsets = new long[offsets.length-index];
		for(int j=0; j<newOffsets.length; ++j)
			newOffsets[j] = offsets[j+index]-i;
		ChonkerNode<M>[] newData = Arrays.copyOfRange(data, index, data.length);
		ChonkerNode<M> ret = c.newRleCaterpillar(layerTag, newData, newOffsets);
		long numChildren = numChildren();
		for(long j = i; j<numChildren; ++j) {
			assert getChild(j) == ret.getChild(j-i);
		}
		return ret;
	}
	@Override
	public ChonkerNode<M> dropSuffixChildren(long i, ChonkerConfig<M> c) {
		long r = repetitions();
		if(i<0 || i>=r)
			throw new IllegalArgumentException("dropSuffixChildren: i "+i+" is out of range");
		if(i==0)
			return this;
		if(i==r-1)
			return data[0];
		
		int index = Arrays.binarySearch(offsets, r-i);
		ChonkerNode<M> ret;
		if(index >= 0) {
			long[] newOffsets = Arrays.copyOf(offsets, index+1);
			ChonkerNode<M>[] newData = Arrays.copyOf(data, index+1);
			ret = c.newRleCaterpillar(layerTag, newData, newOffsets);
			return ret;
		}else {
			index = -(index+1);
			if(index==offsets.length-1) {
				long[] newOffsets = offsets.clone();
	            newOffsets[index] -= i;
	            ret = c.newRleCaterpillar(layerTag, data, newOffsets);
			}else {
				long[] newOffsets = Arrays.copyOfRange(offsets, 0, index+1);
	            newOffsets[index] = r - i;
                ChonkerNode<M>[] newData = Arrays.copyOf(data, index+1);
                ret = c.newRleCaterpillar(layerTag, newData, newOffsets);
			}
		}
		for(int j=0; j<r-i; ++j)
			assert getChild(j) == ret.getChild(j);
		return ret;
	}
	@Override
	public ChonkerNode<M> substChild(long i, ChonkerNode<M> subst, ChonkerConfig<M> c) {
		long reps = repetitions();
		if(i<0 || i>=reps)
			throw new IllegalArgumentException("substChild: i "+i+" is out of range");
		ChonkerNode<M> child = getChild(i);
		if(subst.equalStructure(child))
			return this;
		if(subst.equalContent(child)) {
			child = c.canonical(child);
			long[] offsetBuffer = new long[offsets.length+2];
			ChonkerNode<M>[] dataBuffer = Arrays.copyOf(data, offsetBuffer.length);
			int fill = 0;
			offsetBuffer[0] = 0;
			dataBuffer[0] = i==0?child:data[0];
			for(int index = 0; index<offsets.length; ++index) {
				long beginningOfRange = index==0?0:offsets[index-1];
				long endOfRange = offsets[index];
				if(endOfRange<=i) {
					offsetBuffer[fill] = endOfRange;
					dataBuffer[fill] = data[index];
					++fill;
				}else if(beginningOfRange==i) {
					if(fill>0 && dataBuffer[fill-1].equalStructure(child)) {
						offsetBuffer[fill-1] ++;
					}else {
						offsetBuffer[fill] = beginningOfRange+1;
						dataBuffer[fill] = child;
						++fill;
					}
					beginningOfRange++;
					if(beginningOfRange<endOfRange) {
						offsetBuffer[fill] = endOfRange;
						dataBuffer[fill] = data[index];
						++fill;
					}
				}else if(i<endOfRange) {
					offsetBuffer[fill] = i;
					dataBuffer[fill] = data[index];
					++fill;
					offsetBuffer[fill] = i+1;
					dataBuffer[fill] = child;
					++fill;
					beginningOfRange = i+i;
					if(beginningOfRange<endOfRange) {
						offsetBuffer[fill] = endOfRange;
						dataBuffer[fill] = data[index];
						++fill;
					}
				}else if(i+1==beginningOfRange && data[index].equalStructure(child)) {
					offsetBuffer[fill-1] += endOfRange - beginningOfRange;
				}else {
					offsetBuffer[fill] = endOfRange;
                    dataBuffer[fill] = data[index];
                    ++fill;
				}
			}
			if(fill!=dataBuffer.length) {
				offsetBuffer = Arrays.copyOf(offsetBuffer, fill);
				dataBuffer = Arrays.copyOf(dataBuffer, fill);
			}
			return c.newRleCaterpillar(layerTag, dataBuffer, offsetBuffer);
		}
		if(i==0)
			return c.branch(layerTag, subst, dropPrefixChildren(1, c));
		if(i==reps-1)
			return c.branch(layerTag, dropSuffixChildren(1, c), subst);
		ChonkerNode<M> l = dropPrefixChildren(reps - i, c);
		ChonkerNode<M> r = dropPrefixChildren(i-1, c);
		return c.branch(layerTag, c.branch(layerTag, l, subst), r);
	}
	@Override
	public boolean equalStructure(ChonkerNode<?> node) {
		
		return equals(node);
	}
	@Override
	public boolean equals(Object o) {
		if (o == this)
			return true;
		if (o == null)
			return false;
		if (o instanceof Caterpillar) {
			Caterpillar<?> a = (Caterpillar<?>) o;
			if(a.layerTag()!=layerTag())
				return false;
			if(!a.getClass().equals(getClass()))
				return false;
			if(a.augHash()!=augHash())
				return false;
			if(!Arrays.equals(a.offsets, offsets))
				return false;
			for(int i=0; i<data.length; ++i) {
				if(data[i]!=a.data[i])
					return false;
			}

			return true;
		}
		
		return super.equals(o);
	}
	@Override
	public Set<ChonkerNode<M>> giveAllNodes(Set<ChonkerNode<M>> out){
		if(out == null)
			out = new HashSet<>();
		else if(out.contains(this))
			return out;
		out.add(this);
		getMonoidData().content().giveAllNodes(out);
		for(ChonkerNode<M> child: data)
			child.giveAllNodes(out);
		return out;
	}
	@Override
	public Set<M> giveAllContents(Set<M> out){
		if(out == null)
			out = new HashSet<>();
		else if(out.contains(getMonoidData()))
			return out;
		out.add(getMonoidData());
//		getMonoidData().content().giveAllContents(out);
		for(ChonkerNode<M> child: data)
			child.giveAllContents(out);
		return out;
	}
	@Override
	public int hashCode() {
		int h = Arrays.hashCode(offsets);
		for(ChonkerNode<?> n: data)
			h=31*h + System.identityHashCode(n);
		return h;
	}
	public ChonkerNode<M> getSegment() {
		return data[0];
	}
}
