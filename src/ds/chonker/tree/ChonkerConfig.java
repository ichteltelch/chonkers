package ds.chonker.tree;

import java.util.Collections;

import ds.RefMap;
import ds.RefMap.WrappedKey;

public abstract class ChonkerConfig<M extends ChonkersMonoidData<M>> {
	//	static final int [] diffbitLengths={
	//			4, //highest order diffbit: Only 6 states are used for the index; one state is for overflow, so 5 bits can be indexed; plus one bit for direction
	//			5, //4 bits indexed by next higher level (giving 15 states + 1 overflow), together with one bit for direction
	//			15, //14 bits indexed by next higher level (giving 16383 states + 1 overflow), together with one bit for direction
	////			16383, //explosion
	//			30, //but we don't actually use all of them
	//		};
	static final int [] diffbitLengths={
			3, 3, 4, 8, 62
	};
	public ChonkerConfig(ChonkersMonoid<M> m) {
		monoid = m;
	}
	final ChonkersMonoid<M> monoid;
	public static ChonkerConfig<ChonkersMonoidData.Minimal> BYTES= new ChonkerConfig<ChonkersMonoidData.Minimal>(ChonkersMonoid.DEFAULT) {


		@Override
		public long absoluteUnit(int layer) {
			return 1+(8L<<((layer)));
			//			return 1+(8L<<((layer*2+5)));
			//			return 1+(8L<<((Math.max(1, 2*(layer-5)))));
			//			return Long.MAX_VALUE/2;
		}
		public int weightBits(int layer) {
			//			return 8+((5*layer));
			return 63;
		}
		@Override
		public int augHashLength(int layer) {
			if(((layer))<=2)
				return 0;
			return 32;
		}
		protected ChonkersMonoidData.Minimal makeMonoidData(
				ChonkersMonoidData.Minimal left, 
				ChonkersMonoidData.Minimal right, 
				long weight, int augHash, ChonkerBranch<ChonkersMonoidData.Minimal> resultContent) {
			return new ChonkersMonoidData.MinimalImpl(weight, resultContent, augHash);
		};

	};

	public static ChonkerConfig<ChonkersMonoidData.Minimal> bytesWithTarget(int targetBytes){
		// 34 17 9 5 3 2 
		// 32 16 8 4 2
		int targetLayer = Integer.bitCount(Integer.highestOneBit(targetBytes-1)-1)+1;
		int[] sizes = new int[targetLayer+1];
		int s = targetBytes;
		for(int i = 0; i<=targetLayer; i++) {
			sizes[i] = 1+s*8;
            s = (s+1)>>1;
		}
		return new ChonkerConfig<ChonkersMonoidData.Minimal>(ChonkersMonoid.DEFAULT) {



			@Override
			public long absoluteUnit(int layer) {
				int relativeLayer = layer - targetLayer;
				if(relativeLayer <= 0) {
					return sizes[Math.min(-relativeLayer, sizes.length-1)];
				}else {
					return 1 + (targetBytes<<(relativeLayer+3));
				}
			}
			public int weightBits(int layer) {
				//			return 8+((5*layer));
				return 63;
			}
			@Override
			public int augHashLength(int layer) {
				if(((layer))<=2)
					return 0;
				return 32;
			}
			protected ChonkersMonoidData.Minimal makeMonoidData(
					ChonkersMonoidData.Minimal left, 
					ChonkersMonoidData.Minimal right, 
					long weight, int augHash, ChonkerBranch<ChonkersMonoidData.Minimal> resultContent) {
				return new ChonkersMonoidData.MinimalImpl(weight, resultContent, augHash);
			};

		};

	}

	public static ChonkerConfig<ChonkersMonoidData.WithUserMonoids> CHARS= new ChonkerConfig<ChonkersMonoidData.WithUserMonoids>(ChonkersMonoid.WITHUSERMONOIDS) {
		@Override
		public long absoluteUnit(int layer) {
			return 1+(32L<<((layer)));
			//			return 1+(8<<((layer*2+5)));
			//			return 1+(8<<((Math.max(1, 2*(layer-5)))));
			//			return Long.MAX_VALUE/2;
		}
		public int weightBits(int layer) {
			//			return 8+((5*layer));
			return 62;
		}
		@Override
		public int augHashLength(int layer) {
			if(((layer))<=2)
				return 0;
			return 32;
		}
		protected ChonkersMonoidData.WithUserMonoids makeMonoidData(
				ChonkersMonoidData.WithUserMonoids left, 
				ChonkersMonoidData.WithUserMonoids right, 
				long weight, int augHash, ChonkerBranch<ChonkersMonoidData.WithUserMonoids> resultContent) {
			return new ChonkersMonoidData.UserMonoidsImpl(weight, resultContent, augHash);
		};
	};

	/**
	 * The absolute unit corresponding to each level.
	 * @return
	 */
	public abstract long absoluteUnit(int layer);

	public int augHashLength(int layer) {
		return 32;
	}
	/**
	 * How many bits are sufficient to represent the length?
	 * This should only depend on the level.
	 * @return
	 */
	public  int weightBits(int level) {
		return 64;
	}
	//	public  boolean isHeftyconk(ChonkerNode node) {
	//		return node.rawBitLength()>=absoluteUnit(node.level());
	//	}
	public long augBitLength(ChonkerNode<M> node) {
		int layer = node.level();
		return augHashLength(layer) + weightBits(layer) + node.weight();
	}





	public  long encodeMergePriority_HashChonking(
			boolean forMegachonker,
			int hashFunctionIndex,
			int hashOrderingIndex
			) 
	{
		if(hashFunctionIndex<0 || hashFunctionIndex < 0x10000) {
			throw new IllegalArgumentException("Invalid hash function index: "+hashFunctionIndex);
		}
		if(hashOrderingIndex<0 || hashOrderingIndex < 0x10000) {
			throw new IllegalArgumentException("Invalid hash ordering index: "+hashOrderingIndex);
		}
		return
				0x8000000000000000L
				| ((hashFunctionIndex+1L)<<32)
				| (hashOrderingIndex+1L)<<8
				| (forMegachonker? 0L : 1L);
	}

	public  int maxDiffbitOrder(int level) {
		return diffbitLengths.length+1;
	}
	public  int diffBitLength(int level, int order) {
		return diffbitLengths[maxDiffbitOrder(level) - order];
	}

	//	public  ChonkerNode repeat(int levelTag, int repetitions, ChonkerNode[] nodes) {
	//		for(ChonkerNode node: nodes)
	//			if(node.levelTag()>levelTag)
	//				throw new IllegalArgumentException("Cannot repeat a node at a higher level than the current level: "+node.levelTag());
	//		if(repetitions==0)
	//			return null;
	//		if(repetitions==1)
	//			return canonical(nodes[0]);
	//
	//		Caterpillar r = new Caterpillar(this, levelTag, repetitions, nodes);
	//		return canonical(r);
	//	}

	RefMap<ChonkerNode<M>, RefMap.WrappedKey<ChonkerNode<M>>, ?> canon=RefMap.forConcurrentHashMap();
	RefMap<M, RefMap.WrappedKey<M>, ?> monoidCanon=RefMap.forConcurrentHashMap();

	public ChonkerNode<M> canonical(ChonkerNode<M> node) {
		if(node==null)
			return null;
		while(true) {
			WrappedKey<ChonkerNode<M>> ref = canon.computeFromWrappedKeyIfAbsent(node, RefMap.id());
			ChonkerNode<M> ret = ref.get();
			if(ret!=null) {
				ret.setCanonical(true);
				return ret;
			}else {
				//				canon.computeFromWrappedKeyIfAbsent(node, RefMap.id());
				//				canon.put(node, new WeakReference<>(node));
			}
		}
	}
	public M canonicalMonoidData(M node) {
		if(node==null)
			return null;
		while(true) {
			WrappedKey<M> ref = monoidCanon.computeFromWrappedKeyIfAbsent(node, RefMap.id());
			M ret = ref.get();
			if(ret!=null) {
				return ret;
			} else {
				//				monoidCanon.computeFromWrappedKeyIfAbsent(node, RefMap.id());
				//				monoidCanon.put(node, new WeakReference<>(node));
			}
		}
	}

	public ChonkerNode<M> branch(int layerTag, ChonkerNode<M> left, ChonkerNode<M> right) {
		left=canonical(left);
		right=canonical(right);

		ChonkerNode<M> c = tryFuse(left, right, layerTag, 0, 0);
		if(c!=null)
			return c;
		ChonkerBranch<M> r = new ChonkerBranch<M>(this, layerTag, left, right);
		return canonical(r);
	}

	public ChonkerNode<M> tryFuse(ChonkerNode<M> left, ChonkerNode<M> right, int layerTag, int omitLeft, int omitRight) {
		if(left.layerTag()>layerTag || right.layerTag()>layerTag)
			return null;
		if(
				left.layerTag() == layerTag && 
				left instanceof Caterpillar) {
			Caterpillar<M> l = (Caterpillar<M>) left;
			if(
					right.layerTag() == layerTag && 
					right instanceof Caterpillar) {
				//				if(right.level()!=level)
				//					return null;
				Caterpillar<M> r = (Caterpillar<M>) right;
				if(l.data[0].equalContent(r.data[0]))
					return Caterpillar.<M>concat(this, l, r)
							.dropPrefixChildren(omitLeft, this).dropSuffixChildren(omitRight, this);
				;
			}
			if(right.equalContent(l.data[0]))
				return  l.appendSinlge(this, right)
						.dropPrefixChildren(omitLeft, this).dropSuffixChildren(omitRight, this);

			//			int tilesCount = l.dataArray[0].tiles(right);
			//			if(tilesCount>0) {
			//				ChonkerNode l2 = repeat(level, 0, repetitions-omitLeft, l.data, true);
			//				return branch(level, 0, l2, right, true);
			//			}
		}else {
			if(
					right.layerTag() == layerTag && 
					right instanceof Caterpillar) {

				Caterpillar<M> r = (Caterpillar<M>) right;
				if(left.equalContent(r.data[0]))
					return r.prependSingle(this, left)							
							.dropPrefixChildren(omitLeft, this).dropSuffixChildren(omitRight, this);

			}else {
			}
		}
		if(left.equalContent(right)) {
			return Caterpillar.make(this, layerTag, left, right)
					.dropPrefixChildren(omitLeft, this).dropSuffixChildren(omitRight, this);

		}

		return null;

	}

	ChonkerNode<M> newRleCaterpillar(int layerTag, ChonkerNode<M>[] dataArray, long[] offsets) {
		return canonical(new Caterpillar<>(this, layerTag, dataArray, offsets));
	}

	public M computeMonoidData(ChonkerBranch<M> resultContent, ChonkerNode<M> left, ChonkerNode<M> right) {
		return monoid.combine(left.getMonoidData(), right.getMonoidData(), resultContent);
	}
	public M computeMonoidData(ChonkerNode<M> resultContent, ChonkerNode<M> period, long exponent) {
		return monoid.power(period.getMonoidData(), exponent, resultContent);
	}

	abstract protected M makeMonoidData(M left, M right, long weight, int augHash, ChonkerBranch<M> resultContent);


	public ChonkerNode<M> computeRepeats(ChonkerNode<M> node, long repetitions) {
		if(repetitions==0)
			return null;
		if(repetitions==1)
			return node;
		if(repetitions<0)
			throw new IllegalArgumentException("Cannot compute repeats for a negative number of repetitions: "+repetitions);
		if(node==null)
			return null;
		ChonkerNode<M> square = node;
		ChonkerNode<M> result = null;
		for(long pow = repetitions; pow>0; pow >>>= 1) {
			if((pow & 1)== 1) {
				result = computeConcat(square, result);
			}
			square = computeConcat(square, square);
		}

		return result;
	}

	public ChonkerNode<M> computeConcat(ChonkerNode<M> left, ChonkerNode<M> right){
		if(left == null)
			return right;
		if(right == null)
			return left;
		return new Rechonker<>(this, 
				new ChonkerTreeZipper<>(left).rightMost(0), 
				Collections.emptyList(), 
				new ChonkerTreeZipper<>(right).leftMost(0)
				)
				.run();

	}


}
